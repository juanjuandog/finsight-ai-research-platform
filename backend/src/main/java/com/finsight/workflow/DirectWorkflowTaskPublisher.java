package com.finsight.workflow;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!rabbitmq")
public class DirectWorkflowTaskPublisher implements WorkflowTaskPublisher {
    private final WorkflowOrchestrator orchestrator;

    public DirectWorkflowTaskPublisher(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void publish(WorkflowTask task) {
        orchestrator.execute(task.id());
    }
}

