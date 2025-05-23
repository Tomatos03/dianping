package com.hmdp;

import com.hmdp.constants.RedisConstants;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl shopService;

    @Autowired
    RedisWorker redisWorker;


    @Test
    public void TestRedisWorker() {
        Long id = redisWorker.nextId(RedisConstants.SHOP_GEO_KEY);
        System.out.println(id);
    }
}
