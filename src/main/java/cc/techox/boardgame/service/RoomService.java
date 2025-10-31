package cc.techox.boardgame.service;

import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.*;
import cc.techox.boardgame.repo.*;
import cc.techox.boardgame.websocket.GameEventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RoomService {
    private final RoomRepository roomRepo;
    private final GameRepository gameRepo;
    private final GameEventBroadcaster eventBroadcaster;
    private final GameStateManager gameStateManager;

    public RoomService(RoomRepository roomRepo, 
                      GameRepository gameRepo, 
                      GameEventBroadcaster eventBroadcaster,
                      GameStateManager gameStateManager) {
        this.roomRepo = roomRepo;
        this.gameRepo = gameRepo;
        this.eventBroadcaster = eventBroadcaster;
        this.gameStateManager = gameStateManager;
    }

    public List<Room> listRooms() {
        return roomRepo.findAll();
    }

    @Transactional
    public Room createRoom(String name, String gameCode, Integer maxPlayers, boolean isPrivate, String passwordHash, User owner) {
        Game game = gameRepo.findByCode(gameCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("游戏不存在: " + gameCode));

        Room room = new Room();
        room.setName(name);
        room.setGame(game);
        room.setOwner(owner);
        room.setMaxPlayers(maxPlayers);
        room.setPrivateRoom(isPrivate);
        room.setPasswordHash(passwordHash);
        room.setStatus(Room.Status.waiting);
        room.setCreatedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());
        
        room = roomRepo.save(room);

        // 房主自动加入房间（只在内存中管理）
        joinRoom(room, owner);
        
        return room;
    }

    @Transactional
    public void joinRoom(Room room, User user) {
        // 检查房间是否已满（从内存获取当前玩家数）
        Map<Long, GameStateManager.PlayerRoomState> currentPlayers = gameStateManager.getRoomPlayers(room.getId());
        if (currentPlayers.size() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("房间已满");
        }

        // 检查是否已经在房间中
        if (currentPlayers.containsKey(user.getId())) {
            return; // 已经在房间中，直接返回
        }

        // 在内存中管理玩家状态（包括加入时间等）
        gameStateManager.joinRoom(room.getId(), user.getId());

        // 广播用户加入事件
        eventBroadcaster.broadcastRoomUserJoined(room.getId(), user);
    }

    @Transactional
    public void leaveRoom(Room room, User user) {
        // 从内存中移除玩家状态
        gameStateManager.leaveRoom(room.getId(), user.getId());

        // 广播用户离开事件
        eventBroadcaster.broadcastRoomUserLeft(room.getId(), user);

        // 如果房主离开，直接解散房间（简化逻辑）
        if (room.getOwner().getId().equals(user.getId())) {
            // 房主离开，解散房间
            roomRepo.delete(room);
            gameStateManager.clearRoom(room.getId());
            
            // 广播房间解散
            eventBroadcaster.broadcastRoomDisbanded(room.getId());
            return;
        }

        // 更新房间状态
        eventBroadcaster.broadcastRoomUpdate(room.getId());
    }

    @Transactional
    public void ready(Room room, User user, boolean ready) {
        // 检查用户是否在房间中（从内存检查）
        Map<Long, GameStateManager.PlayerRoomState> roomPlayers = gameStateManager.getRoomPlayers(room.getId());
        if (!roomPlayers.containsKey(user.getId())) {
            throw new IllegalArgumentException("用户不在房间中");
        }

        // 更新内存中的准备状态
        gameStateManager.setPlayerReady(room.getId(), user.getId(), ready);

        // 广播房间状态更新
        eventBroadcaster.broadcastRoomUpdate(room.getId());
    }

    @Transactional
    public boolean ownerDisbandRoom(Long roomId, User user) {
        Room room = roomRepo.findById(roomId).orElse(null);
        if (room == null) return false;
        
        if (!room.getOwner().getId().equals(user.getId())) {
            throw new IllegalArgumentException("只有房主可以解散房间");
        }
        
        if (room.getStatus() != Room.Status.waiting) {
            throw new IllegalArgumentException("只能解散等待中的房间");
        }

        return disbandRoom(room, user, "房主解散房间");
    }

    @Transactional
    public boolean adminDeleteRoom(Long roomId, User admin) {
        Room room = roomRepo.findById(roomId).orElse(null);
        if (room == null) return false;

        return disbandRoom(room, admin, "管理员删除房间");
    }

    private boolean disbandRoom(Room room, User initiator, String reason) {
        // 广播房间即将解散
        eventBroadcaster.broadcastRoomDisbanding(room.getId(), initiator, reason);

        // 踢出所有玩家
        eventBroadcaster.kickAllUsersFromRoom(room.getId(), reason);

        // 直接删除房间（而不是置为 disbanded 状态）
        roomRepo.delete(room);

        // 清理内存状态
        gameStateManager.clearRoom(room.getId());

        // 广播房间已解散
        eventBroadcaster.broadcastRoomDisbanded(room.getId());

        return true;
    }
}