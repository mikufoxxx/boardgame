package cc.techox.boardgame.memory;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 内存游戏状态管理器
 * 负责管理所有活跃游戏的状态数据，避免频繁的数据库读写
 */
@Component
public class GameStateManager {
    
    // 游戏状态存储 matchId -> GameStateData
    private final Map<Long, GameStateData> gameStates = new ConcurrentHashMap<>();
    
    // 房间玩家状态 roomId -> Map<userId, PlayerRoomState>
    private final Map<Long, Map<Long, PlayerRoomState>> roomPlayers = new ConcurrentHashMap<>();
    
    // 定时清理器
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    // 内存使用限制配置
    private static final int MAX_ACTIVE_GAMES = 1000; // 最大同时活跃游戏数
    private static final int MAX_MEMORY_MB = 100; // 最大内存使用限制(MB)
    private static final long CLEANUP_INTERVAL_MINUTES = 5; // 清理间隔
    private static final long EXPIRE_HOURS = 2; // 状态过期时间
    
    // 内存使用监控
    private volatile long estimatedMemoryUsage = 0;
    
    public GameStateManager() {
        // 每5分钟清理一次过期状态
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredStates, 
            CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        
        // 每分钟检查内存使用
        cleanupExecutor.scheduleAtFixedRate(this::checkMemoryUsage, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 游戏状态数据
     */
    public static class GameStateData {
        private final Long matchId;
        private final String gameCode;
        private Object state; // 具体的游戏状态对象
        private LocalDateTime lastUpdated;
        private LocalDateTime lastAccessed;
        
        // 游戏会话元数据
        private final Long roomId;
        private final LocalDateTime startedAt;
        private final Integer playerCount;
        private Integer turnCount;
        private LocalDateTime lastActionAt;
        private String status; // playing, finished, aborted
        private Long winnerId;
        
        public GameStateData(Long matchId, String gameCode, Object state, Long roomId, Integer playerCount) {
            this.matchId = matchId;
            this.gameCode = gameCode;
            this.state = state;
            this.roomId = roomId;
            this.playerCount = playerCount;
            this.startedAt = LocalDateTime.now();
            this.lastUpdated = LocalDateTime.now();
            this.lastAccessed = LocalDateTime.now();
            this.turnCount = 0;
            this.lastActionAt = LocalDateTime.now();
            this.status = "playing";
        }
        
        public void updateState(Object newState) {
            this.state = newState;
            this.lastUpdated = LocalDateTime.now();
            this.lastAccessed = LocalDateTime.now();
        }
        
        public void markAccessed() {
            this.lastAccessed = LocalDateTime.now();
        }
        
        public void incrementTurn() {
            this.turnCount++;
            this.lastActionAt = LocalDateTime.now();
        }
        
        public void finishGame(String status, Long winnerId) {
            this.status = status;
            this.winnerId = winnerId;
        }
        
        // getters
        public Long getMatchId() { return matchId; }
        public String getGameCode() { return gameCode; }
        public Object getState() { return state; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public LocalDateTime getLastAccessed() { return lastAccessed; }
        public Long getRoomId() { return roomId; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public Integer getPlayerCount() { return playerCount; }
        public Integer getTurnCount() { return turnCount; }
        public LocalDateTime getLastActionAt() { return lastActionAt; }
        public String getStatus() { return status; }
        public Long getWinnerId() { return winnerId; }
    }
    
    /**
     * 房间内玩家状态
     */
    public static class PlayerRoomState {
        private final Long userId;
        private final Long roomId;
        private boolean ready;
        private LocalDateTime joinedAt;
        private LocalDateTime lastActiveAt;
        private Integer seatNo;
        private String team;
        
        public PlayerRoomState(Long userId, Long roomId) {
            this.userId = userId;
            this.roomId = roomId;
            this.ready = false;
            this.joinedAt = LocalDateTime.now();
            this.lastActiveAt = LocalDateTime.now();
        }
        
        public void markActive() {
            this.lastActiveAt = LocalDateTime.now();
        }
        
        // getters and setters
        public Long getUserId() { return userId; }
        public Long getRoomId() { return roomId; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public LocalDateTime getLastActiveAt() { return lastActiveAt; }
        public Integer getSeatNo() { return seatNo; }
        public void setSeatNo(Integer seatNo) { this.seatNo = seatNo; }
        public String getTeam() { return team; }
        public void setTeam(String team) { this.team = team; }
    }
    
    // ==================== 游戏状态管理 ====================
    
    /**
     * 创建新的游戏状态（带内存检查）
     */
    public void createGameState(Long matchId, String gameCode, Object initialState, Long roomId, Integer playerCount) {
        // 检查是否超过最大游戏数限制
        if (gameStates.size() >= MAX_ACTIVE_GAMES) {
            // 强制清理最老的游戏状态
            forceCleanupOldestGames(10);
        }
        
        // 估算新游戏状态的内存占用
        long estimatedSize = estimateGameStateSize(gameCode, initialState);
        
        // 检查内存使用是否会超限
        if (estimatedMemoryUsage + estimatedSize > MAX_MEMORY_MB * 1024 * 1024) {
            // 强制清理以释放内存
            forceCleanupOldestGames(20);
        }
        
        gameStates.put(matchId, new GameStateData(matchId, gameCode, initialState, roomId, playerCount));
        estimatedMemoryUsage += estimatedSize;
    }
    
    /**
     * 增加游戏回合数
     */
    public void incrementGameTurn(Long matchId) {
        GameStateData gameData = gameStates.get(matchId);
        if (gameData != null) {
            gameData.incrementTurn();
        }
    }
    
    /**
     * 结束游戏
     */
    public void finishGame(Long matchId, String status, Long winnerId) {
        GameStateData gameData = gameStates.get(matchId);
        if (gameData != null) {
            gameData.finishGame(status, winnerId);
        }
    }
    
    /**
     * 获取游戏会话信息
     */
    public Optional<GameStateData> getGameSession(Long matchId) {
        GameStateData gameData = gameStates.get(matchId);
        if (gameData != null) {
            gameData.markAccessed();
            return Optional.of(gameData);
        }
        return Optional.empty();
    }
    
    /**
     * 获取游戏状态
     */
    public Optional<Object> getGameState(Long matchId) {
        GameStateData data = gameStates.get(matchId);
        if (data != null) {
            data.markAccessed();
            return Optional.of(data.getState());
        }
        return Optional.empty();
    }
    
    /**
     * 更新游戏状态
     */
    public void updateGameState(Long matchId, Object newState) {
        GameStateData data = gameStates.get(matchId);
        if (data != null) {
            data.updateState(newState);
        }
    }
    
    /**
     * 删除游戏状态
     */
    public void removeGameState(Long matchId) {
        GameStateData removed = gameStates.remove(matchId);
        if (removed != null) {
            // 减少内存使用估算
            long estimatedSize = estimateGameStateSize(removed.getGameCode(), removed.getState());
            estimatedMemoryUsage = Math.max(0, estimatedMemoryUsage - estimatedSize);
        }
    }
    
    /**
     * 估算游戏状态内存占用
     */
    private long estimateGameStateSize(String gameCode, Object state) {
        // 根据游戏类型估算内存占用
        return switch (gameCode.toLowerCase()) {
            case "uno" -> 1500; // UNO游戏约1.5KB
            case "chess" -> 2000; // 象棋约2KB
            case "go" -> 5000; // 围棋约5KB
            default -> 2000; // 默认2KB
        };
    }
    
    /**
     * 强制清理最老的游戏状态
     */
    private void forceCleanupOldestGames(int count) {
        gameStates.entrySet().stream()
            .sorted((e1, e2) -> e1.getValue().getLastAccessed().compareTo(e2.getValue().getLastAccessed()))
            .limit(count)
            .map(Map.Entry::getKey)
            .forEach(this::removeGameState);
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        // 如果JVM内存使用超过80%，强制清理
        if (usedMemory > maxMemory * 0.8) {
            System.gc(); // 建议垃圾回收
            forceCleanupOldestGames(50); // 清理50个最老的游戏状态
        }
    }
    
    /**
     * 检查游戏是否存在
     */
    public boolean hasGameState(Long matchId) {
        return gameStates.containsKey(matchId);
    }
    
    /**
     * 根据房间ID查找当前进行中的对局
     */
    public Optional<GameStateData> getGameSessionByRoomId(Long roomId) {
        return gameStates.values().stream()
                .filter(gameData -> roomId.equals(gameData.getRoomId()))
                .filter(gameData -> "playing".equals(gameData.getStatus()))
                .findFirst();
    }
    
    // ==================== 房间玩家状态管理 ====================
    
    /**
     * 玩家加入房间
     */
    public void joinRoom(Long roomId, Long userId) {
        roomPlayers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                   .put(userId, new PlayerRoomState(userId, roomId));
    }
    
    /**
     * 玩家离开房间
     */
    public void leaveRoom(Long roomId, Long userId) {
        Map<Long, PlayerRoomState> players = roomPlayers.get(roomId);
        if (players != null) {
            players.remove(userId);
            if (players.isEmpty()) {
                roomPlayers.remove(roomId);
            }
        }
    }
    
    /**
     * 获取房间内所有玩家
     */
    public Map<Long, PlayerRoomState> getRoomPlayers(Long roomId) {
        return roomPlayers.getOrDefault(roomId, Map.of());
    }
    
    /**
     * 获取玩家在房间内的状态
     */
    public Optional<PlayerRoomState> getPlayerRoomState(Long roomId, Long userId) {
        Map<Long, PlayerRoomState> players = roomPlayers.get(roomId);
        if (players != null) {
            PlayerRoomState state = players.get(userId);
            if (state != null) {
                state.markActive();
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
    
    /**
     * 设置玩家准备状态
     */
    public void setPlayerReady(Long roomId, Long userId, boolean ready) {
        getPlayerRoomState(roomId, userId).ifPresent(state -> state.setReady(ready));
    }
    
    /**
     * 设置玩家座位号
     */
    public void setPlayerSeat(Long roomId, Long userId, Integer seatNo) {
        getPlayerRoomState(roomId, userId).ifPresent(state -> state.setSeatNo(seatNo));
    }
    
    /**
     * 清空房间所有玩家
     */
    public void clearRoom(Long roomId) {
        roomPlayers.remove(roomId);
    }
    
    // ==================== 统计和维护 ====================
    
    /**
     * 获取活跃游戏数量
     */
    public int getActiveGameCount() {
        return gameStates.size();
    }
    
    /**
     * 获取活跃房间数量
     */
    public int getActiveRoomCount() {
        return roomPlayers.size();
    }
    
    /**
     * 获取在线玩家总数
     */
    public long getTotalOnlinePlayerCount() {
        return roomPlayers.values().stream()
                .mapToLong(Map::size)
                .sum();
    }
    
    /**
     * 清理过期状态
     */
    private void cleanupExpiredStates() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(EXPIRE_HOURS);
        
        // 清理过期的游戏状态
        gameStates.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getLastAccessed().isBefore(expireTime);
            if (expired) {
                // 减少内存使用估算
                long estimatedSize = estimateGameStateSize(entry.getValue().getGameCode(), entry.getValue().getState());
                estimatedMemoryUsage = Math.max(0, estimatedMemoryUsage - estimatedSize);
            }
            return expired;
        });
        
        // 清理过期的房间玩家状态
        roomPlayers.entrySet().removeIf(entry -> {
            Map<Long, PlayerRoomState> players = entry.getValue();
            players.entrySet().removeIf(playerEntry -> 
                playerEntry.getValue().getLastActiveAt().isBefore(expireTime));
            return players.isEmpty();
        });
    }
    
    /**
     * 获取内存使用统计
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeGames", gameStates.size());
        stats.put("activeRooms", roomPlayers.size());
        stats.put("totalOnlinePlayers", getTotalOnlinePlayerCount());
        stats.put("estimatedMemoryUsage", estimatedMemoryUsage);
        stats.put("estimatedMemoryUsageMB", estimatedMemoryUsage / (1024.0 * 1024.0));
        stats.put("maxActiveGames", MAX_ACTIVE_GAMES);
        stats.put("maxMemoryMB", MAX_MEMORY_MB);
        
        // 按游戏类型统计
        Map<String, Long> gameTypeStats = new ConcurrentHashMap<>();
        gameStates.values().forEach(data -> 
            gameTypeStats.merge(data.getGameCode(), 1L, Long::sum));
        stats.put("gamesByType", gameTypeStats);
        
        // 内存使用率
        double memoryUsagePercent = (estimatedMemoryUsage * 100.0) / (MAX_MEMORY_MB * 1024 * 1024);
        stats.put("memoryUsagePercent", Math.round(memoryUsagePercent * 100.0) / 100.0);
        
        // 游戏数量使用率
        double gameCountPercent = (gameStates.size() * 100.0) / MAX_ACTIVE_GAMES;
        stats.put("gameCountPercent", Math.round(gameCountPercent * 100.0) / 100.0);
        
        return stats;
    }
    
    /**
     * 清空所有状态 - 用于服务重启时的初始化
     */
    public void clearAllStates() {
        gameStates.clear();
        roomPlayers.clear();
        estimatedMemoryUsage = 0;
        System.out.println("GameStateManager: 已清空所有游戏状态和房间玩家状态");
    }
}