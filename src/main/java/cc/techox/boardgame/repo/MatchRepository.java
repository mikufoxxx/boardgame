package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
}