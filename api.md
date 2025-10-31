# Boardgame 后端 API 文档（31 个接口 + WebSocket 实时通信）

- 统一返回：除 UNO 专属接口外，均返回 `ApiResponse`
  - 格式：`{"success":boolean, "message":string, "data":any}`
- 鉴权：需要登录的接口在请求头携带 `Authorization: Bearer {session_token}`
- UNO 专属接口：返回原始 JSON（非 `ApiResponse`）
- CORS：通过 `cors.allowed-origins` 配置允许来源；本地开发使用 `dev` profile
- 编码：全面支持 UTF-8，中文昵称和消息正常显示
- **WebSocket**：✅ 已实现实时通信功能，支持认证、心跳、房间管理

---

## 健康检查（1）
- GET `/api/health`
  - resp: `ApiResponse<string>`
    - 成功：`{"success":true,"message":"OK","data":"healthy"}`

## 认证（4）
- POST `/api/register`
  - req: `{ "username": string, "password": string, "inviteCode": string, "displayName"?: string }`
  - resp.data: `{ "id": number, "username": string, "displayName": string, "role": string }`
  - 成功消息：`注册成功`
  - 说明：`displayName` 可选，未提供时使用 `username` 作为昵称；支持中文昵称
- POST `/api/login`
  - req: `{ "username": string, "password": string }`
  - resp.data: `{ "session_token": string, "user": { "id": number, "username": string, "displayName": string, "role": string } }`
  - 成功消息：`登录成功`
  - 说明：`role` 为 `"admin"` 或 `"user"`，用于前端权限判断
- POST `/api/logout`
  - header: `Authorization: Bearer {token}`
  - resp: `{"success":true,"message":"注销成功","data":null}`
- PUT `/api/profile`
  - header: `Authorization: Bearer {token}`
  - req: `{ "displayName"?: string, "currentPassword"?: string, "newPassword"?: string }`
  - resp.data: `{ "id": number, "username": string, "displayName": string, "role": string }`
  - 成功消息：`修改成功`
  - 说明：修改密码时 `currentPassword` 和 `newPassword` 必须同时提供；昵称支持中文

## 游戏目录（1）
- GET `/api/games`
  - resp.data: `[{ "code": string, "name": string, "minPlayers": number, "maxPlayers": number }]`
  - 成功消息：`ok`

## 房间（6）
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
- DELETE `/api/rooms/{id}/disband`
  - header: `Authorization: Bearer {token}`
  - resp: `{"success":true,"message":"房间已解散","data":null}`
  - 说明：只有房主可以解散等待中的房间

## 管理员（15）
- POST `/api/admin/invite-codes`
  - header: `Authorization: Bearer {admin-token}`
  - req: `{ "count": number (1-500), "batchNo"?: string, "expiresDays"?: number }`
  - resp.data: `{ "batchNo": string, "codes": string[], "expiresAt": string|null }`
  - 成功消息：`生成成功`
- GET `/api/admin/invite-codes`
  - header: `Authorization: Bearer {admin-token}`
  - query: `page`（默认1）, `size`（默认20，最大200）, `status`（可选：`used`/`unused`）, `batchNo`（可选：批次号模糊搜索）
  - resp.data: `{ "page": number, "size": number, "total": number, "items": [InviteCodeInfo] }`
    - `InviteCodeInfo`：`{ id, code, used, usedBy, usedAt, createdBy, createdAt, expiresAt, batchNo, expired }`
  - 成功消息：`ok`
  - 说明：支持按使用状态和批次号筛选，按创建时间倒序排列
- GET `/api/admin/invite-codes/stats`
  - header: `Authorization: Bearer {admin-token}`
  - resp.data: `{ "byUsedStatus": {"used": number, "unused": number}, "byBatchNo": {batchNo: number}, "byBatchNoAndUsedStatus": {batchNo: {"used": number, "unused": number}}, "summary": {"total": number, "used": number, "unused": number} }`
  - 成功消息：`ok`
  - 说明：邀请码统计信息，包含使用状态、批次分布等统计数据
- GET `/api/admin/users`
  - header: `Authorization: Bearer {admin-token}`
  - query: `page`（默认1）, `size`（默认20，最大200）, `role`（可选：`admin`/`user`）, `status`（可选：`active`/`banned`）, `search`（可选：用户名或显示名搜索）
  - resp.data: `{ "page": number, "size": number, "total": number, "items": [AdminUserInfo] }`
    - `AdminUserInfo`：`{ id, username, displayName, role, status, createdAt, updatedAt }`
  - 成功消息：`ok`
  - 说明：支持按角色、状态筛选和用户名搜索，按ID倒序排列
- GET `/api/admin/users/stats`
  - header: `Authorization: Bearer {admin-token}`
  - resp.data: `{ "byRole": {"admin": number, "user": number}, "byStatus": {"active": number, "banned": number}, "total": number }`
  - 成功消息：`ok`
  - 说明：用户统计信息，包含角色分布、状态分布等统计数据
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
- GET `/api/admin/rooms`
  - header: `Authorization: Bearer {admin-token}`
  - query: `page`（默认1）, `size`（默认20，最大200）, `status`（可选：`waiting`/`playing`/`finished`/`disbanded`）, `gameCode`（可选：游戏类型如`UNO`）, `name`（可选：房间名模糊搜索）
  - resp.data: `{ "page": number, "size": number, "total": number, "items": [RoomInfo] }`
    - `RoomInfo`：`{ id, name, gameCode, gameName, ownerUsername, status, maxPlayers, isPrivate, createdAt, updatedAt }`
  - 成功消息：`ok`
  - 说明：支持按状态、游戏类型筛选和房间名搜索，按创建时间倒序排列
- GET `/api/admin/rooms/stats`
  - header: `Authorization: Bearer {admin-token}`
  - resp.data: `{ "byStatus": {"waiting": number, "playing": number, "finished": number, "disbanded": number}, "byGameType": {gameCode: number}, "total": number }`
  - 成功消息：`ok`
  - 说明：房间统计信息，包含状态分布、游戏类型分布等统计数据
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

## WebSocket 实时通信（已实现）

### **连接与认证**
- **连接地址**：`ws://localhost:50001/ws` (开发环境)
- **认证方式**：连接后立即发送认证消息
- **心跳机制**：每30秒自动ping/pong，超时60秒断开连接
- **状态**：✅ 已完全实现并测试通过

### **消息格式规范**
所有WebSocket消息均为JSON格式：
```json
{
  "type": "消息类型",
  "data": "消息数据",
  "timestamp": "时间戳（自动生成）",
  "messageId": "消息ID（自动生成）"
}
```

### **1. 连接与认证流程**

**连接建立后服务端自动发送：**
```json
{
  "type": "connected",
  "data": {
    "message": "连接成功，请发送认证信息",
    "sessionId": "session_uuid"
  }
}
```

**客户端发送认证：**
```json
{
  "type": "auth",
  "data": {
    "token": "Bearer_token_here"
  }
}
```

**服务端认证响应：**
```json
// 成功
{
  "type": "auth_success",
  "data": {
    "userId": 123,
    "username": "alice",
    "displayName": "Alice",
    "role": "user"
  }
}

// 失败
{
  "type": "error",
  "data": {
    "code": "INVALID_TOKEN",
    "message": "令牌无效或已过期"
  }
}
```

### **2. 心跳检测**

**客户端发送心跳：**
```json
{
  "type": "ping"
}
```

**服务端响应：**
```json
{
  "type": "pong",
  "data": {
    "timestamp": 1698765432000
  }
}
```

### **3. 房间频道管理**

**加入房间频道：**
```json
{
  "type": "join_room",
  "data": {
    "roomId": 123
  }
}
```

**服务端响应：**
```json
{
  "type": "room_joined",
  "data": {
    "roomId": 123,
    "message": "成功加入房间"
  }
}
```

**离开房间频道：**
```json
{
  "type": "leave_room",
  "data": {
    "roomId": 123
  }
}
```

**服务端响应：**
```json
{
  "type": "room_left",
  "data": {
    "roomId": 123,
    "message": "已离开房间"
  }
}
```

### **4. 房间解散通知**

当房主或管理员解散房间时，服务端通过WS依次推送以下事件：

**即将解散（广播到房间频道）：**
```json
{
  "type": "room_disbanding",
  "data": {
    "roomId": 123,
    "initiatedBy": { "userId": 1, "username": "alice", "displayName": "Alice" },
    "reason": "房主解散房间"
  }
}
```

**逐个踢出（单播到各用户）：**
```json
{
  "type": "room_kicked",
  "data": {
    "roomId": 123,
    "reason": "房间已解散"
  }
}
```

**最终解散（可能无人订阅房间频道）：**
```json
{
  "type": "room_disbanded",
  "data": {
    "roomId": 123
  }
}
```

### **4. 错误处理**

**统一错误格式：**
```json
{
  "type": "error",
  "data": {
    "code": "错误代码",
    "message": "错误描述"
  }
}
```

**常见错误代码：**
- `AUTH_REQUIRED` - 需要先认证
- `INVALID_TOKEN` - 令牌无效
- `UNKNOWN_MESSAGE_TYPE` - 未知消息类型
- `MESSAGE_PARSE_ERROR` - 消息解析失败
- `JOIN_ROOM_ERROR` - 加入房间失败
- `LEAVE_ROOM_ERROR` - 离开房间失败

### **5. 实现状态**

**✅ 已实现功能：**
- WebSocket连接建立和管理
- 用户认证和会话管理
- 心跳检测和超时处理
- 房间频道加入/离开
- 错误处理和消息路由
- 会话超时自动清理

**🚧 计划中功能：**
- 游戏状态实时推送
- 玩家操作广播
- 断线重连状态同步
- 游戏事件通知

### **6. 技术实现细节**

**后端架构：**
- `GameWebSocketHandler` - 主要消息处理器
- `WebSocketSessionManager` - 会话和房间管理
- `WebSocketMessage` - 统一消息格式
- 心跳检测：30秒间隔，60秒超时
- 异步事件处理，避免阻塞

**连接管理：**
- 支持多用户并发连接
- 房间频道隔离
- 自动清理超时会话
- 优雅的错误处理

### **7. 测试验证**

**测试工具：** `websocket_test.html`
**测试Token：** 使用 `/api/login` 获取的有效token
**测试步骤：**
1. 建立WebSocket连接
2. 发送认证消息
3. 测试心跳功能
4. 测试房间操作
5. 验证错误处理

---

### **8. Flutter 集成指南**
      "userId": 1,
      "username": "alice"
    },
    "actionData": {
      "card": "R-5",
      "chosenColor": null
    },
    "newGameState": {
      "currentPlayer": 2,
      "topCard": "R-5",
      "players": [
        {"userId": 1, "handCount": 6},
        {"userId": 2, "handCount": 7, "hand": ["B-2", "G-3"]}
      ]
    }
  }
}
```

**游戏结束：**
```json
{
  "type": "game_finished",
  "data": {
    "matchId": 456,
    "winner": {
      "userId": 1,
      "username": "alice"
    },
    "finalState": {
      "players": [
        {"userId": 1, "handCount": 0, "score": 100},
        {"userId": 2, "handCount": 3, "score": 50}
      ]
    }
  }
}
```

### **4. 断线重连机制**

**连接状态检测：**
- 客户端每30秒发送心跳：`{"type": "ping"}`
- 服务端响应：`{"type": "pong"}`
- 超过60秒无响应视为断线

**重连流程：**
1. 检测到断线后，客户端自动重连（指数退避：1s, 2s, 4s, 8s, 最大30s）
2. 重连成功后重新认证
3. 发送状态同步请求：
```json
{
  "type": "sync_state",
  "data": {
    "lastMessageId": "msg_12345",
    "roomId": 123,
    "matchId": 456
  }
}
```

**服务端状态同步响应：**
```json
{
  "type": "state_sync",
  "data": {
    "room": {...},
    "match": {...},
    "missedMessages": [
      {"type": "game_action", "data": {...}, "messageId": "msg_12346"},
      {"type": "game_action", "data": {...}, "messageId": "msg_12347"}
    ]
  }
}
```

### **5. 错误处理**

**通用错误格式：**
```json
{
  "type": "error",
  "data": {
    "code": "INVALID_ACTION",
    "message": "不是你的回合",
    "details": {
      "currentPlayer": 2,
      "yourUserId": 1
    }
  }
}
```

**常见错误码：**
- `AUTH_REQUIRED`: 需要先认证
- `INVALID_TOKEN`: 令牌无效
- `ROOM_NOT_FOUND`: 房间不存在
- `GAME_NOT_FOUND`: 游戏不存在
- `INVALID_ACTION`: 操作无效
- `NOT_YOUR_TURN`: 不是你的回合
- `GAME_FINISHED`: 游戏已结束

### **6. 前端实现指南**

**基础连接管理：**
```javascript
class GameWebSocket {
  constructor(token) {
    this.token = token;
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 10;
    this.reconnectDelay = 1000;
    this.lastMessageId = null;
    this.currentRoomId = null;
    this.currentMatchId = null;
  }

  connect() {
    const wsUrl = process.env.NODE_ENV === 'production' 
      ? 'wss://boardgame.techox.cc/ws'
      : 'ws://localhost:50001/ws';
    
    this.ws = new WebSocket(wsUrl);
    this.ws.onopen = () => this.onOpen();
    this.ws.onmessage = (event) => this.onMessage(event);
    this.ws.onclose = () => this.onClose();
    this.ws.onerror = (error) => this.onError(error);
  }

  onOpen() {
    console.log('WebSocket连接成功');
    this.reconnectAttempts = 0;
    this.authenticate();
    this.startHeartbeat();
  }

  authenticate() {
    this.send({
      type: 'auth',
      data: { token: this.token }
    });
  }

  onMessage(event) {
    const message = JSON.parse(event.data);
    this.lastMessageId = message.messageId;
    
    switch (message.type) {
      case 'auth_success':
        this.onAuthSuccess(message.data);
        break;
      case 'room_updated':
        this.onRoomUpdated(message.data);
        break;
      case 'game_started':
        this.onGameStarted(message.data);
        break;
      case 'game_action':
        this.onGameAction(message.data);
        break;
      case 'error':
        this.onError(message.data);
        break;
    }
  }

  onClose() {
    console.log('WebSocket连接断开');
    this.stopHeartbeat();
    this.reconnect();
  }

  reconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      const delay = Math.min(this.reconnectDelay * Math.pow(2, this.reconnectAttempts), 30000);
      console.log(`${delay}ms后尝试重连...`);
      
      setTimeout(() => {
        this.reconnectAttempts++;
        this.connect();
      }, delay);
    }
  }

  startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      if (this.ws.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' });
      }
    }, 30000);
  }

  stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
    }
  }

  send(message) {
    if (this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        ...message,
        timestamp: new Date().toISOString(),
        messageId: this.generateMessageId()
      }));
    }
  }

  // 加入房间
  joinRoom(roomId) {
    this.currentRoomId = roomId;
    this.send({
      type: 'join_room',
      data: { roomId }
    });
  }

  // 游戏操作
  playCard(matchId, card, chosenColor = null) {
    this.send({
      type: 'play_card',
      data: { matchId, card, chosenColor }
    });
  }

  drawCard(matchId) {
    this.send({
      type: 'draw_card',
      data: { matchId }
    });
  }
}
```

**React集成示例：**
```javascript
// hooks/useGameWebSocket.js
import { useEffect, useRef, useState } from 'react';

export function useGameWebSocket(token) {
  const [connected, setConnected] = useState(false);
  const [gameState, setGameState] = useState(null);
  const [roomState, setRoomState] = useState(null);
  const wsRef = useRef(null);

  useEffect(() => {
    if (token) {
      wsRef.current = new GameWebSocket(token);
      wsRef.current.onAuthSuccess = () => setConnected(true);
      wsRef.current.onRoomUpdated = (data) => setRoomState(data.room);
      wsRef.current.onGameAction = (data) => setGameState(data.newGameState);
      wsRef.current.connect();
    }

    return () => {
      if (wsRef.current) {
        wsRef.current.disconnect();
      }
    };
  }, [token]);

  return {
    connected,
    gameState,
    roomState,
    joinRoom: (roomId) => wsRef.current?.joinRoom(roomId),
    playCard: (matchId, card, color) => wsRef.current?.playCard(matchId, card, color),
    drawCard: (matchId) => wsRef.current?.drawCard(matchId)
  };
}
```

**Vue集成示例：**
```javascript
// composables/useGameWebSocket.js
import { ref, onMounted, onUnmounted } from 'vue';

export function useGameWebSocket(token) {
  const connected = ref(false);
  const gameState = ref(null);
  const roomState = ref(null);
  let ws = null;

  onMounted(() => {
    if (token.value) {
      ws = new GameWebSocket(token.value);
      ws.onAuthSuccess = () => connected.value = true;
      ws.onRoomUpdated = (data) => roomState.value = data.room;
      ws.onGameAction = (data) => gameState.value = data.newGameState;
      ws.connect();
    }
  });

  onUnmounted(() => {
    ws?.disconnect();
  });

  return {
    connected,
    gameState,
    roomState,
    joinRoom: (roomId) => ws?.joinRoom(roomId),
    playCard: (matchId, card, color) => ws?.playCard(matchId, card, color),
    drawCard: (matchId) => ws?.drawCard(matchId)
  };
}
```

### **7. 性能优化建议**

**消息压缩：**
- 生产环境启用WebSocket压缩
- 大型游戏状态使用增量更新

**连接池管理：**
- 服务端限制单用户连接数
- 实现连接复用和负载均衡

**状态缓存：**
- Redis缓存房间和游戏状态
- 支持水平扩展和高可用

**监控告警：**
- WebSocket连接数监控
- 消息延迟和丢失率统计
- 断线重连成功率追踪

### **8. Flutter 集成指南**

**依赖添加 (pubspec.yaml)：**
```yaml
dependencies:
  web_socket_channel: ^2.4.0
  json_annotation: ^4.8.1
  
dev_dependencies:
  json_serializable: ^6.7.1
  build_runner: ^2.4.7
```

**WebSocket 消息模型：**
```dart
// models/websocket_message.dart
import 'package:json_annotation/json_annotation.dart';

part 'websocket_message.g.dart';

@JsonSerializable()
class WebSocketMessage {
  final String type;
  final Map<String, dynamic>? data;
  final String? timestamp;
  final String? messageId;

  WebSocketMessage({
    required this.type,
    this.data,
    this.timestamp,
    this.messageId,
  });

  factory WebSocketMessage.fromJson(Map<String, dynamic> json) =>
      _$WebSocketMessageFromJson(json);

  Map<String, dynamic> toJson() => _$WebSocketMessageToJson(this);
}

@JsonSerializable()
class GameState {
  final int currentPlayer;
  final int direction;
  final String topCard;
  final List<PlayerState> players;
  final int pendingDraw;
  final String? forcedColor;

  GameState({
    required this.currentPlayer,
    required this.direction,
    required this.topCard,
    required this.players,
    required this.pendingDraw,
    this.forcedColor,
  });

  factory GameState.fromJson(Map<String, dynamic> json) =>
      _$GameStateFromJson(json);
}

@JsonSerializable()
class PlayerState {
  final int userId;
  final int handCount;
  final List<String>? hand; // 只有自己能看到完整手牌

  PlayerState({
    required this.userId,
    required this.handCount,
    this.hand,
  });

  factory PlayerState.fromJson(Map<String, dynamic> json) =>
      _$PlayerStateFromJson(json);
}
```

**WebSocket 服务类：**
```dart
// services/game_websocket_service.dart
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as status;

class GameWebSocketService {
  WebSocketChannel? _channel;
  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;
  
  int _reconnectAttempts = 0;
  final int _maxReconnectAttempts = 10;
  final Duration _heartbeatInterval = Duration(seconds: 30);
  
  String? _token;
  String? _lastMessageId;
  int? _currentRoomId;
  int? _currentMatchId;
  
  // 事件流控制器
  final StreamController<WebSocketMessage> _messageController = 
      StreamController<WebSocketMessage>.broadcast();
  final StreamController<bool> _connectionController = 
      StreamController<bool>.broadcast();
  final StreamController<GameState> _gameStateController = 
      StreamController<GameState>.broadcast();
  
  // 公开的流
  Stream<WebSocketMessage> get messageStream => _messageController.stream;
  Stream<bool> get connectionStream => _connectionController.stream;
  Stream<GameState> get gameStateStream => _gameStateController.stream;
  
  bool get isConnected => _channel != null;
  
  Future<void> connect(String token) async {
    _token = token;
    
    try {
      final wsUrl = Platform.isAndroid 
          ? 'ws://10.0.2.2:50001/ws'  // Android 模拟器
          : 'ws://localhost:50001/ws'; // iOS 模拟器
      
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      
      // 监听消息
      _channel!.stream.listen(
        _onMessage,
        onError: _onError,
        onDone: _onDisconnected,
      );
      
      // 发送认证消息
      await _authenticate();
      
      // 启动心跳
      _startHeartbeat();
      
      _reconnectAttempts = 0;
      _connectionController.add(true);
      
    } catch (e) {
      print('WebSocket连接失败: $e');
      _scheduleReconnect();
    }
  }
  
  Future<void> _authenticate() async {
    final authMessage = WebSocketMessage(
      type: 'auth',
      data: {'token': _token},
      timestamp: DateTime.now().toIso8601String(),
      messageId: _generateMessageId(),
    );
    
    _sendMessage(authMessage);
  }
  
  void _onMessage(dynamic message) {
    try {
      final data = json.decode(message);
      final wsMessage = WebSocketMessage.fromJson(data);
      
      _lastMessageId = wsMessage.messageId;
      _messageController.add(wsMessage);
      
      // 处理特定消息类型
      switch (wsMessage.type) {
        case 'auth_success':
          print('WebSocket认证成功');
          break;
        case 'game_started':
        case 'game_action':
          if (wsMessage.data?['newGameState'] != null) {
            final gameState = GameState.fromJson(wsMessage.data!['newGameState']);
            _gameStateController.add(gameState);
          }
          break;
        case 'error':
          print('WebSocket错误: ${wsMessage.data}');
          break;
      }
    } catch (e) {
      print('消息解析失败: $e');
    }
  }
  
  void _onError(error) {
    print('WebSocket错误: $error');
    _connectionController.add(false);
    _scheduleReconnect();
  }
  
  void _onDisconnected() {
    print('WebSocket连接断开');
    _connectionController.add(false);
    _stopHeartbeat();
    _scheduleReconnect();
  }
  
  void _scheduleReconnect() {
    if (_reconnectAttempts >= _maxReconnectAttempts) {
      print('达到最大重连次数，停止重连');
      return;
    }
    
    final delay = Duration(
      seconds: (1 << _reconnectAttempts).clamp(1, 30)
    );
    
    print('${delay.inSeconds}秒后尝试重连...');
    
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(delay, () {
      _reconnectAttempts++;
      if (_token != null) {
        connect(_token!);
      }
    });
  }
  
  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(_heartbeatInterval, (timer) {
      if (isConnected) {
        _sendMessage(WebSocketMessage(type: 'ping'));
      }
    });
  }
  
  void _stopHeartbeat() {
    _heartbeatTimer?.cancel();
  }
  
  void _sendMessage(WebSocketMessage message) {
    if (_channel != null) {
      final json = message.toJson();
      _channel!.sink.add(jsonEncode(json));
    }
  }
  
  // 公开方法
  void joinRoom(int roomId) {
    _currentRoomId = roomId;
    _sendMessage(WebSocketMessage(
      type: 'join_room',
      data: {'roomId': roomId},
    ));
  }
  
  void leaveRoom(int roomId) {
    _sendMessage(WebSocketMessage(
      type: 'leave_room',
      data: {'roomId': roomId},
    ));
  }
  
  void playCard(int matchId, String card, {String? chosenColor}) {
    final data = <String, dynamic>{
      'matchId': matchId,
      'card': card,
    };
    if (chosenColor != null) {
      data['chosenColor'] = chosenColor;
    }
    
    _sendMessage(WebSocketMessage(
      type: 'play_card',
      data: data,
    ));
  }
  
  void drawCard(int matchId) {
    _sendMessage(WebSocketMessage(
      type: 'draw_card',
      data: {'matchId': matchId},
    ));
  }
  
  void syncState() {
    final data = <String, dynamic>{};
    if (_lastMessageId != null) {
      data['lastMessageId'] = _lastMessageId;
    }
    if (_currentRoomId != null) {
      data['roomId'] = _currentRoomId;
    }
    if (_currentMatchId != null) {
      data['matchId'] = _currentMatchId;
    }
    
    _sendMessage(WebSocketMessage(
      type: 'sync_state',
      data: data,
    ));
  }
  
  void disconnect() {
    _heartbeatTimer?.cancel();
    _reconnectTimer?.cancel();
    _channel?.sink.close(status.goingAway);
    _channel = null;
    _connectionController.add(false);
  }
  
  String _generateMessageId() {
    return DateTime.now().millisecondsSinceEpoch.toString();
  }
  
  void dispose() {
    disconnect();
    _messageController.close();
    _connectionController.close();
    _gameStateController.close();
  }
}
```

**Provider 状态管理集成：**
```dart
// providers/game_provider.dart
import 'package:flutter/foundation.dart';
import '../services/game_websocket_service.dart';
import '../models/websocket_message.dart';

class GameProvider extends ChangeNotifier {
  final GameWebSocketService _wsService = GameWebSocketService();
  
  bool _isConnected = false;
  GameState? _gameState;
  String? _connectionError;
  
  bool get isConnected => _isConnected;
  GameState? get gameState => _gameState;
  String? get connectionError => _connectionError;
  
  GameProvider() {
    _wsService.connectionStream.listen((connected) {
      _isConnected = connected;
      if (!connected) {
        _connectionError = '连接断开';
      } else {
        _connectionError = null;
      }
      notifyListeners();
    });
    
    _wsService.gameStateStream.listen((gameState) {
      _gameState = gameState;
      notifyListeners();
    });
    
    _wsService.messageStream.listen((message) {
      _handleMessage(message);
    });
  }
  
  Future<void> connect(String token) async {
    try {
      await _wsService.connect(token);
    } catch (e) {
      _connectionError = '连接失败: $e';
      notifyListeners();
    }
  }
  
  void joinRoom(int roomId) {
    _wsService.joinRoom(roomId);
  }
  
  void playCard(int matchId, String card, {String? chosenColor}) {
    _wsService.playCard(matchId, card, chosenColor: chosenColor);
  }
  
  void drawCard(int matchId) {
    _wsService.drawCard(matchId);
  }
  
  void _handleMessage(WebSocketMessage message) {
    switch (message.type) {
      case 'room_updated':
        // 处理房间更新
        break;
      case 'game_started':
        // 处理游戏开始
        break;
      case 'error':
        _connectionError = message.data?['message'] ?? '未知错误';
        notifyListeners();
        break;
    }
  }
  
  @override
  void dispose() {
    _wsService.dispose();
    super.dispose();
  }
}
```

**使用示例：**
```dart
// screens/game_screen.dart
class GameScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Consumer<GameProvider>(
      builder: (context, gameProvider, child) {
        return Scaffold(
          appBar: AppBar(
            title: Text('UNO游戏'),
            actions: [
              Icon(
                gameProvider.isConnected 
                    ? Icons.wifi 
                    : Icons.wifi_off,
                color: gameProvider.isConnected 
                    ? Colors.green 
                    : Colors.red,
              ),
            ],
          ),
          body: gameProvider.gameState != null
              ? GameBoard(gameState: gameProvider.gameState!)
              : Center(child: CircularProgressIndicator()),
        );
      },
    );
  }
}
```

---

## WebSocket 实际测试示例

### **测试工具使用**
1. **打开测试页面：** `file:///d:/DevelopProject/Java/boardgame/websocket_test.html`
2. **获取测试Token：**
```bash
curl -X POST http://localhost:50001/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"SpecialFox","password":"Specialfox233"}'
```

### **完整测试流程**
```javascript
// 1. 建立连接
const ws = new WebSocket('ws://localhost:50001/ws');

// 2. 连接成功后认证
ws.onopen = function() {
  ws.send(JSON.stringify({
    type: 'auth',
    data: { token: 'your_token_here' }
  }));
};

// 3. 心跳测试
ws.send(JSON.stringify({ type: 'ping' }));

// 4. 房间操作测试
ws.send(JSON.stringify({
  type: 'join_room',
  data: { roomId: 1 }
}));
```

### **预期响应示例**
```json
// 连接建立
{"type":"connected","data":{"message":"连接成功，请发送认证信息","sessionId":"uuid"}}

// 认证成功
{"type":"auth_success","data":{"userId":2,"username":"SpecialFox","displayName":"SpecialFox","role":"admin"}}

// 心跳响应
{"type":"pong","data":{"timestamp":1698765432000}}

// 房间操作成功
{"type":"room_joined","data":{"roomId":1,"message":"成功加入房间"}}
```

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
{"success":true,"message":"登录成功","data":{"session_token":"...","user":{"id":1,"username":"alice","displayName":"Alice","role":"user"}}}
```
- 管理员登录：
```json
{"success":true,"message":"登录成功","data":{"session_token":"...","user":{"id":2,"username":"admin","displayName":"管理员","role":"admin"}}}
```
- 创建房间成功：
```json
{"success":true,"message":"房间创建成功","data":{"id":10,"name":"UNO房","gameCode":"UNO","ownerId":1,"status":"waiting","maxPlayers":4,"isPrivate":false,"createdAt":"...","updatedAt":"..."}}
```