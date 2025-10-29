package cc.techox.boardgame.websocket;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class GameEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public GameEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 发布游戏开始事件
     */
    public void publishGameStarted(Long roomId, Long matchId) {
        eventPublisher.publishEvent(new GameStartedEvent(roomId, matchId));
    }
    
    /**
     * 发布房间状态更新事件
     */
    public void publishRoomUpdated(Long roomId) {
        eventPublisher.publishEvent(new RoomUpdatedEvent(roomId));
    }
    
    /**
     * 游戏开始事件
     */
    public static class GameStartedEvent {
        private final Long roomId;
        private final Long matchId;
        
        public GameStartedEvent(Long roomId, Long matchId) {
            this.roomId = roomId;
            this.matchId = matchId;
        }
        
        public Long getRoomId() { return roomId; }
        public Long getMatchId() { return matchId; }
    }
    
    /**
     * 房间更新事件
     */
    public static class RoomUpdatedEvent {
        private final Long roomId;
        
        public RoomUpdatedEvent(Long roomId) {
            this.roomId = roomId;
        }
        
        public Long getRoomId() { return roomId; }
    }
}