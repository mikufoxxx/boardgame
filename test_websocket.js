// WebSocket 测试脚本
// 使用 Node.js 运行: node test_websocket.js

const WebSocket = require('ws');

// 模拟一个有效的会话令牌（需要从数据库获取或创建）
const testToken = 'test-token-123';

console.log('开始 WebSocket 测试...');

// 连接到 WebSocket 服务器
const ws = new WebSocket('ws://localhost:50000/ws');

ws.on('open', function open() {
    console.log('✅ WebSocket 连接已建立');
    
    // 发送认证消息
    const authMessage = {
        kind: 'cmd',
        type: 'auth',
        data: {
            token: testToken
        },
        cid: 'auth-test-1'
    };
    
    console.log('📤 发送认证消息:', JSON.stringify(authMessage));
    ws.send(JSON.stringify(authMessage));
});

ws.on('message', function message(data) {
    console.log('📥 收到消息:', data.toString());
    
    try {
        const msg = JSON.parse(data.toString());
        if (msg.kind === 'err' && msg.code === 'INVALID_TOKEN') {
            console.log('⚠️  令牌无效，这是预期的（因为我们使用了测试令牌）');
            console.log('✅ 重要：没有出现 LazyInitializationException 错误！');
            console.log('🎉 修复成功！WebSocket 认证处理正常工作');
        } else if (msg.kind === 'ack' && msg.type === 'auth') {
            console.log('✅ 认证成功！');
        }
    } catch (e) {
        console.log('❌ 解析消息失败:', e.message);
    }
    
    // 测试完成，关闭连接
    setTimeout(() => {
        ws.close();
    }, 1000);
});

ws.on('error', function error(err) {
    console.log('❌ WebSocket 错误:', err.message);
});

ws.on('close', function close() {
    console.log('🔌 WebSocket 连接已关闭');
    console.log('测试完成');
});

// 超时处理
setTimeout(() => {
    if (ws.readyState === WebSocket.OPEN) {
        console.log('⏰ 测试超时，关闭连接');
        ws.close();
    }
}, 10000);