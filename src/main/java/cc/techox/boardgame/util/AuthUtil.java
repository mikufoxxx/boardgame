package cc.techox.boardgame.util;

import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;

/**
 * 认证工具类 - 统一处理token解析和用户验证
 */
public class AuthUtil {
    
    /**
     * 从Authorization header中解析Bearer token
     */
    public static String parseBearer(String authHeader) {
        if (authHeader == null) return null;
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
    
    /**
     * 验证token并返回用户，失败时抛出异常
     */
    public static User requireAuth(String authHeader, AuthService authService) {
        String token = parseBearer(authHeader);
        if (token == null) {
            throw new IllegalArgumentException("未提供令牌");
        }
        return authService.getUserByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("令牌无效或已过期"));
    }
    
    /**
     * 验证token并返回用户，失败时返回null
     */
    public static User tryAuth(String authHeader, AuthService authService) {
        try {
            return requireAuth(authHeader, authService);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * 验证管理员权限
     */
    public static User requireAdmin(String authHeader, AuthService authService) {
        User user = requireAuth(authHeader, authService);
        if (user.getRole() != User.Role.admin) {
            throw new IllegalArgumentException("需要管理员权限");
        }
        return user;
    }
}