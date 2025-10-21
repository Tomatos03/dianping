package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.VoucherStatus;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.voucher.VoucherOrderProducer;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * <p>
 *  服务实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private VoucherOrderProducer voucherOrderProducer;
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisWorker idWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    private Long executeSeckillScript(VoucherOrder voucherOrder) {
        return stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherOrder.getVoucherId()
                            .toString(),
                voucherOrder.getUserId()
                            .toString()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deductStockAndCreateOrder(VoucherOrder voucherOrder) {
        seckillVoucherService.update()
                             .setSql("stock = stock - 1")
                             .eq("voucher_id", voucherOrder.getVoucherId())
                             .gt("stock", 0)
                             .update();

        voucherOrder.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY));
        save(voucherOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deductStockAndCreateOrderIfNotReceived(VoucherOrder voucherOrder) {
        // 判断用户是否已经领取过该优惠券
        if (hasUserReceivedVoucher(voucherOrder.getUserId(), voucherOrder.getVoucherId())) {
            return;
        }
        deductStockAndCreateOrder(voucherOrder);
    }

    private boolean hasUserReceivedVoucher(Long userId, Long voucherId) {
        return this.lambdaQuery()
                   .eq(VoucherOrder::getUserId, userId)
                   .eq(VoucherOrder::getVoucherId, voucherId)
                   .count() > 0;
    }

    // TODO:
    //  当前设计的缺陷: 秒杀优惠券时可以直接返回秒杀的结果, 秒杀的结果存储在redis
    //  当出现宕机的时候AOF模式下的redis可能丢失一秒的数据, 如果用户接收到抢卷成功信息,
    //  应该保证数据不被丢失
    @Override
    public Result seckillVoucher(Long voucherId) {
        VoucherOrder voucherOrder = createOrder(voucherId);

        Long result = executeSeckillScript(voucherOrder);

        return handlerVoucherSeckillResult(VoucherStatus.of(result), voucherOrder);
    }

    private Result handlerVoucherSeckillResult(VoucherStatus status, VoucherOrder voucherOrder) {
        voucherOrderProducer.addVoucherOrderToQueue(voucherOrder);
        if (status == VoucherStatus.SUCCESS) {
            log.info("Voucher collect success!");
            voucherOrderProducer.addVoucherOrderToQueue(voucherOrder);
            return Result.ok();
        }

        if (status == VoucherStatus.ALREADY_CLAIMED) {
            log.info("Voucher collected!");
            return Result.error("优惠券领取失败, 当前优惠券已经领取过了");
        }

        log.info("Voucher collect failure!");
        return Result.error("优惠券领取失败, 优惠券库存不足");
    }

    private VoucherOrder createOrder(Long voucherId) {
        return VoucherOrder.builder()
                           .userId(UserHolder.getUser().getId())
                           .voucherId(voucherId)
                           .build();
    }
}
