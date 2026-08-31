# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

```bash
./mvnw spring-boot:run    # 启动后端（默认 http://localhost:8080）
./mvnw compile            # 编译
./mvnw compile -q         # 静默编译
./mvnw test               # 运行单元测试
./mvnw verify             # 运行单元测试 + H2/Flyway 集成测试
```

测试默认激活 `test` profile，并使用随机命名的 H2 内存数据库，不连接开发 MySQL。`*IntegrationTest` 由 Maven Failsafe 在 `verify` 阶段执行。

## 环境依赖

| 组件 | 说明 |
|------|------|
| JDK | 17+ |
| 构建 | Maven Wrapper，Spring Boot 3.4.4 |
| 数据库 | MySQL 8.0；Flyway 管理结构，JPA `ddl-auto: validate`；运行与迁移使用不同账号 |
| 安全 | Spring Security + JWT（jjwt 0.12.6），7天过期，BCrypt 密码加密 |
| 文件存储 | `uploads/` 目录，静态映射 `/files/**` |

## 架构分层

```
Controller → Service → Repository（JPA + Hibernate 6.6）
```

### Controller（`controller/`）8个

| 控制器 | 职责 |
|--------|------|
| `AuthController` | `POST /register` `POST /login`，无状态 JWT |
| `ChannelController` | 频道 CRUD + 成员管理（角色/踢出/提升/降级/转让）+ 消息历史 |
| `ChatController` | `@MessageMapping` 处理 STOMP 消息（发送/撤回/打字/已读），通过 `Principal` 获取 userId |
| `PrivateChatController` | 私聊 REST API + `@MessageMapping("/private.send")` |
| `FileController` | `POST /api/files/upload`，50MB，13种 MIME 白名单 |
| `SearchController` | `GET /api/search/messages?q=` 全局搜索（content+filename LIKE） |
| `UserController` | `GET /me` `PUT /status` `GET /presence` |
| `UserSearchController` | `GET /users/search?q=`（独立搜索控制器） |

### Service（`service/`）6个

- `UserService` — 注册（BCrypt）、登录验证
- `ChannelService` — 频道/成员/角色/禁言/转让/解散，注入 `SimpMessagingTemplate` 发系统消息
- `MessageService` — 消息收发/历史（按 HistoryLevel 过滤）/撤回/已读/@提及。`buildMessagePayload()` **非静态**，被 `PrivateChatService` 和 `SearchController` 依赖注入调用
- `PrivateChatService` — PENDING→ACTIVE→REJECTED→DELETED 状态机，注入 `MessageService`
- `FileService` — 50MB校验 + MIME白名单（图片5种 + 文档8种），UUID重命名防冲突
- `PresenceService` — `ConcurrentHashMap<userId, status>` 管理在线状态

### Config（`config/`）6个

- `SecurityConfig` — 无状态 JWT 过滤链，`/api/auth/**` `/ws/**` `/api/files/**` `/files/**` permitAll
- `WebSocketConfig` — STOMP over SockJS，`/topic` `/queue` Broker，`/app` 应用前缀，`/user` 用户前缀
- `CorsConfig` — 允许 `localhost:*`
- `GlobalExceptionHandler` — `@RestControllerAdvice`，处理 `RuntimeException` `MaxUploadSizeExceededException` `IOException`
- `WebMvcConfig` — `/files/**` → `uploads/` 静态资源映射
- `MultipartConfig` — `MultipartConfigElement` Bean，50MB

## WebSocket 关键要点

- **`StompInterceptor`**：CONNECT 帧校验 JWT → 设 `Principal`（`accessor.setUser(principal)`）。**后续帧不校验**
- **WebSocket 消息处理线程无 `SecurityContext`**：所有 `@MessageMapping` 方法必须通过 `Principal` 参数获取 userId，**绝不能调 `SecurityContextHolder`**
- `WebSocketEventListener`：监听 `SessionConnectEvent`/`SessionDisconnectEvent` → 调 `PresenceService`
- 前端连接前订阅自动排队（`pendingSubs`），`onConnect` 时回放

## 数据库关键设计

- **消息表复用**：`channel_id` 非空 + `private_chat_id` 空 = 频道消息；反之 = 私聊消息
- **频道权限**：CREATOR > ADMIN > MEMBER。提升/降级/转让仅创建者；禁言/踢人/邀请 管理员+创建者
- **私聊状态机**：PENDING → ACTIVE（同意）/ REJECTED（拒绝）/ DELETED（删除，含消息记录清除）
- **@提醒**：消息 payload 内嵌 `mentions: [{userId, username, nickname}]`，不单独发 WebSocket 通知
- **在线状态**：连接时恢复 DB 持久化状态（INVISIBLE 保持不变），断开不写 DB

## 文档位置

设计文档：`F:\ByInternetVibeCoding\docs\superpowers\specs\`
实施计划：`F:\ByInternetVibeCoding\docs\superpowers\plans\`
