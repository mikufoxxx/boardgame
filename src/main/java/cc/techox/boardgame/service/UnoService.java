package cc.techox.boardgame.service;

import cc.techox.boardgame.config.GameDataManager;
import cc.techox.boardgame.memory.GameStateManager;
import cc.techox.boardgame.model.*;
import cc.techox.boardgame.repo.*;
import cc.techox.boardgame.game.uno.UnoEngine;
import cc.techox.boardgame.game.uno.UnoState;
import cc.techox.boardgame.game.uno.UnoCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UnoService {
    private final RoomRepository roomRepo;
    private final UserRepository userRepo;
    private final GameStateManager gameStateManager;
    private final GameDataManager gameDataManager;
    
    // 使用原子递增器生成 matchId，避免 JavaScript 精度问题
    private static final AtomicLong matchIdGenerator = new AtomicLong(1000);
    
    public UnoService(RoomRepository roomRepo, 
                     UserRepository userRepo,
                     GameStateManager gameStateManager,
                     GameDataManager gameDataManager) {
        this.roomRepo = roomRepo;
        this.userRepo = userRepo;
        this.gameStateManager = gameStateManager;
        this.gameDataManager = gameDataManager;
    }

    @Transactional
    public Map<String, Object> drawAndPassWithDetails(long matchId, User player) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行摸牌逻辑（获取详细信息）
        UnoEngine.DrawResult drawResult = UnoEngine.drawAndPassWithDetails(currentState, player.getId());
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, drawResult.newState);
        
        // 增加回合数
        gameStateManager.incrementGameTurn(matchId);
        
        // 构建返回结果，包含摸牌详细信息
        Map<String, Object> result = new HashMap<>(UnoEngine.publicViewWithUserInfo(drawResult.newState, player.getId(), getUserInfoMap(drawResult.newState)));
        result.put("drawnCards", drawResult.drawnCards.stream()
            .map(UnoCard::codeToObject)
            .collect(java.util.stream.Collectors.toList()));
        result.put("drawCount", drawResult.drawCount);
        
        return result;
    }

    @Transactional
    public Long startInRoom(long roomId, User starter) {
        System.out.println("=== 开始游戏请求 ===");
        System.out.println("房间ID: " + roomId + ", 发起者: " + starter.getUsername() + " (ID: " + starter.getId() + ")");
        
        Room room = roomRepo.findById(roomId).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
        System.out.println("房间信息: " + room.getName() + ", 房主: " + room.getOwner().getUsername() + " (ID: " + room.getOwner().getId() + ")");
        
        if (!room.getOwner().getId().equals(starter.getId())) {
            System.err.println("权限检查失败: 只有房主可以开始游戏");
            throw new IllegalArgumentException("只有房主可以开始游戏");
        }
        
        if (room.getStatus() != Room.Status.waiting) {
            System.err.println("房间状态检查失败: 当前状态为 " + room.getStatus() + ", 需要 waiting 状态");
            throw new IllegalArgumentException("房间状态不允许开始游戏");
        }

        // 从内存获取房间玩家列表
        Map<Long, GameStateManager.PlayerRoomState> roomPlayers = gameStateManager.getRoomPlayers(roomId);
        System.out.println("房间玩家数量: " + roomPlayers.size() + ", 玩家列表: " + roomPlayers.keySet());
        
        if (roomPlayers.size() < 2) {
            System.err.println("玩家数量不足: 当前 " + roomPlayers.size() + " 人, 至少需要 2 人");
            throw new IllegalArgumentException("至少需要2名玩家才能开始游戏");
        }

        // 生成唯一的 matchId（使用递增 ID，避免 JavaScript 精度问题）
        Long matchId = matchIdGenerator.incrementAndGet();
        System.out.println("生成对局ID: " + matchId + " (房间ID: " + roomId + ")");

        // 创建初始游戏状态并存储到内存
        System.out.println("正在加载卡牌数据...");
        List<Map<String, Object>> cardDeck = gameDataManager.createCardDeck("uno");
        System.out.println("卡牌数据加载完成, 总计: " + cardDeck.size() + " 张卡牌");
        
        System.out.println("正在创建初始游戏状态...");
        UnoState initialState = UnoEngine.createInitialStateWithDeck(
            roomPlayers.keySet().stream().toList(), // 使用玩家ID列表
            cardDeck
        );
        System.out.println("初始游戏状态创建完成, 当前玩家: " + initialState.currentPlayer().userId);
        
        // 在内存中创建游戏会话
        System.out.println("正在保存游戏状态到内存...");
        gameStateManager.createGameState(matchId, "uno", initialState, roomId, roomPlayers.size());

        // 更新房间状态
        System.out.println("正在更新房间状态为 playing...");
        room.setStatus(Room.Status.playing);
        roomRepo.save(room);

        System.out.println("=== 游戏开始成功 ===");
        System.out.println("对局ID: " + matchId + ", 房间ID: " + roomId + ", 玩家数: " + roomPlayers.size());
        return matchId;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(long matchId, long viewerId) {
        // 验证游戏会话存在
        gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        // 从内存获取游戏状态
        UnoState state = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));
        
        return UnoEngine.publicViewWithUserInfo(state, viewerId, getUserInfoMap(state));
    }

    @Transactional
    public Map<String, Object> play(long matchId, User player, String card, String chosenColor) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行游戏逻辑
        UnoState newState = UnoEngine.playCard(currentState, player.getId(), card, chosenColor);
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, newState);
        
        // 增加回合数
        gameStateManager.incrementGameTurn(matchId);
        
        // 检查游戏是否结束
        if (UnoEngine.isGameFinished(newState)) {
            Long winnerId = UnoEngine.getWinner(newState);
            gameStateManager.finishGame(matchId, "finished", winnerId);
        }
        
        return UnoEngine.publicViewWithUserInfo(newState, player.getId(), getUserInfoMap(newState));
    }

    @Transactional
    public Map<String, Object> drawAndPass(long matchId, User player) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行摸牌逻辑
        UnoState newState = UnoEngine.drawAndPass(currentState, player.getId());
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, newState);
        
        // 增加回合数
        gameStateManager.incrementGameTurn(matchId);
        
        return UnoEngine.publicViewWithUserInfo(newState, player.getId(), getUserInfoMap(newState));
    }
    
    @Transactional
    public Map<String, Object> callUno(long matchId, User player) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行 UNO 调用逻辑
        UnoState newState = UnoEngine.callUno(currentState, player.getId());
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, newState);
        
        return UnoEngine.publicViewWithUserInfo(newState, player.getId(), getUserInfoMap(newState));
    }

    @Transactional
    public Map<String, Object> challengeWildDraw4(long matchId, User challenger) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行质疑逻辑
        UnoEngine.ChallengeResult challengeResult = UnoEngine.challengeWildDraw4(currentState, challenger.getId());
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, challengeResult.newState);
        
        // 构建返回结果，包含质疑详细信息
        Map<String, Object> result = new HashMap<>(UnoEngine.publicViewWithUserInfo(challengeResult.newState, challenger.getId(), getUserInfoMap(challengeResult.newState)));
        result.put("challengeSuccessful", challengeResult.challengeSuccessful);
        result.put("challengerId", challengeResult.challengerId);
        result.put("challengedPlayerId", challengeResult.challengedPlayerId);
        result.put("penaltyCards", challengeResult.penaltyCards);
        result.put("reason", challengeResult.reason);
        
        return result;
    }

    @Transactional
    public Map<String, Object> penalizeForgetUno(long matchId, long penalizedPlayerId, User reporter) {
        // 从内存获取游戏会话
        GameStateManager.GameStateData gameSession = gameStateManager.getGameSession(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏会话不存在"));
        
        if (!"playing".equals(gameSession.getStatus())) {
            throw new IllegalArgumentException("游戏已结束");
        }

        // 从内存获取游戏状态
        UnoState currentState = (UnoState) gameStateManager.getGameState(matchId)
            .orElseThrow(() -> new IllegalArgumentException("游戏状态不存在"));

        // 执行 UNO 惩罚逻辑
        UnoEngine.UnoPenaltyResult penaltyResult = UnoEngine.penalizeForgetUno(currentState, penalizedPlayerId);
        
        // 更新内存中的游戏状态
        gameStateManager.updateGameState(matchId, penaltyResult.newState);
        
        // 构建返回结果，包含惩罚详细信息
        Map<String, Object> result = new HashMap<>(UnoEngine.publicViewWithUserInfo(penaltyResult.newState, reporter.getId(), getUserInfoMap(penaltyResult.newState)));
        result.put("penalizedPlayerId", penaltyResult.penalizedPlayerId);
        result.put("penaltyCards", penaltyResult.penaltyCards);
        result.put("reason", penaltyResult.reason);
        
        return result;
    }

    /**
     * 获取游戏中所有玩家的用户信息
     */
    private Map<Long, Map<String, Object>> getUserInfoMap(UnoState state) {
        Map<Long, Map<String, Object>> userInfoMap = new HashMap<>();
        for (UnoState.PlayerState player : state.players) {
            User user = userRepo.findById(player.userId).orElse(null);
            if (user != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", user.getUsername());
                userInfo.put("displayName", user.getDisplayName());
                userInfoMap.put(player.userId, userInfo);
            }
        }
        return userInfoMap;
    }
}