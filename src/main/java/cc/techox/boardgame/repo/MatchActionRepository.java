package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.MatchAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchActionRepository extends JpaRepository<MatchAction, Long> {
}