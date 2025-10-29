package cc.techox.boardgame.websocket;

import cc.techox.boardgame.model.User;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {
    
    // 用户ID -> WebSocket会话
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    
    // 会话ID -> 用户信息
    private final Map<String, User> sessionUsers = new ConcurrentHashMap<>();
    
    // 房间ID -> 用户ID集合
    private final Map<Long, Set<Long>> roomChannels = new ConcurrentHashMap<>();
    
    // 用户ID -> 当前所在房间ID
    private final Map<Long, Long> userCurrentRoom = new ConcurrentHashMap<>();
    
    /**
     * 用户连接认证成功后注册会话
     */
    public void registerSession(WebSocketSession session, User user) {
        userSessions.put(user.getId(), session);
        sessionUsers.put(session.getId(), user);
    }
    
    /**
     * 移除会话
     */
    public void removeSession(WebSocketSession session) {
        User user = sessionUsers.remove(session.getId());
        if (user != null) {
            userSessions.remove(user.getId());
            // 离开当前房间
            Long roomId = userCurrentRoom.remove(user.getId());
            if (roomId != null) {
                leaveRoom(user.getId(), roomId);
            }
        }
    }
    
    /**
     * 用户加入房间频道
     */
    public void joinRoom(Long userId, Long roomId) {
        // 先离开之前的房间
        Long previousRoom = userCurrentRoom.get(userId);
        if (previousRoom != null && !previousRoom.equals(roomId)) {
            leaveRoom(userId, previousRoom);
        }
        
        roomChannels.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        userCurrentRoom.put(userId, roomId);
    }
    
    /**
     * 用户离开房间频道
     */
    public void leaveRoom(Long userId, Long roomId) {
        Set<Long> roomUsers = roomChannels.get(roomId);
        if (roomUsers != null) {
            roomUsers.remove(userId);
            if (roomUsers.isEmpty()) {
                roomChannels.remove(roomId);
            }
        }
        userCurrentRoom.remove(userId);
    }
    
    /**
     * 向指定用户发送消息
     */
    public boolean sendToUser(Long userId, WebSocketMessage message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message.toJson()));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    
    /**
     * 向房间内所有用户广播消息
     */
    public void broadcastToRoom(Long roomId, WebSocketMessage message) {
        Set<Long> roomUsers = roomChannels.get(roomId);
        if (roomUsers != null) {
            roomUsers.forEach(userId -> sendToUser(userId, message));
        }
    }
    
    /**
     * 向房间内除指定用户外的其他用户广播消息
     */
    public void broadcastToRoomExcept(Long roomId, Long excludeUserId, WebSocketMessage message) {
        Set<Long> roomUsers = roomChannels.get(roomId);
        if (roomUsers != null) {
            roomUsers.stream()
                    .filter(userId -> !userId.equals(excludeUserId))
                    .forEach(userId -> sendToUser(userId, message));
        }
    }
    
    /**
     * 获取用户当前所在房间
     */
    public Long getUserCurrentRoom(Long userId) {
        return userCurrentRoom.get(userId);
    }
    
    /**
     * 获取房间内的用户列表
     */
    public Set<Long> getRoomUsers(Long roomId) {
        return roomChannels.getOrDefault(roomId, Collections.emptySet());
    }
    
    /**
     * 获取在线用户数量
     */
    public int getOnlineUserCount() {
        return userSessions.size();
    }
    
    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    /**
     * 根据会话获取用户信息
     */
    public User getUserBySession(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }
}