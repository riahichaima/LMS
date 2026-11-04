package com.course.lms.config;

import com.course.lms.security.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration WebSocket (STOMP) pour la messagerie et les notifications en temps réel.
 *
 * - /ws : point d'entrée (SockJS, ou WebSocket natif sur /ws/websocket)
 * - Authentification : en-tête STOMP « Authorization: Bearer <jwt> » à la connexion
 * - Le serveur pousse vers les files privées de l'utilisateur (identifié par son email) :
 *     /user/queue/messages       → nouveaux messages
 *     /user/queue/notifications  → nouvelles notifications
 *     /user/queue/read           → accusés de lecture
 * - Les messages sont envoyés par l'API REST (POST /api/messages) : contrôles d'accès centralisés.
 * - Exception : la signalisation des réunions en visio (/app/meeting.*, file /user/queue/meeting).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;

                StompCommand command = accessor.getCommand();
                if (command == StompCommand.CONNECT) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (command == StompCommand.SUBSCRIBE) {
                    // Uniquement ses propres files privées : pas d'écoute des files ou sujets des autres
                    String destination = accessor.getDestination();
                    if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessagingException("Abonnement refusé : " + destination);
                    }
                } else if (command == StompCommand.SEND) {
                    // Seule la signalisation des réunions passe par le WebSocket ; les autres envois utilisent l'API REST
                    String destination = accessor.getDestination();
                    if (accessor.getUser() == null || destination == null || !destination.startsWith("/app/meeting.")) {
                        throw new MessagingException("Envoyez les messages via l'API REST /api/messages");
                    }
                }
                return message;
            }
        });
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Jeton JWT manquant");
        }
        String token = header.substring(7);
        try {
            String email = jwtUtil.extractUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, user)) throw new MessagingException("Jeton JWT invalide");
            // Le nom du « principal » est l'email : convertAndSendToUser(email, ...) cible cet utilisateur
            return new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities());
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("Jeton JWT invalide ou expiré");
        }
    }
}
