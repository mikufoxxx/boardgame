package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.dto.CreateRoomRequest;
import cc.techox.boardgame.model.Room;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.RoomService;
import cc.techox.boardgame.util.AuthUtil;
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
        try {
            User owner = AuthUtil.requireAuth(authHeader, authService);
            String passwordHash = null;
            if (Boolean.TRUE.equals(req.getIsPrivate()) && req.getPassword() != null && !req.getPassword().isBlank()) {
                passwordHash = HashUtil.hashPassword(req.getPassword());
            }
            Room r = roomService.createRoom(req.getName(), req.getGameCode(), req.getMaxPlayers(),
                    Boolean.TRUE.equals(req.getIsPrivate()), passwordHash, owner);
            return ApiResponse.ok("房间创建成功", new RoomInfo(r));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("创建房间失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}/disband")
    public ApiResponse<?> disband(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                  @PathVariable("id") Long roomId) {
        try {
            User user = AuthUtil.requireAuth(authHeader, authService);
            boolean success = roomService.ownerDisbandRoom(roomId, user);
            if (!success) {
                return ApiResponse.error("解散失败：房间不存在、您不是房主或房间状态不允许解散");
            }
            return ApiResponse.ok("房间已解散", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("解散房间失败: " + e.getMessage());
        }
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