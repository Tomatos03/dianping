package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/26 13:37
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient () {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://127.0.0.1:6379")
              .setPassword("zjlljz");
        return Redisson.create(config);
    }
}
