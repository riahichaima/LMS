package com.course.lms.cluster;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Mode « plusieurs backends » : actif seulement avec APP_CLUSTER_ENABLED=true (Redis requis). */
@Configuration
@ConditionalOnProperty(name = "app.cluster.enabled", havingValue = "true")
public class ClusterConfig {

    @Bean
    public RedisMessageListenerContainer clusterListeners(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

    @Bean
    public ClusterBus clusterBus(StringRedisTemplate redis, RedisMessageListenerContainer clusterListeners) {
        return new ClusterBus(redis, clusterListeners);
    }
}
