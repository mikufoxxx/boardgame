-- 数据库优化迁移脚本
-- 执行前请备份数据库！

-- 1. 为现有表添加索引以提升查询性能
-- Users 表索引
CREATE INDEX IF NOT EXISTS idx_user_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_user_created ON users(created_at);

-- Rooms 表索引
CREATE INDEX IF NOT EXISTS idx_room_status ON rooms(status);
CREATE INDEX IF NOT EXISTS idx_room_game ON rooms(game_id);
CREATE INDEX IF NOT EXISTS idx_room_owner ON rooms(owner_id);
CREATE INDEX IF NOT EXISTS idx_room_created ON rooms(created_at);
CREATE INDEX IF NOT EXISTS idx_room_status_game ON rooms(status, game_id);

-- Room Players 表索引
CREATE INDEX IF NOT EXISTS idx_room_player_room ON room_players(room_id);
CREATE INDEX IF NOT EXISTS idx_room_player_user ON room_players(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_room_player_unique ON room_players(room_id, user_id);

-- Matches 表索引
CREATE INDEX IF NOT EXISTS idx_match_room_status ON matches(room_id, status);
CREATE INDEX IF NOT EXISTS idx_match_game_status ON matches(game_id, status);
CREATE INDEX IF NOT EXISTS idx_match_started_at ON matches(started_at);

-- Match Actions 表索引
CREATE INDEX IF NOT EXISTS idx_match_action_match ON match_actions(match_id);
CREATE INDEX IF NOT EXISTS idx_match_action_type ON match_actions(action_type);
CREATE INDEX IF NOT EXISTS idx_match_action_created ON match_actions(created_at);

-- Auth Sessions 表索引
CREATE INDEX IF NOT EXISTS idx_auth_session_token ON auth_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_auth_session_user ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires ON auth_sessions(expires_at);

-- Invite Codes 表索引
CREATE INDEX IF NOT EXISTS idx_invite_code_code ON invite_codes(code);
CREATE INDEX IF NOT EXISTS idx_invite_code_used ON invite_codes(is_used);
CREATE INDEX IF NOT EXISTS idx_invite_code_batch ON invite_codes(batch_no);
CREATE INDEX IF NOT EXISTS idx_invite_code_expires ON invite_codes(expires_at);

-- Admin Audit Logs 表索引
CREATE INDEX IF NOT EXISTS idx_audit_log_operator ON admin_audit_logs(operator_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON admin_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON admin_audit_logs(created_at);

-- Chat Messages 表索引
CREATE INDEX IF NOT EXISTS idx_chat_message_room ON chat_messages(room_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_user ON chat_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_created ON chat_messages(created_at);

-- 2. 修改 Matches 表结构
-- 移除 state_json 字段（游戏状态现在存储在内存中）
ALTER TABLE matches DROP COLUMN IF EXISTS state_json;

-- 添加新的统计字段
ALTER TABLE matches ADD COLUMN IF NOT EXISTS player_count INT NOT NULL DEFAULT 0;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS turn_count INT NOT NULL DEFAULT 0;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS last_action_at TIMESTAMP NULL;

-- 3. 修改 Room Players 表结构
-- 移除频繁更新的字段到内存管理
ALTER TABLE room_players DROP COLUMN IF EXISTS seat_no;
ALTER TABLE room_players DROP COLUMN IF EXISTS team;
ALTER TABLE room_players DROP COLUMN IF EXISTS is_ready;
ALTER TABLE room_players DROP COLUMN IF EXISTS last_active_at;

-- 移除 joined_at 字段 - 加入时间现在由内存管理
ALTER TABLE room_players DROP COLUMN IF EXISTS joined_at;

-- 4. 修改 Match Actions 表结构
-- 移除 turn_no 字段，重命名 action_json 为 action_data
ALTER TABLE match_actions DROP COLUMN IF EXISTS turn_no;
ALTER TABLE match_actions CHANGE COLUMN action_json action_data JSON;

-- 5. 清理过期数据
-- 删除超过30天的已结束对局记录（保留重要统计信息）
DELETE FROM match_actions 
WHERE match_id IN (
    SELECT id FROM matches 
    WHERE status IN ('finished', 'aborted') 
    AND ended_at < DATE_SUB(NOW(), INTERVAL 30 DAY)
);

-- 删除超过7天的已解散房间记录
DELETE FROM rooms 
WHERE status = 'disbanded' 
AND updated_at < DATE_SUB(NOW(), INTERVAL 7 DAY);

-- 删除过期的认证会话
DELETE FROM auth_sessions 
WHERE expires_at < NOW() OR revoked = 1;

-- 删除超过90天的审计日志
DELETE FROM admin_audit_logs 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 6. 优化表存储引擎和字符集（如果需要）
-- ALTER TABLE users ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ALTER TABLE rooms ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ALTER TABLE matches ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 分析表以更新统计信息
ANALYZE TABLE users;
ANALYZE TABLE rooms;
ANALYZE TABLE room_players;
ANALYZE TABLE matches;
ANALYZE TABLE match_actions;
ANALYZE TABLE auth_sessions;
ANALYZE TABLE invite_codes;
ANALYZE TABLE admin_audit_logs;
ANALYZE TABLE chat_messages;
ANALYZE TABLE games;

-- 8. 创建视图以简化常用查询
CREATE OR REPLACE VIEW active_rooms AS
SELECT 
    r.id,
    r.name,
    r.status,
    r.max_players,
    r.is_private,
    r.created_at,
    g.code as game_code,
    g.name as game_name,
    u.username as owner_username,
    COUNT(rp.id) as current_players
FROM rooms r
JOIN games g ON r.game_id = g.id
JOIN users u ON r.owner_id = u.id
LEFT JOIN room_players rp ON r.id = rp.room_id
WHERE r.status IN ('waiting', 'playing')
GROUP BY r.id, r.name, r.status, r.max_players, r.is_private, r.created_at, 
         g.code, g.name, u.username;

CREATE OR REPLACE VIEW match_statistics AS
SELECT 
    m.id,
    m.status,
    m.player_count,
    m.turn_count,
    m.started_at,
    m.ended_at,
    TIMESTAMPDIFF(MINUTE, m.started_at, COALESCE(m.ended_at, NOW())) as duration_minutes,
    g.code as game_code,
    r.name as room_name,
    winner.username as winner_username
FROM matches m
JOIN games g ON m.game_id = g.id
LEFT JOIN rooms r ON m.room_id = r.id
LEFT JOIN users winner ON m.winner_user_id = winner.id;

-- 迁移完成提示
SELECT 'Database migration completed successfully!' as status;
SELECT 'Please restart the application to use the new memory-based game state management.' as note;