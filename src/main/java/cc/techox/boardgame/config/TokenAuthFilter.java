package cc.techox.boardgame.config;

import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class TokenAuthFilter extends OncePerRequestFilter {
    private final AuthService authService;

    public TokenAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String h = request.getHeader("Authorization");
        String token = parseBearer(h);
        if (token != null) {
            Optional<User> userOpt = authService.getUserByToken(token);
            userOpt.ifPresent(user -> request.setAttribute("authUser", user));
        }
        filterChain.doFilter(request, response);
    }

    private String parseBearer(String h) {
        if (h == null) return null;
        String lower = h.toLowerCase();
        if (lower.startsWith("bearer ")) return h.substring(7).trim();
        return null;
    }
}