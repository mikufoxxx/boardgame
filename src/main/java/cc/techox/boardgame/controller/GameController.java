package cc.techox.boardgame.controller;

import cc.techox.boardgame.common.ApiResponse;
import cc.techox.boardgame.model.Game;
import cc.techox.boardgame.repo.GameRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameRepository gameRepo;

    public GameController(GameRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    @GetMapping("")
    public ApiResponse<?> listActive() {
        List<Game> games = gameRepo.findByActiveTrue();
        return ApiResponse.ok("ok", games.stream().map(GameInfo::new).toList());
    }

    static class GameInfo {
        public String code;
        public String name;
        public Integer minPlayers;
        public Integer maxPlayers;
        public GameInfo(Game g) {
            this.code = g.getCode();
            this.name = g.getName();
            this.minPlayers = g.getMinPlayers();
            this.maxPlayers = g.getMaxPlayers();
        }
    }
}