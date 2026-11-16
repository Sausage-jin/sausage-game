package com.doudizhu.engine;

import com.doudizhu.model.*;

import java.util.*;
import java.util.stream.*;

/**
 * AI 出牌引擎。
 * 策略要点：
 *  1. 首出：优先打出长组合（顺子/飞机/连对），其次小对子/小单张，保留炸弹和大牌。
 *  2. 跟牌：找能压过的最小组合；队友（农民配合）占优时选择过牌。
 *  3. 残局：手牌少时果断出大牌/炸弹冲刺。
 */
public class AIEngine {

    /**
     * 计算 AI 的出牌决策。
     * @param hand      AI 手牌
     * @param lastPlay  上一手有效出牌（null 表示首出）
     * @param lastPlayer 上一手出牌玩家 id
     * @param selfId    AI 自身 id
     * @param landlordId 地主 id
     * @param players   所有玩家（用于判断残局）
     * @return 出牌组合，null 表示过牌
     */
    public static CardPlay decide(List<Card> hand, CardPlay lastPlay,
                                  int lastPlayer, int selfId,
                                  int landlordId, List<Player> players) {
        boolean amLandlord = (selfId == landlordId);
        boolean lastFromTeammate = !amLandlord && lastPlayer != landlordId && lastPlayer != selfId;

        // ===== 首出 =====
        if (lastPlay == null || lastPlayer == selfId) {
            return leadPlay(hand, players, landlordId, selfId);
        }

        // ===== 跟牌 =====
        List<CardPlay> beats = RuleEngine.findBeats(hand, lastPlay);
        if (beats.isEmpty()) return null; // 要不起

        // 队友出的牌且牌力较强 → 配合过牌
        if (lastFromTeammate) {
            // 队友出的是大牌（A以上单张/对子，或任何三张以上组合）→ 过
            if (lastPlay.getKeyRank() >= 14 || lastPlay.getType() != CardType.SINGLE && lastPlay.getType() != CardType.PAIR) {
                // 但如果自己手牌很少(<=2)，可以尝试接
                if (hand.size() <= 2) {
                    return pickSmallest(beats, true);
                }
                return null;
            }
            // 队友出小牌，自己有小牌可以接（避免拆牌）
            List<CardPlay> cheap = beats.stream()
                    .filter(b -> !b.getType().isBombLike())
                    .filter(b -> b.getKeyRank() <= 13)
                    .collect(Collectors.toList());
            if (!cheap.isEmpty() && hand.size() > 4) {
                // 接小牌时只出单张/对子，不拆大组合
                List<CardPlay> simple = cheap.stream()
                        .filter(b -> b.getType() == CardType.SINGLE || b.getType() == CardType.PAIR)
                        .collect(Collectors.toList());
                if (!simple.isEmpty()) return pickSmallest(simple, false);
            }
            return null;
        }

        // 对手（地主或敌方农民）出牌 → 尽量压制
        // 对手快赢了（剩牌<=2）→ 竭尽全力
        boolean opponentUrgent = false;
        for (Player p : players) {
            if (p.getId() != selfId) {
                boolean isOpponent = amLandlord ? p.getId() != landlordId : p.getId() == landlordId;
                if (isOpponent && p.getCardCount() <= 2) opponentUrgent = true;
            }
        }

        // 非炸弹的压制选项
        List<CardPlay> normal = beats.stream()
                .filter(b -> !b.getType().isBombLike())
                .collect(Collectors.toList());

        if (!normal.isEmpty()) {
            CardPlay best = pickSmallest(normal, false);
            // 不轻易拆牌：如果出的组合会拆掉更大的组合，且对手不紧急，考虑过
            if (!opponentUrgent && wouldBreakHand(hand, best) && hand.size() > 6) {
                return null;
            }
            return best;
        }

        // 只有炸弹能压
        if (opponentUrgent || hand.size() <= 4) {
            return pickSmallest(beats, true);
        }
        // 对手手牌还多，不轻易扔炸弹
        return null;
    }

    /** 首出策略 */
    private static CardPlay leadPlay(List<Card> hand, List<Player> players,
                                     int landlordId, int selfId) {
        List<CardPlay> all = RuleEngine.allPlays(hand);

        // 手牌<=3 → 直接出最大的冲刺
        if (hand.size() <= 3) {
            return all.stream()
                    .max(Comparator.comparingInt(CardPlay::getKeyRank))
                    .orElse(null);
        }

        // 优先出长组合（顺子 > 连对 > 飞机），用最小的
        List<CardPlay> longs = all.stream()
                .filter(p -> p.getType() == CardType.STRAIGHT
                        || p.getType() == CardType.DOUBLE_STRAIGHT
                        || p.getType() == CardType.PLANE
                        || p.getType() == CardType.PLANE_ONE
                        || p.getType() == CardType.PLANE_TWO)
                .collect(Collectors.toList());
        if (!longs.isEmpty()) {
            // 选牌数最多的组合，同长度选 keyRank 最小
            longs.sort(Comparator.comparingInt(CardPlay::getLength).reversed()
                    .thenComparingInt(CardPlay::getKeyRank));
            return longs.get(0);
        }

        // 出三带一/三带二（小的）
        List<CardPlay> triples = all.stream()
                .filter(p -> p.getType() == CardType.TRIPLE_ONE || p.getType() == CardType.TRIPLE_TWO)
                .sorted(Comparator.comparingInt(CardPlay::getKeyRank))
                .collect(Collectors.toList());
        if (!triples.isEmpty() && hand.size() > 5) return triples.get(0);

        // 出小对子
        List<CardPlay> pairs = all.stream()
                .filter(p -> p.getType() == CardType.PAIR && p.getKeyRank() <= 13)
                .sorted(Comparator.comparingInt(CardPlay::getKeyRank))
                .collect(Collectors.toList());
        if (!pairs.isEmpty()) return pairs.get(0);

        // 出小单张（不拆对子/三张）
        List<CardPlay> singles = all.stream()
                .filter(p -> p.getType() == CardType.SINGLE && p.getKeyRank() <= 14)
                .filter(p -> isSafeSingle(hand, p.getKeyRank()))
                .sorted(Comparator.comparingInt(CardPlay::getKeyRank))
                .collect(Collectors.toList());
        if (!singles.isEmpty()) return singles.get(0);

        // 实在没办法，出最小单张
        return all.stream()
                .filter(p -> p.getType() == CardType.SINGLE)
                .min(Comparator.comparingInt(CardPlay::getKeyRank))
                .orElse(null);
    }

    /** 该单张是否是"散牌"（不构成对子/三张的一部分） */
    private static boolean isSafeSingle(List<Card> hand, int rank) {
        int cnt = 0;
        for (Card c : hand) if (c.getRank() == rank) cnt++;
        return cnt == 1;
    }

    /** 出这张牌是否会严重破坏手牌结构（拆炸弹/拆对王） */
    private static boolean wouldBreakHand(List<Card> hand, CardPlay play) {
        // 检查是否拆了四张
        Map<Integer, Integer> rc = new HashMap<>();
        for (Card c : hand) rc.merge(c.getRank(), 1, Integer::sum);
        for (Card c : play.getCards()) {
            int r = c.getRank();
            if (rc.getOrDefault(r, 0) == 4) return true; // 拆炸弹
        }
        return false;
    }

    /** 从候选中选最小的（avoidBomb=true 时允许炸弹） */
    private static CardPlay pickSmallest(List<CardPlay> plays, boolean allowBomb) {
        List<CardPlay> pool = allowBomb ? plays : plays.stream()
                .filter(p -> !p.getType().isBombLike()).collect(Collectors.toList());
        if (pool.isEmpty()) pool = plays;
        return pool.stream()
                .min(Comparator.comparingInt(CardPlay::getKeyRank)
                        .thenComparingInt(CardPlay::getLength))
                .orElse(null);
    }

    /** AI 叫地主决策：根据手牌强度决定 */
    public static boolean shouldCallLandlord(List<Card> hand) {
        int score = 0;
        for (Card c : hand) {
            if (c.getRank() == 17) score += 8;      // 大王
            else if (c.getRank() == 16) score += 6; // 小王
            else if (c.getRank() == 15) score += 4; // 2
            else if (c.getRank() == 14) score += 2; // A
        }
        // 炸弹加分
        Map<Integer, Integer> rc = new HashMap<>();
        for (Card c : hand) rc.merge(c.getRank(), 1, Integer::sum);
        for (int cnt : rc.values()) if (cnt == 4) score += 10;

        return score >= 14; // 阈值
    }

    /**
     * AI 加倍决策：手牌强则加倍。
     * @param hand 手牌（地主含底牌，农民为原始17张）
     * @param isLandlord 是否为地主
     */
    public static boolean shouldDouble(List<Card> hand, boolean isLandlord) {
        int score = 0;
        for (Card c : hand) {
            if (c.getRank() == 17) score += 8;
            else if (c.getRank() == 16) score += 6;
            else if (c.getRank() == 15) score += 4;
            else if (c.getRank() == 14) score += 2;
        }
        Map<Integer, Integer> rc = new HashMap<>();
        for (Card c : hand) rc.merge(c.getRank(), 1, Integer::sum);
        for (int cnt : rc.values()) if (cnt == 4) score += 10;
        // 王炸额外加分
        if (rc.containsKey(16) && rc.containsKey(17)) score += 6;

        // 地主已有底牌加成，阈值稍高；农民手牌强才加倍
        return isLandlord ? score >= 18 : score >= 16;
    }

    // ==================== 四人 2v2 模式 ====================

    /**
     * AI 猜牌阶段选亮牌策略。
     * 手牌强 → 亮大牌争取进大牌组；手牌弱 → 亮小牌进小牌组（以弱胜强奖励更高）。
     */
    public static Card chooseGuessCard(List<Card> hand) {
        int score = 0;
        for (Card c : hand) {
            if (c.getRank() == 17) score += 8;
            else if (c.getRank() == 16) score += 6;
            else if (c.getRank() == 15) score += 4;
            else if (c.getRank() == 14) score += 2;
        }
        Map<Integer, Integer> rc = new HashMap<>();
        for (Card c : hand) rc.merge(c.getRank(), 1, Integer::sum);
        for (int cnt : rc.values()) if (cnt >= 4) score += 10;

        List<Card> sorted = new ArrayList<>(hand);
        sorted.sort(Comparator.comparingInt(Card::getRank));
        if (score >= 24) {
            // 强牌：亮大牌（保留王，亮 2/A/K）
            for (int i = sorted.size() - 1; i >= 0; i--) {
                int r = sorted.get(i).getRank();
                if (r < 16) return sorted.get(i);
            }
            return sorted.get(sorted.size() - 1);
        } else {
            // 弱牌：亮小牌进小牌组
            return sorted.get(0);
        }
    }

    /**
     * 四人 2v2 模式 AI 出牌决策。
     * @param teams teams[playerId] = 队伍号（0=大牌组，1=小牌组）
     */
    public static CardPlay decide4p(List<Card> hand, CardPlay lastPlay,
                                    int lastPlayer, int selfId,
                                    int[] teams, List<Player> players) {
        int myTeam = teams[selfId];
        boolean lastFromTeammate = lastPlayer >= 0 && lastPlayer != selfId
                && teams[lastPlayer] == myTeam;

        // ===== 首出 =====
        if (lastPlay == null || lastPlayer == selfId) {
            return leadPlay(hand, players, -1, selfId);
        }

        // ===== 跟牌 =====
        List<CardPlay> beats = RuleEngine.findBeats(hand, lastPlay);
        if (beats.isEmpty()) return null;

        // 队友出牌 → 配合
        if (lastFromTeammate) {
            if (lastPlay.getKeyRank() >= 14
                    || (lastPlay.getType() != CardType.SINGLE && lastPlay.getType() != CardType.PAIR)) {
                if (hand.size() <= 2) return pickSmallest(beats, true);
                return null;
            }
            List<CardPlay> cheap = beats.stream()
                    .filter(b -> !b.getType().isBombLike())
                    .filter(b -> b.getKeyRank() <= 13)
                    .collect(Collectors.toList());
            if (!cheap.isEmpty() && hand.size() > 4) {
                List<CardPlay> simple = cheap.stream()
                        .filter(b -> b.getType() == CardType.SINGLE || b.getType() == CardType.PAIR)
                        .collect(Collectors.toList());
                if (!simple.isEmpty()) return pickSmallest(simple, false);
            }
            return null;
        }

        // 对手出牌 → 压制
        boolean opponentUrgent = false;
        for (Player p : players) {
            if (p.getId() != selfId && teams[p.getId()] != myTeam && p.getCardCount() <= 2) {
                opponentUrgent = true;
            }
        }
        // 队友已出完 → 自己必须顶住
        boolean teammateDone = false;
        for (Player p : players) {
            if (p.getId() != selfId && teams[p.getId()] == myTeam && p.getCardCount() == 0) {
                teammateDone = true;
            }
        }

        List<CardPlay> normal = beats.stream()
                .filter(b -> !b.getType().isBombLike())
                .collect(Collectors.toList());

        if (!normal.isEmpty()) {
            CardPlay best = pickSmallest(normal, false);
            if (!opponentUrgent && wouldBreakHand(hand, best) && hand.size() > 6) {
                return null;
            }
            return best;
        }

        // 只有炸弹能压
        if (opponentUrgent || hand.size() <= 4 || teammateDone) {
            return pickSmallest(beats, true);
        }
        return null;
    }
}