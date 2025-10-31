# 项目结构说明

## 📁 **后端项目结构**

```
src/main/java/cc/techox/boardgame/
├── config/                     # 配置管理
│   └── GameDataManager.java   # 游戏数据管理器（从文件加载卡牌、规则等）
├── controller/                 # HTTP API 控制器
│   ├── AdminController.java    # 管理员接口
│   ├── AuthController.java     # 认证接口
│   ├── GameController.java     # 游戏列表接口
│   ├── MemoryStatsController.java # 内存监控接口
│   ├── RoomController.java     # 房间管理接口
│   ├── RoomPlayerController.java # 房间玩家接口
│   └── UnoController.java      # UNO游戏接口
├── memory/                     # 内存状态管理
│   └── GameStateManager.java  # 游戏状态内存管理器
├── model/                      # 数据模型
│   ├── User.java              # 用户模型
│   ├── Room.java              # 房间模型
│   ├── RoomPlayer.java        # 房间玩家模型（简化）
│   ├── Match.java             # 对局模型（简化）
│   ├── MatchAction.java       # 对局动作记录（简化）
│   └── ...                    # 其他模型
├── service/                    # 业务服务
│   ├── AuthService.java       # 认证服务
│   ├── RoomService.java       # 房间服务
│   └── UnoService.java        # UNO游戏服务
├── websocket/                  # WebSocket 通信
│   ├── GameWebSocketHandler.java    # WebSocket 处理器
│   ├── WebSocketSessionManager.java # 会话管理
│   ├── GameEventBroadcaster.java    # 事件广播
│   ├── CommandRouter.java           # 命令路由
│   └── ChannelNames.java           # 频道命名工具
├── game/uno/                   # UNO游戏引擎
│   ├── UnoEngine.java         # UNO游戏逻辑
│   ├── UnoState.java          # UNO游戏状态
│   └── UnoCard.java           # UNO卡牌定义
├── util/                       # 工具类
│   ├── AuthUtil.java          # 认证工具
│   ├── HashUtil.java          # 哈希工具
│   └── TokenUtil.java         # 令牌工具
└── common/                     # 通用类
    └── ApiResponse.java       # 统一响应格式

src/main/resources/
├── gamedata/                   # 游戏数据文件
│   ├── uno_cards.json         # UNO卡牌定义
│   └── uno_config.json        # UNO游戏配置
├── i18n/                      # 国际化文件
│   └── uno_zh_CN.json         # 中文本地化
└── application.properties      # 应用配置

项目根目录/
├── api_and_ws.md              # 完整API文档
├── MEMORY_ANALYSIS.md         # 内存使用分析
├── DATA_OPTIMIZATION.md       # 数据优化方案
├── PROJECT_STRUCTURE.md       # 项目结构说明
├── database_migration.sql     # 数据库迁移脚本
└── websocket_test.html        # WebSocket测试工具
```

## 🔄 **数据流转架构**

### HTTP API 流程
```
前端请求 → Controller → Service → Repository → 数据库
                    ↓
                GameDataManager (文件数据)
                    ↓
                GameStateManager (内存状态)
```

### WebSocket 流程
```
前端WebSocket → GameWebSocketHandler → CommandRouter → Service
                                                        ↓
                                              GameStateManager
                                                        ↓
                                              GameEventBroadcaster
                                                        ↓
                                              WebSocketSessionManager
                                                        ↓
                                                  前端WebSocket
```

## 📊 **数据存储策略**

### 🗄️ **数据库存储** (MySQL)
**用途**: 持久化数据、用户信息、历史记录
```sql
-- 核心表
users               -- 用户账户
auth_sessions       -- 登录会话
rooms               -- 房间基本信息
room_players        -- 房间成员（仅加入时间）
matches             -- 对局记录（仅基本信息）
match_actions       -- 重要事件记录
games               -- 游戏类型定义
invite_codes        -- 邀请码
admin_audit_logs    -- 管理日志
chat_messages       -- 聊天记录
```

### 📁 **文件存储** (JSON)
**用途**: 静态配置、卡牌数据、本地化文本
```json
gamedata/uno_cards.json     -- 卡牌定义（108张UNO牌）
gamedata/uno_config.json    -- 游戏规则配置
i18n/uno_zh_CN.json         -- 中文界面文本
```

### 💾 **内存存储** (Java对象)
**用途**: 高频变化数据、实时游戏状态
```java
GameStateManager {
    Map<Long, GameStateData> gameStates;     // 游戏状态
    Map<Long, Map<Long, PlayerRoomState>> roomPlayers; // 房间玩家状态
}
```

## 🚀 **性能优化特点**

### ⚡ **响应速度**
- **游戏操作**: 0.01ms (内存访问)
- **卡牌数据**: 0.01ms (内存缓存)
- **配置读取**: 0.01ms (内存缓存)
- **数据库查询**: 仅用于持久化数据

### 💾 **内存使用**
- **单局游戏**: 1.5KB
- **1000局同时**: 1.5MB
- **卡牌数据**: 5KB (所有游戏共享)
- **配置数据**: 2KB (所有游戏共享)

### 🛡️ **安全保护**
- **内存限制**: 最大100MB，1000个同时游戏
- **自动清理**: 2小时未访问自动清理
- **强制保护**: JVM内存超80%强制清理

## 🔌 **前端集成要点**

### 1. **HTTP API 使用**
```javascript
// 基础操作使用HTTP API
const api = new ApiClient('http://localhost:50001');
await api.login(username, password);
await api.createRoom(name, 'uno', 4);
```

### 2. **WebSocket 连接**
```javascript
// 实时通信使用WebSocket
const ws = new GameWebSocket('ws://localhost:50001/ws');
await ws.connect();
await ws.joinRoom(roomId);
```

### 3. **消息格式**
```json
// 统一的Envelope格式
{
  "kind": "cmd|evt|ack|err",
  "type": "消息类型",
  "cid": "客户端请求ID",
  "data": {},
  "channel": "频道名称",
  "game": "游戏代码"
}
```

### 4. **错误处理**
```javascript
// 统一的错误处理
try {
  const result = await api.request('POST', '/api/rooms', data);
} catch (error) {
  console.error('API错误:', error.message);
}
```

## 🔧 **开发和调试**

### 1. **本地开发**
```bash
# 启动后端服务
mvn spring-boot:run

# 访问测试页面
file:///path/to/websocket_test.html
```

### 2. **监控接口**
```bash
# 查看内存使用情况
GET /api/admin/memory/stats

# 查看活跃游戏
GET /api/admin/memory/active-games
```

### 3. **日志调试**
```java
// 在开发环境启用详细日志
logging.level.cc.techox.boardgame=DEBUG
```

## 📝 **前端开发建议**

### 1. **状态管理**
- 使用Vuex/Redux管理全局状态
- WebSocket连接状态、用户信息、房间状态等

### 2. **组件设计**
```
components/
├── Auth/           # 登录注册组件
├── Lobby/          # 大厅房间列表
├── Room/           # 房间内组件
├── Game/           # 游戏界面组件
└── Common/         # 通用组件
```

### 3. **网络层封装**
```javascript
// 建议的网络层结构
network/
├── ApiClient.js    # HTTP API客户端
├── WebSocketClient.js # WebSocket客户端
├── ErrorHandler.js # 错误处理
└── ReconnectManager.js # 重连管理
```

这个架构设计保证了高性能、高可用性和良好的开发体验！