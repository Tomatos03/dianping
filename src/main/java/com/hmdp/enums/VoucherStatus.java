package com.hmdp.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/10/17
 */
@Getter
public enum VoucherStatus {
    SUCCESS(0, "领取成功"),        // 抢优惠券成功
    OUT_OF_STOCK(1, "优惠券库存不足"),   // 剩余库存不足
    ALREADY_CLAIMED(2, "已领取当前优惠券");// 已经抢过该优惠券

    private final long code;
    private final String message;

    VoucherStatus(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public static VoucherStatus of(long code) {
        return Arrays.stream(values())
                     .filter(voucherStatus -> voucherStatus.code == code)
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException("没有找到对应的类型"));
    }
}
