package com.grupomariposa.orderworker.infrastructure.config;

import com.grupomariposa.orderworker.application.port.out.DistributedLock;
import com.grupomariposa.orderworker.application.port.out.FailedMessageStore;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.redis.RedisFailedMessageStore;
import com.grupomariposa.orderworker.infrastructure.adapter.outbound.redis.RedissonDistributedLock;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the Redis adapters (distributed lock + failed-message store).
 */
@Configuration
@EnableConfigurationProperties(RedissonProperties.class)
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(RedissonProperties props) {
        Config config = new Config();
        config.useSingleServer().setAddress(props.url());
        return Redisson.create(config);
    }

    @Bean
    DistributedLock distributedLock(RedissonClient client, RedissonProperties props) {
        return new RedissonDistributedLock(client, props.lockWaitTime(), props.lockLeaseTime());
    }

    @Bean
    FailedMessageStore failedMessageStore(RedissonClient client, RedissonProperties props) {
        return new RedisFailedMessageStore(client.reactive(), props.failedTtl());
    }
}
