# 阶段 0 执行记录

- 执行日期：2026-07-30
- 数据库：本机 MySQL 8.0.43，schema `chat_room`
- 原则：先备份与审计，不直接修正业务数据

## 1. Application 配置复核

用户已完成两项有效改动：

- 数据库连接项支持环境变量；
- JWT 固定密钥已移除，`JWT_SECRET` 成为必填环境变量。

阶段 0 进一步移除了 `DB_USERNAME` 和 `DB_PASSWORD` 的 `root/root` 回退。未配置凭据时应用应启动失败，不能静默使用数据库管理员账户。

## 2. 数据库备份与恢复验证

- 备份文件位于项目目录之外：`../db-backups/chat_room_stage0_20260730-183919.sql`
- 文件大小：16,103 bytes
- SHA-256：`F169D2F1B8FCE0F41DF055F7C803530A0E680BCF971BA97FF876929C09B162F4`
- 备份方式：一致性快照，包含表、数据、触发器、事件和存储例程
- 恢复验证：已恢复到独立临时 schema
- 核心六张表行数差异：0
- 临时验证 schema：已删除

备份文件可能包含业务数据，不得提交 Git 或发送到公开渠道。

## 3. 密钥轮换与最小权限账户

已在 Windows 用户环境中配置 7 个变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
DB_MIGRATOR_USERNAME
DB_MIGRATOR_PASSWORD
JWT_SECRET
JWT_EXPIRATION_MS
```

实际值不写入仓库或本文档。新 JWT 密钥为 256 bit Base64 密钥，审计指纹为 `A7C603AABE202939`。项目执行时没有 8080 端口监听进程，因此不存在需要热切换的后端实例；下一次启动即使用新密钥，旧 Token 随之失效。IDE 或终端若在轮换前已启动，需要重启后再运行应用。

数据库账户：

| 账户 | 权限 |
|---|---|
| `chatroom_app@localhost` | `SELECT, INSERT, UPDATE, DELETE` on `chat_room.*` |
| `chatroom_migrator@localhost` | 运行账户权限，加 `CREATE, ALTER, DROP, INDEX, REFERENCES` |

日常应用只能使用 `chatroom_app`。后续 Flyway 迁移使用 `chatroom_migrator`，业务运行时不得使用 `root`。

## 4. 只读数据预检结果

数据规模：

| 表 | 行数 |
|---|---:|
| users | 16 |
| channels | 8 |
| channel_members | 19 |
| private_chats | 13 |
| messages | 29 |
| message_reads | 4 |

通过项：

- 非法消息归属：0
- 重复频道成员：0
- 重复已读记录：0
- 重复或自聊私聊：0
- 私聊参与者与发起人异常：0
- 孤立频道、成员、消息、已读记录：0
- 已删除私聊残留消息：0

待迁移修复项：

| channel_id | 当前 `creator_id` | 当前唯一 `CREATOR` 镜像 | 问题 |
|---:|---:|---:|---|
| 20 | 12 | 13 | 旧转让只改角色，未更新 `creator_id` |
| 23 | 4 | 3 | 旧转让只改角色，未更新 `creator_id` |

处理规则见 [ADR 0001](../decisions/0001-channel-ownership.md)。阶段 0 没有修改这两条数据。

## 5. 已冻结的业务决策

- 频道所有权以 `channels.creator_id` 为唯一事实来源；
- 所有权转让后原创建者固定降为 `ADMIN`；
- 私聊普通删除采用“只清空当前用户视图”；
- 附件上传与下载都必须认证，后续再升级到消息参与者级授权。

详细依据：

- [ADR 0001：频道所有权](../decisions/0001-channel-ownership.md)
- [ADR 0002：私聊删除](../decisions/0002-private-chat-deletion.md)
- [ADR 0003：附件访问](../decisions/0003-attachment-access.md)

## 6. 阶段 0 完成边界

已完成备份、恢复校验、密钥轮换、最小权限账户、数据预检和业务决策固化。尚未进入下一阶段，因此以下内容仍保持现状：

- `ddl-auto:update`
- 频道角色与转让实现
- 私聊删除实现与表结构
- 文件上传、下载及 WebSocket 授权
- Flyway、DTO、统一异常和测试隔离

这些项目必须按后续阶段实施，不能把本记录误认为全部安全问题已经修复。

## 7. 验证结果

使用 `chatroom_app` 与新 JWT 环境变量执行了 Maven `verify`。验证时通过系统属性将 Hibernate DDL 临时设为 `none`，确保不会改动开发数据库结构。

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Database version: MySQL 8.0.43
```

这只证明阶段 0 配置和 Spring 上下文能够正常加载；现有测试仍只有一个空的 `contextLoads`，不能替代后续权限、WebSocket、文件和数据库迁移测试。
