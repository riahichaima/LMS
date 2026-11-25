package com.course.lms.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Canal partagé entre plusieurs backends placés derrière un répartiteur de charge (Redis).
 * N'existe que si {@code app.cluster.enabled=true} : avec un seul backend, aucun composant ne l'utilise
 * et le comportement reste exactement celui d'avant.
 * <ul>
 *   <li>{@link #publish} / {@link #subscribe} : messages reçus par <b>tous</b> les backends, y compris l'émetteur
 *       (vidage du cache, notifications temps réel).</li>
 *   <li>{@link #redis()} : données partagées (limites IA, nombre de participants d'une visio).</li>
 * </ul>
 */
public class ClusterBus {

    private static final Logger log = LoggerFactory.getLogger(ClusterBus.class);

    /** Le catalogue a changé : chaque backend vide son cache local. */
    public static final String CACHE_CHANNEL = "lms:cache";
    /** Notification WebSocket à remettre au backend qui tient la connexion de l'utilisateur. */
    public static final String PUSH_CHANNEL = "lms:push";

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listeners;

    public ClusterBus(StringRedisTemplate redis, RedisMessageListenerContainer listeners) {
        this.redis = redis;
        this.listeners = listeners;
    }

    public void publish(String channel, String message) {
        try {
            redis.convertAndSend(channel, message);
        } catch (RuntimeException e) {
            // Redis indisponible : le backend continue de servir ; seul l'autre backend n'est pas prévenu
            log.warn("Message {} non diffusé aux autres backends : {}", channel, e.getMessage());
        }
    }

    public void subscribe(String channel, Consumer<String> handler) {
        listeners.addMessageListener((message, pattern) -> {
            try {
                handler.accept(new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                log.warn("Message {} ignoré : {}", channel, e.getMessage());
            }
        }, new ChannelTopic(channel));
    }

    public StringRedisTemplate redis() {
        return redis;
    }
}
