package cc.techox.boardgame.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class GameEventListener {
    
    private final GameEventBroadcaster eventBroadcaster;
    
    public GameEventListener(GameEventBroadcaster eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }
    
    @EventListener
    @Async
    public void handleGameStarted(GameEventPublisher.GameStartedEvent event) {
        eventBroadcaster.broadcastGameStarted(event.getRoomId(), event.getMatchId());
    }
    
    @EventListener
    @Async
    public void handleRoomUpdated(GameEventPublisher.RoomUpdatedEvent event) {
        eventBroadcaster.broadcastRoomUpdate(event.getRoomId());
    }
}