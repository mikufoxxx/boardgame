package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.AuthSession;
import cc.techox.boardgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findBySessionToken(String sessionToken);
    long deleteByUser(User user);
}