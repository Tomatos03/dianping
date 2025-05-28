package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

    private final static ExecutorService VOUCHER_ORDER_EXECUTOR = Executors.newFixedThreadPool(30);

    private final static BlockingQueue<VoucherOrder> ORDER_TASK_QUEUE =
            new ArrayBlockingQueue<>(80);

    private IVoucherOrderService proxy;

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
        @Override
        public void run() {
            try {
                while (true) {
                    VoucherOrder voucherOrder = ORDER_TASK_QUEUE.take();
                    proxy.createOrder(voucherOrder);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        save(voucherOrder);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        // 包装类不能够直接与基本类型进行比较，直接比较会使基本类型被自动装箱成包装类，然后调用equals方法比较
        int r = result.intValue();

        if (r != 0) {
            return Result.fail(r == -1 ? "库存不足" : "用户已购买", 500);
        }

        VoucherOrder voucherOrder = new VoucherOrder();

        Long voucherOrderId  = redisWorker.nextId("order:");
        voucherOrder.setId(voucherOrderId);

        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        ORDER_TASK_QUEUE.add(voucherOrder);

        return Result.ok(voucherOrderId);
    }
}
