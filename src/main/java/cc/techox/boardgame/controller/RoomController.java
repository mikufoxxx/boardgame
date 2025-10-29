package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.dto.CreateRoomRequest;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.RoomService;
import cc.techox.boardgame.util.HashUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final AuthService authService;

    public RoomController(RoomService roomService, AuthService authService) {
        this.roomService = roomService;
        this.authService = authService;
    }

    @GetMapping("")
    public ApiResponse<?> list() {
        List<Room> rooms = roomService.listRooms();
        return ApiResponse.ok("ok", rooms.stream().map(RoomInfo::new).toList());
    }

    @PostMapping("")
    public ApiResponse<?> create(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                 @RequestBody CreateRoomRequest req) {
        String token = parseBearer(authHeader);
        if (token == null) return ApiResponse.error("未提供令牌");
        User owner = authService.getUserByToken(token).orElse(null);
        if (owner == null) return ApiResponse.error("令牌无效");
        String passwordHash = null;
        if (Boolean.TRUE.equals(req.getIsPrivate()) && req.getPassword() != null && !req.getPassword().isBlank()) {
            passwordHash = HashUtil.hashPassword(req.getPassword());
        }
        Room r = roomService.createRoom(req.getName(), req.getGameCode(), req.getMaxPlayers(),
                Boolean.TRUE.equals(req.getIsPrivate()), passwordHash, owner);
        return ApiResponse.ok("房间创建成功", new RoomInfo(r));
    }

    private String parseBearer(String h) {
        if (h == null) return null;
        if (h.toLowerCase().startsWith("bearer ")) return h.substring(7).trim();
        return null;
    }

    static class RoomInfo {
        public Long id;
        public String name;
        public String gameCode;
        public Long ownerId;
        public String status;
        public Integer maxPlayers;
        public Boolean isPrivate;
        public String createdAt;
        public String updatedAt;
        public RoomInfo(Room r) {
            this.id = r.getId();
            this.name = r.getName();
            this.gameCode = r.getGame().getCode();
            this.ownerId = r.getOwner().getId();
            this.status = r.getStatus().name();
            this.maxPlayers = r.getMaxPlayers();
            this.isPrivate = r.isPrivateRoom();
            this.createdAt = r.getCreatedAt().toString();
            this.updatedAt = r.getUpdatedAt().toString();
        }
    }
}