package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Integer> {
    Optional<Game> findByCode(String code);
    List<Game> findByActiveTrue();
}