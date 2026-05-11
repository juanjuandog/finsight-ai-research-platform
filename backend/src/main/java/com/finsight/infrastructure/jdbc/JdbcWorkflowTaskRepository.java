package com.finsight.infrastructure.jdbc;

import com.finsight.workflow.WorkflowStatus;
import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JdbcWorkflowTaskRepository implements WorkflowTaskRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcWorkflowTaskRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public WorkflowTask save(WorkflowTask task) {
        jdbcTemplate.update("""
                INSERT INTO workflow_tasks(
                    id, task_type, idempotency_key, status, attempts, created_at, payload, error_message, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id)
                DO UPDATE SET task_type = EXCLUDED.task_type,
                              idempotency_key = EXCLUDED.idempotency_key,
                              status = EXCLUDED.status,
                              attempts = EXCLUDED.attempts,
                              payload = EXCLUDED.payload,
                              error_message = EXCLUDED.error_message,
                              updated_at = now()
                """,
                task.id(),
                task.taskType(),
                task.idempotencyKey(),
                task.status().name(),
                task.attempts(),
                Timestamp.from(task.createdAt()),
                jsonColumnMapper.jsonb(task.payload()),
                task.errorMessage()
        );
        return task;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM workflow_tasks
                WHERE idempotency_key = ?
                """, Integer.class, idempotencyKey);
        return count != null && count > 0;
    }

    @Override
    public Optional<WorkflowTask> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, task_type, idempotency_key, status, attempts, created_at, payload::text, error_message
                FROM workflow_tasks
                WHERE id = ?
                """, this::mapTask, id).stream().findFirst();
    }

    @Override
    public Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT id, task_type, idempotency_key, status, attempts, created_at, payload::text, error_message
                FROM workflow_tasks
                WHERE idempotency_key = ?
                """, this::mapTask, idempotencyKey).stream().findFirst();
    }

    @Override
    public List<WorkflowTask> findAll() {
        return jdbcTemplate.query("""
                SELECT id, task_type, idempotency_key, status, attempts, created_at, payload::text, error_message
                FROM workflow_tasks
                ORDER BY created_at DESC
                """, this::mapTask);
    }

    private WorkflowTask mapTask(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new WorkflowTask(
                rs.getString("id"),
                rs.getString("task_type"),
                rs.getString("idempotency_key"),
                WorkflowStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getTimestamp("created_at").toInstant(),
                jsonColumnMapper.objectMap(rs.getString("payload")),
                rs.getString("error_message")
        );
    }
}
