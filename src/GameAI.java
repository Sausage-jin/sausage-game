import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 烂头背枪AI：基于优先级规则的决策，公平不偷看暗牌。
 * 优先级：斩首 > 合成 > 安全击杀 > 击杀中立 > 逃离危险 > 靠近目标 > 翻牌 > 随机移动
 *
 * 动作格式：
 *   int[3] = {FLIP, row, col}
 *   int[5] = {MOVE, fromRow, fromCol, toRow, toCol}
 *   int[5] = {SYN, row1, col1, row2, col2}
 *   null   = 无可行动作
 */
public class GameAI {
    public static final int FLIP = 0, MOVE = 1, SYN = 2;
    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private int aiPlayer;      // AI玩家号
    private int enemyPlayer;   // 对手玩家号
    private Random rnd = new Random();

    public GameAI(int aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.enemyPlayer = (aiPlayer == 1) ? 2 : 1;
    }

    public int[] decide(GameLogic game) {
        int[] a;
        if ((a = findWinningKill(game)) != null) return a;
        if ((a = findSynthesis(game)) != null) return a;
        if ((a = findSafeKill(game)) != null) return a;
        if ((a = findNeutralKill(game)) != null) return a;
        if ((a = findEscape(game)) != null) return a;
        if ((a = findApproach(game)) != null) return a;
        if ((a = findFlip(game)) != null) return a;
        if ((a = findRandomMove(game)) != null) return a;
        return null;
    }

    // ==================== 优先级1：斩首 ====================
    // 击杀敌方明牌烂头或烂头背枪（直接获胜）
    private int[] findWinningKill(GameLogic game) {
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (!isMyMovableFaceUp(card)) continue;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card target = game.getCard(nr, nc);
                    if (target == null || !target.isFaceUp() || target.getOwner() != enemyPlayer) continue;
                    if (target.getType() == CardType.LANTOU
                            && GameLogic.canKillType(card.getType(), CardType.LANTOU)) {
                        return new int[]{MOVE, r, c, nr, nc};
                    }
                    if (target.getType() == CardType.LANTOUBEIQIANG
                            && (GameLogic.isFirstStrike(card.getType(), CardType.LANTOUBEIQIANG)
                                || GameLogic.canKillType(card.getType(), CardType.LANTOUBEIQIANG))) {
                        return new int[]{MOVE, r, c, nr, nc};
                    }
                }
            }
        }
        return null;
    }

    // ==================== 优先级2：合成 ====================
    // 烂头+洋枪→烂头背枪；小孩+捕虫网→捕虫男孩
    private int[] findSynthesis(GameLogic game) {
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (card == null || !card.isFaceUp() || card.getOwner() != aiPlayer) continue;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card target = game.getCard(nr, nc);
                    if (target == null || !target.isFaceUp() || target.getOwner() != 0) continue;
                    if ((card.getType() == CardType.LANTOU && target.getType().isQiang())
                            || (card.getType() == CardType.XIAOHAI && target.getType().isBuchong())) {
                        return new int[]{SYN, r, c, nr, nc};
                    }
                }
            }
        }
        return null;
    }

    // ==================== 优先级3：安全击杀 ====================
    // 击杀敌方明牌（attKills=true时进攻方必胜）
    private int[] findSafeKill(GameLogic game) {
        int bestScore = -1;
        int[] best = null;
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (!isMyMovableFaceUp(card)) continue;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card target = game.getCard(nr, nc);
                    if (target == null || !target.isFaceUp() || target.getOwner() != enemyPlayer) continue;
                    boolean canKill = GameLogic.canKillType(card.getType(), target.getType())
                            || GameLogic.isFirstStrike(card.getType(), target.getType());
                    if (!canKill) continue;
                    int score = cardValue(target.getType());
                    // 烂头背枪不杀低价值目标（太珍贵）
                    if (card.getType() == CardType.LANTOUBEIQIANG && score < 60) continue;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[]{MOVE, r, c, nr, nc};
                    }
                }
            }
        }
        return best;
    }

    // ==================== 优先级4：击杀中立 ====================
    // 烂头背枪击杀猫咪/旺财（削减潜在资源）
    private int[] findNeutralKill(GameLogic game) {
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (card == null || !card.isFaceUp() || card.getOwner() != aiPlayer) continue;
                if (card.getType() != CardType.LANTOUBEIQIANG) continue;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card target = game.getCard(nr, nc);
                    if (target == null || !target.isFaceUp() || target.getOwner() != 0) continue;
                    if (target.getType() == CardType.MAOMI || target.getType() == CardType.WANGCAI) {
                        return new int[]{MOVE, r, c, nr, nc};
                    }
                }
            }
        }
        return null;
    }

    // ==================== 优先级5：逃离危险 ====================
    // 烂头/烂头背枪/捕虫男孩受威胁时逃跑
    private int[] findEscape(GameLogic game) {
        // 优先救烂头和烂头背枪，其次捕虫男孩
        int[] best = escapeForType(game, CardType.LANTOU);
        if (best == null) best = escapeForType(game, CardType.LANTOUBEIQIANG);
        if (best == null) best = escapeForType(game, CardType.BUCHONGXIAOHAI);
        return best;
    }

    private int[] escapeForType(GameLogic game, CardType type) {
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (card == null || !card.isFaceUp() || card.getOwner() != aiPlayer) continue;
                if (card.getType() != type) continue;
                if (!isThreatened(game, r, c, type)) continue;
                // 方案1：与巨石交换
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card t = game.getCard(nr, nc);
                    if (t != null && t.isFaceUp() && t.getType() == CardType.JUSHI
                            && game.canSwapWithRock(r, c)) {
                        return new int[]{MOVE, r, c, nr, nc};
                    }
                }
                // 方案2：移到安全的空格
                List<int[]> safeCells = new ArrayList<int[]>();
                List<int[]> anyCells = new ArrayList<int[]>();
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    if (game.getCard(nr, nc) == null) {
                        if (!isThreatened(game, nr, nc, type)) safeCells.add(new int[]{nr, nc});
                        else anyCells.add(new int[]{nr, nc});
                    }
                }
                if (!safeCells.isEmpty()) {
                    int[] dest = safeCells.get(rnd.nextInt(safeCells.size()));
                    return new int[]{MOVE, r, c, dest[0], dest[1]};
                }
                if (!anyCells.isEmpty()) {
                    int[] dest = anyCells.get(rnd.nextInt(anyCells.size()));
                    return new int[]{MOVE, r, c, dest[0], dest[1]};
                }
            }
        }
        return null;
    }

    // 检查(r,c)处的牌是否被敌方明牌威胁
    private boolean isThreatened(GameLogic game, int r, int c, CardType myType) {
        for (int[] d : DIRS) {
            int nr = r + d[0], nc = c + d[1];
            if (!inBoard(nr, nc)) continue;
            Card enemy = game.getCard(nr, nc);
            if (enemy == null || !enemy.isFaceUp() || enemy.getOwner() != enemyPlayer) continue;
            if (GameLogic.canKillType(enemy.getType(), myType)) return true;
            if (GameLogic.isFirstStrike(enemy.getType(), myType)) return true;
        }
        return false;
    }

    // ==================== 优先级6：靠近目标 ====================
    // 移向合成目标或敌方烂头
    private int[] findApproach(GameLogic game) {
        int bestDist = Integer.MAX_VALUE;
        int[] best = null;
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (!isMyMovableFaceUp(card)) continue;
                int[] target = findNearestTarget(game, r, c, card.getType());
                if (target == null) continue;
                int dist = Math.abs(r - target[0]) + Math.abs(c - target[1]);
                if (dist < 2) continue; // 已相邻，由更高优先级处理
                int[] step = stepToward(game, r, c, target[0], target[1], card.getType());
                if (step != null && dist < bestDist) {
                    bestDist = dist;
                    best = step;
                }
            }
        }
        return best;
    }

    // 根据卡牌类型找最近的目标位置
    private int[] findNearestTarget(GameLogic game, int r, int c, CardType type) {
        int bestDist = Integer.MAX_VALUE;
        int[] best = null;
        for (int tr = 0; tr < GameLogic.ROWS; tr++) {
            for (int tc = 0; tc < GameLogic.COLS; tc++) {
                Card t = game.getCard(tr, tc);
                if (t == null || !t.isFaceUp()) continue;
                boolean isTarget = false;
                if (type == CardType.LANTOU && t.getOwner() == 0 && t.getType().isQiang()) isTarget = true;
                if (type == CardType.XIAOHAI && t.getOwner() == 0 && t.getType().isBuchong()) isTarget = true;
                if ((type == CardType.SHARENHU || type == CardType.LANTOUBEIQIANG || type == CardType.MIFENG)
                        && t.getOwner() == enemyPlayer && t.getType() == CardType.LANTOU) isTarget = true;
                if (type == CardType.MIFENG && t.getOwner() == enemyPlayer
                        && t.getType() == CardType.LANTOUBEIQIANG) isTarget = true;
                if (type == CardType.BUCHONGXIAOHAI && t.getOwner() == enemyPlayer
                        && (t.getType() == CardType.MIFENG || t.getType() == CardType.GONGJI)) isTarget = true;
                if (type == CardType.GONGJI && t.getOwner() == enemyPlayer && t.getType() == CardType.MIFENG) isTarget = true;
                if (isTarget) {
                    int dist = Math.abs(r - tr) + Math.abs(c - tc);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = new int[]{tr, tc};
                    }
                }
            }
        }
        return best;
    }

    // 尝试向目标移动一步（烂头/烂头背枪避开危险格）
    private int[] stepToward(GameLogic game, int r, int c, int tr, int tc, CardType type) {
        int dr = Integer.compare(tr - r, 0);
        int dc = Integer.compare(tc - c, 0);
        boolean precious = (type == CardType.LANTOU || type == CardType.LANTOUBEIQIANG);
        // 先尝试主方向
        if (dr != 0 && canStepTo(game, r + dr, c, precious, type)) {
            return new int[]{MOVE, r, c, r + dr, c};
        }
        if (dc != 0 && canStepTo(game, r, c + dc, precious, type)) {
            return new int[]{MOVE, r, c, r, c + dc};
        }
        // 绕行
        if (dr != 0) {
            for (int dc2 : new int[]{-1, 1}) {
                if (dc2 != dc && canStepTo(game, r, c + dc2, precious, type)) {
                    return new int[]{MOVE, r, c, r, c + dc2};
                }
            }
        }
        if (dc != 0) {
            for (int dr2 : new int[]{-1, 1}) {
                if (dr2 != dr && canStepTo(game, r + dr2, c, precious, type)) {
                    return new int[]{MOVE, r, c, r + dr2, c};
                }
            }
        }
        return null;
    }

    private boolean canStepTo(GameLogic game, int nr, int nc, boolean precious, CardType type) {
        if (!inBoard(nr, nc)) return false;
        if (game.getCard(nr, nc) != null) return false;
        if (precious && isThreatened(game, nr, nc, type)) return false;
        return true;
    }

    // ==================== 优先级7：翻牌 ====================
    // 翻开暗牌探索（加权随机：优先翻己方强牌附近）
    private int[] findFlip(GameLogic game) {
        List<int[]> candidates = new ArrayList<int[]>();
        List<Integer> scores = new ArrayList<Integer>();
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (card == null || card.isFaceUp()) continue;
                int score = 1;
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    Card adj = game.getCard(nr, nc);
                    if (adj != null && adj.isFaceUp() && adj.getOwner() == aiPlayer) {
                        if (adj.getType() == CardType.SHARENHU || adj.getType() == CardType.LANTOUBEIQIANG
                                || adj.getType() == CardType.BUCHONGXIAOHAI) {
                            score += 3;
                        } else {
                            score += 1;
                        }
                    }
                }
                candidates.add(new int[]{r, c});
                scores.add(score);
            }
        }
        if (candidates.isEmpty()) return null;
        int total = 0;
        for (int s : scores) total += s;
        int pick = rnd.nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            pick -= scores.get(i);
            if (pick < 0) {
                return new int[]{FLIP, candidates.get(i)[0], candidates.get(i)[1]};
            }
        }
        return new int[]{FLIP, candidates.get(0)[0], candidates.get(0)[1]};
    }

    // ==================== 优先级8：随机移动 ====================
    private int[] findRandomMove(GameLogic game) {
        List<int[]> moves = new ArrayList<int[]>();
        for (int r = 0; r < GameLogic.ROWS; r++) {
            for (int c = 0; c < GameLogic.COLS; c++) {
                Card card = game.getCard(r, c);
                if (!isMyMovableFaceUp(card)) continue;
                if (card.getType() == CardType.LANTOU) continue; // 烂头不乱跑
                for (int[] d : DIRS) {
                    int nr = r + d[0], nc = c + d[1];
                    if (!inBoard(nr, nc)) continue;
                    if (game.getCard(nr, nc) == null) {
                        moves.add(new int[]{MOVE, r, c, nr, nc});
                    }
                }
            }
        }
        if (moves.isEmpty()) return null;
        return moves.get(rnd.nextInt(moves.size()));
    }

    // ==================== 工具方法 ====================
    private boolean isMyMovableFaceUp(Card card) {
        return card != null && card.isFaceUp() && card.getOwner() == aiPlayer && card.getType().isMovable();
    }

    private boolean inBoard(int r, int c) {
        return r >= 0 && r < GameLogic.ROWS && c >= 0 && c < GameLogic.COLS;
    }

    private int cardValue(CardType type) {
        switch (type) {
            case LANTOUBEIQIANG: return 100;
            case BUCHONGXIAOHAI: return 80;
            case SHARENHU: return 70;
            case LANTOU: return 60;
            case XIAOHAI: return 40;
            case GONGJI: return 30;
            case MIFENG: return 55;
            default: return 10;
        }
    }
}