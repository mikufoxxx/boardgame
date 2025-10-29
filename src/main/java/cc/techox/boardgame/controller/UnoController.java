package cc.techox.boardgame.controller;

import cc.techox.boardgame.model.Match;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.service.AuthService;
import cc.techox.boardgame.service.UnoService;
import cc.techox.boardgame.websocket.GameEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uno")
public class UnoController {
    private final UnoService unoService;
    private final AuthService authService;
    private final GameEventPublisher eventPublisher;

    public UnoController(UnoService unoService, AuthService authService, GameEventPublisher eventPublisher) {
        this.unoService = unoService;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
    }

    private User authed(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) throw new IllegalArgumentException("缺少令牌");
        String token = authHeader.substring(7);
        return authService.getUserByToken(token).orElseThrow(() -> new IllegalArgumentException("无效令牌"));
    }

    @PostMapping("/rooms/{roomId}/start")
    public ResponseEntity<?> start(@PathVariable long roomId, @RequestHeader(value = "Authorization", required = false) String auth) {
        User u = authed(auth);
        Match match = unoService.startInRoom(roomId, u);
        
        // 发布游戏开始事件，触发WebSocket推送
        eventPublisher.publishGameStarted(roomId, match.getId());
        
        return ResponseEntity.ok(unoService.view(match.getId(), u.getId()));
    }

    @GetMapping("/matches/{id}")
    public ResponseEntity<?> view(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        User u = authed(auth);
        return ResponseEntity.ok(unoService.view(id, u.getId()));
    }

    @PostMapping("/matches/{id}/play")
    public ResponseEntity<?> play(@PathVariable long id, @RequestBody Map<String,String> body, @RequestHeader(value = "Authorization", required = false) String auth) {
        User u = authed(auth);
        String card = body.get("card");
        String color = body.get("color");
        return ResponseEntity.ok(unoService.play(id, u, card, color));
    }

    @PostMapping("/matches/{id}/draw-pass")
    public ResponseEntity<?> drawPass(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        User u = authed(auth);
        return ResponseEntity.ok(unoService.drawAndPass(id, u));
    }
}