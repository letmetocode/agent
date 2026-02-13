-- 3.4 observability: 日志分页查询性能优化索引

CREATE INDEX IF NOT EXISTS idx_plan_task_events_created_at_id_desc
    ON plan_task_events(created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_plan_task_events_task_id_created_at
    ON plan_task_events(task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_plan_task_events_trace_id
    ON plan_task_events((event_data->>'traceId'));
