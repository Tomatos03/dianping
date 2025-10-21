package com.hmdp.mq.voucher;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @RabbitListener(queues = "voucher_order_queue", concurrency = "10")
    public void receiveTopicMessage(String message) {
        VoucherOrder voucherOrder = JSONUtil.toBean(message, VoucherOrder.class);
        // TODO: 由于消费者消费信息可能不只有一条, 扣除和创建订单方法需要做幂等判断
        voucherOrderService.deductStockAndCreateOrderIfNotReceived(voucherOrder);
    }
}