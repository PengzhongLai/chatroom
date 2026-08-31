# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

```bash
# 启动 Vite 开发服务器（默认 http://localhost:5173）
npm run dev
# vue-tsc 类型检查 + vite 生产构建
npm run build
# 预览生产构建
npm run preview
```

无测试、无 linter 配置。

## 后端依赖

前端连接 `http://localhost:8080`（Spring Boot 后端，在 `../chat-room-backend`）：
- MySQL `localhost:3306`，数据库 `chat_room`，用户名 `root:root`
- WebSocket：`http://localhost:8080/ws`（STOMP over SockJS）
- REST API 基础路径：`http://localhost:8080/api`
- JWT 认证：Authorization header Bearer token

## 架构（Vue 3 + TypeScript + Vite）

**无 UI 库** — Element Plus 已移除，所有组件自建 macOS 风格。
`@` 别名指向 `src/`。

### 状态管理（Pinia store，`src/stores/`）

| Store | 职责 |
|-------|------|
| auth | JWT token（localStorage）、用户信息、登录/登出 |
| channel | 频道列表、我的频道、当前频道、成员管理、创建/加入/退出/禁言 |
| message | 消息列表、分页（hasMore）、WebSocket 订阅新消息+打字、未读+提及、消息撤回（recallViaHttp + RECALL WebSocket 事件） |
| privateChat | 私信列表、消息、当前私信、hasMore 分页、发送/接受/拒绝/删除 |
| presence | 在线状态映射（`/topic/presence` WebSocket + HTTP 拉取） |
| theme | 深色模式初始化 |

### HTTP API（`src/api/`）

- `request.ts` — Axios 实例，baseURL 为 `/api`，JWT 拦截器，错误提示
- 按资源分模块：auth、channels、privateChats、files、search

### 实时通信（`src/composables/useStomp.ts`）

- SockJS 连接 `http://localhost:8080/ws`
- 自动重连（指数退避，最多 10 次）
- 连接前的订阅会排队，连接后自动重放

### UI 组件（`src/components/ui/`）

| 组件 | 关键属性 | 用途 |
|------|---------|------|
| MacAvatar | name, size(28), gradient(1-6), status, showStatus | 彩色首字母头像，三态状态点 |
| MacButton | variant(default/primary/danger/plain), size(sm/md), disabled, loading | 按钮 |
| MacInput | v-model, placeholder, type, disabled, readonly, rows | 输入框/文本框 |
| MacBadge | variant, size | 彩色标签 |
| MacSheet | visible, title, width, close(emit) | 模态弹窗，Teleport 到 body |
| MacPopover | visible, close(emit) | 弹出层 |

### 布局（`src/views/MainLayout.vue`）

2栏布局：侧边栏 240px + 聊天区 flex:1。Chat area 根据 `viewMode` 切换频道/私信。

**分层玻璃卡片设计**：每块组件是独立圆角卡片，2px 间隙露出黑色背景：
- 侧边栏：`sidebar-top`（搜索+频道列表+私信列表）+ `sidebar-user`（头像+状态）
- 频道：`ChatHeader` + `MessageList`（`msg-glass-card`）+ `MessageInput`
- 私信：`pv-header` + `pv-messages` + `pv-input`
- 所有卡片：`border-radius: 14px`、`backdrop-filter`、`border: 1px solid rgba(255,255,255,0.03)`

### 频道设置（`src/components/channel/ChannelSettings.vue`）

通过 ChatHeader 的 ⋯ 菜单控制，按角色显示：
- **查看成员**：全部角色 → 打开成员列表 MacSheet
- **频道设置**：管理员/创建者 → 编辑名称/描述/邀请码
- **禁言**：管理员/创建者
- **退出频道**：成员/管理员（创建者不可退出）
- **解散频道**：仅创建者 → 红色危险卡片 + 二次确认 MacSheet

### 关键实现细节

- **28px 对齐基线**：侧边栏头像、textarea、MacButton、发送按钮统一 28px 高
- **输入框自动增高**：监听内容变化，读取 `scrollHeight` 设显式高度，`transition: height 0.2s`。上限约 220px（10行），超出后内滚动
- **hasMore 翻页保护**：`messageStore` 和 `privateChatStore` 都有 `data.length < PAGE_SIZE` 检查，防止无限翻页
- **@提及高亮**：黄色 `#ffd60a` 背景 + 深色 `#1d1d1f` 文字（蓝色气泡和灰色气泡上都可见）
- **状态圆点**：ONLINE=绿色、INVISIBLE=橙色、OFFLINE=灰色
- **动画**：气泡 spring 弹入、频道/私信切换 out-in、弹窗 spring 曲线、按钮点击缩放、未读徽章脉冲
- **消息撤回**：仅频道消息，仅自己的消息。hover 气泡时左侧显示「撤回」按钮（`visibility` 切换，不影响布局），调 `recallViaHttp`。RECALL WebSocket 事件需在 `handleIncomingMessage` 中优先于 channelId 守卫处理（因为 RECALL 事件不带 channelId）。`MessageBubble` 中用 `isRecalled ? '消息已撤回' : message.content` 显示，避免刷新后显示原文
- **搜索**：频道和私信都是直接显示的 MacInput，无需点击按钮
