package com.course.lms.cluster;

import com.course.lms.service.AiRateLimiter;
import com.course.lms.service.CourseCatalogCache;
import com.course.lms.service.MeetingRoomService;
import com.course.lms.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** APP_CLUSTER_ENABLED=true : le canal Redis existe et chaque composant partagé y est relié. */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
@SpringBootTest(properties = {"app.cluster.enabled=true", "spring.data.redis.host=${REDIS_TEST_HOST}"})
class ClusterContextIntegrationTest {

    @Autowired ClusterBus bus;
    @Autowired CourseCatalogCache cache;
    @Autowired NotificationService notifications;
    @Autowired AiRateLimiter aiLimiter;
    @Autowired MeetingRoomService rooms;
    @Autowired JobLock jobLock;

    @Test
    void everySharedComponentUsesRedis() {
        assertThat(bus.redis().getConnectionFactory().getConnection().ping()).isEqualTo("PONG");
        assertThat(ReflectionTestUtils.getField(cache, "bus")).isSameAs(bus);
        assertThat(ReflectionTestUtils.getField(notifications, "bus")).isSameAs(bus);
        assertThat(ReflectionTestUtils.getField(aiLimiter, "bus")).isSameAs(bus);
        assertThat(ReflectionTestUtils.getField(rooms, "bus")).isSameAs(bus);
        assertThat(jobLock.instance()).isNotBlank();
    }
}
