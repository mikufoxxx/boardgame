package cc.techox.boardgame.game.uno;

import java.security.SecureRandom;
import java.util.*;

public class UnoEngine {
    private static final SecureRandom RND = new SecureRandom();

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

    public static void drawAndPass(UnoState s, long userId) {
        if (s.finished) throw new IllegalStateException("game finished");
        UnoState.PlayerState p = s.currentPlayer();
        if (p.userId != userId) throw new IllegalArgumentException("not your turn");
        ensureDrawPile(s);
        int n = Math.max(1, s.pendingDraw);
        for (int i=0;i<n;i++) {
            p.hand.add(s.drawPile.pop());
        }
        s.pendingDraw = 0;
        s.forcedColor = null;
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