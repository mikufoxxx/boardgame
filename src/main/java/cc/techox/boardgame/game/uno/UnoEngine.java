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
        if (card.getType() == UnoCard.Type.WILD || card.getType() == UnoCard.Type.WILDDRAW4) return true;
        if (s.forcedColor != null && card.getColor() != UnoCard.Color.BLACK) {
            return switch (s.forcedColor) {
                case "R" -> card.getColor() == UnoCard.Color.RED;
                case "G" -> card.getColor() == UnoCard.Color.GREEN;
                case "B" -> card.getColor() == UnoCard.Color.BLUE;
                case "Y" -> card.getColor() == UnoCard.Color.YELLOW;
                default -> false;
            };
        }
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
        // 处理功能
        switch (card.getType()) {
            case REVERSE -> s.direction = -s.direction;
            case SKIP -> s.currentIdx = s.nextIndex(1);
            case DRAW2 -> s.pendingDraw += 2;
            case WILD -> s.forcedColor = normalizeColor(chooseColor);
            case WILDDRAW4 -> { s.pendingDraw += 4; s.forcedColor = normalizeColor(chooseColor); }
            default -> {}
        }
        // 胜利判定
        if (p.hand.isEmpty()) { s.finished = true; s.winnerUserId = p.userId; return; }
        // 推进到下家
        s.currentIdx = s.nextIndex(1);
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
        m.put("currentIdx", s.currentIdx);
        m.put("direction", s.direction);
        m.put("pendingDraw", s.pendingDraw);
        m.put("forcedColor", s.forcedColor);
        m.put("top", s.discardPile.peek());
        m.put("started", s.started);
        m.put("finished", s.finished);
        m.put("winnerUserId", s.winnerUserId);
        List<Map<String,Object>> players = new ArrayList<>();
        for (int i=0;i<s.players.size();i++) {
            UnoState.PlayerState p = s.players.get(i);
            Map<String,Object> pm = new LinkedHashMap<>();
            pm.put("userId", p.userId);
            pm.put("handCount", p.hand.size());
            if (p.userId == viewerId) pm.put("hand", new ArrayList<>(p.hand));
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
     * 摸牌并跳过方法（使用新的状态对象）
     */
    public static UnoState drawAndPass(UnoState originalState, long userId) {
        // 创建状态副本以避免修改原状态
        UnoState state = copyState(originalState);
        
        // 实现摸牌逻辑
        if (state.finished) throw new IllegalStateException("game finished");
        UnoState.PlayerState p = state.currentPlayer();
        if (p.userId != userId) throw new IllegalArgumentException("not your turn");
        ensureDrawPile(state);
        int n = Math.max(1, state.pendingDraw);
        for (int i = 0; i < n; i++) {
            p.hand.add(state.drawPile.pop());
        }
        state.pendingDraw = 0;
        state.forcedColor = null;
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