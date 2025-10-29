package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    Optional<InviteCode> findByCode(String code);
    boolean existsByCode(String code);
}