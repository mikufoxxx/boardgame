-- Auth Sessions 表优化脚本
-- 实现"一个用户同时只能在一个设备登录"的机制

-- 1. 添加索引以提升查询性能
CREATE INDEX IF NOT EXISTS idx_auth_session_user_active ON auth_sessions(user_id, revoked, expires_at);

-- 2. 清理冗余的会话数据
-- 对于每个用户，只保留最新的一个活跃会话，其他的标记为已撤销

-- 首先，找出每个用户最新的会话ID
CREATE TEMPORARY TABLE latest_sessions AS
SELECT user_id, MAX(id) as latest_session_id
FROM auth_sessions 
WHERE revoked = 0 AND (expires_at IS NULL OR expires_at > NOW())
GROUP BY user_id;

-- 将除了最新会话之外的所有会话标记为已撤销
UPDATE auth_sessions 
SET revoked = 1 
WHERE revoked = 0 
  AND (expires_at IS NULL OR expires_at > NOW())
  AND id NOT IN (SELECT latest_session_id FROM latest_sessions);

-- 清理临时表
DROP TEMPORARY TABLE latest_sessions;

-- 3. 删除过期的已撤销会话（保留最近30天的记录用于审计）
DELETE FROM auth_sessions 
WHERE revoked = 1 
  AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 4. 统计优化结果
SELECT 
    COUNT(*) as total_sessions,
    SUM(CASE WHEN revoked = 0 THEN 1 ELSE 0 END) as active_sessions,
    SUM(CASE WHEN revoked = 1 THEN 1 ELSE 0 END) as revoked_sessions,
    COUNT(DISTINCT user_id) as unique_users
FROM auth_sessions;

-- 5. 验证每个用户最多只有一个活跃会话
SELECT user_id, COUNT(*) as active_session_count
FROM auth_sessions 
WHERE revoked = 0 AND (expires_at IS NULL OR expires_at > NOW())
GROUP BY user_id
HAVING COUNT(*) > 1;

-- 如果上面的查询返回任何结果，说明还有用户拥有多个活跃会话，需要进一步处理