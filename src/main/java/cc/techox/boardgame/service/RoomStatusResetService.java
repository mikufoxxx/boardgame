package cc.techox.boardgame.service;

import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务启动时重置房间状态的服务
 * 
 * 当服务重启时，需要处理各种状态的房间：
 * 1. waiting 房间 - 保持等待状态，但清空内存中的玩家数据
 * 2. playing 房间 - 解散（因为游戏状态已丢失）
 * 3. finished 房间 - 解散（已经结束的游戏）
 * 4. disbanded 房间 - 保持不变
 */
@Service
public class RoomStatusResetService implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(RoomStatusResetService.class);
    
    private final RoomRepository roomRepository;
    private final GameStateManager gameStateManager;
    
    public RoomStatusResetService(RoomRepository roomRepository, GameStateManager gameStateManager) {
        this.roomRepository = roomRepository;
        this.gameStateManager = gameStateManager;
    }
    
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("服务启动：开始重置房间状态...");
        resetRoomStatusOnStartup();
        log.info("服务启动：房间状态重置完成");
    }
    
    @Transactional
    public void resetRoomStatusOnStartup() {
        try {
            // 1. 保留正在游戏中的房间，但清空内存状态（允许玩家重连）
            long playingRooms = roomRepository.countByStatus(Room.Status.playing);
            if (playingRooms > 0) {
                log.info("保留了 {} 个正在游戏中的房间（支持玩家重连）", playingRooms);
            }
            
            // 2. 删除已结束的房间
            int deletedFinishedRooms = roomRepository.deleteFinishedRooms();
            if (deletedFinishedRooms > 0) {
                log.info("删除了 {} 个已结束的房间", deletedFinishedRooms);
            }
            
            // 3. 更新等待中房间的时间戳（这些房间可以继续使用）
            int touchedWaitingRooms = roomRepository.touchWaitingRooms();
            if (touchedWaitingRooms > 0) {
                log.info("保留了 {} 个等待中的房间（玩家可重新加入）", touchedWaitingRooms);
            }
            
            // 4. 清空所有内存中的房间和游戏状态（但保留数据库中的房间记录）
            gameStateManager.clearAllStates();
            log.info("清空了所有内存中的房间和游戏状态");
            
            // 5. 统计最终状态
            logFinalRoomStatus();
            
        } catch (Exception e) {
            log.error("重置房间状态时发生错误", e);
            throw e;
        }
    }
    
    private void logFinalRoomStatus() {
        try {
            Object[][] statusStats = roomRepository.countByStatus();
            log.info("当前房间状态统计：");
            for (Object[] row : statusStats) {
                Room.Status status = Room.Status.valueOf(row[0].toString());
                Long count = (Long) row[1];
                log.info("  {} 状态房间: {} 个", status.name(), count);
            }
        } catch (Exception e) {
            log.warn("获取房间状态统计失败", e);
        }
    }
}