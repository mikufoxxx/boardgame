# Boardgame 后端 API 文档（22 个接口 + WS 说明）

- 统一返回：除 UNO 专属接口外，均返回 `ApiResponse`
  - 格式：`{"success":boolean, "message":string, "data":any}`
- 鉴权：需要登录的接口在请求头携带 `Authorization: Bearer {session_token}`
- UNO 专属接口：返回原始 JSON（非 `ApiResponse`）
- CORS：通过 `cors.allowed-origins` 配置允许来源；本地开发使用 `dev` profile

---

## 健康检查（1）
- GET `/api/health`
  - resp: `ApiResponse<string>`
    - 成功：`{"success":true,"message":"OK","data":"healthy"}`

## 认证（3）
- POST `/api/register`
  - req: `{ "username": string, "password": string, "inviteCode": string }`
  - resp.data: `{ "id": number, "username": string, "displayName": string }`
  - 成功消息：`注册成功`
- POST `/api/login`
  - req: `{ "username": string, "password": string }`
  - resp.data: `{ "session_token": string, "user": { "id": number, "username": string, "displayName": string } }`
  - 成功消息：`登录成功`
- POST `/api/logout`
  - header: `Authorization: Bearer {token}`
  - resp: `{"success":true,"message":"注销成功","data":null}`

## 游戏目录（1）
- GET `/api/games`
  - resp.data: `[{ "code": string, "name": string, "minPlayers": number, "maxPlayers": number }]`
  - 成功消息：`ok`

## 房间（5）
- GET `/api/rooms`
  - resp.data: `RoomInfo[]`
    - `RoomInfo`：`{ id, name, gameCode, ownerId, status, maxPlayers, isPrivate, createdAt, updatedAt }`
  - 成功消息：`ok`
- POST `/api/rooms`
  - header: `Authorization: Bearer {token}`
  - req: `{ "name": string, "gameCode": string, "maxPlayers": number, "isPrivate": boolean, "password"?: string }`
  - resp.data: `RoomInfo`
  - 成功消息：`房间创建成功`
- POST `/api/rooms/{id}/join`
  - header: `Authorization: Bearer {token}`
  - req: `{ "password"?: string }`（私房必填）
  - resp: `{"success":true,"message":"加入成功","data":null}`
- POST `/api/rooms/{id}/leave`
  - header: `Authorization: Bearer {token}`
  - resp: `{"success":true,"message":"已离开","data":null}`
- POST `/api/rooms/{id}/ready`
  - header: `Authorization: Bearer {token}`
  - req: `{ "ready": boolean }`
  - resp: `{"success":true,"message":"状态已更新","data":null}`

## 管理员（8）
- POST `/api/admin/invite-codes`
  - header: `Authorization: Bearer {admin-token}`
  - req: `{ "count": number (1-500), "batchNo"?: string, "expiresDays"?: number }`
  - resp.data: `{ "batchNo": string, "codes": string[], "expiresAt": string|null }`
  - 成功消息：`生成成功`
- GET `/api/admin/users`
  - header: `Authorization: Bearer {admin-token}`
  - query: `page`（默认1）, `size`（默认20，最大200）
  - resp.data: `{ "page": number, "size": number, "total": number, "items": [AdminUserInfo] }`
    - `AdminUserInfo`：`{ id, username, displayName, role, status, createdAt, updatedAt }`
  - 成功消息：`ok`
- POST `/api/admin/users`
  - header: `Authorization: Bearer {admin-token}`
  - req: `{ "username": string, "password": string, "displayName"?: string, "role"?: "admin"|"user" }`
  - resp.data: `AdminUserInfo`
  - 成功消息：`创建成功`
- PUT `/api/admin/users/{id}/role`
  - header: `Authorization: Bearer {admin-token}`
  - req: `{ "role": "admin"|"user" }`
  - resp.data: `AdminUserInfo`
  - 成功消息：`更新成功`
- PUT `/api/admin/users/{id}/password`
  - header: `Authorization: Bearer {admin-token}`
  - req: `{ "password": string }`
  - resp.data: `AdminUserInfo`
  - 成功消息：`重置成功`
- DELETE `/api/admin/users/{id}`
  - header: `Authorization: Bearer {admin-token}`
  - resp: `{"success":true,"message":"删除成功","data":null}`
- GET `/api/admin/audit-logs`
  - header: `Authorization: Bearer {admin-token}`
  - query: `page`, `size`
  - resp.data: `{ "page": number, "size": number, "total": number, "items": [AuditInfo] }`
    - `AuditInfo`：`{ id, action, operatorId, targetType, targetId, detail, createdAt }`
  - 成功消息：`ok`
- DELETE `/api/admin/rooms/{id}`
  - header: `Authorization: Bearer {admin-token}`
  - resp: `{"success":true,"message":"删除成功","data":null}`

## UNO（4，原始 JSON，非 ApiResponse）
- POST `/api/uno/rooms/{roomId}/start`
  - header: `Authorization: Bearer {token}`
  - req: `{}`（房主/管理员触发）
  - resp: `Match 初始/公共视图`（具体见返回示例）
- GET `/api/uno/matches/{id}`
  - header: `Authorization: Bearer {token}`
  - resp: `publicView`
    - 字段（示例）：
      ```json
      {
        "currentIdx": 0,
        "direction": 1,
        "pendingDraw": 0,
        "forcedColor": null,
        "top": "R-4",
        "players": [
          {"userId": 2, "handCount": 5, "hand": ["B-SKIP","R-1","Y-4","Y-8","Y-1"]},
          {"userId": 4, "handCount": 10}
        ]
      }
      ```
- POST `/api/uno/matches/{id}/play`
  - header: `Authorization: Bearer {token}`
  - req: `{ "card": string, "color"?: "R"|"G"|"B"|"Y" }`（当 `card` 为 `W-WILD` 或 `W-D4` 时必须提供 `color`）
  - resp: 最新 `publicView`
- POST `/api/uno/matches/{id}/draw-pass`
  - header: `Authorization: Bearer {token}`
  - req: `{}`
  - resp: 最新 `publicView`

---

## WebSocket（说明）
- 依赖：`spring-boot-starter-websocket`
- 现状：尚未实现具体 WS 端点与消息处理器（计划路径：`/ws/{session_token}`）
- 事件（规划）：`rooms_list`, `room_created`, `room_joined`, `room_updated`, `room_left`, `game_started`, `game_action_result`
- 约定（规划）：消息均为原生 JSON；根据事件类型携带对应 payload

---

## 错误响应（统一）
- 统一结构：`{"success":false, "message":"错误原因", "data":null}`
- 常见：
  - `未提供令牌`、`未登录或令牌无效`、`权限不足或令牌无效`
  - `房间不存在`、`密码错误`、`用户名已存在`
  - 管理员保护：`不能移除最后一个管理员`、`不能删除最后一个管理员`

## 示例（简）
- 登录成功：
```json
{"success":true,"message":"登录成功","data":{"session_token":"...","user":{"id":1,"username":"alice","displayName":"Alice"}}}
```
- 创建房间成功：
```json
{"success":true,"message":"房间创建成功","data":{"id":10,"name":"UNO房","gameCode":"UNO","ownerId":1,"status":"waiting","maxPlayers":4,"isPrivate":false,"createdAt":"...","updatedAt":"..."}}
```