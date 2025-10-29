package cc.techox.boardgame.game.uno;

import java.util.Locale;

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