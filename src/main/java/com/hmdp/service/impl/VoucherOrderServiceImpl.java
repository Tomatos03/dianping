package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.esotericsoftware.minlog.Log;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;

    private final static ExecutorService VOUCHER_ORDER_EXECUTOR = Executors.newFixedThreadPool(300);

    private IVoucherOrderService proxy;
    private final String queueName = "stream.orders";

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    @PostConstruct
    private void init() {
        // 不使用lambda表达式而是直接使用内部类是因为内部类的名称能够阐述线程作用
        VOUCHER_ORDER_EXECUTOR.submit(new HandleVoucherOrder());
    }

    private class HandleVoucherOrder implements Runnable {

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            proxy.createOrder(voucherOrder);
        }

        public void handlePendingOrder() {
            Consumer consumer = Consumer.from("mygroup", "voucher-order-consumer");
            StreamReadOptions streamReadOptions = StreamReadOptions.empty()
                                                                   .count(1);
            StreamOffset<String> stringStreamOffset = StreamOffset.create(queueName,
                                                                          ReadOffset.from("0"));
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream()
                                                                                      .read(
                                                                                              consumer,
                                                                                              streamReadOptions,
                                                                                              stringStreamOffset
                                                                                      );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();

                    VoucherOrder voucherOrder = BeanUtil.toBean(value, VoucherOrder.class);
                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge("mygroup", entries);
                } catch (Exception e) {
                    Log.error("处理待处理异常订单", e);
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        @Override
        public void run() {
            Consumer consumer = Consumer.from("mygroup", "voucher-order-consumer");
            StreamReadOptions streamReadOptions = StreamReadOptions.empty()
                                                                   .count(1)
                                                                   .block(Duration.ofSeconds(2));
            StreamOffset<String> stringStreamOffset = StreamOffset.create(queueName, ReadOffset.lastConsumed());
            try {
                while (true) {
                    // xreadgroup group groupname consumer_name count 1 block 2000 stream_name
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream()
                                                                                      .read(
                                                                                              consumer,
                                                                                              streamReadOptions,
                                                                                              stringStreamOffset
                                                                                      );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();

                    VoucherOrder voucherOrder = BeanUtil.toBean(value, VoucherOrder.class);
                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge("mygroup", entries);
                }
            } catch (Exception e) {
                handlePendingOrder();
            }
        }
    }


    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.update()
                                               .setSql("stock = stock - 1")
                                               .eq("voucher_id", voucherOrder.getVoucherId())
                                               .gt("stock", 0)
                                               .update();
        if (!success) {
            throw new RuntimeException("库存不足");
        }
        save(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long result = addOrderToQueue(voucherId);
        // 包装类不能够直接与基本类型进行比较，直接比较会使基本类型被自动装箱成包装类，然后调用equals方法比较
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == -1 ? "库存不足" : "用户已购买", 500);
        }

        // 必须通过Spring 的代理对象执行的方法才能够触发事务
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok();
    }

    private Long addOrderToQueue(Long voucherId) {
        long orderId = redisWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY);
        Long userId = UserHolder.getUser().getId();
        return stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                orderId + ""
        );
    }
}
