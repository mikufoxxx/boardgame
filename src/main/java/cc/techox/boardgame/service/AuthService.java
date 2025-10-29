package cc.techox.boardgame.service;

import cc.techox.boardgame.model.AuthSession;
import cc.techox.boardgame.model.InviteCode;
import cc.techox.boardgame.model.User;
import cc.techox.boardgame.repo.AuthSessionRepository;
import cc.techox.boardgame.repo.InviteCodeRepository;
import cc.techox.boardgame.repo.UserRepository;
import cc.techox.boardgame.util.HashUtil;
import cc.techox.boardgame.util.TokenUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepo;
    private final AuthSessionRepository sessionRepo;
    private final InviteCodeRepository inviteRepo;

    public AuthService(UserRepository userRepo, AuthSessionRepository sessionRepo, InviteCodeRepository inviteRepo) {
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.inviteRepo = inviteRepo;
    }

    @Transactional
    public User register(String username, String password, String inviteCode, String displayName) {
        if (userRepo.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        InviteCode code = inviteRepo.findByCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("邀请码不存在"));
        if (code.isUsed()) {
            throw new IllegalArgumentException("邀请码已被使用");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(HashUtil.hashPassword(password));
        u.setDisplayName(displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : username);
        u.setStatus(User.Status.active);
        u.setRole(User.Role.user);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userRepo.save(u);

        code.setUsed(true);
        code.setUsedBy(u);
        code.setUsedAt(LocalDateTime.now());
        inviteRepo.save(code);
        return u;
    }

    @Transactional
    public String login(String username, String password, String ip) {
        User u = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!HashUtil.verifyPassword(password, u.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        AuthSession s = new AuthSession();
        s.setUser(u);
        s.setSessionToken(TokenUtil.randomToken32());
        s.setCreatedAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(30));
        s.setRevoked(false);
        s.setIpAddress(ip);
        sessionRepo.save(s);
        return s.getSessionToken();
    }

    @Transactional
    public void logout(String token) {
        Optional<AuthSession> opt = sessionRepo.findBySessionToken(token);
        if (opt.isEmpty()) return;
        AuthSession s = opt.get();
        s.setRevoked(true);
        sessionRepo.save(s);
    }

    public Optional<User> getUserByToken(String token) {
        return sessionRepo.findBySessionToken(token)
                .filter(s -> !s.isRevoked() && (s.getExpiresAt() == null || s.getExpiresAt().isAfter(LocalDateTime.now())))
                .map(AuthSession::getUser);
    }

    @Transactional
    public User updateProfile(User user, String displayName, String currentPassword, String newPassword) {
        // 重新从数据库获取用户，确保是managed状态
        User managedUser = userRepo.findById(user.getId()).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        
        // 如果要修改密码，需要验证当前密码
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                throw new IllegalArgumentException("修改密码需要提供当前密码");
            }
            if (!HashUtil.verifyPassword(currentPassword, managedUser.getPasswordHash())) {
                throw new IllegalArgumentException("当前密码错误");
            }
            managedUser.setPasswordHash(HashUtil.hashPassword(newPassword.trim()));
        }

        // 修改昵称
        if (displayName != null && !displayName.trim().isEmpty()) {
            managedUser.setDisplayName(displayName.trim());
        }

        managedUser.setUpdatedAt(LocalDateTime.now());
        return userRepo.save(managedUser);
    }
}