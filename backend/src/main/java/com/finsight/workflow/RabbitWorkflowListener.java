package com.finsight.workflow;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rabbitmq")
public class RabbitWorkflowListener {
    private final WorkflowOrchestrator orchestrator;

    public RabbitWorkflowListener(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = "${finsight.workflow.ingestion-queue}")
    public void consume(WorkflowMessage message) {
        orchestrator.execute(message.taskId());
    }
}

