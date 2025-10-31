package cc.techox.boardgame.game.uno;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class UnoCard {
    public enum Color { RED, GREEN, BLUE, YELLOW, BLACK }
    public enum Type { ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, SKIP, REVERSE, DRAW2, WILD, WILDDRAW4 }

    private final Color color;
    private final Type type;

    public UnoCard(Color color, Type type) {
        this.color = color;
        this.type = type;
    }

    public Color getColor() { return color; }
    public Type getType() { return type; }

    public String code() {
        String c;
        switch (color) {
            case RED -> c = "R";
            case GREEN -> c = "G";
            case BLUE -> c = "B";
            case YELLOW -> c = "Y";
            default -> c = "W"; // BLACK
        }
        String t;
        switch (type) {
            case ZERO -> t = "0";
            case ONE -> t = "1";
            case TWO -> t = "2";
            case THREE -> t = "3";
            case FOUR -> t = "4";
            case FIVE -> t = "5";
            case SIX -> t = "6";
            case SEVEN -> t = "7";
            case EIGHT -> t = "8";
            case NINE -> t = "9";
            case SKIP -> t = "SKIP";
            case REVERSE -> t = "REV";
            case DRAW2 -> t = "D2";
            case WILD -> t = "WILD";
            case WILDDRAW4 -> t = "D4";
            default -> t = "?";
        }
        return c + "-" + t;
    }

    /**
     * 返回标准的对象格式，供前端使用
     */
    public Map<String, Object> toObject() {
        Map<String, Object> cardObj = new LinkedHashMap<>();
        cardObj.put("id", code());
        cardObj.put("color", getColorName());
        cardObj.put("value", getValueName());
        cardObj.put("type", getCardType());
        return cardObj;
    }

    /**
     * 从卡牌代码创建对象格式
     */
    public static Map<String, Object> codeToObject(String code) {
        try {
            UnoCard card = fromCode(code);
            return card.toObject();
        } catch (Exception e) {
            // 如果解析失败，返回基本信息
            Map<String, Object> cardObj = new LinkedHashMap<>();
            cardObj.put("id", code);
            cardObj.put("color", "unknown");
            cardObj.put("value", "unknown");
            cardObj.put("type", "unknown");
            return cardObj;
        }
    }

    private String getColorName() {
        return switch (color) {
            case RED -> "red";
            case GREEN -> "green";
            case BLUE -> "blue";
            case YELLOW -> "yellow";
            case BLACK -> "black";
        };
    }

    private String getValueName() {
        return switch (type) {
            case ZERO -> "0";
            case ONE -> "1";
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case SKIP -> "skip";
            case REVERSE -> "reverse";
            case DRAW2 -> "draw2";
            case WILD -> "wild";
            case WILDDRAW4 -> "wilddraw4";
        };
    }

    private String getCardType() {
        return switch (type) {
            case ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE -> "number";
            case SKIP, REVERSE, DRAW2 -> "action";
            case WILD, WILDDRAW4 -> "wild";
        };
    }

    public static UnoCard fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("code null");
        String[] parts = code.split("-");
        if (parts.length != 2) throw new IllegalArgumentException("bad code:" + code);
        Color color = switch (parts[0].toUpperCase(Locale.ROOT)) {
            case "R" -> Color.RED;
            case "G" -> Color.GREEN;
            case "B" -> Color.BLUE;
            case "Y" -> Color.YELLOW;
            default -> Color.BLACK;
        };
        String t = parts[1].toUpperCase(Locale.ROOT);
        Type type = switch (t) {
            case "0" -> Type.ZERO;
            case "1" -> Type.ONE;
            case "2" -> Type.TWO;
            case "3" -> Type.THREE;
            case "4" -> Type.FOUR;
            case "5" -> Type.FIVE;
            case "6" -> Type.SIX;
            case "7" -> Type.SEVEN;
            case "8" -> Type.EIGHT;
            case "9" -> Type.NINE;
            case "SKIP" -> Type.SKIP;
            case "REV" -> Type.REVERSE;
            case "D2" -> Type.DRAW2;
            case "WILD" -> Type.WILD;
            case "D4" -> Type.WILDDRAW4;
            default -> throw new IllegalArgumentException("bad type:" + t);
        };
        return new UnoCard(color, type);
    }

    @Override public String toString() { return code(); }
}