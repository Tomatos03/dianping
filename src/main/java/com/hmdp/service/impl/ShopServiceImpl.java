package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.*;
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
            return Result.error("no exist");
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

    @Override
    public List<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            return this.query()
                       .eq("type_id", typeId)
                       .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE))
                       .getRecords();
        }
        String geoKey = RedisConstants.SHOP_GEO_KEY + typeId;
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> searchResult =
                stringRedisTemplate.opsForGeo()
                                   .search(
                                           geoKey,
                                           GeoReference.fromCoordinate(x, y),
                                           new Distance(5000),
                                           RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                                                                .includeDistance()
                                                                                .limit(end)
                                   );
        if (searchResult == null) {
            return Collections.emptyList();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> locations = searchResult.getContent();
        if (locations.isEmpty()) {
            return Collections.emptyList();
        }
        int size = locations.size();
        if (size < start) {
            return Collections.emptyList();
        }
        List<Long> shopIds = new ArrayList<>(size);
        Map<Long, Double> shopDistances = new HashMap<>();
        locations.stream()
                 .skip(start)
                 .forEach((locationGeoResult) -> {
                     Long shopId = Long.valueOf(locationGeoResult.getContent().getName());
                     shopIds.add(shopId);

                     Double distance = locationGeoResult.getDistance().getValue();
                     shopDistances.put(shopId, distance);
                 });
        List<Shop> shops = this.query()
                               .in("id", shopIds)
                               .list();
        for (Shop shop : shops) {
            Long shopId = shop.getId();
            Double distance = shopDistances.get(shopId);
            shop.setDistance(distance);
        }
        shops.sort(Comparator.comparingDouble(Shop::getDistance));
        return shops;
    }
}