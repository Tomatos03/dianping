package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);

//        List<Voucher> newVouchers = new ArrayList<>();
//
//        for (Voucher voucher : vouchers) {
//            if (voucher.getStatus() == 0) {
//                newVouchers.add(voucher);
//                continue;
//            }
//            LocalDateTime beginTime = voucher.getBeginTime();
//            LocalDateTime endTime = voucher.getEndTime();
//            if (beginTime != null && LocalDateTime.now().isBefore(beginTime)
//                    || endTime != null && LocalDateTime.now().isAfter(endTime)
//                    || voucher.getStock() <= 0
//            ) {
//                continue;
//            }
//            voucher.setStock(voucher.getStock() - 1);
//            newVouchers.add(voucher);
//        }
//        return Result.ok(newVouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }
}
