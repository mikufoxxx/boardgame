package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.UnoService;
import cc.techox.boardgame.util.AuthUtil;
import cc.techox.boardgame.websocket.GameEventBroadcaster;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uno")
public class UnoController {
    private final UnoService unoService;
    private final AuthService authService;
    private final GameEventBroadcaster eventBroadcaster;

    public UnoController(UnoService unoService, AuthService authService, GameEventBroadcaster eventBroadcaster) {
        this.unoService = unoService;
        this.authService = authService;
        this.eventBroadcaster = eventBroadcaster;
    }

    /**
     * 开始游戏 - 仅限房主操作
     */
    @PostMapping("/rooms/{roomId}/start")
    public ApiResponse<?> start(@PathVariable long roomId, @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            System.out.println("=== HTTP 开始游戏请求 ===");
            System.out.println("请求路径: POST /api/uno/rooms/" + roomId + "/start");
            
            User u = AuthUtil.requireAuth(auth, authService);
            System.out.println("认证用户: " + u.getUsername() + " (ID: " + u.getId() + ")");
            
            Long matchId = unoService.startInRoom(roomId, u);
            System.out.println("UnoService.startInRoom 执行成功, 返回 matchId: " + matchId);
            
            // 直接调用广播器发布游戏开始事件
            System.out.println("正在广播游戏开始事件...");
            eventBroadcaster.broadcastGameStarted(roomId, matchId);
            System.out.println("游戏开始事件广播完成");
            
            System.out.println("=== HTTP 响应成功 ===");
            return ApiResponse.ok("游戏已开始", Map.of("matchId", matchId));
        } catch (IllegalArgumentException e) {
            System.err.println("开始游戏失败 (参数错误): " + e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            System.err.println("开始游戏失败 (系统错误): " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error("开始游戏失败: " + e.getMessage());
        }
    }

    /**
     * 查看游戏状态 - 仅用于调试或初始加载
     */
    @GetMapping("/matches/{id}")
    public ApiResponse<?> view(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            User u = AuthUtil.requireAuth(auth, authService);
            return ApiResponse.ok("ok", unoService.view(id, u.getId()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("获取游戏状态失败: " + e.getMessage());
        }
    }
}