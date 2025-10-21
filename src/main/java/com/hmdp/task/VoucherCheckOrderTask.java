package com.hmdp.task;

import com.hmdp.constants.RedisConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/10/18
 */
@Component
public class VoucherCheckOrderTask {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IVoucherOrderService voucherOrderService;

//    @Scheduled(cron = "0 0/5 * * * ?")
    public void checkVoucherOrderRecord() {
        List<String> keys = getAllVoucherOrderKey(RedisConstants.SECKILL_VOUCHER_KEY);
        Map<String, Set<String>> voucherOrderMap = getVoucherSetByKeys(keys);

        for (Map.Entry<String, Set<String>> entry : voucherOrderMap.entrySet()) {
            String voucherOrderKey = entry.getKey();
            Set<String> userIdSet = entry.getValue();

            Long voucherId = Long.valueOf(
                    voucherOrderKey.substring(voucherOrderKey.lastIndexOf(":") + 1)
            );
            for (String userId : userIdSet) {
                VoucherOrder voucherOrder = VoucherOrder.builder()
                                                        .userId(Long.valueOf(userId))
                                                        .voucherId(voucherId)
                                                        .build();

                // TODO: 这里每次都去查询数据库, 存在一个优化点:
                //  先根据优惠券ID查询领取的所有用户, 后续判断某个用户是否还其中
                voucherOrderService.deductStockAndCreateOrder(voucherOrder);
            }
        }
    }

    private Map<String, Set<String>> getVoucherSetByKeys(List<String> keys) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String key : keys) {
            Set<String> members = stringRedisTemplate.opsForSet()
                                                     .members(key);
            result.put(key, members);
        }
        return result;
    }

    private List<String> getAllVoucherOrderKey(String voucherOrderKey) {
        ScanOptions options = ScanOptions.scanOptions()
                                         .match(String.format("%s*", voucherOrderKey))
                                         .count(1000)
                                         .build();

        Cursor<byte[]> cursor = Objects.requireNonNull(stringRedisTemplate.getConnectionFactory())
                                       .getConnection()
                                       .scan(options);

        List<String> keys = new ArrayList<>();
        while (cursor.hasNext()) {
            keys.add(new String(cursor.next()));
        }
        cursor.close();
        return keys;
    }
}
