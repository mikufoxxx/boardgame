package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.InviteCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    Optional<InviteCode> findByCode(String code);
    boolean existsByCode(String code);
    
    // 管理员查询方法
    Page<InviteCode> findByUsed(boolean used, Pageable pageable);
    Page<InviteCode> findByBatchNoContainingIgnoreCase(String batchNo, Pageable pageable);
    Page<InviteCode> findByUsedAndBatchNoContainingIgnoreCase(boolean used, String batchNo, Pageable pageable);
}