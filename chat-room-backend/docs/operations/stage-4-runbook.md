# 阶段 4 执行记录：测试隔离与 CI

- 执行日期：2026-08-01
- 范围：测试生命周期分层、数据库和文件隔离、误连保护、GitHub Actions
- 前置条件：阶段 3 已启用 Flyway，现有开发库已接管到 V2

## 1. Maven 测试分层

测试现在按命名进入不同生命周期：

| 类型 | 命名约定 | Maven 插件 | 执行阶段 |
|---|---|---|---|
| 单元测试 | `*Test`、`*Tests`，但不含 `*IntegrationTest` | Surefire | `test` |
| 集成测试 | `*IntegrationTest` | Failsafe | `integration-test`、`verify` |

常用命令：

```bash
./mvnw test
./mvnw verify
./mvnw verify -DskipITs
```

- `test`：只运行单元测试；
- `verify`：运行单元测试和集成测试；
- `verify -DskipITs`：完整构建但跳过集成测试。

原来的应用上下文和数据库搜索冒烟测试已统一改为 `*IntegrationTest` 命名，避免被 Surefire 和 Failsafe 重复执行。

## 2. 本地测试隔离

默认测试 profile 为 `test`：

- 数据库：`jdbc:h2:mem:chat_room_test_${random.uuid}`；
- 每个不同的 Spring 测试上下文使用随机数据库名；
- Flyway 每次从空数据库执行 H2 V1、V2；
- Hibernate 使用 `ddl-auto:validate`；
- 文件上传目录位于系统临时目录，并带随机 UUID；
- 集成测试中的数据写入使用事务回滚。

因此普通 `mvn test` / `mvn verify` 不需要开发数据库账号，也不会读写 `chat_room`。

## 3. 误连开发库保护

测试资源注册了 `TestEnvironmentSafetyInitializer`，它在 Spring Bean 和 Flyway 初始化之前检查：

1. 激活的 profile 必须是 `test` 或 `test-mysql`；
2. JDBC URL 必须包含明确的测试库标记 `chat_room_test`。

任何测试如果误用 `dev` profile 或连接 `chat_room`，应用上下文会立即终止，错误信息为：

```text
Test startup blocked: an isolated chat_room_test database is required
```

该保护本身有独立单元测试，覆盖允许测试库和拒绝开发库两条路径。

## 4. MySQL 测试 profile

`application-test-mysql.yml` 用于验证真实 MySQL 方言、原生搜索 SQL 和 MySQL 版本的 Flyway V1/V2。

所需变量：

```text
SPRING_PROFILES_ACTIVE=test-mysql
TEST_DB_URL=jdbc:mysql://127.0.0.1:3306/chat_room_test?...
TEST_DB_USERNAME=chatroom_app
TEST_DB_PASSWORD=...
TEST_DB_MIGRATOR_USERNAME=...
TEST_DB_MIGRATOR_PASSWORD=...
```

数据库必须是可丢弃的独立 `chat_room_test`，不能指向开发库。Flyway 的 `baseline-on-migrate` 在该 profile 中为 `false`，因此迁移必须从空库真实执行，不能把未知旧结构登记成基线。

## 5. GitHub Actions

工作流：`.github/workflows/backend-ci.yml`

触发条件：

- push；
- pull request；
- 手动触发。

包含两个并行任务：

| 任务 | 数据库 | 验证内容 |
|---|---|---|
| `h2-verification` | 随机 H2 内存库 | 快速运行全部单元与集成测试 |
| `mysql-verification` | 临时 MySQL 8.0.43 Service Container | 从空库执行 MySQL V1/V2、Hibernate 校验、全部测试和原生 SQL |

CI 只授予 `contents: read` 权限，并配置：

- 同一分支的新运行会取消旧运行；
- 每个任务最多 15 分钟；
- Maven 依赖缓存；
- 无论成功失败都上传 Surefire/Failsafe 报告；
- 报告保留 7 天；
- 所有数据库口令均为临时 CI 容器内的测试值，不使用仓库或生产密钥。

## 6. 验证结果

本地 H2 验证：

| 阶段 | 数量 | 结果 |
|---|---:|---|
| Surefire 单元测试 | 49 | 全部通过 |
| Failsafe 集成测试 | 16 | 全部通过 |
| 合计 | 65 | 0 失败、0 错误、0 跳过 |

本机未安装 Docker，因此 MySQL Service Container 任务不能在本地复现；它会在代码提交并推送到 GitHub 后由 `mysql-verification` 任务执行。阶段 3 已单独确认现有 MySQL 8.0.43 上 Flyway V2 和 Hibernate 校验可用，但全新 MySQL V1 的最终证据应以首次 GitHub Actions 运行结果为准。

