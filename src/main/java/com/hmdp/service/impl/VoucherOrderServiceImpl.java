package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.VoucherStatus;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.voucher.VoucherOrderProducer;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * <p>
 * 服务实现
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
    private RedisWorker idWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    @Override
    public void seckillVoucher(Long voucherId) {
        VoucherOrder voucherOrder = createOrder(voucherId);
        VoucherStatus status = VoucherStatus.of(executeSeckillScript(voucherOrder));
        if (status == VoucherStatus.SUCCESS) {
            voucherOrderProducer.addVoucherOrderToQueue(voucherOrder);
        } else {
            applicationEventPublisher.publishEvent(status);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public VoucherStatus deductStockAndCreateOrder(VoucherOrder voucherOrder) {
        // 判断用户是否已经领取过该优惠券
        if (hasUserReceivedVoucher(voucherOrder.getUserId(), voucherOrder.getVoucherId())) {
            return VoucherStatus.ALREADY_CLAIMED;
        }

        int rowNum = seckillVoucherMapper.update(
                null,
                new UpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherOrder.getVoucherId())
                        .gt("stock", 0)
        );
        if (rowNum == 0) {
            return VoucherStatus.OUT_OF_STOCK;
        }

        voucherOrder.setId(idWorker.nextId(RedisConstants.VOUCHER_ORDER_KEY));
        save(voucherOrder);
        return VoucherStatus.SUCCESS;
    }

    private boolean hasUserReceivedVoucher(Long userId, Long voucherId) {
        return this.lambdaQuery()
                   .eq(VoucherOrder::getUserId, userId)
                   .eq(VoucherOrder::getVoucherId, voucherId)
                   .count() > 0;
    }

    private VoucherOrder createOrder(Long voucherId) {
        return VoucherOrder.builder()
                           .userId(UserHolder.getUser()
                                             .getId())
                           .voucherId(voucherId)
                           .build();
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
}
