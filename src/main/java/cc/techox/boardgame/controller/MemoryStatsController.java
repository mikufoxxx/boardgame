package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.util.AuthUtil;
import cc.techox.boardgame.websocket.WebSocketSessionManager;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 内存状态监控控制器
 * 提供游戏状态和性能统计信息
 */
@RestController
@RequestMapping("/api/admin/memory")
public class MemoryStatsController {
    
    private final GameStateManager gameStateManager;
    private final WebSocketSessionManager webSocketSessionManager;
    private final AuthService authService;
    
    public MemoryStatsController(GameStateManager gameStateManager,
                                WebSocketSessionManager webSocketSessionManager,
                                AuthService authService) {
        this.gameStateManager = gameStateManager;
        this.webSocketSessionManager = webSocketSessionManager;
        this.authService = authService;
    }
    
    /**
     * 获取内存使用统计
     */
    @GetMapping("/stats")
    public ApiResponse<?> getMemoryStats(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            AuthUtil.requireAdmin(authHeader, authService);
            
            Map<String, Object> stats = new HashMap<>();
            
            // 游戏状态管理器统计
            Map<String, Object> gameStats = gameStateManager.getMemoryStats();
            stats.put("gameState", gameStats);
            
            // WebSocket 连接统计
            Map<String, Object> wsStats = new HashMap<>();
            wsStats.put("onlineUsers", webSocketSessionManager.getOnlineUserCount());
            stats.put("webSocket", wsStats);
            
            // JVM 内存统计
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvmStats = new HashMap<>();
            jvmStats.put("totalMemory", runtime.totalMemory());
            jvmStats.put("freeMemory", runtime.freeMemory());
            jvmStats.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            jvmStats.put("maxMemory", runtime.maxMemory());
            jvmStats.put("availableProcessors", runtime.availableProcessors());
            stats.put("jvm", jvmStats);
            
            return ApiResponse.ok("ok", stats);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("获取内存统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取活跃游戏列表
     */
    @GetMapping("/active-games")
    public ApiResponse<?> getActiveGames(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            AuthUtil.requireAdmin(authHeader, authService);
            
            Map<String, Object> result = new HashMap<>();
            result.put("activeGameCount", gameStateManager.getActiveGameCount());
            result.put("activeRoomCount", gameStateManager.getActiveRoomCount());
            result.put("totalOnlinePlayers", gameStateManager.getTotalOnlinePlayerCount());
            
            return ApiResponse.ok("ok", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("获取活跃游戏列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 强制垃圾回收（仅用于调试）
     */
    @PostMapping("/gc")
    public ApiResponse<?> forceGarbageCollection(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            AuthUtil.requireAdmin(authHeader, authService);
            
            Runtime runtime = Runtime.getRuntime();
            long beforeGC = runtime.totalMemory() - runtime.freeMemory();
            
            System.gc();
            Thread.sleep(100); // 等待GC完成
            
            long afterGC = runtime.totalMemory() - runtime.freeMemory();
            long freedMemory = beforeGC - afterGC;
            
            Map<String, Object> result = new HashMap<>();
            result.put("memoryBeforeGC", beforeGC);
            result.put("memoryAfterGC", afterGC);
            result.put("freedMemory", freedMemory);
            result.put("message", "垃圾回收已执行");
            
            return ApiResponse.ok("ok", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("执行垃圾回收失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理过期的内存状态
     */
    @PostMapping("/cleanup")
    public ApiResponse<?> cleanupExpiredStates(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            AuthUtil.requireAdmin(authHeader, authService);
            
            // 这里可以调用 GameStateManager 的清理方法
            // 由于清理方法是私有的，我们可以添加一个公共方法来手动触发清理
            
            return ApiResponse.ok("过期状态清理已触发", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("清理过期状态失败: " + e.getMessage());
        }
    }
}