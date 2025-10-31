package cc.techox.boardgame.websocket;

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
    
    @SuppressWarnings("unused")
    private final AuthService authService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @SuppressWarnings("unused")
    private final GameEventBroadcaster eventBroadcaster;
    private final CommandRouter commandRouter;
    
    // 会话最后活跃时间
    private final Map<String, Long> sessionLastActivity = new ConcurrentHashMap<>();
    
    // 心跳检测定时器
    private ScheduledExecutorService heartbeatExecutor;
    
    public GameWebSocketHandler(AuthService authService, 
                               WebSocketSessionManager sessionManager,
                               GameEventBroadcaster eventBroadcaster,
                               CommandRouter commandRouter) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.eventBroadcaster = eventBroadcaster;
        this.commandRouter = commandRouter;
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
        try {
            WebSocketMessage welcomeMsg = WebSocketMessage.success("connected",
                Map.of("message", "连接成功，请发送认证信息", "sessionId", session.getId()));
            welcomeMsg.setKind("evt");
            session.sendMessage(new TextMessage(welcomeMsg.toJson()));
        } catch (Exception e) {
            System.err.println("发送欢迎消息失败: " + e.getMessage());
            // 如果连接刚建立就失败，可能是客户端问题，关闭连接
            try {
                session.close();
            } catch (Exception closeEx) {
                // 忽略关闭异常
            }
        }
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
        String cid = null;
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope.has("cid")) {
                cid = envelope.get("cid").asText();
            } else if (envelope.has("messageId")) {
                // 兼容旧客户端，将 messageId 作为 cid 返回
                cid = envelope.get("messageId").asText();
            }
            commandRouter.route(session, envelope);
        } catch (Exception e) {
            e.printStackTrace();
            if (cid != null) {
                sendError(session, "ROUTE_ERROR", "消息路由失败: " + e.getMessage(), cid);
            } else {
                sendError(session, "MESSAGE_PARSE_ERROR", "消息解析失败: " + e.getMessage(), null);
            }
        }
    }
    
    // 旧的具体处理方法已由 CommandRouter 统一接管
    
    private void sendError(WebSocketSession session, String code, String message, String cid) {
        if (session == null || !session.isOpen()) {
            System.err.println("尝试向已关闭的会话发送错误消息: " + code);
            return;
        }
        try {
            WebSocketMessage error = WebSocketMessage.error(code, message);
            error.setKind("err");
            if (cid != null) error.setCid(cid);
            session.sendMessage(new TextMessage(error.toJson()));
        } catch (Exception e) {
            System.err.println("发送错误消息失败: " + e.getMessage());
            // 会话可能已经关闭，不需要进一步处理
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