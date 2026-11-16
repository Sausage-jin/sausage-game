package com.doudizhu.engine;

import com.doudizhu.model.Card;
import com.doudizhu.model.Card.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌堆：生成一副 54 张牌并洗牌。
 */
public class Deck {

    private final List<Card> cards = new ArrayList<>();

    /** 默认三人斗地主：54 张牌 */
    public Deck() {
        this(false);
    }

    /**
     * @param fourPlayer true=四人模式（80 张：每种点数 6 张 + 大小王）
     */
    public Deck(boolean fourPlayer) {
        for (int rank = 3; rank <= 15; rank++) {
            cards.add(new Card(Suit.SPADE, rank));
            cards.add(new Card(Suit.HEART, rank));
            cards.add(new Card(Suit.DIAMOND, rank));
            cards.add(new Card(Suit.CLUB, rank));
            if (fourPlayer) {
                // 四人模式：每种点数额外 2 张，使用圈/三角两种新花色（共 6 花色 × 13 + 2 王 = 80 张）
                cards.add(new Card(Suit.CIRCLE, rank));
                cards.add(new Card(Suit.TRIANGLE, rank));
            }
        }
        cards.add(new Card(Suit.JOKER_SMALL, 16));
        cards.add(new Card(Suit.JOKER_BIG, 17));
    }

    public void shuffle() { Collections.shuffle(cards); }

    /** 发一张牌 */
    public Card deal() { return cards.remove(cards.size() - 1); }

    /** 剩余牌数 */
    public int remaining() { return cards.size(); }
}