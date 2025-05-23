package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    public boolean tryLock(Long id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                id.toString(),
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        );
        return BooleanUtil.isTrue(ok);
    }

    public void unlock(Long id) {
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        stringRedisTemplate.delete(lockKey);
    }

    public Shop queryWithMutex(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue()
                                             .get(shopKey);

        if (StringUtils.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        } else if (shopJson != null) {
            return null;
        }

        Shop shop = null;
        try {
            if (!tryLock(id)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            shopJson = stringRedisTemplate.opsForValue()
                                          .get(shopKey);
            if (StringUtils.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            } else if (shopJson != null) {
                return null;
            }

            shop = getById(id);
            if (Objects.isNull(shop)) {
                stringRedisTemplate.opsForValue().set(
                        shopKey,
                        "",
                        RedisConstants.CACHE_NULL_TTL,
                        TimeUnit.MINUTES
                );
                return null;
            }
            stringRedisTemplate.opsForValue().set(
                    shopKey,
                    JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL,
                    TimeUnit.MINUTES
            );
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unlock(id);
        }
        return shop;
    }

    @Override
    public Result queryById(Long id) {
//        Shop shop = queryWithMutex(id);
        Shop shop = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById
        );
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                RedisConstants.CACHE_SHOP_KEY,
//                id,
//                Shop.class,
//                this::getById
//        );
        if (shop == null) {
            return Result.fail("no exist");
        }
        return Result.ok(shop);
    }

    @Override
    public boolean update(Shop shop) {
        Long id = shop.getId();
        if (Objects.isNull(id)) {
            return false;
        }
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);

        updateById(shop);

        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        return true;
    }
}
