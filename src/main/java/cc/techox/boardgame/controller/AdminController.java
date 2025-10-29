package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.dto.CreateInviteCodesRequest;
import cc.techox.boardgame.dto.CreateUserRequest;
import cc.techox.boardgame.dto.UpdateUserPasswordRequest;
import cc.techox.boardgame.dto.UpdateUserRoleRequest;
import cc.techox.boardgame.model.AdminAuditLog;
import cc.techox.boardgame.model.InviteCode;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.AdminAuditLogRepository;
import cc.techox.boardgame.repo.AuthSessionRepository;
import cc.techox.boardgame.repo.InviteCodeRepository;
import cc.techox.boardgame.repo.UserRepository;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.RoomService;
import cc.techox.boardgame.util.HashUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;
    private final UserRepository userRepo;
    private final InviteCodeRepository inviteRepo;
    private final AdminAuditLogRepository auditRepo;
    private final AuthSessionRepository sessionRepo;
    private final RoomService roomService;

    public AdminController(AuthService authService,
                           UserRepository userRepo,
                           InviteCodeRepository inviteRepo,
                           AdminAuditLogRepository auditRepo,
                           AuthSessionRepository sessionRepo,
                           RoomService roomService) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.inviteRepo = inviteRepo;
        this.auditRepo = auditRepo;
        this.sessionRepo = sessionRepo;
        this.roomService = roomService;
    }

    // 引导：若无管理员，创建默认管理员 SpecialFox
    @PostConstruct
    public void bootstrapAdmin() {
        try {
            long adminCount = userRepo.countByRole(User.Role.admin);
            if (adminCount == 0) {
                Optional<User> opt = userRepo.findByUsername("SpecialFox");
                User u = opt.orElseGet(User::new);
                u.setUsername("SpecialFox");
                u.setDisplayName("SpecialFox");
                u.setPasswordHash(HashUtil.hashPassword("Specialfox233"));
                u.setStatus(User.Status.active);
                u.setRole(User.Role.admin);
                if (u.getId() == null) { u.setCreatedAt(LocalDateTime.now()); }
                u.setUpdatedAt(LocalDateTime.now());
                userRepo.save(u);
            }
        } catch (Exception ignored) {
        }
    }

    private String parseBearer(String h) {
        if (h == null) return null;
        if (h.toLowerCase().startsWith("bearer ")) return h.substring(7).trim();
        return null;
    }

    private Optional<User> requireAdmin(String authHeader) {
        String token = parseBearer(authHeader);
        if (token == null) return Optional.empty();
        return authService.getUserByToken(token).filter(u -> u.getRole() == User.Role.admin);
    }

    private void audit(User operator, String action, String targetType, Long targetId, String detail) {
        try {
            AdminAuditLog log = new AdminAuditLog();
            log.setAction(action);
            log.setOperator(operator);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            log.setCreatedAt(LocalDateTime.now());
            auditRepo.save(log);
        } catch (Exception ignored) {}
    }

    // 批量生成邀请码：6位大小写不敏感字母数字（统一大写），唯一
    @PostMapping("/invite-codes")
    public ApiResponse<?> createInviteCodes(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                            @RequestBody CreateInviteCodesRequest req) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        int count = Math.max(0, req.getCount());
        if (count <= 0 || count > 500) return ApiResponse.error("生成数量必须在 1-500 之间");
        String batchNo = (req.getBatchNo() == null || req.getBatchNo().isBlank())
                ? "B" + LocalDateTime.now().toString().replace("-", "").replace(":", "").replace(".", "")
                : req.getBatchNo();
        LocalDateTime expiresAt = null;
        if (req.getExpiresDays() != null && req.getExpiresDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(req.getExpiresDays());
        }
        List<String> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code;
            int tries = 0;
            do {
                code = randomCode();
                tries++;
                if (tries > 2000) return ApiResponse.error("生成唯一邀请码超时");
            } while (inviteRepo.existsByCode(code));
            InviteCode ic = new InviteCode();
            ic.setCode(code);
            ic.setUsed(false);
            ic.setCreatedBy(admin);
            ic.setCreatedAt(LocalDateTime.now());
            ic.setBatchNo(batchNo);
            ic.setExpiresAt(expiresAt);
            inviteRepo.save(ic);
            created.add(code);
        }
        audit(admin, "create_invite_codes", "InviteCodeBatch", null, "batch=" + batchNo + ", count=" + count);
        Map<String, Object> resp = new HashMap<>();
        resp.put("batchNo", batchNo);
        resp.put("codes", created);
        resp.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        return ApiResponse.ok("生成成功", resp);
    }

    // 查询邀请码列表
    @GetMapping("/invite-codes")
    public ApiResponse<?> listInviteCodes(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                          @RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "size", defaultValue = "20") int size,
                                          @RequestParam(name = "status", required = false) String status,
                                          @RequestParam(name = "batchNo", required = false) String batchNo) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        
        size = Math.min(size, 200);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<InviteCode> invitePage;
        
        // 根据状态和批次号筛选
        if (status != null && batchNo != null) {
            boolean isUsed = "used".equalsIgnoreCase(status);
            invitePage = inviteRepo.findByUsedAndBatchNoContainingIgnoreCase(isUsed, batchNo, pageable);
        } else if (status != null) {
            boolean isUsed = "used".equalsIgnoreCase(status);
            invitePage = inviteRepo.findByUsed(isUsed, pageable);
        } else if (batchNo != null) {
            invitePage = inviteRepo.findByBatchNoContainingIgnoreCase(batchNo, pageable);
        } else {
            invitePage = inviteRepo.findAll(pageable);
        }
        
        List<InviteCodeInfo> items = invitePage.getContent().stream()
                .map(InviteCodeInfo::new)
                .toList();
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("page", page);
        resp.put("size", size);
        resp.put("total", invitePage.getTotalElements());
        resp.put("items", items);
        return ApiResponse.ok("ok", resp);
    }

    private static final char[] CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private String randomCode() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS[r.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    // 用户分页列表
    @GetMapping("/users")
    public ApiResponse<?> listUsers(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                    @RequestParam(name = "page", defaultValue = "1") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        page = Math.max(1, page);
        size = Math.min(200, Math.max(1, size));
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> pg = userRepo.findAll(pageable);
        Map<String, Object> resp = new HashMap<>();
        resp.put("page", page);
        resp.put("size", size);
        resp.put("total", pg.getTotalElements());
        resp.put("items", pg.getContent().stream().map(UserInfo::new).toList());
        return ApiResponse.ok("ok", resp);
    }

    // 创建用户
    @PostMapping("/users")
    public ApiResponse<?> createUser(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                     @RequestBody CreateUserRequest req) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        if (req.getUsername() == null || req.getUsername().isBlank() || req.getPassword() == null || req.getPassword().isBlank()) {
            return ApiResponse.error("用户名与密码不能为空");
        }
        if (userRepo.findByUsername(req.getUsername()).isPresent()) {
            return ApiResponse.error("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(HashUtil.hashPassword(req.getPassword()));
        u.setDisplayName(req.getDisplayName());
        u.setStatus(User.Status.active);
        if ("admin".equalsIgnoreCase(req.getRole())) u.setRole(User.Role.admin);
        else u.setRole(User.Role.user);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);
        audit(admin, "create_user", "User", u.getId(), "username=" + u.getUsername() + ", role=" + u.getRole());
        return ApiResponse.ok("创建成功", new UserInfo(u));
    }

    // 更新角色
    @PutMapping("/users/{id}/role")
    public ApiResponse<?> updateUserRole(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                         @PathVariable("id") Long id,
                                         @RequestBody UpdateUserRoleRequest req) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("用户不存在");
        User u = opt.get();
        User.Role newRole = "admin".equalsIgnoreCase(req.getRole()) ? User.Role.admin : User.Role.user;
        if (u.getRole() == User.Role.admin && newRole == User.Role.user) {
            long adminCount = userRepo.countByRole(User.Role.admin);
            if (adminCount <= 1) return ApiResponse.error("不能移除最后一个管理员");
        }
        u.setRole(newRole);
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);
        audit(admin, "update_user_role", "User", u.getId(), "role=" + newRole);
        return ApiResponse.ok("更新成功", new UserInfo(u));
    }

    // 重置密码
    @PutMapping("/users/{id}/password")
    public ApiResponse<?> resetPassword(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                        @PathVariable("id") Long id,
                                        @RequestBody UpdateUserPasswordRequest req) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("用户不存在");
        if (req.getPassword() == null || req.getPassword().isBlank()) return ApiResponse.error("密码不能为空");
        User u = opt.get();
        u.setPasswordHash(HashUtil.hashPassword(req.getPassword()));
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);
        // 清理会话
        try { sessionRepo.deleteByUser(u); } catch (Exception ignored) {}
        audit(admin, "reset_user_password", "User", u.getId(), null);
        return ApiResponse.ok("重置成功", new UserInfo(u));
    }

    // 删除用户
    @DeleteMapping("/users/{id}")
    public ApiResponse<?> deleteUser(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                     @PathVariable("id") Long id) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("用户不存在");
        User u = opt.get();
        if (u.getRole() == User.Role.admin) {
            long adminCount = userRepo.countByRole(User.Role.admin);
            if (adminCount <= 1) return ApiResponse.error("不能删除最后一个管理员");
        }
        try { sessionRepo.deleteByUser(u); } catch (Exception ignored) {}
        userRepo.delete(u);
        audit(admin, "delete_user", "User", id, null);
        return ApiResponse.ok("删除成功", null);
    }

    // 审计日志分页
    @GetMapping("/audit-logs")
    public ApiResponse<?> listAuditLogs(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                        @RequestParam(name = "page", defaultValue = "1") int page,
                                        @RequestParam(name = "size", defaultValue = "20") int size) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        page = Math.max(1, page);
        size = Math.min(200, Math.max(1, size));
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AdminAuditLog> pg = auditRepo.findAll(pageable);
        Map<String, Object> resp = new HashMap<>();
        resp.put("page", page);
        resp.put("size", size);
        resp.put("total", pg.getTotalElements());
        resp.put("items", pg.getContent().stream().map(AdminAuditInfo::new).toList());
        return ApiResponse.ok("ok", resp);
    }

    // 管理员删除房间
    @DeleteMapping("/rooms/{id}")
    public ApiResponse<?> deleteRoom(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                     @PathVariable("id") Long id) {
        User admin = requireAdmin(authHeader).orElse(null);
        if (admin == null) return ApiResponse.error("权限不足或令牌无效");
        boolean ok = roomService.adminDeleteRoom(id);
        if (!ok) return ApiResponse.error("房间不存在");
        audit(admin, "delete_room", "Room", id, null);
        return ApiResponse.ok("删除成功", null);
    }

    // -------- DTOs --------
    static class UserInfo {
        public Long id;
        public String username;
        public String displayName;
        public String role;
        public String status;
        public String createdAt;
        public String updatedAt;
        public UserInfo(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.displayName = u.getDisplayName();
            this.role = u.getRole().name();
            this.status = u.getStatus().name();
            this.createdAt = u.getCreatedAt() == null ? null : u.getCreatedAt().toString();
            this.updatedAt = u.getUpdatedAt() == null ? null : u.getUpdatedAt().toString();
        }
    }

    static class AdminAuditInfo {
        public Long id;
        public String action;
        public Long operatorId;
        public String targetType;
        public Long targetId;
        public String detail;
        public String createdAt;
        public AdminAuditInfo(AdminAuditLog l) {
            this.id = l.getId();
            this.action = l.getAction();
            this.operatorId = l.getOperator() == null ? null : l.getOperator().getId();
            this.targetType = l.getTargetType();
            this.targetId = l.getTargetId();
            this.detail = l.getDetail();
            this.createdAt = l.getCreatedAt() == null ? null : l.getCreatedAt().toString();
        }
    }

    static class InviteCodeInfo {
        public Long id;
        public String code;
        public boolean used;
        public String usedBy;
        public String usedAt;
        public String createdBy;
        public String createdAt;
        public String expiresAt;
        public String batchNo;
        public boolean expired;
        
        public InviteCodeInfo(InviteCode ic) {
            this.id = ic.getId();
            this.code = ic.getCode();
            this.used = ic.isUsed();
            this.usedBy = ic.getUsedBy() == null ? null : ic.getUsedBy().getUsername();
            this.usedAt = ic.getUsedAt() == null ? null : ic.getUsedAt().toString();
            this.createdBy = ic.getCreatedBy() == null ? null : ic.getCreatedBy().getUsername();
            this.createdAt = ic.getCreatedAt() == null ? null : ic.getCreatedAt().toString();
            this.expiresAt = ic.getExpiresAt() == null ? null : ic.getExpiresAt().toString();
            this.batchNo = ic.getBatchNo();
            this.expired = ic.getExpiresAt() != null && ic.getExpiresAt().isBefore(LocalDateTime.now());
        }
    }
}