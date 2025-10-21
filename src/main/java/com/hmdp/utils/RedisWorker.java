package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/22 13:48
 */
@Component
public class RedisWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final int BASE_COUNT = 32;

    private final long BEGIN_TIMESTAMP =
            LocalDateTime.of(2022, 5, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);

    public RedisWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String prefixKey) {
        // 1. 获取当前时间戳
//        long timeMillis = System.currentTimeMillis() - BEGIN_TIMESTAMP;
        long timeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 获取当前时间并格式化
        String nowDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String key = String.format("incr:%s:%s", prefixKey, nowDate);

        Long count = stringRedisTemplate.opsForValue()
                                        .increment(key);
        return timeStamp << BASE_COUNT | count;
    }
}
