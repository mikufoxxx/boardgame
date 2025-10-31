package cc.techox.boardgame.websocket;

import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Component
public class CommandRouter {

    private final AuthService authService;
    private final WebSocketSessionManager sessionManager;
    private final GameEventBroadcaster eventBroadcaster;
    private final GameStateManager gameStateManager;

    public CommandRouter(AuthService authService,
                         WebSocketSessionManager sessionManager,
                         GameEventBroadcaster eventBroadcaster,
                         GameStateManager gameStateManager) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.eventBroadcaster = eventBroadcaster;
        this.gameStateManager = gameStateManager;
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
        
        // 同时更新 WebSocket 频道管理和游戏状态管理
        sessionManager.leaveRoom(user.getId(), roomId);
        gameStateManager.leaveRoom(roomId, user.getId());
        
        System.out.println("用户 " + user.getId() + " 离开房间 " + roomId + "，WebSocket频道和游戏状态已同步");
        
        sendAck(session, "room.leave", cid, Map.of("roomId", roomId, "left", true));
        eventBroadcaster.broadcastRoomUserLeft(roomId, user);
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
        Long matchId = data.get("matchId").asLong();
        String card = data.has("card") ? data.get("card").asText() : null;
        String chosenColor = data.has("color") ? data.get("color").asText() : null;
        eventBroadcaster.handlePlayCard(matchId, user, card, chosenColor);
        sendAck(session, "match.play", cid, Map.of("matchId", matchId));
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
        Long matchId = data.get("matchId").asLong();
        eventBroadcaster.handleDrawCard(matchId, user);
        sendAck(session, "match.draw", cid, Map.of("matchId", matchId));
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