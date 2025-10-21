package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.VoucherStatus;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    void seckillVoucher(Long voucherId);

    VoucherStatus deductStockAndCreateOrder(VoucherOrder voucherOrder);
}
