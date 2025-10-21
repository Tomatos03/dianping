package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author : Tomatos
 * @date : 2025/10/18
 */
@Component
public class MQConfig {
    @Bean
    public DirectExchange directExchange() {
        // 交换机和队列还提供了Builder模式进行配置
        return new DirectExchange(
                "voucher_exchange",    // 交换机名称
                true,                 // 是否持久化
                false                 // 是否自动删除
        );
    }

    @Bean
    public Queue directQueue() {
        return new Queue(
                "voucher_order_queue",      // 队列名称
                true                 // 是否持久化
        );
    }

    @Bean
    public Binding bindingDirect(Queue directQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(directQueue)    // 绑定队列
                             .to(directExchange)       // 到交换机
                             .with("");
    }
}
