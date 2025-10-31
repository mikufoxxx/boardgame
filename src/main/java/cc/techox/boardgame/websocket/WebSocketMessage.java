package cc.techox.boardgame.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    // 统一Envelope扩展字段（可选，兼容旧客户端）
    private String kind;      // cmd | ack | evt | err
    private String type;
    private Object data;
    private String timestamp;
    private String messageId;
    private String cid;       // 客户端请求ID（可选）
    private String channel;   // room:uno:123 / match:uno:456
    private String game;      // uno | chess | ...
    
    public WebSocketMessage() {
        this.timestamp = LocalDateTime.now().toString();
        this.messageId = UUID.randomUUID().toString();
    }
    
    public WebSocketMessage(String type, Object data) {
        this();
        this.type = type;
        this.data = data;
    }
    
    public static WebSocketMessage success(String type, Object data) {
        return new WebSocketMessage(type, data);
    }
    
    public static WebSocketMessage error(String code, String message) {
        ErrorData errorData = new ErrorData();
        errorData.code = code;
        errorData.message = message;
        return new WebSocketMessage("error", errorData);
    }
    
    public static WebSocketMessage error(String code, String message, Object details) {
        ErrorData errorData = new ErrorData();
        errorData.code = code;
        errorData.message = message;
        errorData.details = details;
        return new WebSocketMessage("error", errorData);
    }

    // 统一Envelope构造辅助
    public static WebSocketMessage ack(String type, String cid, Object data) {
        WebSocketMessage m = new WebSocketMessage(type, data);
        m.setKind("ack");
        m.setCid(cid);
        return m;
    }

    public static WebSocketMessage evt(String type, String game, String channel, Object data) {
        WebSocketMessage m = new WebSocketMessage(type, data);
        m.setKind("evt");
        m.setGame(game);
        m.setChannel(channel);
        return m;
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"data\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"消息序列化失败\"}}";
        }
    }

    // Getters and Setters
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getCid() { return cid; }
    public void setCid(String cid) { this.cid = cid; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getGame() { return game; }
    public void setGame(String game) { this.game = game; }
    
    public static class ErrorData {
        public String code;
        public String message;
        public Object details;
    }
}