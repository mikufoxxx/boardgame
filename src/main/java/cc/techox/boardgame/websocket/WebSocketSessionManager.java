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
    
    // 会话ID -> 认证状态（防止重复认证）
    private final Map<String, Boolean> sessionAuthStatus = new ConcurrentHashMap<>();
    
    /**
     * 用户连接认证成功后注册会话
     * @return true 如果是新注册或替换了旧会话，false 如果是重复注册同一会话
     */
    public synchronized boolean registerSession(WebSocketSession session, User user) {
        // 检查当前会话是否已经关闭
        if (session == null || !session.isOpen()) {
            System.err.println("尝试注册已关闭的会话，用户: " + user.getId());
            return false;
        }
        
        // 检查会话是否已经认证过
        Boolean isAuthenticated = sessionAuthStatus.get(session.getId());
        if (Boolean.TRUE.equals(isAuthenticated)) {
            System.out.println("用户 " + user.getId() + " 会话已认证，跳过重复注册: " + session.getId());
            return false; // 已认证，不需要重复处理
        }
        
        // 检查用户是否已有活跃会话
        WebSocketSession existingSession = userSessions.get(user.getId());
        if (existingSession != null) {
            // 如果是同一个会话，标记为已认证并返回
            if (existingSession.getId().equals(session.getId())) {
                sessionAuthStatus.put(session.getId(), true);
                System.out.println("用户 " + user.getId() + " 同一会话首次认证: " + session.getId());
                return true; // 首次认证，需要发送响应
            }
            
            // 如果是不同的会话，关闭旧会话
            if (existingSession.isOpen()) {
                System.out.println("用户 " + user.getId() + " 已有活跃会话，关闭旧会话: " + existingSession.getId());
                try {
                    existingSession.close();
                } catch (Exception e) {
                    System.err.println("关闭旧会话失败: " + e.getMessage());
                }
            }
            // 清理旧会话数据
            sessionUsers.remove(existingSession.getId());
            sessionAuthStatus.remove(existingSession.getId());
        }
        
        // 注册新会话
        userSessions.put(user.getId(), session);
        sessionUsers.put(session.getId(), user);
        sessionAuthStatus.put(session.getId(), true);
        System.out.println("用户 " + user.getId() + " 会话注册成功: " + session.getId());
        return true; // 新注册，需要发送响应
    }
    
    /**
     * 移除会话
     */
    public synchronized void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }
        
        User user = sessionUsers.remove(session.getId());
        sessionAuthStatus.remove(session.getId()); // 清理认证状态
        
        if (user != null) {
            // 只有当前会话是用户的活跃会话时才移除
            WebSocketSession currentSession = userSessions.get(user.getId());
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                userSessions.remove(user.getId());
                System.out.println("移除用户 " + user.getId() + " 的会话: " + session.getId());
            } else {
                System.out.println("会话 " + session.getId() + " 不是用户 " + user.getId() + " 的当前活跃会话，跳过移除");
            }
            
            // 离开当前房间
            Long roomId = userCurrentRoom.remove(user.getId());
            if (roomId != null) {
                leaveRoom(user.getId(), roomId);
            }
        } else {
            System.out.println("移除未注册的会话: " + session.getId());
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
                System.err.println("向用户 " + userId + " 发送消息失败: " + e.getMessage());
                // 连接可能已断开，清理会话
                removeSession(session);
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