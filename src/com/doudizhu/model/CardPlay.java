package com.doudizhu.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.Serializable;

/**
 * 一次出牌动作：包含牌列表、牌型、以及用于比较大小的关键 rank。
 */
public class CardPlay implements Serializable {

    private final List<Card> cards;
    private final CardType type;
    private final int keyRank;   // 用于比较大小的关键牌 rank（顺子取最小，飞机取最小三张等）
    private final int length;    // 牌数（顺子/连对/飞机的长度判断需要）

    public CardPlay(List<Card> cards, CardType type, int keyRank) {
        this.cards = new ArrayList<>(cards);
        Collections.sort(this.cards);
        this.type = type;
        this.keyRank = keyRank;
        this.length = cards.size();
    }

    public List<Card> getCards() { return cards; }
    public CardType getType() { return type; }
    public int getKeyRank() { return keyRank; }
    public int getLength() { return length; }

    /** 判断本牌型能否压过 target（target 为上一手牌） */
    public boolean canBeat(CardPlay target) {
        if (target == null) return true;
        if (type == CardType.ROCKET) return true;
        if (target.type == CardType.ROCKET) return false;
        // 六张炸弹 > 炸弹 > 普通牌型
        if (type == CardType.SIX_BOMB && !target.type.isBombLike()) return true;
        if (type == CardType.SIX_BOMB && target.type == CardType.BOMB) return true;
        if (type == CardType.BOMB && !target.type.isBombLike()) return true;
        if (target.type.isBombLike() && !type.isBombLike()) return false;
        // 同为炸弹比 keyRank（SIX_BOMB vs SIX_BOMB 也比 keyRank）
        if (type.isBombLike() && target.type.isBombLike())
            return keyRank > target.keyRank;
        // 普通牌型：类型相同、长度相同、keyRank 更大
        if (type != target.type || length != target.length) return false;
        return keyRank > target.keyRank;
    }

    @Override
    public String toString() {
        return type.label + " " + cards;
    }
}