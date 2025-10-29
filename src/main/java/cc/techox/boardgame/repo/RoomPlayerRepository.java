package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.RoomPlayer;
import cc.techox.boardgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoom(Room room);
    long deleteByRoomAndUser(Room room, User user);
    boolean existsByRoomAndUser(Room room, User user);
}