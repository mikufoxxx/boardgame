# UNO 游戏 WebSocket API 文档

## 概述

本文档描述了 UNO 游戏的 WebSocket API 接口，包括所有可用的命令、响应格式和错误处理。

## 连接信息

- **WebSocket URL**: `ws://localhost:50000/ws`
- **协议**: WebSocket
- **消息格式**: JSON

## 通用消息格式

### 请求格式
```json
{
  "kind": "cmd",
  "type": "命令类型",
  "cid": "客户端消息ID",
  "data": {
    // 命令特定数据
  }
}
```

### 响应格式
```json
{
  "kind": "ack",
  "type": "响应类型",
  "cid": "对应的客户端消息ID",
  "data": {
    // 响应数据
  }
}
```

### 错误格式
```json
{
  "kind": "err",
  "code": "错误代码",
  "message": "错误描述",
  "cid": "对应的客户端消息ID"
}
```

## 认证命令

### 1. 用户认证
**命令**: `auth`

**请求**:
```json
{
  "kind": "cmd",
  "type": "auth",
  "cid": "auth_1",
  "data": {
    "token": "用户认证令牌"
  }
}
```

**成功响应**:
```json
{
  "kind": "ack",
  "type": "auth",
  "cid": "auth_1",
  "data": {
    "success": true,
    "user": {
      "id": 2,
      "username": "SpecialFox",
      "displayName": "SpecialFox"
    }
  }
}
```

## 房间管理命令

### 2. 加入房间
**命令**: `room.join`

**请求**:
```json
{
  "kind": "cmd",
  "type": "room.join",
  "cid": "join_1",
  "data": {
    "roomId": 26,
    "password": "房间密码（可选）"
  }
}
```

### 3. 离开房间
**命令**: `room.leave`

**请求**:
```json
{
  "kind": "cmd",
  "type": "room.leave",
  "cid": "leave_1",
  "data": {
    "roomId": 26
  }
}
```

### 4. 准备状态
**命令**: `room.ready`

**请求**:
```json
{
  "kind": "cmd",
  "type": "room.ready",
  "cid": "ready_1",
  "data": {
    "roomId": 26,
    "ready": true
  }
}
```

## 游戏状态命令

### 5. 获取游戏完整状态
**命令**: `get_game_state`

**请求**:
```json
{
  "kind": "cmd",
  "type": "get_game_state",
  "cid": "state_1",
  "data": {
    "matchId": 1001
  }
}
```

**成功响应**:
```json
{
  "kind": "ack",
  "type": "game_state_updated",
  "cid": "state_1",
  "data": {
    "match": {
      "currentPlayer": 0,
      "direction": 1,
      "drawCount": 0,
      "lastColor": "blue",
      "deckSize": 85,
      "topCard": {
        "id": "B-6",
        "color": "blue",
        "value": "6",
        "type": "number"
      },
      "started": true,
      "finished": false,
      "winnerUserId": null,
      "players": [
        {
          "userId": 2,
          "username": "SpecialFox",
          "displayName": "SpecialFox",
          "handSize": 7,
          "position": 0,
          "isReady": false,
          "hasCalledUno": false
        }
      ]
    },
    "myHand": [
      {
        "id": "B-SKIP",
        "color": "blue",
        "value": "skip",
        "type": "action"
      },
      {
        "id": "G-1",
        "color": "green",
        "value": "1",
        "type": "number"
      }
    ]
  }
}
```

## 游戏操作命令

### 6. 出牌
**命令**: `play_card`

**请求**:
```json
{
  "kind": "cmd",
  "type": "play_card",
  "cid": "play_1",
  "data": {
    "matchId": 1001,
    "cardId": "B-SKIP",
    "chosenColor": "red"  // 仅万能牌需要
  }
}
```

**成功响应**:
```json
{
  "kind": "ack",
  "type": "card_played",
  "cid": "play_1",
  "data": {
    "success": true,
    "gameAction": {
      "type": "card_played",
      "playerId": 2,
      "playerName": "SpecialFox",
      "card": "B-SKIP",
      "message": "SpecialFox 出了一张牌",
      "timestamp": "2024-01-01T12:00:00Z"
    },
    "updatedMatch": {
      // 完整的游戏状态
    },
    "updatedHand": [
      // 更新后的手牌
    ]
  }
}
```

**失败响应**:
```json
{
  "kind": "ack",
  "type": "play_card_error",
  "cid": "play_1",
  "data": {
    "success": false,
    "error": "invalid_card",
    "message": "这张牌无法在当前情况下出牌"
  }
}
```

### 7. 摸牌
**命令**: `draw_card`

**请求**:
```json
{
  "kind": "cmd",
  "type": "draw_card",
  "cid": "draw_1",
  "data": {
    "matchId": 1001
  }
}
```

**成功响应**:
```json
{
  "kind": "ack",
  "type": "card_drawn",
  "cid": "draw_1",
  "data": {
    "success": true,
    "drawnCards": [],
    "updatedHand": [
      // 更新后的手牌
    ],
    "updatedMatch": {
      // 更新后的游戏状态
    },
    "gameAction": {
      "type": "card_drawn",
      "playerId": 2,
      "playerName": "SpecialFox",
      "drawCount": 1,
      "message": "SpecialFox 摸了 1 张牌",
      "timestamp": "2024-01-01T12:00:00Z"
    }
  }
}
```

## 旧版兼容命令

### 8. 同步状态（旧版）
**命令**: `sync_state`

### 9. 出牌（旧版）
**命令**: `match.play`

### 10. 摸牌（旧版）
**命令**: `match.draw`

## 通用命令

### 11. 心跳检测
**命令**: `ping`

**请求**:
```json
{
  "kind": "cmd",
  "type": "ping",
  "cid": "ping_1"
}
```

**响应**:
```json
{
  "kind": "ack",
  "type": "ping",
  "cid": "ping_1",
  "data": {
    "timestamp": 123456789
  }
}
```

## 卡牌对象格式

### 卡牌类型
- **number**: 数字牌 (0-9)
- **action**: 功能牌 (Skip, Reverse, Draw2)
- **wild**: 万能牌 (Wild, WildDraw4)

### 卡牌颜色
- **red**: 红色
- **green**: 绿色
- **blue**: 蓝色
- **yellow**: 黄色
- **black**: 黑色（万能牌）

### 卡牌示例
```json
{
  "id": "B-SKIP",
  "color": "blue",
  "value": "skip",
  "type": "action"
}
```

## 错误代码

| 错误代码 | 描述 |
|---------|------|
| `AUTH_REQUIRED` | 需要先进行认证 |
| `MISSING_MATCH_ID` | 缺少 matchId 参数 |
| `MISSING_PARAMETERS` | 缺少必要参数 |
| `GAME_STATE_ERROR` | 游戏状态错误 |
| `DRAW_CARD_ERROR` | 摸牌失败 |
| `UNKNOWN_MESSAGE_TYPE` | 未知的消息类型 |

## 游戏状态字段说明

| 字段 | 类型 | 描述 |
|------|------|------|
| `currentPlayer` | number | 当前玩家位置索引 |
| `direction` | number | 游戏方向 (1: 顺时针, -1: 逆时针) |
| `drawCount` | number | 待摸牌数量（惩罚摸牌） |
| `lastColor` | string | 当前有效颜色（万能牌选择的颜色） |
| `deckSize` | number | 牌库剩余数量 |
| `topCard` | object | 顶部弃牌 |
| `started` | boolean | 游戏是否已开始 |
| `finished` | boolean | 游戏是否已结束 |
| `winnerUserId` | number/null | 获胜者用户ID |
| `mustDraw` | boolean | 当前玩家是否必须摸牌 |
| `playableCards` | array | 当前玩家可以出的牌列表 |

## UNO 游戏规则说明

### 出牌规则
1. **基本规则**: 出的牌必须与顶部弃牌颜色相同或数字/类型相同
2. **万能牌**: Wild和WildDraw4可以在任何时候出
3. **强制颜色**: 万能牌出后，下一张牌必须是指定颜色
4. **惩罚牌叠加**: 
   - Draw2只能叠加Draw2
   - WildDraw4可以叠加任何惩罚牌
   - 如果无法叠加，必须摸牌并跳过回合

### 特殊牌效果
- **Skip**: 跳过下一个玩家的回合
- **Reverse**: 改变游戏方向（2人游戏中相当于Skip）
- **Draw2**: 下一个玩家摸2张牌并跳过回合（除非叠加）
- **Wild**: 选择新的有效颜色
- **WildDraw4**: 下一个玩家摸4张牌并跳过回合，选择新的有效颜色

### 摸牌规则
- **正常摸牌**: 没有合法牌可出时摸1张牌并跳过回合
- **惩罚摸牌**: 面临Draw2/WildDraw4时必须摸相应数量的牌
- **强制摸牌**: 有待摸牌惩罚且无法叠加时，必须摸牌

### 胜利条件
- 手牌全部出完的玩家获胜
- 牌库耗尽时游戏结束（平局）

## 使用示例

### JavaScript 客户端示例
```javascript
const ws = new WebSocket('ws://localhost:50000/ws');

// 认证
ws.send(JSON.stringify({
  kind: "cmd",
  type: "auth",
  cid: "auth_1",
  data: { token: "your_token_here" }
}));

// 获取游戏状态
ws.send(JSON.stringify({
  kind: "cmd",
  type: "get_game_state",
  cid: "state_1",
  data: { matchId: 1001 }
}));

// 出牌
ws.send(JSON.stringify({
  kind: "cmd",
  type: "play_card",
  cid: "play_1",
  data: {
    matchId: 1001,
    cardId: "B-SKIP"
  }
}));

// 摸牌
ws.send(JSON.stringify({
  kind: "cmd",
  type: "draw_card",
  cid: "draw_1",
  data: { matchId: 1001 }
}));
```

## 注意事项

1. **认证**: 除了 `ping` 命令外，所有命令都需要先进行认证
2. **消息ID**: 建议为每个请求提供唯一的 `cid`，便于匹配响应
3. **错误处理**: 客户端应该处理所有可能的错误响应
4. **连接管理**: 建议实现自动重连机制
5. **状态同步**: 游戏状态变化会自动广播给房间内所有玩家

## 更新日志

- **v1.0**: 初始版本，包含基本的游戏命令
- **v1.1**: 添加新的游戏状态命令和标准化的响应格式
- **v1.2**: 优化卡牌对象格式，添加详细的错误处理
- **v1.3**: 修复UNO游戏核心逻辑，正确实现特殊牌效果和惩罚机制