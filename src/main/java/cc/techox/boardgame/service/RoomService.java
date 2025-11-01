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
import java.util.Optional;

@Service
public class RoomService {
    private final RoomRepository roomRepo;
    private final GameRepository gameRepo;
    private final UserRepository userRepo;
    private final GameEventBroadcaster eventBroadcaster;
    private final GameStateManager gameStateManager;

    public RoomService(RoomRepository roomRepo, 
                      GameRepository gameRepo, 
                      UserRepository userRepo,
                      GameEventBroadcaster eventBroadcaster,
                      GameStateManager gameStateManager) {
        this.roomRepo = roomRepo;
        this.gameRepo = gameRepo;
        this.userRepo = userRepo;
        this.eventBroadcaster = eventBroadcaster;
        this.gameStateManager = gameStateManager;
    }

    public List<Room> listRooms() {
        return roomRepo.findAll();
    }

    public Room getRoomById(Long roomId) {
        return roomRepo.findByIdWithGame(roomId)
                .orElseThrow(() -> new IllegalArgumentException("房间不存在"));
    }

    /**
     * 获取房间当前的对局信息
     */
    public Map<String, Object> getCurrentMatch(Long roomId) {
        // 验证房间是否存在
        getRoomById(roomId);
        
        // 查找房间当前的对局
        Optional<GameStateManager.GameStateData> gameSession = gameStateManager.getGameSessionByRoomId(roomId);
        
        if (gameSession.isPresent()) {
            GameStateManager.GameStateData gameData = gameSession.get();
            return Map.of(
                "matchId", gameData.getMatchId(),
                "status", gameData.getStatus(),
                "gameCode", gameData.getGameCode(),
                "startedAt", gameData.getStartedAt().toString(),
                "playerCount", gameData.getPlayerCount(),
                "turnCount", gameData.getTurnCount()
            );
        } else {
            return null; // 房间当前没有对局
        }
    }

    @Transactional
    public Room createRoom(String name, String gameCode, Integer maxPlayers, boolean isPrivate, String passwordHash, User owner) {
        // 检查用户是否已经有活跃的房间
        List<Room> activeRooms = roomRepo.findActiveRoomsByOwner(owner);
        if (!activeRooms.isEmpty()) {
            Room existingRoom = activeRooms.get(0);
            throw new IllegalArgumentException("您已经创建了房间 \"" + existingRoom.getName() + "\"，请先解散现有房间再创建新房间");
        }
        
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

        System.out.println("用户 " + owner.getUsername() + " 创建房间: " + room.getName() + " (ID: " + room.getId() + ")");

        // 房主自动加入房间（只在内存中管理）
        joinRoom(room, owner);
        
        return room;
    }

    @Transactional
    public void joinRoom(Room room, User user) {
        // 检查房间状态
        if (room.getStatus() == Room.Status.disbanded) {
            throw new IllegalArgumentException("房间已解散");
        }
        
        if (room.getStatus() == Room.Status.finished) {
            throw new IllegalArgumentException("游戏已结束");
        }
        
        // 如果不是房主，自动删除该玩家作为房主的其他房间
        if (!room.getOwner().getId().equals(user.getId())) {
            List<Room> userOwnedRooms = roomRepo.findActiveRoomsByOwner(user);
            for (Room ownedRoom : userOwnedRooms) {
                System.out.println("玩家 " + user.getUsername() + " 加入其他房间，自动解散其拥有的房间: " + ownedRoom.getName() + " (ID: " + ownedRoom.getId() + ")");
                disbandRoom(ownedRoom, user, "房主加入其他房间，自动解散");
            }
        }
        
        // 从内存获取当前玩家状态
        Map<Long, GameStateManager.PlayerRoomState> currentPlayers = gameStateManager.getRoomPlayers(room.getId());
        
        // 如果房间正在游戏中，需要验证玩家是否是原参与者
        if (room.getStatus() == Room.Status.playing) {
            // 检查玩家是否是游戏的原参与者（通过检查游戏状态）
            if (!isPlayerInGame(room.getId(), user.getId())) {
                throw new IllegalArgumentException("游戏进行中，只允许原参与者重连");
            }
            
            // 重连逻辑：重新加入内存管理
            gameStateManager.joinRoom(room.getId(), user.getId());
            
            System.out.println("玩家 " + user.getUsername() + " 重连到游戏房间 " + room.getId());
            
            // 广播用户重连事件
            eventBroadcaster.broadcastRoomUserJoined(room.getId(), user);
            return;
        }
        
        // 等待中的房间：正常加入逻辑
        if (room.getStatus() == Room.Status.waiting) {
            // 检查房间是否已满
            if (currentPlayers.size() >= room.getMaxPlayers()) {
                throw new IllegalArgumentException("房间已满");
            }

            // 检查是否已经在房间中
            if (currentPlayers.containsKey(user.getId())) {
                return; // 已经在房间中，直接返回
            }

            // 在内存中管理玩家状态
            gameStateManager.joinRoom(room.getId(), user.getId());

            System.out.println("玩家 " + user.getUsername() + " 加入等待房间 " + room.getId());
            
            // 广播用户加入事件
            eventBroadcaster.broadcastRoomUserJoined(room.getId(), user);
        }
    }
    
    /**
     * 检查玩家是否是游戏的原参与者
     * 这里可以通过多种方式验证：
     * 1. 检查游戏状态中的玩家列表
     * 2. 检查数据库中的游戏记录
     * 3. 检查房间的历史参与者
     */
    private boolean isPlayerInGame(Long roomId, Long userId) {
        // 方法1：检查内存中是否有该玩家的游戏状态
        // 如果服务重启，内存状态会丢失，但我们可以通过其他方式验证
        
        // 方法2：简化验证 - 允许任何玩家重连（可以根据需要加强验证）
        // 在实际应用中，可以：
        // - 检查数据库中的 match 记录
        // - 检查房间的参与者历史
        // - 使用 JWT token 中的房间信息
        
        // 暂时返回 true，允许重连（后续可以加强验证）
        return true;
    }

    /**
     * 房主转让功能
     */
    @Transactional
    public void transferOwnership(Room room, User currentOwner, User newOwner) {
        // 验证当前用户是房主
        if (!room.getOwner().getId().equals(currentOwner.getId())) {
            throw new IllegalArgumentException("只有房主可以转让房间");
        }
        
        // 验证新房主在房间中
        Map<Long, GameStateManager.PlayerRoomState> roomPlayers = gameStateManager.getRoomPlayers(room.getId());
        if (!roomPlayers.containsKey(newOwner.getId())) {
            throw new IllegalArgumentException("新房主必须在房间中");
        }
        
        // 自动解散新房主拥有的其他房间
        List<Room> newOwnerActiveRooms = roomRepo.findActiveRoomsByOwner(newOwner);
        for (Room ownedRoom : newOwnerActiveRooms) {
            System.out.println("房主转让：自动解散新房主 " + newOwner.getUsername() + " 拥有的房间: " + ownedRoom.getName() + " (ID: " + ownedRoom.getId() + ")");
            disbandRoom(ownedRoom, newOwner, "房主转让，自动解散原房间");
        }
        
        // 执行转让
        room.setOwner(newOwner);
        room.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(room);
        
        System.out.println("房间 " + room.getId() + " 的房主从 " + currentOwner.getUsername() + " 转让给 " + newOwner.getUsername());
        
        // 广播房间更新事件
        eventBroadcaster.broadcastRoomUpdate(room.getId());
    }
    
    @Transactional
    public void leaveRoom(Room room, User user) {
        // 从内存中移除玩家状态
        gameStateManager.leaveRoom(room.getId(), user.getId());

        // 广播用户离开事件
        eventBroadcaster.broadcastRoomUserLeft(room.getId(), user);

        // 如果房主离开，需要处理房主转让或解散房间
        if (room.getOwner().getId().equals(user.getId())) {
            // 获取房间内剩余玩家
            Map<Long, GameStateManager.PlayerRoomState> remainingPlayers = gameStateManager.getRoomPlayers(room.getId());
            
            if (remainingPlayers.isEmpty()) {
                // 没有其他玩家，直接删除房间（无需广播，因为没有人接收）
                System.out.println("房主离开且无其他玩家，直接删除房间: " + room.getName() + " (ID: " + room.getId() + ")");
                roomRepo.delete(room);
                gameStateManager.clearRoom(room.getId());
                return;
            } else {
                // 有其他玩家，自动转让给第一个玩家
                Long newOwnerId = remainingPlayers.keySet().iterator().next();
                
                // 查找新房主用户信息
                User newOwner = userRepo.findById(newOwnerId).orElse(null);
                if (newOwner != null) {
                    System.out.println("房主 " + user.getUsername() + " 离开，自动转让房间 " + room.getName() + " 给 " + newOwner.getUsername());
                    
                    // 执行房主转让
                    room.setOwner(newOwner);
                    room.setUpdatedAt(LocalDateTime.now());
                    roomRepo.save(room);
                    
                    // 广播房间更新（包含新房主信息）
                    eventBroadcaster.broadcastRoomUpdate(room.getId());
                    return;
                } else {
                    // 找不到新房主用户，解散房间
                    System.err.println("无法找到新房主用户 ID: " + newOwnerId + "，解散房间");
                    roomRepo.delete(room);
                    gameStateManager.clearRoom(room.getId());
                    eventBroadcaster.broadcastRoomDisbanded(room.getId());
                    return;
                }
            }
        }

        // 普通玩家离开，更新房间状态
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