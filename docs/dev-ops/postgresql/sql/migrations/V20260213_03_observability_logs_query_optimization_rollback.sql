-- rollback for V20260213_03_observability_logs_query_optimization

DROP INDEX IF EXISTS idx_plan_task_events_trace_id;
DROP INDEX IF EXISTS idx_plan_task_events_task_id_created_at;
DROP INDEX IF EXISTS idx_plan_task_events_created_at_id_desc;
