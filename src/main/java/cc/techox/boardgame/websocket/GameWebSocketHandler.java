package cc.techox.boardgame.websocket;

import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GameWebSocketHandler implements WebSocketHandler {
    
    private final AuthService authService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 会话最后活跃时间
    private final Map<String, Long> sessionLastActivity = new ConcurrentHashMap<>();
    
    // 心跳检测定时器
    private ScheduledExecutorService heartbeatExecutor;
    
    public GameWebSocketHandler(AuthService authService, 
                               WebSocketSessionManager sessionManager) {
        this.authService = authService;
        this.sessionManager = sessionManager;
    }
    
    @PostConstruct
    public void init() {
        // 在Spring容器完全初始化后启动心跳检测
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2);
        startHeartbeatChecker();
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket连接建立: " + session.getId());
        sessionLastActivity.put(session.getId(), System.currentTimeMillis());
        
        // 发送连接成功消息，要求客户端进行认证
        WebSocketMessage welcomeMsg = WebSocketMessage.success("connected", 
            Map.of("message", "连接成功，请发送认证信息", "sessionId", session.getId()));
        session.sendMessage(new TextMessage(welcomeMsg.toJson()));
    }
    
    @Override
    public void handleMessage(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message) throws Exception {
        // 更新会话活跃时间
        sessionLastActivity.put(session.getId(), System.currentTimeMillis());
        
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            handleTextMessage(session, payload);
        }
    }
    
    private void handleTextMessage(WebSocketSession session, String payload) {
        try {
            JsonNode messageNode = objectMapper.readTree(payload);
            String type = messageNode.get("type").asText();
            JsonNode data = messageNode.get("data");
            
            switch (type) {
                case "auth":
                    handleAuth(session, data);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                case "join_room":
                    handleJoinRoom(session, data);
                    break;
                case "leave_room":
                    handleLeaveRoom(session, data);
                    break;
                default:
                    sendError(session, "UNKNOWN_MESSAGE_TYPE", "未知的消息类型: " + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, "MESSAGE_PARSE_ERROR", "消息解析失败: " + e.getMessage());
        }
    }
    
    private void handleAuth(WebSocketSession session, JsonNode data) {
        try {
            String token = data.get("token").asText();
            User user = authService.getUserByToken(token).orElse(null);
            
            if (user == null) {
                sendError(session, "INVALID_TOKEN", "令牌无效或已过期");
                return;
            }
            
            // 注册会话
            sessionManager.registerSession(session, user);
            
            // 发送认证成功消息
            Map<String, Object> authData = Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole().name()
            );
            
            WebSocketMessage authSuccess = WebSocketMessage.success("auth_success", authData);
            session.sendMessage(new TextMessage(authSuccess.toJson()));
            
        } catch (Exception e) {
            sendError(session, "AUTH_ERROR", "认证过程出错: " + e.getMessage());
        }
    }
    
    private void handlePing(WebSocketSession session) {
        try {
            WebSocketMessage pong = WebSocketMessage.success("pong", Map.of("timestamp", System.currentTimeMillis()));
            session.sendMessage(new TextMessage(pong.toJson()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleJoinRoom(WebSocketSession session, JsonNode data) {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendError(session, "AUTH_REQUIRED", "请先进行认证");
            return;
        }
        
        try {
            Long roomId = data.get("roomId").asLong();
            sessionManager.joinRoom(user.getId(), roomId);
            
            WebSocketMessage joinSuccess = WebSocketMessage.success("room_joined", 
                Map.of("roomId", roomId, "message", "成功加入房间"));
            session.sendMessage(new TextMessage(joinSuccess.toJson()));
            
        } catch (Exception e) {
            sendError(session, "JOIN_ROOM_ERROR", "加入房间失败: " + e.getMessage());
        }
    }
    
    private void handleLeaveRoom(WebSocketSession session, JsonNode data) {
        User user = sessionManager.getUserBySession(session);
        if (user == null) {
            sendError(session, "AUTH_REQUIRED", "请先进行认证");
            return;
        }
        
        try {
            Long roomId = data.get("roomId").asLong();
            sessionManager.leaveRoom(user.getId(), roomId);
            
            WebSocketMessage leaveSuccess = WebSocketMessage.success("room_left", 
                Map.of("roomId", roomId, "message", "已离开房间"));
            session.sendMessage(new TextMessage(leaveSuccess.toJson()));
            
        } catch (Exception e) {
            sendError(session, "LEAVE_ROOM_ERROR", "离开房间失败: " + e.getMessage());
        }
    }
    
    private void sendError(WebSocketSession session, String code, String message) {
        try {
            WebSocketMessage error = WebSocketMessage.error(code, message);
            session.sendMessage(new TextMessage(error.toJson()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket传输错误: " + session.getId() + " - " + exception.getMessage());
        sessionManager.removeSession(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        System.out.println("WebSocket连接关闭: " + session.getId() + " - " + closeStatus.toString());
        sessionManager.removeSession(session);
        sessionLastActivity.remove(session.getId());
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    /**
     * 启动心跳检测任务
     */
    private void startHeartbeatChecker() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                long currentTime = System.currentTimeMillis();
                long timeout = 60000; // 60秒超时
                
                sessionLastActivity.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue() > timeout) {
                        System.out.println("会话超时，准备关闭: " + entry.getKey());
                        return true;
                    }
                    return false;
                });
            }, 30, 30, TimeUnit.SECONDS);
        }
    }
}