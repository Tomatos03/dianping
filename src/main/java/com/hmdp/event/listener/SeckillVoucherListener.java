package com.hmdp.event.listener;

import com.hmdp.enums.VoucherStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/11/26
 */
@Slf4j
@Component
public class SeckillVoucherListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendNotifyToClient(VoucherStatus voucherStatus) {
        String message = voucherStatus.getMessage();
        log.info("发送消息到前端, 抢卷结果:[{}]", message);
    }
}
