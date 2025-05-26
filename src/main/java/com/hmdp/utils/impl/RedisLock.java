package com.hmdp.utils.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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

    private static final DefaultRedisScript<Long> DEFAULT_SCRIPT;

    static {
        DEFAULT_SCRIPT = new DefaultRedisScript<>();
        DEFAULT_SCRIPT.setResultType(Long.class);
        DEFAULT_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }

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
        // 使用 Lua 脚本来保证解锁的原子性
        stringRedisTemplate.execute(
                DEFAULT_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getName()
        );
    }
}
