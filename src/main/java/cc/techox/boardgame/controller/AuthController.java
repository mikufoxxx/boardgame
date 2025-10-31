package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.dto.LoginRequest;
import cc.techox.boardgame.dto.RegisterRequest;
import cc.techox.boardgame.dto.UpdateProfileRequest;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.util.AuthUtil;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest req) {
        try {
            User u = authService.register(req.getUsername(), req.getPassword(), req.getInviteCode(), req.getDisplayName());
            return ApiResponse.ok("注册成功", new UserInfo(u));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        try {
            String token = authService.login(req.getUsername(), req.getPassword(), http.getRemoteAddr());
            User u = authService.getUserByToken(token).orElseThrow();
            return ApiResponse.ok("登录成功", new LoginResp(token, new UserInfo(u)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            String token = AuthUtil.parseBearer(authHeader);
            if (token == null) return ApiResponse.error("未提供令牌");
            authService.logout(token);
            return ApiResponse.ok("注销成功", null);
        } catch (Exception e) {
            return ApiResponse.error("注销失败: " + e.getMessage());
        }
    }

    @PostMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                        @RequestBody UpdateProfileRequest req) {
        try {
            User user = AuthUtil.requireAuth(authHeader, authService);
            User updated = authService.updateProfile(user, req.getDisplayName(), req.getCurrentPassword(), req.getNewPassword());
            return ApiResponse.ok("个人信息更新成功", new UserInfo(updated));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("更新个人信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<?> getCurrentUser(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        try {
            User user = AuthUtil.requireAuth(authHeader, authService);
            return ApiResponse.ok("ok", new UserInfo(user));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }



    static class UserInfo {
        public Long id;
        public String username;
        public String displayName;
        public String role;
        public UserInfo(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.displayName = u.getDisplayName();
            this.role = u.getRole().name();
        }
    }

    static class LoginResp {
        public String session_token;
        public UserInfo user;
        public LoginResp(String t, UserInfo u) { this.session_token = t; this.user = u; }
    }
}