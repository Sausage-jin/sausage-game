package com.doudizhu.model;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.Serializable;

/**
 * 扑克牌类 —— 斗地主核心数据模型。
 * rank 取值：3~10 对应数字牌，11=J, 12=Q, 13=K, 14=A, 15=2, 16=小王, 17=大王
 */
public class Card implements Comparable<Card>, Serializable {

    private static final long serialVersionUID = 1L;
    private static int nextId = 0;

    public enum Suit {
        SPADE("♠", Color.BLACK), HEART("♥", Color.RED),
        DIAMOND("♦", Color.RED), CLUB("♣", Color.BLACK),
        CIRCLE("●", new Color(210, 90, 20)), TRIANGLE("▲", new Color(30, 110, 60)),
        JOKER_SMALL("★", Color.BLACK), JOKER_BIG("★", Color.RED);

        public final String symbol;
        public final Color color;
        Suit(String s, Color c) { symbol = s; color = c; }
    }

    public static final int CARD_W = 72;
    public static final int CARD_H = 100;

    private final int id;       // 唯一标识（四人模式同点数有多张）
    private final Suit suit;
    private final int rank;

    public Card(Suit suit, int rank) {
        this.id = nextId++;
        this.suit = suit;
        this.rank = rank;
    }

    public int getId() { return id; }

    public Suit getSuit() { return suit; }
    public int getRank() { return rank; }

    /** 是否是王 */
    public boolean isJoker() { return rank >= 16; }

    /** 显示用文字：3~10、J、Q、K、A、2、小王、大王 */
    public String getDisplayText() {
        if (rank == 16) return "小王";
        if (rank == 17) return "大王";
        if (rank == 15) return "2";
        if (rank == 14) return "A";
        if (rank == 13) return "K";
        if (rank == 12) return "Q";
        if (rank == 11) return "J";
        return String.valueOf(rank);
    }

    /** 角标短文字（用于牌面左上/右下） */
    public String getCornerText() {
        if (rank == 16) return "小";
        if (rank == 17) return "大";
        return getDisplayText();
    }

    @Override
    public int compareTo(Card o) {
        int c = Integer.compare(this.rank, o.rank);
        if (c != 0) return c;
        return this.suit.ordinal() - o.suit.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        return this.id == ((Card) o).id;
    }

    @Override
    public int hashCode() { return id; }

    @Override
    public String toString() { return getDisplayText() + suit.symbol; }
}