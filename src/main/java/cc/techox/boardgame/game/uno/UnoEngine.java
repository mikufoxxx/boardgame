package cc.techox.boardgame.game.uno;

import java.security.SecureRandom;
import java.util.*;

public class UnoEngine {
    private static final SecureRandom RND = new SecureRandom();

    /**
     * 使用外部卡牌数据创建新游戏（推荐）
     */
    public static UnoState createInitialStateWithDeck(List<Long> userIds, List<Map<String, Object>> cardData) {
        UnoState s = new UnoState();
        for (Long uid : userIds) s.players.add(new UnoState.PlayerState(uid));
        
        // 从外部数据构建卡牌
        List<String> deck = buildDeckFromData(cardData);
        shuffle(deck);
        
        // 发牌 7 张
        for (int r = 0; r < 7; r++) {
            for (UnoState.PlayerState p : s.players) {
                p.hand.add(deck.remove(deck.size()-1));
            }
        }
        
        // 翻第一张（避免万能牌）
        String first;
        do { 
            first = deck.remove(deck.size()-1); 
        } while (first.startsWith("wild"));
        s.discardPile.push(first);
        
        // 初始方向与效果
        UnoCard.Type t = UnoCard.fromCode(first).getType();
        if (t == UnoCard.Type.REVERSE && s.players.size() > 2) s.direction = -1;
        if (t == UnoCard.Type.SKIP) s.currentIdx = s.nextIndex(1);
        if (t == UnoCard.Type.DRAW2) s.pendingDraw += 2;
        
        // 剩余入牌堆
        for (int i = deck.size()-1; i >= 0; i--) s.drawPile.push(deck.get(i));
        s.started = true;
        return s;
    }

    /**
     * 从外部数据构建卡牌列表
     */
    private static List<String> buildDeckFromData(List<Map<String, Object>> cardData) {
        List<String> deck = new ArrayList<>();
        
        for (Map<String, Object> cardInfo : cardData) {
            String cardId = (String) cardInfo.get("id");
            Integer count = (Integer) cardInfo.getOrDefault("count", 1);
            
            // 将 JSON 格式的卡牌 ID 转换为 UnoCard 期望的格式
            String convertedCardCode = convertCardIdToCode(cardId);
            
            // 根据count添加多张相同卡牌
            for (int i = 0; i < count; i++) {
                deck.add(convertedCardCode);
            }
        }
        
        return deck;
    }
    
    /**
     * 将 JSON 中的卡牌 ID 转换为 UnoCard 期望的格式
     * 例如：green_reverse -> G-REV
     */
    private static String convertCardIdToCode(String cardId) {
        if (cardId == null) return "?-?";
        
        // 特殊处理万能牌
        if ("wild".equals(cardId)) {
            return "W-WILD";
        }
        if ("wild_draw4".equals(cardId)) {
            return "W-D4";
        }
        
        String[] parts = cardId.split("_");
        if (parts.length != 2) return cardId; // 如果格式不对，直接返回原值
        
        String color = parts[0];
        String value = parts[1];
        
        // 转换颜色
        String colorCode = switch (color.toLowerCase()) {
            case "red" -> "R";
            case "green" -> "G";
            case "blue" -> "B";
            case "yellow" -> "Y";
            case "wild" -> "W";
            default -> "?";
        };
        
        // 转换值
        String valueCode = switch (value.toLowerCase()) {
            case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> value;
            case "skip" -> "SKIP";
            case "reverse" -> "REV";
            case "draw2" -> "D2";
            case "wild" -> "WILD";
            case "wild_draw4" -> "D4";
            default -> value.toUpperCase();
        };
        
        return colorCode + "-" + valueCode;
    }

    /**
     * 兼容旧版本的方法（使用硬编码卡牌）
     */
    @Deprecated
    public static UnoState createInitialState(List<Long> userIds) {
        return newGame(userIds);
    }

    public static UnoState newGame(List<Long> userIds) {
        UnoState s = new UnoState();
        for (Long uid : userIds) s.players.add(new UnoState.PlayerState(uid));
        // 构建一副 UNO 牌
        List<String> deck = buildDeck();
        shuffle(deck);
        // 发牌 7 张
        for (int r = 0; r < 7; r++) {
            for (UnoState.PlayerState p : s.players) {
                p.hand.add(deck.remove(deck.size()-1));
            }
        }
        // 翻第一张
        String first;
        do { first = deck.remove(deck.size()-1); } while (first.startsWith("W-"));
        s.discardPile.push(first);
        // 初始方向与效果
        UnoCard.Type t = UnoCard.fromCode(first).getType();
        if (t == UnoCard.Type.REVERSE && s.players.size() > 2) s.direction = -1;
        if (t == UnoCard.Type.SKIP) s.currentIdx = s.nextIndex(1);
        if (t == UnoCard.Type.DRAW2) s.pendingDraw += 2;
        // 剩余入牌堆
        for (int i = deck.size()-1; i >= 0; i--) s.drawPile.push(deck.get(i));
        s.started = true;
        return s;
    }

    public static List<String> buildDeck() {
        List<String> deck = new ArrayList<>();
        for (UnoCard.Color c : List.of(UnoCard.Color.RED, UnoCard.Color.GREEN, UnoCard.Color.BLUE, UnoCard.Color.YELLOW)) {
            deck.add(new UnoCard(c, UnoCard.Type.ZERO).code());
            for (int k=0;k<2;k++) {
                deck.add(new UnoCard(c, UnoCard.Type.ONE).code());
                deck.add(new UnoCard(c, UnoCard.Type.TWO).code());
                deck.add(new UnoCard(c, UnoCard.Type.THREE).code());
                deck.add(new UnoCard(c, UnoCard.Type.FOUR).code());
                deck.add(new UnoCard(c, UnoCard.Type.FIVE).code());
                deck.add(new UnoCard(c, UnoCard.Type.SIX).code());
                deck.add(new UnoCard(c, UnoCard.Type.SEVEN).code());
                deck.add(new UnoCard(c, UnoCard.Type.EIGHT).code());
                deck.add(new UnoCard(c, UnoCard.Type.NINE).code());
                deck.add(new UnoCard(c, UnoCard.Type.SKIP).code());
                deck.add(new UnoCard(c, UnoCard.Type.REVERSE).code());
                deck.add(new UnoCard(c, UnoCard.Type.DRAW2).code());
            }
        }
        for (int k=0;k<4;k++) deck.add(new UnoCard(UnoCard.Color.BLACK, UnoCard.Type.WILD).code());
        for (int k=0;k<4;k++) deck.add(new UnoCard(UnoCard.Color.BLACK, UnoCard.Type.WILDDRAW4).code());
        return deck;
    }

    private static void shuffle(List<String> deck) {
        for (int i=deck.size()-1;i>0;i--) {
            int j = RND.nextInt(i+1);
            String tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
    }

    public static boolean canPlay(UnoState s, String cardCode) {
        UnoCard card = UnoCard.fromCode(cardCode);
        UnoCard top = UnoCard.fromCode(s.discardPile.peek());
        
        // 如果有待摸牌惩罚，只能出相同的惩罚牌或摸牌
        if (s.pendingDraw > 0) {
            // 只能出Draw2叠加Draw2，或WildDraw4叠加任何惩罚牌
            if (card.getType() == UnoCard.Type.DRAW2 && top.getType() == UnoCard.Type.DRAW2) {
                return true;
            }
            if (card.getType() == UnoCard.Type.WILDDRAW4) {
                return true;
            }
            return false; // 其他情况必须摸牌
        }
        
        // 万能牌总是可以出
        if (card.getType() == UnoCard.Type.WILD || card.getType() == UnoCard.Type.WILDDRAW4) return true;
        
        // 检查强制颜色
        if (s.forcedColor != null && card.getColor() != UnoCard.Color.BLACK) {
            return switch (s.forcedColor) {
                case "R" -> card.getColor() == UnoCard.Color.RED;
                case "G" -> card.getColor() == UnoCard.Color.GREEN;
                case "B" -> card.getColor() == UnoCard.Color.BLUE;
                case "Y" -> card.getColor() == UnoCard.Color.YELLOW;
                default -> false;
            };
        }
        
        // 正常匹配规则：颜色相同或类型相同
        return card.getColor() == top.getColor() || card.getType() == top.getType();
    }

    public static void play(UnoState s, long userId, String cardCode, String chooseColor) {
        if (s.finished) throw new IllegalStateException("game finished");
        UnoState.PlayerState p = s.currentPlayer();
        if (p.userId != userId) throw new IllegalArgumentException("not your turn");
        if (!p.hand.remove(cardCode)) throw new IllegalArgumentException("no such card in hand");
        if (!canPlay(s, cardCode)) throw new IllegalArgumentException("cannot play");
        
        s.discardPile.push(cardCode);
        UnoCard card = UnoCard.fromCode(cardCode);
        s.forcedColor = null;
        
        // 处理特殊牌效果
        switch (card.getType()) {
            case REVERSE -> {
                s.direction = -s.direction;
                // 在2人游戏中，Reverse相当于Skip
                if (s.players.size() == 2) {
                    s.currentIdx = s.nextIndex(1);
                }
            }
            case SKIP -> {
                // Skip直接跳过下一个玩家
                s.currentIdx = s.nextIndex(1);
            }
            case DRAW2 -> {
                s.pendingDraw += 2;
                // 如果下一个玩家没有Draw2或WildDraw4，他们必须摸牌并跳过
                s.currentIdx = s.nextIndex(1);
            }
            case WILD -> {
                s.forcedColor = normalizeColor(chooseColor);
            }
            case WILDDRAW4 -> {
                s.pendingDraw += 4;
                s.forcedColor = normalizeColor(chooseColor);
                // 下一个玩家必须摸牌并跳过（除非他们也有WildDraw4）
                s.currentIdx = s.nextIndex(1);
            }
            default -> {
                // 普通牌，正常推进到下一个玩家
                s.currentIdx = s.nextIndex(1);
            }
        }
        
        // 检查是否需要自动调用 UNO（当玩家只剩一张牌时）
        if (p.hand.size() == 1) {
            p.hasCalledUno = true;
        }
        
        // 胜利判定
        if (p.hand.isEmpty()) { 
            s.finished = true; 
            s.winnerUserId = p.userId; 
            return; 
        }
        
        // 注意：回合切换已经在上面的switch中处理了，不需要再次推进
    }

    private static void ensureDrawPile(UnoState s) {
        if (s.drawPile.isEmpty()) {
            String top = s.discardPile.pop();
            List<String> rest = new ArrayList<>(s.discardPile);
            s.discardPile.clear();
            Collections.shuffle(rest, RND);
            for (String c : rest) s.drawPile.push(c);
            s.discardPile.push(top);
        }
    }

    public static Map<String, Object> publicView(UnoState s, long viewerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currentPlayer", s.currentIdx);
        m.put("direction", s.direction);
        m.put("drawCount", s.pendingDraw);
        m.put("lastColor", s.forcedColor);
        
        // 添加牌库数量
        m.put("deckSize", s.drawPile.size());
        
        // 返回顶部卡牌的对象格式
        if (s.discardPile.peek() != null) {
            m.put("topCard", UnoCard.codeToObject(s.discardPile.peek()));
        } else {
            m.put("topCard", null);
        }
        
        m.put("started", s.started);
        m.put("finished", s.finished);
        m.put("winnerUserId", s.winnerUserId);
        
        List<Map<String,Object>> players = new ArrayList<>();
        for (int i=0;i<s.players.size();i++) {
            UnoState.PlayerState p = s.players.get(i);
            Map<String,Object> pm = new LinkedHashMap<>();
            pm.put("userId", p.userId);
            pm.put("handSize", p.hand.size());
            pm.put("position", i);
            pm.put("hasCalledUno", p.hasCalledUno);
            
            // 如果是当前查看者，返回完整手牌对象
            if (p.userId == viewerId) {
                List<Map<String, Object>> handObjects = new ArrayList<>();
                for (String cardCode : p.hand) {
                    handObjects.add(UnoCard.codeToObject(cardCode));
                }
                pm.put("hand", handObjects);
            }
            
            players.add(pm);
        }
        m.put("players", players);
        return m;
    }

    /**
     * 增强版的 publicView，包含用户信息
     */
    public static Map<String, Object> publicViewWithUserInfo(UnoState s, long viewerId, Map<Long, Map<String, Object>> userInfoMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currentPlayer", s.currentIdx);
        m.put("direction", s.direction);
        m.put("drawCount", s.pendingDraw);
        m.put("lastColor", s.forcedColor);
        
        // 添加牌库数量
        m.put("deckSize", s.drawPile.size());
        
        // 返回顶部卡牌的对象格式
        if (s.discardPile.peek() != null) {
            m.put("topCard", UnoCard.codeToObject(s.discardPile.peek()));
        } else {
            m.put("topCard", null);
        }
        
        m.put("started", s.started);
        m.put("finished", s.finished);
        m.put("winnerUserId", s.winnerUserId);
        
        // 添加当前玩家的游戏状态信息
        UnoState.PlayerState currentPlayerState = s.currentPlayer();
        if (currentPlayerState.userId == viewerId) {
            m.put("mustDraw", mustDraw(s, viewerId));
            m.put("playableCards", getPlayableCards(s, viewerId).stream()
                .map(UnoCard::codeToObject)
                .collect(java.util.stream.Collectors.toList()));
        }
        
        List<Map<String,Object>> players = new ArrayList<>();
        for (int i=0;i<s.players.size();i++) {
            UnoState.PlayerState p = s.players.get(i);
            Map<String,Object> pm = new LinkedHashMap<>();
            pm.put("userId", p.userId);
            
            // 添加用户信息
            Map<String, Object> userInfo = userInfoMap.get(p.userId);
            if (userInfo != null) {
                pm.put("username", userInfo.get("username"));
                pm.put("displayName", userInfo.get("displayName"));
            } else {
                pm.put("username", "unknown");
                pm.put("displayName", "未知用户");
            }
            
            pm.put("handSize", p.hand.size());
            pm.put("position", i);
            pm.put("isReady", false); // 游戏中不需要准备状态
            pm.put("hasCalledUno", p.hasCalledUno);
            pm.put("isCurrentPlayer", i == s.currentIdx);
            
            // 如果是当前查看者，返回完整手牌对象
            if (p.userId == viewerId) {
                List<Map<String, Object>> handObjects = new ArrayList<>();
                for (String cardCode : p.hand) {
                    handObjects.add(UnoCard.codeToObject(cardCode));
                }
                pm.put("hand", handObjects);
            }
            
            players.add(pm);
        }
        m.put("players", players);
        return m;
    }

    /**
     * 检查游戏是否结束
     */
    public static boolean isGameFinished(UnoState state) {
        return state.finished;
    }
    
    /**
     * 获取获胜者ID
     */
    public static Long getWinner(UnoState state) {
        return state.winnerUserId;
    }
    
    /**
     * 出牌方法（使用新的状态对象）
     */
    public static UnoState playCard(UnoState originalState, long userId, String cardCode, String chooseColor) {
        // 创建状态副本以避免修改原状态
        UnoState state = copyState(originalState);
        play(state, userId, cardCode, chooseColor);
        return state;
    }
    
    /**
     * 检查玩家是否必须摸牌（因为有待摸牌惩罚且没有合法牌可出）
     */
    public static boolean mustDraw(UnoState s, long userId) {
        if (s.pendingDraw == 0) return false; // 没有惩罚，不需要强制摸牌
        
        UnoState.PlayerState p = s.currentPlayer();
        if (p.userId != userId) return false;
        
        // 检查手牌中是否有可以叠加的惩罚牌
        for (String cardCode : p.hand) {
            if (canPlay(s, cardCode)) {
                return false; // 有牌可出，不需要强制摸牌
            }
        }
        
        return true; // 没有合法牌，必须摸牌
    }
    
    /**
     * 获取玩家手牌中可以出的牌
     */
    public static List<String> getPlayableCards(UnoState s, long userId) {
        UnoState.PlayerState p = s.currentPlayer();
        if (p.userId != userId) return new ArrayList<>();
        
        List<String> playableCards = new ArrayList<>();
        for (String cardCode : p.hand) {
            if (canPlay(s, cardCode)) {
                playableCards.add(cardCode);
            }
        }
        
        return playableCards;
    }
    
    /**
     * UNO 调用方法
     */
    public static UnoState callUno(UnoState originalState, long userId) {
        UnoState state = copyState(originalState);
        
        if (state.finished) throw new IllegalStateException("game finished");
        
        // 找到调用 UNO 的玩家
        UnoState.PlayerState player = null;
        for (UnoState.PlayerState p : state.players) {
            if (p.userId == userId) {
                player = p;
                break;
            }
        }
        
        if (player == null) {
            throw new IllegalArgumentException("player not found");
        }
        
        // 只有当玩家手牌数为1时才能调用 UNO
        if (player.hand.size() != 1) {
            throw new IllegalArgumentException("can only call UNO with exactly 1 card");
        }
        
        player.hasCalledUno = true;
        return state;
    }

    /**
     * +4 质疑结果类
     */
    public static class ChallengeResult {
        public final UnoState newState;
        public final boolean challengeSuccessful;
        public final long challengerId;
        public final long challengedPlayerId;
        public final int penaltyCards;
        public final String reason;
        
        public ChallengeResult(UnoState newState, boolean challengeSuccessful, long challengerId, 
                             long challengedPlayerId, int penaltyCards, String reason) {
            this.newState = newState;
            this.challengeSuccessful = challengeSuccessful;
            this.challengerId = challengerId;
            this.challengedPlayerId = challengedPlayerId;
            this.penaltyCards = penaltyCards;
            this.reason = reason;
        }
    }

    /**
     * UNO 惩罚结果类
     */
    public static class UnoPenaltyResult {
        public final UnoState newState;
        public final long penalizedPlayerId;
        public final int penaltyCards;
        public final String reason;
        
        public UnoPenaltyResult(UnoState newState, long penalizedPlayerId, int penaltyCards, String reason) {
            this.newState = newState;
            this.penalizedPlayerId = penalizedPlayerId;
            this.penaltyCards = penaltyCards;
            this.reason = reason;
        }
    }

    /**
     * 质疑 +4 万能牌
     */
    public static ChallengeResult challengeWildDraw4(UnoState originalState, long challengerId) {
        UnoState state = copyState(originalState);
        
        if (state.finished) throw new IllegalStateException("game finished");
        
        // 找到质疑者
        UnoState.PlayerState challenger = null;
        for (UnoState.PlayerState p : state.players) {
            if (p.userId == challengerId) {
                challenger = p;
                break;
            }
        }
        
        if (challenger == null) {
            throw new IllegalArgumentException("challenger not found");
        }
        
        // 检查是否有可质疑的情况（上一张牌必须是 +4）
        if (state.discardPile.isEmpty()) {
            throw new IllegalArgumentException("no card to challenge");
        }
        
        String topCard = state.discardPile.peek();
        if (!topCard.equals("W-D4")) {
            throw new IllegalArgumentException("can only challenge Wild Draw 4 cards");
        }
        
        // 找到被质疑的玩家（上一个出牌的玩家）
        int challengedPlayerIdx = (state.currentIdx - state.direction + state.players.size()) % state.players.size();
        UnoState.PlayerState challengedPlayer = state.players.get(challengedPlayerIdx);
        
        // 检查被质疑玩家的手牌中是否有其他可出的牌
        boolean hasOtherPlayableCards = false;
        String previousCard = null;
        
        // 模拟移除 +4 牌后的状态来检查
        if (state.discardPile.size() > 1) {
            List<String> discardList = new ArrayList<>(state.discardPile);
            if (discardList.size() > 1) {
                previousCard = discardList.get(discardList.size() - 2);
            }
        }
        
        if (previousCard != null) {
            // 检查被质疑玩家是否有其他可出的牌
            for (String cardCode : challengedPlayer.hand) {
                if (canPlayAgainstCard(cardCode, previousCard, state.forcedColor)) {
                    hasOtherPlayableCards = true;
                    break;
                }
            }
        }
        
        ensureDrawPile(state);
        
        if (hasOtherPlayableCards) {
            // 质疑成功：被质疑玩家违规出牌，罚摸4张牌
            for (int i = 0; i < 4 && !state.drawPile.isEmpty(); i++) {
                challengedPlayer.hand.add(state.drawPile.pop());
            }
            
            // 清除待摸牌惩罚（质疑者不需要摸牌）
            state.pendingDraw = 0;
            
            return new ChallengeResult(state, true, challengerId, challengedPlayer.userId, 4, 
                "质疑成功：被质疑玩家有其他可出的牌却出了+4");
        } else {
            // 质疑失败：质疑者罚摸6张牌（原本4张+惩罚2张）
            for (int i = 0; i < 6 && !state.drawPile.isEmpty(); i++) {
                challenger.hand.add(state.drawPile.pop());
            }
            
            // 清除待摸牌惩罚
            state.pendingDraw = 0;
            
            return new ChallengeResult(state, false, challengerId, challengedPlayer.userId, 6, 
                "质疑失败：被质疑玩家合法出牌");
        }
    }

    /**
     * 检查卡牌是否可以对指定卡牌出牌
     */
    private static boolean canPlayAgainstCard(String cardCode, String targetCard, String forcedColor) {
        UnoCard card = UnoCard.fromCode(cardCode);
        UnoCard target = UnoCard.fromCode(targetCard);
        
        // 万能牌总是可以出
        if (card.getType() == UnoCard.Type.WILD || card.getType() == UnoCard.Type.WILDDRAW4) {
            return true;
        }
        
        // 检查强制颜色
        if (forcedColor != null && card.getColor() != UnoCard.Color.BLACK) {
            return switch (forcedColor) {
                case "red", "R" -> card.getColor() == UnoCard.Color.RED;
                case "green", "G" -> card.getColor() == UnoCard.Color.GREEN;
                case "blue", "B" -> card.getColor() == UnoCard.Color.BLUE;
                case "yellow", "Y" -> card.getColor() == UnoCard.Color.YELLOW;
                default -> false;
            };
        }
        
        // 颜色或数字/类型匹配
        return card.getColor() == target.getColor() || card.getType() == target.getType();
    }

    /**
     * 惩罚忘记喊 UNO 的玩家
     */
    public static UnoPenaltyResult penalizeForgetUno(UnoState originalState, long penalizedPlayerId) {
        UnoState state = copyState(originalState);
        
        if (state.finished) throw new IllegalStateException("game finished");
        
        // 找到被惩罚的玩家
        UnoState.PlayerState penalizedPlayer = null;
        for (UnoState.PlayerState p : state.players) {
            if (p.userId == penalizedPlayerId) {
                penalizedPlayer = p;
                break;
            }
        }
        
        if (penalizedPlayer == null) {
            throw new IllegalArgumentException("penalized player not found");
        }
        
        // 检查玩家是否确实忘记喊 UNO（手牌数为1且未调用UNO）
        if (penalizedPlayer.hand.size() != 1 || penalizedPlayer.hasCalledUno) {
            throw new IllegalArgumentException("player is not eligible for UNO penalty");
        }
        
        ensureDrawPile(state);
        
        // 罚摸2张牌
        for (int i = 0; i < 2 && !state.drawPile.isEmpty(); i++) {
            penalizedPlayer.hand.add(state.drawPile.pop());
        }
        
        return new UnoPenaltyResult(state, penalizedPlayerId, 2, "忘记喊 UNO");
    }

    /**
     * 摸牌结果类
     */
    public static class DrawResult {
        public final UnoState newState;
        public final List<String> drawnCards;
        public final int drawCount;
        
        public DrawResult(UnoState newState, List<String> drawnCards, int drawCount) {
            this.newState = newState;
            this.drawnCards = drawnCards;
            this.drawCount = drawCount;
        }
    }

    /**
     * 摸牌并跳过方法（返回详细信息）
     */
    public static DrawResult drawAndPassWithDetails(UnoState originalState, long userId) {
        // 创建状态副本以避免修改原状态
        UnoState state = copyState(originalState);
        
        if (state.finished) throw new IllegalStateException("game finished");
        UnoState.PlayerState p = state.currentPlayer();
        if (p.userId != userId) throw new IllegalArgumentException("not your turn");
        
        ensureDrawPile(state);
        
        // 确定摸牌数量
        int drawCount = Math.max(1, state.pendingDraw);
        List<String> drawnCards = new ArrayList<>();
        
        // 摸牌
        for (int i = 0; i < drawCount; i++) {
            if (state.drawPile.isEmpty()) {
                // 如果牌库空了但还需要摸牌，游戏结束（平局）
                break;
            }
            String drawnCard = state.drawPile.pop();
            p.hand.add(drawnCard);
            drawnCards.add(drawnCard);
        }
        
        // 清除待摸牌惩罚和强制颜色
        state.pendingDraw = 0;
        state.forcedColor = null;
        
        // 推进到下一个玩家
        state.currentIdx = state.nextIndex(1);
        
        return new DrawResult(state, drawnCards, drawnCards.size());
    }

    /**
     * 摸牌并跳过方法（使用新的状态对象）
     */
    public static UnoState drawAndPass(UnoState originalState, long userId) {
        // 创建状态副本以避免修改原状态
        UnoState state = copyState(originalState);
        
        if (state.finished) throw new IllegalStateException("game finished");
        UnoState.PlayerState p = state.currentPlayer();
        if (p.userId != userId) throw new IllegalArgumentException("not your turn");
        
        ensureDrawPile(state);
        
        // 确定摸牌数量
        int drawCount = Math.max(1, state.pendingDraw);
        
        // 摸牌
        for (int i = 0; i < drawCount; i++) {
            if (state.drawPile.isEmpty()) {
                // 如果牌库空了但还需要摸牌，游戏结束（平局）
                break;
            }
            p.hand.add(state.drawPile.pop());
        }
        
        // 清除待摸牌惩罚和强制颜色
        state.pendingDraw = 0;
        state.forcedColor = null;
        
        // 推进到下一个玩家
        state.currentIdx = state.nextIndex(1);
        
        return state;
    }
    
    /**
     * 复制游戏状态
     */
    private static UnoState copyState(UnoState original) {
        UnoState copy = new UnoState();
        
        // 复制玩家状态
        for (UnoState.PlayerState player : original.players) {
            UnoState.PlayerState playerCopy = new UnoState.PlayerState(player.userId);
            playerCopy.hand.addAll(player.hand);
            playerCopy.hasCalledUno = player.hasCalledUno;
            copy.players.add(playerCopy);
        }
        
        // 复制牌堆
        copy.drawPile.addAll(original.drawPile);
        copy.discardPile.addAll(original.discardPile);
        
        // 复制游戏状态
        copy.currentIdx = original.currentIdx;
        copy.direction = original.direction;
        copy.pendingDraw = original.pendingDraw;
        copy.forcedColor = original.forcedColor;
        copy.started = original.started;
        copy.finished = original.finished;
        copy.winnerUserId = original.winnerUserId;
        
        return copy;
    }
    
    private static String normalizeColor(String chooseColor) {
        if (chooseColor == null) return null;
        return switch (chooseColor.toUpperCase(Locale.ROOT)) {
            case "R" -> "R";
            case "G" -> "G";
            case "B" -> "B";
            case "Y" -> "Y";
            default -> null;
        };
    }
}