package cc.techox.boardgame.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

@Component
public class SimpleWebSocketHandler implements WebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Simple WebSocket连接建立: " + session.getId());
        session.sendMessage(new TextMessage("连接成功"));
    }

    @Override
    public void handleMessage(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message) throws Exception {
        System.out.println("收到消息: " + message.getPayload());
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            session.sendMessage(new TextMessage("回复: " + payload));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket传输错误: " + session.getId() + " - " + exception.getMessage());
        exception.printStackTrace();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        System.out.println("Simple WebSocket连接关闭: " + session.getId() + " - " + closeStatus.toString());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}