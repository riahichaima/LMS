package com.course.lms.service;

import com.course.lms.cluster.ClusterBus;
import com.course.lms.dto.CourseResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Deux « backends » simulés dans le même processus, reliés par un vrai Redis : ce que l'un fait, l'autre le voit.
 * Lancé quand REDIS_TEST_HOST est défini (CI : service Redis ; en local : docker run -p 6379:6379 redis).
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class ClusterRedisIntegrationTest {

    static LettuceConnectionFactory factory;
    static RedisMessageListenerContainer listeners;
    static ClusterBus bus;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(System.getenv("REDIS_TEST_HOST"), 6379);
        factory.afterPropertiesSet();
        factory.start();
        listeners = new RedisMessageListenerContainer();
        listeners.setConnectionFactory(factory);
        listeners.afterPropertiesSet();
        listeners.start();
        bus = new ClusterBus(new StringRedisTemplate(factory), listeners);
    }

    @AfterAll
    static void close() throws Exception {
        listeners.stop();
        listeners.destroy();
        factory.destroy();
    }

    @Test
    void theAiLimitIsSharedByBothBackends() {
        AiRateLimiter backend1 = new AiRateLimiter(3, 10, 30, Clock.systemUTC());
        AiRateLimiter backend2 = new AiRateLimiter(3, 10, 30, Clock.systemUTC());
        backend1.setClusterBus(bus);
        backend2.setClusterBus(bus);
        String user = "ia-" + UUID.randomUUID() + "@test.lms";

        backend1.check(user, AiRateLimiter.Usage.CHAT);
        backend2.check(user, AiRateLimiter.Usage.CHAT);
        backend1.check(user, AiRateLimiter.Usage.CHAT);
        // 4e question, quel que soit le backend : refusée (en mémoire, chacun en aurait accepté 3)
        assertThatThrownBy(() -> backend2.check(user, AiRateLimiter.Usage.CHAT))
                .isInstanceOf(AiRateLimiter.AiRateLimitException.class)
                .hasMessageContaining("Limite atteinte : 3");
        // Autre utilisateur : non concerné
        backend2.check("autre-" + user, AiRateLimiter.Usage.CHAT);
    }

    @Test
    void aCatalogueChangeOnOneBackendEmptiesTheOtherCache() throws Exception {
        CourseCatalogCache backend1 = new CourseCatalogCache(new ConcurrentMapCacheManager(CourseCatalogCache.NAME));
        CourseCatalogCache backend2 = new CourseCatalogCache(new ConcurrentMapCacheManager(CourseCatalogCache.NAME));
        backend1.setClusterBus(bus);
        backend2.setClusterBus(bus);
        Thread.sleep(300); // abonnements Redis en place
        AtomicInteger loads = new AtomicInteger();
        CourseResponse course = new CourseResponse();

        backend2.course(1L, () -> { loads.incrementAndGet(); return course; });
        backend2.course(1L, () -> { loads.incrementAndGet(); return course; });
        assertThat(loads).hasValue(1); // gardé en cache sur le backend 2

        backend1.clear(); // le cours est modifié sur le backend 1
        long until = System.currentTimeMillis() + 3000;
        while (loads.get() == 1 && System.currentTimeMillis() < until) {
            backend2.course(1L, () -> { loads.incrementAndGet(); return course; });
            Thread.sleep(50);
        }
        assertThat(loads).hasValue(2); // le backend 2 a relu la base
    }

    @Test
    void aNotificationReachesTheBackendThatHoldsTheConnection() throws Exception {
        NotificationService backend1 = new NotificationService();
        NotificationService backend2 = new NotificationService();
        SimpMessagingTemplate ws1 = mock(SimpMessagingTemplate.class);
        SimpMessagingTemplate ws2 = mock(SimpMessagingTemplate.class);
        ReflectionTestUtils.setField(backend1, "messagingTemplate", ws1);
        ReflectionTestUtils.setField(backend2, "messagingTemplate", ws2);
        backend1.setClusterBus(bus);
        backend2.setClusterBus(bus);
        Thread.sleep(300);
        String email = "push-" + UUID.randomUUID() + "@test.lms";

        // Événement traité par le backend 1 ; l'utilisateur peut être connecté à l'un ou l'autre
        backend1.pushToUser(email, "/queue/notifications", Map.of("title", "Nouveau message", "id", 7));

        verify(ws2, timeout(3000)).convertAndSendToUser(eq(email), eq("/queue/notifications"),
                eq(Map.of("title", "Nouveau message", "id", 7)));
        verify(ws1, timeout(3000)).convertAndSendToUser(eq(email), eq("/queue/notifications"),
                eq(Map.of("title", "Nouveau message", "id", 7)));
    }

    @Test
    void theNumberOfParticipantsIsVisibleFromTheOtherBackend() {
        MeetingRoomService wsBackend = new MeetingRoomService();
        MeetingRoomService otherBackend = new MeetingRoomService();
        wsBackend.setClusterBus(bus);
        otherBackend.setClusterBus(bus);
        long meeting = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);

        wsBackend.join(meeting, 1L, "a@test.lms", "A", "TEACHER", null, "s1");
        wsBackend.join(meeting, 2L, "b@test.lms", "B", "STUDENT", null, "s2");
        assertThat(otherBackend.count(meeting)).isEqualTo(2);

        wsBackend.removeSession("s2");
        assertThat(otherBackend.count(meeting)).isEqualTo(1);
        wsBackend.leave(meeting, 1L);
        assertThat(otherBackend.count(meeting)).isZero();
    }
}
