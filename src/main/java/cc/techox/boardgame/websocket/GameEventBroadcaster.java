package cc.techox.boardgame.websocket;

import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.RoomRepository;
import cc.techox.boardgame.repo.UserRepository;
import cc.techox.boardgame.service.UnoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GameEventBroadcaster {
    
    private final WebSocketSessionManager sessionManager;
    private final UnoService unoService;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameStateManager gameStateManager;
    
    public GameEventBroadcaster(WebSocketSessionManager sessionManager,
                               UnoService unoService,
                               RoomRepository roomRepository,
                               UserRepository userRepository,
                               GameStateManager gameStateManager) {
        this.sessionManager = sessionManager;
        this.unoService = unoService;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.gameStateManager = gameStateManager;
    }
    
    /**
     * 广播房间状态更新
     */
    public void broadcastRoomUpdate(Long roomId) {
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        if (room == null) return;
        
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("id", room.getId());
        roomData.put("name", room.getName());
        roomData.put("status", room.getStatus().name());
        roomData.put("maxPlayers", room.getMaxPlayers());
        roomData.put("gameCode", room.getGame().getCode());
        
        // 添加房主信息
        if (room.getOwner() != null) {
            Map<String, Object> ownerInfo = new HashMap<>();
            ownerInfo.put("userId", room.getOwner().getId());
            ownerInfo.put("username", room.getOwner().getUsername());
            ownerInfo.put("displayName", room.getOwner().getDisplayName());
            roomData.put("owner", ownerInfo);
        }
        
        // 从内存获取玩家状态
        Map<Long, GameStateManager.PlayerRoomState> memoryPlayers = gameStateManager.getRoomPlayers(roomId);
        
        // 构建玩家信息列表，包含用户详细信息
        roomData.put("players", memoryPlayers.entrySet().stream().map(entry -> {
            Long userId = entry.getKey();
            GameStateManager.PlayerRoomState playerState = entry.getValue();
            
            // 获取用户详细信息
            User user = userRepository.findById(userId).orElse(null);
            
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("userId", userId);
            playerInfo.put("ready", playerState.isReady());
            playerInfo.put("seatNo", playerState.getSeatNo());
            playerInfo.put("team", playerState.getTeam());
            playerInfo.put("joinedAt", playerState.getJoinedAt().toString());
            
            // 添加用户基本信息
            if (user != null) {
                playerInfo.put("username", user.getUsername());
                playerInfo.put("displayName", user.getDisplayName());
            } else {
                // 如果用户不存在，使用默认值
                playerInfo.put("username", "Unknown");
                playerInfo.put("displayName", "Unknown User");
            }
            
            return playerInfo;
        }).collect(Collectors.toList()));
        
        Map<String, Object> eventData = Map.of(
            "roomId", roomId,
            "room", roomData
        );
        
        String gameCode = room.getGame().getCode();
        String channel = ChannelNames.room(gameCode, roomId);
        WebSocketMessage message = WebSocketMessage.success("room_updated", eventData);
        message.setKind("evt");
        message.setGame(gameCode.toLowerCase());
        message.setChannel(channel);
        sessionManager.broadcastToRoom(roomId, message);
    }
    
    /**
     * 广播用户加入房间事件
     */
    public void broadcastRoomUserJoined(Long roomId, User user) {
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        String gameCode = room != null ? room.getGame().getCode().toLowerCase() : null;
        String channel = gameCode != null ? ChannelNames.room(gameCode, roomId) : null;
        Map<String, Object> eventData = Map.of(
            "roomId", roomId,
            "user", Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName()
            ),
            "action", "joined"
        );
        
        WebSocketMessage message = WebSocketMessage.success("room_user_event", eventData);
        message.setKind("evt");
        if (gameCode != null) { message.setGame(gameCode); }
        if (channel != null) { message.setChannel(channel); }
        sessionManager.broadcastToRoomExcept(roomId, user.getId(), message);
        
        // 同时更新房间状态
        broadcastRoomUpdate(roomId);
    }
    
    /**
     * 广播用户离开房间事件
     */
    public void broadcastRoomUserLeft(Long roomId, User user) {
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        String gameCode = room != null ? room.getGame().getCode().toLowerCase() : null;
        String channel = gameCode != null ? ChannelNames.room(gameCode, roomId) : null;
        Map<String, Object> eventData = Map.of(
            "roomId", roomId,
            "user", Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName()
            ),
            "action", "left"
        );
        
        WebSocketMessage message = WebSocketMessage.success("room_user_event", eventData);
        message.setKind("evt");
        if (gameCode != null) { message.setGame(gameCode); }
        if (channel != null) { message.setChannel(channel); }
        sessionManager.broadcastToRoom(roomId, message);
        
        // 同时更新房间状态
        broadcastRoomUpdate(roomId);
    }

    /**
     * 广播房间即将解散事件（通知房内所有玩家）
     */
    public void broadcastRoomDisbanding(Long roomId, User initiator, String reason) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("roomId", roomId);
        if (initiator != null) {
            eventData.put("initiatedBy", Map.of(
                "userId", initiator.getId(),
                "username", initiator.getUsername(),
                "displayName", initiator.getDisplayName()
            ));
        }
        if (reason != null) {
            eventData.put("reason", reason);
        }
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        String gameCode = room != null ? room.getGame().getCode().toLowerCase() : null;
        String channel = gameCode != null ? ChannelNames.room(gameCode, roomId) : null;
        WebSocketMessage message = WebSocketMessage.success("room_disbanding", eventData);
        message.setKind("evt");
        if (gameCode != null) message.setGame(gameCode);
        if (channel != null) message.setChannel(channel);
        sessionManager.broadcastToRoom(roomId, message);
    }

    /**
     * 将房间内所有玩家从频道踢出，并逐个发送通知
     */
    public void kickAllUsersFromRoom(Long roomId, String reason) {
        Set<Long> users = sessionManager.getRoomUsers(roomId);
        if (users == null || users.isEmpty()) return;
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        String gameCode = room != null ? room.getGame().getCode().toLowerCase() : null;
        String channel = gameCode != null ? ChannelNames.room(gameCode, roomId) : null;
        users.forEach(uid -> {
            WebSocketMessage notify = WebSocketMessage.success("room_kicked", Map.of(
                "roomId", roomId,
                "reason", reason
            ));
            notify.setKind("evt");
            if (gameCode != null) notify.setGame(gameCode);
            if (channel != null) notify.setChannel(channel);
            sessionManager.sendToUser(uid, notify);
            sessionManager.leaveRoom(uid, roomId);
        });
    }

    /**
     * 广播房间已解散事件
     */
    public void broadcastRoomDisbanded(Long roomId) {
        Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
        String gameCode = room != null ? room.getGame().getCode().toLowerCase() : null;
        String channel = gameCode != null ? ChannelNames.room(gameCode, roomId) : null;
        WebSocketMessage message = WebSocketMessage.success("room_disbanded", Map.of("roomId", roomId));
        message.setKind("evt");
        if (gameCode != null) message.setGame(gameCode);
        if (channel != null) message.setChannel(channel);
        sessionManager.broadcastToRoom(roomId, message);
    }
    
    /**
     * 广播游戏开始事件
     */
    public void broadcastGameStarted(Long roomId, Long matchId) {
        try {
            System.out.println("=== 广播游戏开始事件 ===");
            System.out.println("房间ID: " + roomId + ", 对局ID: " + matchId);
            
            // 检查游戏会话是否存在
            if (!gameStateManager.hasGameState(matchId)) {
                System.err.println("游戏状态不存在, matchId: " + matchId);
                return;
            }
            
            // 获取房间信息以确定游戏类型
            Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
            if (room == null) {
                System.err.println("房间不存在, roomId: " + roomId);
                return;
            }
            
            String gameCode = room.getGame().getCode().toLowerCase();
            System.out.println("游戏类型: " + gameCode);
            
            // 获取房间内的用户列表
            Set<Long> roomUsers = sessionManager.getRoomUsers(roomId);
            System.out.println("房间内用户数量: " + roomUsers.size() + ", 用户列表: " + roomUsers);
            
            // 为房间内每个用户生成个性化的游戏状态
            roomUsers.forEach(userId -> {
                try {
                    System.out.println("正在为用户 " + userId + " 生成游戏状态...");
                    Map<String, Object> gameState = unoService.view(matchId, userId);
                    System.out.println("用户 " + userId + " 的游戏状态生成完成");
                    
                    Map<String, Object> eventData = Map.of(
                        "roomId", roomId,
                        "matchId", matchId,
                        "gameState", gameState
                    );
                    
                    String channel = ChannelNames.match(gameCode, matchId);
                    WebSocketMessage message = WebSocketMessage.success("game_started", eventData);
                    message.setKind("evt");
                    message.setGame(gameCode);
                    message.setChannel(channel);
                    
                    System.out.println("正在向用户 " + userId + " 发送游戏开始消息...");
                    sessionManager.sendToUser(userId, message);
                    System.out.println("用户 " + userId + " 游戏开始消息发送完成");
                } catch (Exception e) {
                    System.err.println("为用户 " + userId + " 生成游戏状态失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            System.out.println("=== 游戏开始事件广播完成 ===");
        } catch (Exception e) {
            System.err.println("广播游戏开始事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 处理出牌操作
     */
    public void handlePlayCard(Long matchId, User user, String card, String chosenColor) {
        try {
            // 调用游戏服务处理出牌
            unoService.play(matchId, user, card, chosenColor);
            
            // 从游戏会话获取房间ID
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession == null) return;
            
            Long roomId = gameSession.getRoomId();
            
            // 广播游戏操作事件
            broadcastGameAction(roomId, matchId, user, "play_card", 
                Map.of("card", card, "chosenColor", chosenColor));
            
        } catch (Exception e) {
            // 发送错误消息给操作用户
            WebSocketMessage error = WebSocketMessage.error("PLAY_CARD_FAILED", e.getMessage());
            error.setKind("err");
            sessionManager.sendToUser(user.getId(), error);
        }
    }
    
    /**
     * 处理玩家摸牌操作
     */
    public void handleDrawCard(Long matchId, User user) {
        try {
            // 调用游戏服务处理摸牌
            unoService.drawAndPass(matchId, user);
            
            // 从游戏会话获取房间ID
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession == null) return;
            
            Long roomId = gameSession.getRoomId();
            
            // 广播游戏操作事件
            broadcastGameAction(roomId, matchId, user, "draw_card", Map.of());
            
        } catch (Exception e) {
            // 发送错误消息给操作用户
            WebSocketMessage error = WebSocketMessage.error("DRAW_CARD_FAILED", e.getMessage());
            error.setKind("err");
            sessionManager.sendToUser(user.getId(), error);
        }
    }
    
    /**
     * 广播游戏操作事件
     */
    private void broadcastGameAction(Long roomId, Long matchId, User player, String action, Map<String, Object> actionData) {
        // 为房间内每个用户生成个性化的游戏状态
        sessionManager.getRoomUsers(roomId).forEach(userId -> {
            try {
                Map<String, Object> gameState = unoService.view(matchId, userId);
                
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("matchId", matchId);
                eventData.put("action", action);
                eventData.put("player", Map.of(
                    "userId", player.getId(),
                    "username", player.getUsername(),
                    "displayName", player.getDisplayName()
                ));
                eventData.put("actionData", actionData);
                eventData.put("newGameState", gameState);
                
                String gameCode = roomRepository.findByIdWithGame(roomId).map(r -> r.getGame().getCode().toLowerCase()).orElse("uno");
                String channel = ChannelNames.match(gameCode, matchId);
                WebSocketMessage message = WebSocketMessage.success("game_action", eventData);
                message.setKind("evt");
                message.setGame(gameCode);
                message.setChannel(channel);
                sessionManager.sendToUser(userId, message);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        // 检查游戏是否结束
        checkGameFinished(roomId, matchId);
    }
    
    /**
     * 检查游戏是否结束并广播结束事件
     */
    private void checkGameFinished(Long roomId, Long matchId) {
        try {
            GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
            if (gameSession != null && "finished".equals(gameSession.getStatus())) {
                // 获取房间信息以确定游戏类型
                Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
                if (room == null) return;
                
                // 游戏结束，广播结束事件
                Map<String, Object> eventData = Map.of(
                    "matchId", matchId,
                    "status", "finished",
                    "endedAt", gameSession.getLastActionAt().toString()
                );
                
                String gameCode = room.getGame().getCode().toLowerCase();
                String channel = ChannelNames.match(gameCode, matchId);
                WebSocketMessage message = WebSocketMessage.success("game_finished", eventData);
                message.setKind("evt");
                message.setGame(gameCode);
                message.setChannel(channel);
                sessionManager.broadcastToRoom(roomId, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理状态同步请求
     */
    public void handleStateSync(WebSocketSession session, User user, JsonNode data, String cid) {
        try {
            Long roomId = sessionManager.getUserCurrentRoom(user.getId());
            Long matchId = data.has("matchId") ? data.get("matchId").asLong() : null;
            
            Map<String, Object> syncData = new HashMap<>();
            
            // 同步房间状态
            if (roomId != null) {
                Room room = roomRepository.findByIdWithGame(roomId).orElse(null);
                if (room != null) {
                    Map<String, Object> roomData = new HashMap<>();
                    roomData.put("id", room.getId());
                    roomData.put("name", room.getName());
                    roomData.put("status", room.getStatus().name());
                    
                    // 从内存获取玩家状态
                    Map<Long, GameStateManager.PlayerRoomState> memoryPlayers = gameStateManager.getRoomPlayers(roomId);
                    roomData.put("players", memoryPlayers.entrySet().stream().map(entry -> {
                        Long userId = entry.getKey();
                        GameStateManager.PlayerRoomState playerState = entry.getValue();
                        
                        Map<String, Object> playerInfo = new HashMap<>();
                        playerInfo.put("userId", userId);
                        playerInfo.put("ready", playerState.isReady());
                        playerInfo.put("seatNo", playerState.getSeatNo());
                        playerInfo.put("team", playerState.getTeam());
                        
                        return playerInfo;
                    }).collect(Collectors.toList()));
                    
                    syncData.put("room", roomData);
                }
            }
            
            // 同步游戏状态
            if (matchId != null) {
                Map<String, Object> gameState = unoService.view(matchId, user.getId());
                syncData.put("match", gameState);
            }
            
            WebSocketMessage syncMessage = WebSocketMessage.success("state_sync", syncData);
            // 将状态同步视为对客户端 sync_state 请求的确认
            syncMessage.setKind("ack");
            if (cid != null) syncMessage.setCid(cid);
            // 推断频道与游戏：优先使用 match，其次 room
            String gameCode = null;
            String channel = null;
            if (matchId != null) {
                GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId).orElse(null);
                if (gameSession != null) {
                    // 从房间获取游戏代码
                    Room room = roomRepository.findByIdWithGame(gameSession.getRoomId()).orElse(null);
                    if (room != null) {
                        gameCode = room.getGame().getCode().toLowerCase();
                        channel = ChannelNames.match(gameCode, matchId);
                    }
                }
            } else if (roomId != null) {
                Room r = roomRepository.findByIdWithGame(roomId).orElse(null);
                if (r != null) {
                    gameCode = r.getGame().getCode().toLowerCase();
                    channel = ChannelNames.room(gameCode, roomId);
                }
            }
            if (gameCode != null) syncMessage.setGame(gameCode);
            if (channel != null) syncMessage.setChannel(channel);
            session.sendMessage(new TextMessage(syncMessage.toJson()));
            
        } catch (Exception e) {
            WebSocketMessage error = WebSocketMessage.error("SYNC_FAILED", "状态同步失败: " + e.getMessage());
            error.setKind("err");
            try {
                session.sendMessage(new TextMessage(error.toJson()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}