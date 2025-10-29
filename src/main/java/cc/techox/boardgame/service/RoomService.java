package cc.techox.boardgame.service;

import cc.techox.boardgame.model.Game;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.RoomPlayer;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.GameRepository;
import cc.techox.boardgame.repo.RoomPlayerRepository;
import cc.techox.boardgame.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomService {
    private final RoomRepository roomRepo;
    private final RoomPlayerRepository roomPlayerRepo;
    private final GameRepository gameRepo;

    public RoomService(RoomRepository roomRepo, RoomPlayerRepository roomPlayerRepo, GameRepository gameRepo) {
        this.roomRepo = roomRepo;
        this.roomPlayerRepo = roomPlayerRepo;
        this.gameRepo = gameRepo;
    }

    public List<Room> listRooms() {
        return roomRepo.findAll();
    }

    @Transactional
    public Room createRoom(String name, String gameCode, int maxPlayers, boolean isPrivate, String passwordHash, User owner) {
        Game game = gameRepo.findByCode(gameCode).orElseThrow(() -> new IllegalArgumentException("游戏不存在"));
        Room r = new Room();
        r.setName(name);
        r.setGame(game);
        r.setOwner(owner);
        r.setStatus(Room.Status.waiting);
        r.setMaxPlayers(maxPlayers);
        r.setPrivateRoom(isPrivate);
        r.setPasswordHash(passwordHash);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(r);
        // 创建者自动加入
        RoomPlayer rp = new RoomPlayer();
        rp.setRoom(r);
        rp.setUser(owner);
        rp.setReady(false);
        rp.setJoinedAt(LocalDateTime.now());
        roomPlayerRepo.save(rp);
        return r;
    }

    @Transactional
    public void joinRoom(Room room, User user) {
        if (roomPlayerRepo.existsByRoomAndUser(room, user)) return;
        RoomPlayer rp = new RoomPlayer();
        rp.setRoom(room);
        rp.setUser(user);
        rp.setReady(false);
        rp.setJoinedAt(LocalDateTime.now());
        roomPlayerRepo.save(rp);
    }

    @Transactional
    public void leaveRoom(Room room, User user) {
        roomPlayerRepo.deleteByRoomAndUser(room, user);
    }

    @Transactional
    public void ready(Room room, User user, boolean ready) {
        List<RoomPlayer> players = roomPlayerRepo.findByRoom(room);
        for (RoomPlayer rp : players) {
            if (rp.getUser().getId().equals(user.getId())) {
                rp.setReady(ready);
                roomPlayerRepo.save(rp);
                break;
            }
        }
    }

    // 管理员删除房间：删除房间玩家后删除房间
    @Transactional
    public boolean adminDeleteRoom(Long roomId) {
        return roomRepo.findById(roomId).map(r -> {
            List<RoomPlayer> players = roomPlayerRepo.findByRoom(r);
            for (RoomPlayer rp : players) { roomPlayerRepo.delete(rp); }
            roomRepo.delete(r);
            return true;
        }).orElse(false);
    }
}