package cc.techox.boardgame.config;

import cc.techox.boardgame.model.Game;
import cc.techox.boardgame.repo.GameRepository;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

@Component
public class GameSeeder {
    private final GameRepository gameRepo;

    public GameSeeder(GameRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    @PostConstruct
    public void seed() {
        gameRepo.findByCode("UNO").orElseGet(() -> {
            Game g = new Game();
            g.setCode("UNO");
            g.setName("UNO");
            g.setMinPlayers(2);
            g.setMaxPlayers(10);
            g.setActive(true);
            g.setCreatedAt(LocalDateTime.now());
            return gameRepo.save(g);
        });
    }
}