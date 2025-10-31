package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.AuthSession;
import cc.techox.boardgame.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findBySessionToken(String sessionToken);
    long deleteByUser(User user);
    
    /**
     * 查找用户的活跃会话（未撤销且未过期）
     */
    @Query("SELECT s FROM AuthSession s WHERE s.user = :user AND s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > :now)")
    Optional<AuthSession> findActiveSessionByUser(@Param("user") User user, @Param("now") LocalDateTime now);
    
    /**
     * 根据会话令牌查找会话，并主动加载用户信息（解决懒加载问题）
     */
    @Query("SELECT s FROM AuthSession s JOIN FETCH s.user WHERE s.sessionToken = :sessionToken")
    Optional<AuthSession> findBySessionTokenWithUser(@Param("sessionToken") String sessionToken);
}