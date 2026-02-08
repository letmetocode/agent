# MySQL SQL 目录状态说明

> 状态：**已废弃（不维护）**
>
> 日期：**2026-02-08**

当前项目仅支持 **PostgreSQL** 作为生产与开发数据库。

原因：
- 领域模型与 Mapper 已大量使用 PostgreSQL 语义（如 `jsonb`、枚举 cast、`FOR UPDATE SKIP LOCKED`、`RETURNING` 等）。
- 若继续保留“看似可用”的 MySQL 脚本，会产生误导和维护债务。

## 正确使用方式

1. 初始化数据库请使用：`docs/dev-ops/postgresql/sql/01_init_database.sql`
2. 变更表结构请在 PostgreSQL 脚本中维护，不要在本目录新增可执行 DDL。

## 未来若要恢复 MySQL 支持

必须先完成：
- 明确 MySQL 最低版本（建议 `8.0+`）
- 重写并验证所有 DDL / DML / 并发语义（claim、乐观锁、JSON、枚举）
- 在 CI 增加 MySQL 集成测试与迁移回归

在上述条件完成前，本目录仅作为“**不支持 MySQL**”的声明占位。
