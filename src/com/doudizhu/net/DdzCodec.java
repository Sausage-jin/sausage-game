package com.doudizhu.net;

import com.doudizhu.model.Card;
import com.doudizhu.model.CardPlay;
import com.doudizhu.model.CardType;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主文本协议编解码器：Card / CardPlay ↔ 文本。
 * Card 格式:  rank.suitOrdinal   如 "14.0"=黑桃A, "17.7"=大王
 * 牌列表格式: 卡片用逗号分隔      如 "14.0,14.1,15.2"
 * CardPlay:   type|keyRank|cards 如 "STRAIGHT|3|3.0,4.1,5.2,6.0,7.1"
 */
public final class DdzCodec {

    private DdzCodec() {}

    // ==================== Card ====================

    public static String cardToStr(Card c) {
        return c.getRank() + "." + c.getSuit().ordinal();
    }

    public static Card strToCard(String s) {
        int dot = s.indexOf('.');
        int rank = Integer.parseInt(s.substring(0, dot));
        int suitIdx = Integer.parseInt(s.substring(dot + 1));
        return new Card(Card.Suit.values()[suitIdx], rank);
    }

    // ==================== List<Card> ====================

    public static String cardsToStr(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cardToStr(cards.get(i)));
        }
        return sb.toString();
    }

    public static List<Card> strToCards(String s) {
        List<Card> list = new ArrayList<>();
        if (s == null || s.isEmpty()) return list;
        for (String p : s.split(",")) {
            if (!p.isEmpty()) list.add(strToCard(p.trim()));
        }
        return list;
    }

    // ==================== CardPlay ====================

    public static String playToStr(CardPlay play) {
        if (play == null) return "";
        return play.getType().name() + "|" + play.getKeyRank() + "|" + cardsToStr(play.getCards());
    }

    public static CardPlay strToPlay(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split("\\|", -1);
        CardType type = CardType.valueOf(parts[0]);
        int keyRank = Integer.parseInt(parts[1]);
        List<Card> cards = strToCards(parts.length > 2 ? parts[2] : "");
        return new CardPlay(cards, type, keyRank);
    }
}