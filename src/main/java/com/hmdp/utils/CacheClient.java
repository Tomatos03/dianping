package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/22 10:05
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public <T> void set(T obj, String key, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(obj),
                time,
                timeUnit
        );
    }

    public <T> void setWithLogicalExpire(T obj, String key, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(obj);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(redisData)
        );
    }


    public <T> boolean tryLock(T id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                id.toString(),
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        );
        return BooleanUtil.isTrue(ok);
    }

    public <T> void unlock(T id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        stringRedisTemplate.delete(lockKey);
    }

    private <ID, R> void rebuildShopCache(String prefix_key, ID id, Long time,
                                          Function<ID, R> dbFallBack) throws InterruptedException {
        String key = prefix_key + id;
        RedisData redisData = new RedisData();
        redisData.setData(dbFallBack.apply(id));
        Thread.sleep(200L);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        stringRedisTemplate.opsForValue()
                           .set(key, JSONUtil.toJsonStr(redisData));
    }

    public <ID, R> R queryWithLogicalExpire(String prefix_key, ID id, Class<R> clazz,
                                            Function<ID, R> dbFallback) {
        String key = prefix_key + id;
        String jsonStr = stringRedisTemplate.opsForValue()
                                             .get(key);
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        JSONObject data = JSONUtil.toBean((JSONObject) redisData.getData(), JSONObject.class);
        LocalDateTime empireTime = redisData.getExpireTime();

        if (empireTime.isAfter(LocalDateTime.now())) {
            return JSONUtil.toBean(data, clazz);
        }

        if (tryLock(id)) {
            jsonStr = stringRedisTemplate.opsForValue()
                                             .get(key);
            if (!StringUtils.isBlank(jsonStr)) {
                redisData = JSONUtil.toBean(jsonStr, RedisData.class);
                data = JSONUtil.toBean((JSONObject) redisData.getData(), JSONObject.class);
                empireTime = redisData.getExpireTime();
                if (empireTime.isAfter(LocalDateTime.now())) {
                    return JSONUtil.toBean(data, clazz);
                }
            }

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    rebuildShopCache(prefix_key, id, 30L, dbFallback);
                } catch (InterruptedException e) {
                    throw new RuntimeException("<UNK>", e);
                } finally {
                    unlock(id);
                }
            });
        }
        return JSONUtil.toBean(data, clazz);
    }

    public <ID, R> R queryWithPassThrough(String prefixKey, ID id, Class<R> clazz,
                                      Function<ID, R> dbFallback) {
        // 尝试从 Redis 中获取缓存数据
        String key = prefixKey + id;
        String jsonShop = stringRedisTemplate.opsForValue()
                                             .get(key);

        // 如果缓存中存在数据，则直接返回
        if (!StrUtil.isBlank(jsonShop)) {
            return JSONUtil.toBean(jsonShop, clazz);
        } else if (jsonShop != null) {
            return null;
        }

        R res = dbFallback.apply(id);
        // 如果缓存中不存在数据，则查询数据库
        if (Objects.isNull(res)) {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.CACHE_SHOP_KEY + id,
                    "",
                    RedisConstants.CACHE_NULL_TTL,
                    TimeUnit.MINUTES
            );
            return null;
        }

        // 将查询到的数据存入缓存
        this.set(res,  key, 30L, TimeUnit.SECONDS);
        return res;
    }
}
