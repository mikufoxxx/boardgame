package cc.techox.boardgame.repo;

import cc.techox.boardgame.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    long countByRole(User.Role role);
    
    // 按角色查询用户
    Page<User> findByRole(User.Role role, Pageable pageable);
    
    // 按状态查询用户
    Page<User> findByStatus(User.Status status, Pageable pageable);
    
    // 按角色和状态查询用户
    Page<User> findByRoleAndStatus(User.Role role, User.Status status, Pageable pageable);
    
    // 按用户名模糊搜索
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    
    // 按显示名模糊搜索
    Page<User> findByDisplayNameContainingIgnoreCase(String displayName, Pageable pageable);
    
    // 按用户名或显示名模糊搜索
    Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String username, String displayName, Pageable pageable);
    
    // 按用户名搜索和角色筛选
    Page<User> findByUsernameContainingIgnoreCaseAndRole(String username, User.Role role, Pageable pageable);
    
    // 按用户名搜索和状态筛选
    Page<User> findByUsernameContainingIgnoreCaseAndStatus(String username, User.Status status, Pageable pageable);
    
    // 按用户名搜索、角色和状态筛选
    Page<User> findByUsernameContainingIgnoreCaseAndRoleAndStatus(String username, User.Role role, User.Status status, Pageable pageable);
    
    // 统计各角色用户数量
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    Object[][] countByRoleGroup();
    
    // 统计各状态用户数量
    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    Object[][] countByStatusGroup();
}