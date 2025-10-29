package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}