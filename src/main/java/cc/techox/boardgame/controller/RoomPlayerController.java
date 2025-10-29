package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.RoomRepository;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.RoomService;
import cc.techox.boardgame.util.HashUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomPlayerController {
    private final RoomRepository roomRepo;
    private final RoomService roomService;
    private final AuthService authService;

    public RoomPlayerController(RoomRepository roomRepo, RoomService roomService, AuthService authService) {
        this.roomRepo = roomRepo;
        this.roomService = roomService;
        this.authService = authService;
    }

    private String parseBearer(String h) {
        if (h == null) return null;
        if (h.toLowerCase().startsWith("bearer ")) return h.substring(7).trim();
        return null;
    }

    private User authed(String authHeader) {
        String token = parseBearer(authHeader);
        if (token == null) return null;
        return authService.getUserByToken(token).orElse(null);
    }

    @PostMapping("/{id}/join")
    public ApiResponse<?> join(@RequestHeader(name = "Authorization", required = false) String authHeader,
                               @PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        User u = authed(authHeader);
        if (u == null) return ApiResponse.error("未登录或令牌无效");
        Room room = roomRepo.findById(id).orElse(null);
        if (room == null) return ApiResponse.error("房间不存在");
        if (room.isPrivateRoom()) {
            String pwd = body == null ? null : body.get("password");
            if (pwd == null || !HashUtil.verifyPassword(pwd, room.getPasswordHash())) {
                return ApiResponse.error("密码错误");
            }
        }
        roomService.joinRoom(room, u);
        return ApiResponse.ok("加入成功", null);
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<?> leave(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                @PathVariable("id") Long id) {
        User u = authed(authHeader);
        if (u == null) return ApiResponse.error("未登录或令牌无效");
        Room room = roomRepo.findById(id).orElse(null);
        if (room == null) return ApiResponse.error("房间不存在");
        roomService.leaveRoom(room, u);
        return ApiResponse.ok("已离开", null);
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<?> ready(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                @PathVariable("id") Long id,
                                @RequestBody Map<String, Boolean> body) {
        User u = authed(authHeader);
        if (u == null) return ApiResponse.error("未登录或令牌无效");
        Room room = roomRepo.findById(id).orElse(null);
        if (room == null) return ApiResponse.error("房间不存在");
        boolean ready = body != null && Boolean.TRUE.equals(body.get("ready"));
        roomService.ready(room, u, ready);
        return ApiResponse.ok("状态已更新", null);
    }
}