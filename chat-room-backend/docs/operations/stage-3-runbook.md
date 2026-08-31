# 阶段 3 执行记录：环境配置与 Flyway

- 执行日期：2026-08-01
- 范围：开发/生产/测试环境分层、Flyway 迁移、关闭 Hibernate 自动改表、测试库隔离
- 数据原则：保留现有开发库业务数据，不重建或清空已有表

## 1. `schema.sql` 的处理结论

旧 `schema.sql` 不能继续作为迁移依据，也不应留在 Spring Boot 的资源目录中：

- 它混合了两套不同时间生成的建表脚本；
- 同时包含 `CREATE TABLE IF NOT EXISTS` 和 `DROP TABLE`；
- 部分外键引用顺序错误；
- 它不会记录执行版本，无法判断某次结构变更是否已经应用；
- 与 Flyway 并存时容易造成“双重初始化”。

因此该文件已删除，并显式配置 `spring.sql.init.mode=never`。数据库结构的唯一变更入口现在是 `db/migration` 下的 Flyway 迁移。

## 2. 环境配置

| 环境 | 激活方式 | 数据库 | Flyway 策略 | Hibernate |
|---|---|---|---|---|
| `dev` | 默认；或 `SPRING_PROFILES_ACTIVE=dev` | MySQL `chat_room` | 允许首次接管 Hibernate 创建的旧库 | `validate` |
| `test` | 测试资源自动激活 | 独立 H2 内存库 | 每次从空库执行全部迁移 | `validate` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | 由环境变量指定 | 禁止自动接管未知非空库 | `validate` |

通用配置位于：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/test/resources/application-test.yml`

运行账号与迁移账号继续分离：

- `DB_USERNAME` / `DB_PASSWORD`：应用运行账号，只执行日常增删改查；
- `DB_MIGRATOR_USERNAME` / `DB_MIGRATOR_PASSWORD`：Flyway 账号，拥有建表、改表和索引权限。

## 3. 迁移文件

当前迁移版本：

| 版本 | 文件 | 用途 |
|---|---|---|
| V1 | `db/migration/mysql/V1__initial_schema.sql` | 全新 MySQL 数据库从零创建六张业务表 |
| V1 | `db/migration/h2/V1__initial_schema.sql` | 测试环境从零创建等价结构 |
| V2 | `db/migration/common/V2__repair_channel_ownership.sql` | 按 `channels.creator_id` 修复创建者成员关系和角色 |

MySQL 与 H2 的 V1 分开，是因为布尔值、枚举和自增列的数据库语法不同；V2 是两种数据库共用的数据迁移。

## 4. 现有开发库的接管方式

现有 `chat_room` 是 Hibernate `ddl-auto:update` 创建的非空库。开发环境首次启动时使用：

```yaml
baseline-on-migrate: true
baseline-version: 1
```

Flyway 将现有结构登记为 V1 基线，不执行 V1 的建表语句，然后执行 V2。接管完成后，`flyway_schema_history` 中应有：

| version | type | description |
|---|---|---|
| 1 | BASELINE | legacy-hibernate-schema |
| 2 | SQL | repair channel ownership |

Hibernate 随后用 `ddl-auto:validate` 检查实体与实际表结构是否匹配，但不会自动创建、删除或修改列。

## 5. 生产环境规则

全新的空生产库可直接执行 V1、V2。已有数据但没有 `flyway_schema_history` 的生产库会拒绝启动，这是刻意的保护措施。

接管已有生产库时必须先备份并审计表结构；确认它等价于 V1 后，只在首次接管时临时设置：

```text
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=1
```

接管成功后立即移除这两个临时变量。不要在生产环境长期启用自动 baseline。

## 6. 后续数据库变更规则

1. 已经执行过的迁移文件不得修改或重命名，否则 Flyway 校验会因 checksum 不一致而拒绝启动。
2. 下一次结构或数据变更必须新增 `V3__描述.sql`，再依次增加 V4、V5。
3. 实体字段变更和迁移脚本必须在同一次提交中完成。
4. 不得把 `ddl-auto` 改回 `update` 或 `create`。
5. 禁止恢复根目录旧 `schema.sql` 作为自动初始化脚本。

## 7. 验证结果

独立测试环境验证：

- Flyway 从空 H2 数据库执行 V1、V2；
- Hibernate `validate` 接受迁移后的结构；
- 测试数据源 URL 被断言为 `jdbc:h2:mem:chat_room_test`；
- 完整测试：63 项通过，0 失败，0 错误，0 跳过。

现有 MySQL 开发库验证：

- 使用 `chatroom_migrator` 成功创建迁移历史并执行 V2；
- 当前版本为 V2；
- Hibernate 结构校验通过；
- 六张业务表行数在接管前后保持为 16、8、19、13、29、4；
- 频道所有权不一致计数为 0，错误 `CREATOR` 角色计数为 0。

