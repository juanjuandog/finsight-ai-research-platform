package com.finsight.api;

import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowTaskRepository taskRepository;

    public WorkflowController(WorkflowTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public List<WorkflowTask> tasks() {
        return taskRepository.findAll();
    }
}

