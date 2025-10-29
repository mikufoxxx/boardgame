package cc.techox.boardgame.websocket;

import cc.techox.boardgame.model.Match;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.RoomPlayer;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.MatchRepository;
import cc.techox.boardgame.repo.RoomPlayerRepository;
import cc.techox.boardgame.repo.RoomRepository;
import cc.techox.boardgame.service.UnoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GameEventBroadcaster {
    
    private final WebSocketSessionManager sessionManager;
    private final UnoService unoService;
    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final MatchRepository matchRepository;
    
    public GameEventBroadcaster(WebSocketSessionManager sessionManager,
                               UnoService unoService,
                               RoomRepository roomRepository,
                               RoomPlayerRepository roomPlayerRepository,
                               MatchRepository matchRepository) {
        this.sessionManager = sessionManager;
        this.unoService = unoService;
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.matchRepository = matchRepository;
    }
    
    /**
     * 广播房间状态更新
     */
    public void broadcastRoomUpdate(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return;
        
        List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoom(room);
        
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("id", room.getId());
        roomData.put("name", room.getName());
        roomData.put("status", room.getStatus().name());
        roomData.put("maxPlayers", room.getMaxPlayers());
        roomData.put("gameCode", room.getGame().getCode());
        roomData.put("players", roomPlayers.stream().map(rp -> {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("userId", rp.getUser().getId());
            playerInfo.put("username", rp.getUser().getUsername());
            playerInfo.put("displayName", rp.getUser().getDisplayName());
            playerInfo.put("ready", rp.isReady());
            return playerInfo;
        }).collect(Collectors.toList()));
        
        Map<String, Object> eventData = Map.of(
            "roomId", roomId,
            "room", roomData
        );
        
        WebSocketMessage message = WebSocketMessage.success("room_updated", eventData);
        sessionManager.broadcastToRoom(roomId, message);
    }
    
    /**
     * 广播用户加入房间事件
     */
    public void broadcastRoomUserJoined(Long roomId, User user) {
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
        sessionManager.broadcastToRoomExcept(roomId, user.getId(), message);
        
        // 同时更新房间状态
        broadcastRoomUpdate(roomId);
    }
    
    /**
     * 广播用户离开房间事件
     */
    public void broadcastRoomUserLeft(Long roomId, User user) {
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
        sessionManager.broadcastToRoom(roomId, message);
        
        // 同时更新房间状态
        broadcastRoomUpdate(roomId);
    }
    
    /**
     * 广播游戏开始事件
     */
    public void broadcastGameStarted(Long roomId, Long matchId) {
        try {
            // 获取游戏初始状态
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null) return;
            
            // 为房间内每个用户生成个性化的游戏状态
            sessionManager.getRoomUsers(roomId).forEach(userId -> {
                Map<String, Object> gameState = unoService.view(matchId, userId);
                
                Map<String, Object> eventData = Map.of(
                    "roomId", roomId,
                    "matchId", matchId,
                    "gameState", gameState
                );
                
                WebSocketMessage message = WebSocketMessage.success("game_started", eventData);
                sessionManager.sendToUser(userId, message);
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理玩家出牌操作
     */
    public void handlePlayCard(Long matchId, User user, String card, String chosenColor) {
        try {
            // 调用游戏服务处理出牌
            Map<String, Object> newGameState = unoService.play(matchId, user, card, chosenColor);
            
            // 获取对局信息
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null) return;
            
            Long roomId = match.getRoom().getId();
            
            // 广播游戏操作事件
            broadcastGameAction(roomId, matchId, user, "play_card", 
                Map.of("card", card, "chosenColor", chosenColor));
            
        } catch (Exception e) {
            // 发送错误消息给操作用户
            WebSocketMessage error = WebSocketMessage.error("PLAY_CARD_FAILED", e.getMessage());
            sessionManager.sendToUser(user.getId(), error);
        }
    }
    
    /**
     * 处理玩家摸牌操作
     */
    public void handleDrawCard(Long matchId, User user) {
        try {
            // 调用游戏服务处理摸牌
            Map<String, Object> newGameState = unoService.drawAndPass(matchId, user);
            
            // 获取对局信息
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match == null) return;
            
            Long roomId = match.getRoom().getId();
            
            // 广播游戏操作事件
            broadcastGameAction(roomId, matchId, user, "draw_card", Map.of());
            
        } catch (Exception e) {
            // 发送错误消息给操作用户
            WebSocketMessage error = WebSocketMessage.error("DRAW_CARD_FAILED", e.getMessage());
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
                
                WebSocketMessage message = WebSocketMessage.success("game_action", eventData);
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
            Match match = matchRepository.findById(matchId).orElse(null);
            if (match != null && match.getStatus() == Match.Status.finished) {
                // 游戏结束，广播结束事件
                Map<String, Object> eventData = Map.of(
                    "matchId", matchId,
                    "status", "finished",
                    "endedAt", match.getEndedAt().toString()
                );
                
                WebSocketMessage message = WebSocketMessage.success("game_finished", eventData);
                sessionManager.broadcastToRoom(roomId, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理状态同步请求
     */
    public void handleStateSync(WebSocketSession session, User user, JsonNode data) {
        try {
            Long roomId = sessionManager.getUserCurrentRoom(user.getId());
            Long matchId = data.has("matchId") ? data.get("matchId").asLong() : null;
            
            Map<String, Object> syncData = new HashMap<>();
            
            // 同步房间状态
            if (roomId != null) {
                Room room = roomRepository.findById(roomId).orElse(null);
                if (room != null) {
                    List<RoomPlayer> roomPlayers = roomPlayerRepository.findByRoom(room);
                    Map<String, Object> roomData = new HashMap<>();
                    roomData.put("id", room.getId());
                    roomData.put("name", room.getName());
                    roomData.put("status", room.getStatus().name());
                    roomData.put("players", roomPlayers.stream().map(rp -> Map.of(
                        "userId", rp.getUser().getId(),
                        "username", rp.getUser().getUsername(),
                        "ready", rp.isReady()
                    )).collect(Collectors.toList()));
                    
                    syncData.put("room", roomData);
                }
            }
            
            // 同步游戏状态
            if (matchId != null) {
                Map<String, Object> gameState = unoService.view(matchId, user.getId());
                syncData.put("match", gameState);
            }
            
            WebSocketMessage syncMessage = WebSocketMessage.success("state_sync", syncData);
            session.sendMessage(new TextMessage(syncMessage.toJson()));
            
        } catch (Exception e) {
            WebSocketMessage error = WebSocketMessage.error("SYNC_FAILED", "状态同步失败: " + e.getMessage());
            try {
                session.sendMessage(new TextMessage(error.toJson()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}