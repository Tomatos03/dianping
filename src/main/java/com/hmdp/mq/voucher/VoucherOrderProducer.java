package com.hmdp.mq.voucher;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/10/17
 */
@Slf4j(topic = "voucherOrderProducer")
@Component
public class VoucherOrderProducer {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String ROUTE_KEY = "";
    private static final String EXCHANGE_NAME = "voucher_exchange";

    public void addVoucherOrderToQueue(VoucherOrder voucherOrder) {
        String voucherOrderJson = JSONUtil.toJsonStr(voucherOrder);

        CorrelationData correlationData = new CorrelationData(UUID.fastUUID().toString(true));
        correlationData.getFuture().addCallback(
                this::handleAddVoucherToQueueSuccess,
                this::handleAddVoucherToQueueFailure
        );

        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTE_KEY, voucherOrderJson, correlationData);
    }

    private void handleAddVoucherToQueueFailure(Throwable e) {
        log.info("消息投递失败");
    }

    private void handleAddVoucherToQueueSuccess(CorrelationData.Confirm confirm) {
        if (confirm != null && confirm.isAck()) {
            log.info("消息投递成功");
            return;
        }
        log.info("消息无法正常投递到交换机, ack = {}", confirm.isAck());
    }
}
