package cc.techox.boardgame.websocket;

import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.RoomService;
import cc.techox.boardgame.service.UnoService;
import cc.techox.boardgame.game.uno.UnoCard;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CommandRouter {

    private final AuthService authService;
    private final WebSocketSessionManager sessionManager;
    private final GameEventBroadcaster eventBroadcaster;
    private final GameStateManager gameStateManager;
    private final RoomService roomService;
    private final UnoService unoService;

    public CommandRouter(AuthService authService,
                         WebSocketSessionManager sessionManager,
                         GameEventBroadcaster eventBroadcaster,
                         GameStateManager gameStateManager,
                         RoomService roomService,
                         UnoService unoService) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.eventBroadcaster = eventBroadcaster;
        this.gameStateManager = gameStateManager;
        this.roomService = roomService;
        this.unoService = unoService;
    }

    public void route(WebSocketSession session, JsonNode envelope) throws Exception {
        String kind = envelope.has("kind") ? envelope.get("kind").asText() : "cmd";
        String type = envelope.has("type") ? envelope.get("type").asText() : null;
        JsonNode data = envelope.has("data") ? envelope.get("data") : null;
        String cid = envelope.has("cid") ? envelope.get("cid").asText()
                : (envelope.has("messageId") ? envelope.get("messageId").asText() : null);

        // 只处理命令类型的消息
        if (!"cmd".equals(kind) && type != null) {
            // 兼容旧格式，没有kind字段的消息默认为命令
            kind = "cmd";
        }
        
        if (!"cmd".equals(kind)) {
            sendErr(session, "INVALID_KIND", "只接受 kind=cmd 的命令消息", cid);
            return;
        }

        if (type == null) {
            sendErr(session, "MISSING_TYPE", "缺少 type 字段", cid);
            return;
        }

        switch (normalizeType(type)) {
            case "auth":
                handleAuth(session, data, cid);
                break;
            case "ping":
                // ping 命令不需要认证，直接响应
                try {
                    // 使用相对时间戳，避免 JavaScript 精度问题
                    long relativeTimestamp = System.currentTimeMillis() % 1000000000L; // 取模减小数值
                    sendAck(session, "ping", cid, Map.of("timestamp", relativeTimestamp));
                    System.out.println("处理 ping 请求，会话: " + session.getId());
                } catch (Exception e) {
                    System.err.println("处理 ping 请求失败: " + e.getMessage());
                }
                break;
            case "room.join":
                handleJoinRoom(session, data, cid);
                break;
            case "room.leave":
                handleLeaveRoom(session, data, cid);
                break;
            case "room.ready":
                handleRoomReady(session, data, cid);
                break;
            case "sync_state":
                handleSyncState(session, data, cid);
                break;
            case "match.play":
                handleMatchPlay(session, data, cid);
                break;
            case "match.draw":
                handleMatchDraw(session, data, cid);
                break;
            case "get_game_state":
                handleGetGameState(session, data, cid);
                break;
            case "play_card":
                handlePlayCard(session, data, cid);
                break;
            case "draw_card":
                handleDrawCard(session, data, cid);
                break;
            case "call_uno":
                handleCallUno(session, data, cid);
                break;
            case "challenge_wild_draw4":
                handleChallengeWildDraw4(session, data, cid);
                break;
            case "penalize_forget_uno":
                handlePenalizeForgetUno(session, data, cid);
                break;
            default:
                sendErr(session, "UNKNOWN_MESSAGE_TYPE", "未知的消息类型: " + type, cid);
        }
    }

    private String normalizeType(String t) {
        return switch (t) {
            case "join_room" -> "room.join";
            case "leave_room" -> "room.leave";
            case "ready" -> "room.ready";
            case "play_card" -> "match.play";
            case "draw_card" -> "match.draw";
            default -> t;
        };
    }

    private void handleAuth(WebSocketSession session, JsonNode data, String cid) throws Exception {
        // 检查会话状态
        if (session == null || !session.isOpen()) {
            System.err.println("尝试在已关闭的会话上进行认证");
            return;
        }
        
        if (data == null || !data.has("token")) {
            sendErr(session, "INVALID_TOKEN", "缺少 token", cid);
            return;
        }
        
        String token = data.get("token").asText();
        User user = authService.getUserByToken(token).orElse(null);
        if (user == null) {
            sendErr(session, "INVALID_TOKEN", "令牌无效或已过期", cid);
            return;
        }
        
        // 注册会话，获取是否需要发送响应的标志
        boolean shouldSendResponse = sessionManager.registerSession(session, user);
        
        // 检查会话状态并发送响应（无论是否重复注册都要发送响应）
        if (session.isOpen()) {
            sendAck(session, "auth", cid, Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "role", user.getRole().name()
            ));
            if (!shouldSendResponse) {
                System.out.println("重复认证请求，已发送认证成功响应给用户: " + user.getId());
            }
        } else {
            System.err.println("会话在认证过程中被关闭，用户: " + user.getId());
        }
    }

    private void handleJoinRoom(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        if (data == null || !data.has("roomId")) {
            sendErr(session, "JOIN_ROOM_ERROR", "缺少 roomId", cid);
            return;
        }
        Long roomId = data.get("roomId").asLong();
        
        // 同时更新 WebSocket 频道管理和游戏状态管理
        sessionManager.joinRoom(user.getId(), roomId);
        gameStateManager.joinRoom(roomId, user.getId());
        
        System.out.println("用户 " + user.getId() + " 加入房间 " + roomId + "，WebSocket频道和游戏状态已同步");
        
        sendAck(session, "room.join", cid, Map.of("roomId", roomId, "joined", true));
        eventBroadcaster.broadcastRoomUserJoined(roomId, user);
    }

    private void handleLeaveRoom(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        if (data == null || !data.has("roomId")) {
            sendErr(session, "LEAVE_ROOM_ERROR", "缺少 roomId", cid);
            return;
        }
        Long roomId = data.get("roomId").asLong();
        
        try {
            // 获取房间信息
            Room room = roomService.getRoomById(roomId);
            
            // 调用 RoomService.leaveRoom 处理房主转让逻辑
            roomService.leaveRoom(room, user);
            
            // 同时更新 WebSocket 频道管理
            sessionManager.leaveRoom(user.getId(), roomId);
            
            System.out.println("用户 " + user.getUsername() + " 离开房间 " + roomId + "，已处理房主转让逻辑");
            
            sendAck(session, "room.leave", cid, Map.of("roomId", roomId, "left", true));
        } catch (IllegalArgumentException e) {
            sendErr(session, "LEAVE_ROOM_ERROR", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("处理离开房间请求失败: " + e.getMessage());
            sendErr(session, "LEAVE_ROOM_ERROR", "离开房间失败", cid);
        }
    }

    private void handleRoomReady(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        if (data == null || !data.has("roomId")) {
            sendErr(session, "ROOM_READY_ERROR", "缺少 roomId", cid);
            return;
        }
        Long roomId = data.get("roomId").asLong();
        boolean ready = data.has("ready") && data.get("ready").asBoolean();
        
        // 更新玩家在房间中的准备状态
        gameStateManager.setPlayerReady(roomId, user.getId(), ready);
        
        System.out.println("用户 " + user.getId() + " 在房间 " + roomId + " 设置准备状态: " + ready);
        
        sendAck(session, "room.ready", cid, Map.of("roomId", roomId, "ready", ready));
        eventBroadcaster.broadcastRoomUpdate(roomId);
    }

    private void handleSyncState(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        eventBroadcaster.handleStateSync(session, user, data, cid);
    }

    private void handleMatchPlay(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        if (data == null || !data.has("matchId")) {
            sendErr(session, "PLAY_CARD_FAILED", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            String card = data.has("card") ? data.get("card").asText() : null;
            String chosenColor = data.has("color") ? data.get("color").asText() : null;
            
            if (card == null) {
                sendErr(session, "MISSING_CARD", "缺少 card 参数", cid);
                return;
            }
            
            // 调用游戏服务处理出牌
            Map<String, Object> result = unoService.play(matchId, user, card, chosenColor);
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "card_played",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "card", UnoCard.codeToObject(card),
                "message", user.getDisplayName() + " 出了一张牌",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据，包含完整的游戏状态
            Map<String, Object> responseData = Map.of(
                "success", true,
                "matchId", matchId,
                "gameAction", gameAction,
                "updatedMatch", result,
                "updatedHand", result.get("players") != null ? 
                    ((List<?>) result.get("players")).stream()
                        .filter(p -> ((Map<?, ?>) p).get("userId").equals(user.getId()))
                        .findFirst()
                        .map(p -> {
                            Object hand = ((Map<?, ?>) p).get("hand");
                            return hand instanceof List ? (List<?>) hand : Collections.emptyList();
                        })
                        .orElse(Collections.emptyList()) : 
                    Collections.emptyList()
            );
            
            sendAck(session, "match.play", cid, responseData);
            
            // 广播游戏状态更新给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                // 广播游戏操作事件给房间内所有玩家
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "play_card", 
                    Map.of("card", UnoCard.codeToObject(card), "chosenColor", chosenColor != null ? chosenColor : ""));
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "PLAY_CARD_FAILED", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("出牌失败: " + e.getMessage());
            sendErr(session, "PLAY_CARD_FAILED", "出牌失败", cid);
        }
    }

    private void handleMatchDraw(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        if (data == null || !data.has("matchId")) {
            sendErr(session, "DRAW_CARD_FAILED", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Map<String, Object> result = unoService.drawAndPassWithDetails(matchId, user);
            
            // 从结果中获取摸牌信息
            int actualDrawCount = (Integer) result.getOrDefault("drawCount", 1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> drawnCardObjects = (List<Map<String, Object>>) result.getOrDefault("drawnCards", Collections.emptyList());
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "card_drawn",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "drawCount", actualDrawCount,
                "message", user.getDisplayName() + " 摸了 " + actualDrawCount + " 张牌",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据，包含完整的游戏状态
            Map<String, Object> responseData = Map.of(
                "success", true,
                "matchId", matchId,
                "gameAction", gameAction,
                "drawnCards", drawnCardObjects,
                "updatedMatch", result,
                "updatedHand", result.get("players") != null ? 
                    ((List<?>) result.get("players")).stream()
                        .filter(p -> ((Map<?, ?>) p).get("userId").equals(user.getId()))
                        .findFirst()
                        .map(p -> {
                            Object hand = ((Map<?, ?>) p).get("hand");
                            return hand instanceof List ? (List<?>) hand : Collections.emptyList();
                        })
                        .orElse(Collections.emptyList()) : 
                    Collections.emptyList()
            );
            
            sendAck(session, "match.draw", cid, responseData);
            
            // 广播游戏状态更新给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                // 广播游戏操作事件给房间内所有玩家
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "draw_card", 
                    Map.of("drawCount", actualDrawCount));
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "DRAW_CARD_FAILED", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("摸牌失败: " + e.getMessage());
            sendErr(session, "DRAW_CARD_FAILED", "摸牌失败", cid);
        }
    }

    private void sendAck(WebSocketSession session, String type, String cid, Object payload) throws Exception {
        if (session == null || !session.isOpen()) {
            System.err.println("尝试向已关闭的会话发送 ACK 消息: " + type);
            return;
        }
        try {
            WebSocketMessage ack = WebSocketMessage.ack(type, cid, payload);
            session.sendMessage(new TextMessage(ack.toJson()));
        } catch (Exception e) {
            System.err.println("发送 ACK 消息失败: " + e.getMessage());
            // 不重新抛出异常，避免影响其他处理
        }
    }

    /**
     * 处理获取游戏状态命令
     */
    private void handleGetGameState(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId")) {
            sendErr(session, "MISSING_MATCH_ID", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Map<String, Object> gameState = unoService.view(matchId, user.getId());
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "match", gameState,
                "myHand", gameState.get("players") != null ? 
                    ((List<?>) gameState.get("players")).stream()
                        .filter(p -> ((Map<?, ?>) p).get("userId").equals(user.getId()))
                        .findFirst()
                        .map(p -> {
                            Object hand = ((Map<?, ?>) p).get("hand");
                            return hand instanceof List ? (List<?>) hand : Collections.emptyList();
                        })
                        .orElse(Collections.emptyList()) : 
                    Collections.emptyList()
            );
            
            sendAck(session, "game_state_updated", cid, responseData);
        } catch (IllegalArgumentException e) {
            sendErr(session, "GAME_STATE_ERROR", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("获取游戏状态失败: " + e.getMessage());
            sendErr(session, "GAME_STATE_ERROR", "获取游戏状态失败", cid);
        }
    }

    /**
     * 处理出牌命令
     */
    private void handlePlayCard(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId") || !data.has("cardId")) {
            sendErr(session, "MISSING_PARAMETERS", "缺少 matchId 或 cardId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            String cardId = data.get("cardId").asText();
            String chosenColor = data.has("chosenColor") ? data.get("chosenColor").asText() : null;
            
            Map<String, Object> result = unoService.play(matchId, user, cardId, chosenColor);
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "card_played",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "card", UnoCard.codeToObject(cardId),
                "message", user.getDisplayName() + " 出了一张牌",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "success", true,
                "gameAction", gameAction,
                "updatedMatch", result,
                "updatedHand", result.get("players") != null ? 
                    ((List<?>) result.get("players")).stream()
                        .filter(p -> ((Map<?, ?>) p).get("userId").equals(user.getId()))
                        .findFirst()
                        .map(p -> {
                            Object hand = ((Map<?, ?>) p).get("hand");
                            return hand instanceof List ? (List<?>) hand : Collections.emptyList();
                        })
                        .orElse(Collections.emptyList()) : 
                    Collections.emptyList()
            );
            
            sendAck(session, "card_played", cid, responseData);
            
            // 广播游戏状态更新给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                // 广播游戏操作事件给房间内所有玩家
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "play_card", 
                    Map.of("card", UnoCard.codeToObject(cardId), "chosenColor", chosenColor != null ? chosenColor : ""));
            }
            
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorData = Map.of(
                "success", false,
                "error", "invalid_card",
                "message", e.getMessage()
            );
            sendAck(session, "play_card_error", cid, errorData);
        } catch (Exception e) {
            System.err.println("出牌失败: " + e.getMessage());
            Map<String, Object> errorData = Map.of(
                "success", false,
                "error", "play_failed",
                "message", "出牌失败"
            );
            sendAck(session, "play_card_error", cid, errorData);
        }
    }

    /**
     * 处理摸牌命令
     */
    private void handleDrawCard(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId")) {
            sendErr(session, "MISSING_MATCH_ID", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Map<String, Object> result = unoService.drawAndPassWithDetails(matchId, user);
            
            // 从结果中获取摸牌信息
            int actualDrawCount = (Integer) result.getOrDefault("drawCount", 1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> drawnCardObjects = (List<Map<String, Object>>) result.getOrDefault("drawnCards", Collections.emptyList());
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "card_drawn",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "drawCount", actualDrawCount,
                "message", user.getDisplayName() + " 摸了 " + actualDrawCount + " 张牌",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "success", true,
                "drawnCards", drawnCardObjects,
                "updatedHand", result.get("players") != null ? 
                    ((List<?>) result.get("players")).stream()
                        .filter(p -> ((Map<?, ?>) p).get("userId").equals(user.getId()))
                        .findFirst()
                        .map(p -> {
                            Object hand = ((Map<?, ?>) p).get("hand");
                            return hand instanceof List ? (List<?>) hand : Collections.emptyList();
                        })
                        .orElse(Collections.emptyList()) : 
                    Collections.emptyList(),
                "updatedMatch", result,
                "gameAction", gameAction
            );
            
            sendAck(session, "card_drawn", cid, responseData);
            
            // 广播游戏状态更新给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                // 广播游戏操作事件给房间内所有玩家
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "draw_card", 
                    Map.of("drawCount", actualDrawCount));
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "DRAW_CARD_ERROR", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("摸牌失败: " + e.getMessage());
            sendErr(session, "DRAW_CARD_ERROR", "摸牌失败", cid);
        }
    }

    /**
     * 处理 UNO 调用命令
     */
    private void handleCallUno(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId")) {
            sendErr(session, "MISSING_MATCH_ID", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Map<String, Object> result = unoService.callUno(matchId, user);
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "uno_called",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "message", user.getDisplayName() + " 喊了 UNO！",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "success", true,
                "matchId", matchId,
                "gameAction", gameAction,
                "updatedMatch", result
            );
            
            sendAck(session, "uno_called", cid, responseData);
            
            // 广播 UNO 调用事件给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "call_uno", Map.of());
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "CALL_UNO_FAILED", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("UNO 调用失败: " + e.getMessage());
            sendErr(session, "CALL_UNO_FAILED", "UNO 调用失败", cid);
        }
    }

    /**
     * 处理 +4 质疑命令
     */
    private void handleChallengeWildDraw4(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId")) {
            sendErr(session, "MISSING_MATCH_ID", "缺少 matchId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Map<String, Object> result = unoService.challengeWildDraw4(matchId, user);
            
            boolean challengeSuccessful = (Boolean) result.get("challengeSuccessful");
            String reason = (String) result.get("reason");
            int penaltyCards = (Integer) result.get("penaltyCards");
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "challenge_wild_draw4",
                "playerId", user.getId(),
                "playerName", user.getDisplayName(),
                "challengeSuccessful", challengeSuccessful,
                "penaltyCards", penaltyCards,
                "reason", reason,
                "message", user.getDisplayName() + (challengeSuccessful ? " 质疑成功！" : " 质疑失败！"),
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "success", true,
                "matchId", matchId,
                "gameAction", gameAction,
                "challengeSuccessful", challengeSuccessful,
                "penaltyCards", penaltyCards,
                "reason", reason,
                "updatedMatch", result
            );
            
            sendAck(session, "challenge_result", cid, responseData);
            
            // 广播质疑结果给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "challenge_wild_draw4", 
                    Map.of("challengeSuccessful", challengeSuccessful, "penaltyCards", penaltyCards, "reason", reason));
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "CHALLENGE_FAILED", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("质疑失败: " + e.getMessage());
            sendErr(session, "CHALLENGE_FAILED", "质疑失败", cid);
        }
    }

    /**
     * 处理 UNO 惩罚命令
     */
    private void handlePenalizeForgetUno(WebSocketSession session, JsonNode data, String cid) throws Exception {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendErr(session, "AUTH_REQUIRED", "请先进行认证", cid);
            return;
        }
        
        if (data == null || !data.has("matchId") || !data.has("penalizedPlayerId")) {
            sendErr(session, "MISSING_PARAMETERS", "缺少 matchId 或 penalizedPlayerId", cid);
            return;
        }
        
        try {
            Long matchId = data.get("matchId").asLong();
            Long penalizedPlayerId = data.get("penalizedPlayerId").asLong();
            
            Map<String, Object> result = unoService.penalizeForgetUno(matchId, penalizedPlayerId, user);
            
            int penaltyCards = (Integer) result.get("penaltyCards");
            String reason = (String) result.get("reason");
            
            // 构建游戏动作信息
            Map<String, Object> gameAction = Map.of(
                "type", "uno_penalty",
                "reporterId", user.getId(),
                "reporterName", user.getDisplayName(),
                "penalizedPlayerId", penalizedPlayerId,
                "penaltyCards", penaltyCards,
                "reason", reason,
                "message", user.getDisplayName() + " 举报玩家忘记喊 UNO，罚摸 " + penaltyCards + " 张牌",
                "timestamp", java.time.Instant.now().toString()
            );
            
            // 构建响应数据
            Map<String, Object> responseData = Map.of(
                "success", true,
                "matchId", matchId,
                "gameAction", gameAction,
                "penalizedPlayerId", penalizedPlayerId,
                "penaltyCards", penaltyCards,
                "reason", reason,
                "updatedMatch", result
            );
            
            sendAck(session, "uno_penalty_applied", cid, responseData);
            
            // 广播惩罚结果给房间内其他玩家
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null) {
                Long roomId = gameSession.getRoomId();
                eventBroadcaster.broadcastGameAction(roomId, matchId, user, "penalize_forget_uno", 
                    Map.of("penalizedPlayerId", penalizedPlayerId, "penaltyCards", penaltyCards, "reason", reason));
            }
            
        } catch (IllegalArgumentException e) {
            sendErr(session, "PENALTY_FAILED", e.getMessage(), cid);
        } catch (Exception e) {
            System.err.println("UNO 惩罚失败: " + e.getMessage());
            sendErr(session, "PENALTY_FAILED", "UNO 惩罚失败", cid);
        }
    }

    private void sendErr(WebSocketSession session, String code, String msg, String cid) throws Exception {
        if (session == null || !session.isOpen()) {
            System.err.println("尝试向已关闭的会话发送错误消息: " + code);
            return;
        }
        try {
            WebSocketMessage err = WebSocketMessage.error(code, msg);
            err.setKind("err");
            if (cid != null) err.setCid(cid);
            session.sendMessage(new TextMessage(err.toJson()));
        } catch (Exception e) {
            System.err.println("发送错误消息失败: " + e.getMessage());
            // 不重新抛出异常，避免影响其他处理
        }
    }
}