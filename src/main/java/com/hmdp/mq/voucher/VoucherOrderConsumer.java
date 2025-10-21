package com.hmdp.mq.voucher;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.VoucherStatus;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/10/17
 */
@Slf4j(topic = "voucherOrderConsumer")
@Component
public class VoucherOrderConsumer {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "voucher_order_queue", concurrency = "10")
    public void receiveTopicMessage(String message) {
        VoucherOrder voucherOrder = JSONUtil.toBean(message, VoucherOrder.class);
        // TODO: 由于消费者消费信息可能不只有一条, 消费消息前需要做幂等校验
        VoucherStatus result = voucherOrderService.deductStockAndCreateOrder(voucherOrder);
        // 事务执行完成之后, 才返回抢卷结果到前端
        applicationEventPublisher.publishEvent(result);
    }
}