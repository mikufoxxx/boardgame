package cc.techox.boardgame.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    private String type;
    private Object data;
    private String timestamp;
    private String messageId;
    
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
    
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"data\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"消息序列化失败\"}}";
        }
    }
    
    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public static class ErrorData {
        public String code;
        public String message;
        public Object details;
    }
}