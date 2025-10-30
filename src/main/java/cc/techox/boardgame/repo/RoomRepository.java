package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // 按状态查询房间
    Page<Room> findByStatus(Room.Status status, Pageable pageable);
    
    // 按游戏类型查询房间
    Page<Room> findByGame(Game game, Pageable pageable);
    
    // 按状态和游戏类型查询房间
    Page<Room> findByStatusAndGame(Room.Status status, Game game, Pageable pageable);
    
    // 按房间名模糊搜索
    Page<Room> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    // 按房间名和状态查询
    Page<Room> findByNameContainingIgnoreCaseAndStatus(String name, Room.Status status, Pageable pageable);
    
    // 按房间名和游戏类型查询
    Page<Room> findByNameContainingIgnoreCaseAndGame(String name, Game game, Pageable pageable);
    
    // 按房间名、状态和游戏类型查询
    Page<Room> findByNameContainingIgnoreCaseAndStatusAndGame(String name, Room.Status status, Game game, Pageable pageable);
    
    // 统计各状态房间数量
    @Query("SELECT r.status, COUNT(r) FROM Room r GROUP BY r.status")
    Object[][] countByStatus();
    
    // 统计各游戏类型房间数量
    @Query("SELECT g.code, COUNT(r) FROM Room r JOIN r.game g GROUP BY g.code")
    Object[][] countByGameType();
}