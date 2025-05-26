package com.hmdp.utils.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/23 14:44
 */
public class RedisLock implements ILock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    private final String LOCK_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public RedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock() {
        String threadName = Thread.currentThread().getName();
        Boolean isSuccess = stringRedisTemplate.opsForValue()
                                       .setIfAbsent(
                                               LOCK_PREFIX + name,
                                               ID_PREFIX + threadName,
                                               30,
                                               TimeUnit.MINUTES
                                       );
        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void unlock() {
        String lockId = stringRedisTemplate.opsForValue()
                                      .get(LOCK_PREFIX + name);
        String currentLockId = ID_PREFIX + Thread.currentThread().getName();
        if (!currentLockId.equals(lockId)) {
            return;
        }
        stringRedisTemplate.delete(LOCK_PREFIX + name);
    }
}
