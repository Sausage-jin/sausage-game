package com.doudizhu.engine;

import com.doudizhu.model.*;

import java.util.*;
import java.util.stream.*;

/**
 * 斗地主规则引擎：牌型识别、比较、以及为 AI/提示生成所有可能的出牌组合。
 */
public class RuleEngine {

    // ==================== 牌型分析 ====================

    /** 分析一组牌的牌型，非法牌型返回 type=INVALID */
    public static CardPlay analyze(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return null;
        int n = cards.size();

        TreeMap<Integer, Integer> rc = new TreeMap<>();
        for (Card c : cards) rc.merge(c.getRank(), 1, Integer::sum);

        // 王炸
        if (n == 2 && rc.containsKey(16) && rc.containsKey(17))
            return new CardPlay(cards, CardType.ROCKET, 17);

        List<Integer> c1 = ranksOf(rc, 1), c2 = ranksOf(rc, 2),
                      c3 = ranksOf(rc, 3), c4 = ranksOf(rc, 4);

        if (n == 1) return new CardPlay(cards, CardType.SINGLE, firstRank(rc));
        if (n == 2 && c2.size() == 1) return new CardPlay(cards, CardType.PAIR, c2.get(0));
        if (n == 3 && c3.size() == 1) return new CardPlay(cards, CardType.TRIPLE, c3.get(0));
        if (n == 4 && c4.size() == 1) return new CardPlay(cards, CardType.BOMB, c4.get(0));
        // 六张炸弹（四人模式：6张同点数）
        if (n == 6 && rc.size() == 1 && rc.firstEntry().getValue() == 6)
            return new CardPlay(cards, CardType.SIX_BOMB, rc.firstKey());
        if (n == 4 && c3.size() == 1 && c1.size() == 1)
            return new CardPlay(cards, CardType.TRIPLE_ONE, c3.get(0));
        if (n == 5 && c3.size() == 1 && c2.size() == 1)
            return new CardPlay(cards, CardType.TRIPLE_TWO, c3.get(0));

        // 顺子（5张以上连续单牌，不能含2和王）
        if (n >= 5 && c1.size() == n && consecutive(c1) && last(c1) <= 14)
            return new CardPlay(cards, CardType.STRAIGHT, c1.get(0));

        // 连对（3对以上连续对子，不能含2和王）
        if (n >= 6 && n % 2 == 0 && c2.size() == n / 2 && consecutive(c2) && last(c2) <= 14)
            return new CardPlay(cards, CardType.DOUBLE_STRAIGHT, c2.get(0));

        // 飞机（2组以上连续三张，不带翼）
        if (n >= 6 && n % 3 == 0 && c3.size() == n / 3 && consecutive(c3) && last(c3) <= 14)
            return new CardPlay(cards, CardType.PLANE, c3.get(0));

        // 飞机带单 / 飞机带对
        if (c3.size() >= 2 && consecutive(c3) && last(c3) <= 14) {
            int k = c3.size();
            // 飞机带单：k组三张 + k张单牌（单牌可以是散牌或凑成的对子）
            if (n == k * 4 && c4.isEmpty()) {
                int wingCards = n - k * 3;
                if (wingCards == k) return new CardPlay(cards, CardType.PLANE_ONE, c3.get(0));
            }
            // 飞机带对：k组三张 + k个对子
            if (n == k * 5 && c2.size() == k && c1.isEmpty() && c4.isEmpty())
                return new CardPlay(cards, CardType.PLANE_TWO, c3.get(0));
        }

        // 四带二（两张单牌或一对）
        if (n == 6 && c4.size() == 1 && (c1.size() == 2 || (c1.isEmpty() && c2.size() == 1)))
            return new CardPlay(cards, CardType.FOUR_TWO, c4.get(0));
        // 四带两对
        if (n == 8 && c4.size() == 1 && c2.size() == 2 && c1.isEmpty())
            return new CardPlay(cards, CardType.FOUR_TWO_PAIR, c4.get(0));

        return new CardPlay(cards, CardType.INVALID, 0);
    }

    // ==================== 生成所有可能出牌（供AI与提示） ====================

    /** 生成手牌中所有合法出牌组合 */
    public static List<CardPlay> allPlays(List<Card> hand) {
        List<CardPlay> plays = new ArrayList<>();
        genSingles(hand, plays);
        genPairs(hand, plays);
        genTriples(hand, plays);
        genTripleWith(hand, plays);
        genStraights(hand, plays);
        genDoubleStraights(hand, plays);
        genPlanes(hand, plays);
        genFourTwo(hand, plays);
        genBombs(hand, plays);
        genRocket(hand, plays);
        return plays;
    }

    /** 找出能压过 target 的所有出牌 */
    public static List<CardPlay> findBeats(List<Card> hand, CardPlay target) {
        List<CardPlay> result = new ArrayList<>();
        for (CardPlay p : allPlays(hand)) {
            if (p.canBeat(target)) result.add(p);
        }
        return result;
    }

    // ==================== 生成器 ====================

    static void genSingles(List<Card> hand, List<CardPlay> out) {
        Set<Integer> seen = new HashSet<>();
        for (Card c : hand) {
            if (seen.add(c.getRank()))
                out.add(new CardPlay(List.of(c), CardType.SINGLE, c.getRank()));
        }
    }

    static void genPairs(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() >= 2 && e.getKey() < 16)
                out.add(new CardPlay(e.getValue().subList(0, 2), CardType.PAIR, e.getKey()));
        }
    }

    static void genTriples(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() >= 3)
                out.add(new CardPlay(e.getValue().subList(0, 3), CardType.TRIPLE, e.getKey()));
        }
    }

    static void genTripleWith(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        for (var e : byRank.entrySet()) {
            int r = e.getKey();
            if (e.getValue().size() < 3) continue;
            List<Card> tri = e.getValue().subList(0, 3);
            // 三带一
            for (var e2 : byRank.entrySet()) {
                if (e2.getKey().equals(r)) continue;
                List<Card> one = List.of(e2.getValue().get(0));
                List<Card> combo = new ArrayList<>(tri); combo.addAll(one);
                out.add(new CardPlay(combo, CardType.TRIPLE_ONE, r));
            }
            // 三带二
            for (var e2 : byRank.entrySet()) {
                if (e2.getKey().equals(r) || e2.getValue().size() < 2 || e2.getKey() >= 16) continue;
                List<Card> pair = e2.getValue().subList(0, 2);
                List<Card> combo = new ArrayList<>(tri); combo.addAll(pair);
                out.add(new CardPlay(combo, CardType.TRIPLE_TWO, r));
            }
        }
    }

    static void genStraights(List<Card> hand, List<CardPlay> out) {
        TreeSet<Integer> ranks = new TreeSet<>();
        for (Card c : hand) if (c.getRank() <= 14) ranks.add(c.getRank());
        List<Integer> list = new ArrayList<>(ranks);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 4; j < list.size(); j++) {
                if (list.get(j) - list.get(i) == j - i) { // 连续
                    out.add(buildPlay(hand, list.subList(i, j + 1), CardType.STRAIGHT));
                } else break;
            }
        }
    }

    static void genDoubleStraights(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        TreeSet<Integer> pairRanks = new TreeSet<>();
        for (var e : byRank.entrySet())
            if (e.getValue().size() >= 2 && e.getKey() <= 14) pairRanks.add(e.getKey());
        List<Integer> list = new ArrayList<>(pairRanks);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 2; j < list.size(); j++) {
                if (list.get(j) - list.get(i) == j - i) {
                    out.add(buildPairPlay(hand, list.subList(i, j + 1), CardType.DOUBLE_STRAIGHT));
                } else break;
            }
        }
    }

    static void genPlanes(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        TreeSet<Integer> triRanks = new TreeSet<>();
        for (var e : byRank.entrySet())
            if (e.getValue().size() >= 3 && e.getKey() <= 14) triRanks.add(e.getKey());
        List<Integer> list = new ArrayList<>(triRanks);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j) - list.get(i) != j - i) break;
                List<Integer> seq = list.subList(i, j + 1);
                int k = seq.size();
                // 纯飞机
                out.add(buildTriplePlay(hand, seq, CardType.PLANE));
                // 飞机带单
                List<Card> wings = collectWings(hand, seq, k, 1);
                if (wings != null) {
                    List<Card> combo = new ArrayList<>(buildTripleCards(hand, seq));
                    combo.addAll(wings);
                    out.add(new CardPlay(combo, CardType.PLANE_ONE, seq.get(0)));
                }
                // 飞机带对
                List<Card> wingPairs = collectWingPairs(hand, seq, k);
                if (wingPairs != null) {
                    List<Card> combo = new ArrayList<>(buildTripleCards(hand, seq));
                    combo.addAll(wingPairs);
                    out.add(new CardPlay(combo, CardType.PLANE_TWO, seq.get(0)));
                }
            }
        }
    }

    static void genFourTwo(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() < 4) continue;
            int r = e.getKey();
            List<Card> quad = e.getValue().subList(0, 4);
            // 四带二单
            List<Card> singles = new ArrayList<>();
            for (var e2 : byRank.entrySet()) {
                if (!e2.getKey().equals(r) && singles.size() < 2)
                    singles.add(e2.getValue().get(0));
            }
            if (singles.size() == 2) {
                List<Card> combo = new ArrayList<>(quad); combo.addAll(singles);
                out.add(new CardPlay(combo, CardType.FOUR_TWO, r));
            }
            // 四带两对
            List<Card> pairs = new ArrayList<>();
            for (var e2 : byRank.entrySet()) {
                if (!e2.getKey().equals(r) && e2.getValue().size() >= 2 && e2.getKey() < 16 && pairs.size() < 4)
                    pairs.addAll(e2.getValue().subList(0, 2));
            }
            if (pairs.size() == 4) {
                List<Card> combo = new ArrayList<>(quad); combo.addAll(pairs);
                out.add(new CardPlay(combo, CardType.FOUR_TWO_PAIR, r));
            }
        }
    }

    static void genBombs(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() == 4)
                out.add(new CardPlay(e.getValue(), CardType.BOMB, e.getKey()));
            if (e.getValue().size() == 6)
                out.add(new CardPlay(e.getValue(), CardType.SIX_BOMB, e.getKey()));
        }
    }

    static void genRocket(List<Card> hand, List<CardPlay> out) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        if (byRank.containsKey(16) && byRank.containsKey(17)) {
            List<Card> rocket = new ArrayList<>();
            rocket.add(byRank.get(16).get(0));
            rocket.add(byRank.get(17).get(0));
            out.add(new CardPlay(rocket, CardType.ROCKET, 17));
        }
    }

    // ==================== 辅助方法 ====================

    private static Map<Integer, List<Card>> groupByRank(List<Card> hand) {
        Map<Integer, List<Card>> map = new TreeMap<>();
        for (Card c : hand) map.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
        return map;
    }

    private static List<Integer> ranksOf(TreeMap<Integer, Integer> rc, int count) {
        return rc.entrySet().stream()
                .filter(e -> e.getValue() == count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static boolean consecutive(List<Integer> ranks) {
        for (int i = 1; i < ranks.size(); i++)
            if (ranks.get(i) - ranks.get(i - 1) != 1) return false;
        return true;
    }

    private static int last(List<Integer> list) { return list.get(list.size() - 1); }
    private static int firstRank(TreeMap<Integer, Integer> rc) { return rc.firstKey(); }

    private static CardPlay buildPlay(List<Card> hand, List<Integer> ranks, CardType type) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        List<Card> cards = new ArrayList<>();
        for (int r : ranks) cards.add(byRank.get(r).get(0));
        return new CardPlay(cards, type, ranks.get(0));
    }

    private static CardPlay buildPairPlay(List<Card> hand, List<Integer> ranks, CardType type) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        List<Card> cards = new ArrayList<>();
        for (int r : ranks) cards.addAll(byRank.get(r).subList(0, 2));
        return new CardPlay(cards, type, ranks.get(0));
    }

    private static List<Card> buildTripleCards(List<Card> hand, List<Integer> ranks) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        List<Card> cards = new ArrayList<>();
        for (int r : ranks) cards.addAll(byRank.get(r).subList(0, 3));
        return cards;
    }

    private static CardPlay buildTriplePlay(List<Card> hand, List<Integer> ranks, CardType type) {
        return new CardPlay(buildTripleCards(hand, ranks), type, ranks.get(0));
    }

    /** 收集 k 张单牌作为飞机翅膀（不与三张序列冲突） */
    private static List<Card> collectWings(List<Card> hand, List<Integer> triSeq, int k, int each) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        List<Card> wings = new ArrayList<>();
        for (var e : byRank.entrySet()) {
            if (triSeq.contains(e.getKey())) continue;
            if (wings.size() < k) wings.add(e.getValue().get(0));
        }
        return wings.size() == k ? wings : null;
    }

    /** 收集 k 个对子作为飞机翅膀 */
    private static List<Card> collectWingPairs(List<Card> hand, List<Integer> triSeq, int k) {
        Map<Integer, List<Card>> byRank = groupByRank(hand);
        List<Card> wings = new ArrayList<>();
        for (var e : byRank.entrySet()) {
            if (triSeq.contains(e.getKey())) continue;
            if (e.getValue().size() >= 2 && e.getKey() < 16 && wings.size() < k * 2)
                wings.addAll(e.getValue().subList(0, 2));
        }
        return wings.size() == k * 2 ? wings : null;
    }
}