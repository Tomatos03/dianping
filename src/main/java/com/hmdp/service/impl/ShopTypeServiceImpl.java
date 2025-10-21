package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> typeList = stringRedisTemplate.opsForList()
                                                .range(key, 0, -1);
        if (!Objects.isNull(typeList) && !typeList.isEmpty()) {
            List<ShopType> newTypeList = new ArrayList<>();
            for (String type : typeList) {
                ShopType shopType = JSONUtil.toBean(type, ShopType.class);
                newTypeList.add(shopType);
            }
            return Result.ok(newTypeList);
        }

        List<ShopType> newTypeList = this.query()
                                  .orderByAsc("sort")
                                  .list();
        if (Objects.isNull(newTypeList)) {
            return Result.error("no exist!");
        }

        for (ShopType shopType : newTypeList) {
            stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(newTypeList);
    }
}
