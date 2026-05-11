package com.finsight.workflow;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rabbitmq")
public class RabbitWorkflowTaskPublisher implements WorkflowTaskPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitWorkflowProperties properties;

    public RabbitWorkflowTaskPublisher(RabbitTemplate rabbitTemplate, RabbitWorkflowProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(WorkflowTask task) {
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.ingestionRoutingKey(),
                WorkflowMessage.from(task)
        );
    }
}

