package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisWorker redisWorker;

    @Transactional
    public Result createOrder(Long userId, Long voucherId) {
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (!Objects.isNull(count) && count > 0) {
            return Result.fail("用户已购买过该优惠券");
        }
        // 库存充足尝试扣除库存
        boolean isSuccess = seckillVoucherService.update()
                                                 .setSql("stock = stock - 1")
                                                 .eq("voucher_id", voucherId)
                                                 .gt("stock", 0)
                                                 .update();
        if (!isSuccess) {
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        // 设置秒杀券id
        voucherOrder.setVoucherId(voucherId);
        // 生成订单号
        Long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 设置用户id
        voucherOrder.setUserId(userId);
        // 设置订单id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (LocalDateTime.now()
                         .isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀未开始");
        }
        if (LocalDateTime.now()
                         .isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser()
                                .getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrder(userId, voucherId);
        }
    }
}
