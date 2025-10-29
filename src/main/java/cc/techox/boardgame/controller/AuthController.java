package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.dto.LoginRequest;
import cc.techox.boardgame.dto.RegisterRequest;
import cc.techox.boardgame.dto.UpdateProfileRequest;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
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
        String token = parseBearer(authHeader);
        if (token == null) return ApiResponse.error("未提供令牌");
        authService.logout(token);
        return ApiResponse.ok("注销成功", null);
    }

    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                        @RequestBody UpdateProfileRequest req) {
        String token = parseBearer(authHeader);
        if (token == null) return ApiResponse.error("未提供令牌");
        
        User user = authService.getUserByToken(token).orElse(null);
        if (user == null) return ApiResponse.error("未登录或令牌无效");
        
        try {
            User updatedUser = authService.updateProfile(user, req.getDisplayName(), req.getCurrentPassword(), req.getNewPassword());
            return ApiResponse.ok("修改成功", new UserInfo(updatedUser));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    private String parseBearer(String h) {
        if (h == null) return null;
        if (h.toLowerCase().startsWith("bearer ")) return h.substring(7).trim();
        return null;
    }

    static class UserInfo {
        public Long id;
        public String username;
        public String displayName;
        public UserInfo(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.displayName = u.getDisplayName();
        }
    }

    static class LoginResp {
        public String session_token;
        public UserInfo user;
        public LoginResp(String t, UserInfo u) { this.session_token = t; this.user = u; }
    }
}