package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Game;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    
    /**
     * 根据ID查找房间，并主动加载Game和Owner信息（解决懒加载问题）
     */
    @Query("SELECT r FROM Room r JOIN FETCH r.game JOIN FETCH r.owner WHERE r.id = :id")
    Optional<Room> findByIdWithGame(@Param("id") Long id);
    
    // 按状态查询房间
    Page<Room> findByStatus(Room.Status status, Pageable pageable);
    
    // 按状态统计房间数量
    long countByStatus(Room.Status status);
    
    // 查找用户创建的活跃房间（waiting 或 playing 状态）
    @Query("SELECT r FROM Room r WHERE r.owner = :owner AND r.status IN ('waiting', 'playing')")
    List<Room> findActiveRoomsByOwner(@Param("owner") User owner);
    
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
    
    // 服务重启时的房间状态重置方法

    /**
     * 将等待中的房间保持等待状态，但会清空内存中的玩家数据
     * 这些房间可以继续使用，玩家重连后可以重新加入
     */
    @Modifying
    @Query("UPDATE Room r SET r.updatedAt = CURRENT_TIMESTAMP WHERE r.status = 'waiting'")
    int touchWaitingRooms();
    
    /**
     * 删除正在游戏中的房间 (因为游戏状态已丢失)
     */
    @Modifying
    @Query("DELETE FROM Room r WHERE r.status = 'playing'")
    int deletePlayingRooms();
    
    /**
     * 删除已结束的房间
     */
    @Modifying
    @Query("DELETE FROM Room r WHERE r.status = 'finished'")
    int deleteFinishedRooms();
    
    /**
     * 批量删除指定状态的房间
     */
    @Modifying
    @Query("DELETE FROM Room r WHERE r.status = :status")
    int deleteRoomsByStatus(@Param("status") Room.Status status);
}