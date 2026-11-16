import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 五子棋AI：基于棋型评分的决策。
 * 评估每个空位：进攻分（AI落子形成的棋型）+ 防守分（对手落子形成的棋型）。
 * 优先级：己方连五 > 挡对方连五 > 己方活四 > 挡对方活四/己方冲四 > 综合评分。
 */
public class GomokuAI {
    private static final int SIZE = GomokuBoard.SIZE;
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    // 棋型分数
    private static final int FIVE        = 1000000;  // 连五（获胜）
    private static final int LIVE_FOUR   = 100000;   // 活四（两端开放的四连）
    private static final int RUSH_FOUR   = 10000;    // 冲四（一端封闭的四连）
    private static final int LIVE_THREE  = 8000;     // 活三
    private static final int SLEEP_THREE = 500;      // 眠三
    private static final int LIVE_TWO    = 200;      // 活二
    private static final int SLEEP_TWO   = 50;       // 眠二
    private static final int LIVE_ONE    = 10;       // 活一

    private int aiPlayer;      // AI棋色（1黑/2白/3绿）
    private int[] enemies;     // 所有对手棋色（三人模式有2个对手）
    private Random rnd = new Random();

    /** 二人模式：AI执aiPlayer，对手为另一方 */
    public GomokuAI(int aiPlayer) {
        this.aiPlayer = aiPlayer;
        this.enemies = new int[]{(aiPlayer == 1) ? 2 : 1};
    }

    /** 指定对手数：AI执aiPlayer，1..numPlayers中除自己外均为对手 */
    public GomokuAI(int aiPlayer, int numPlayers) {
        this.aiPlayer = aiPlayer;
        List<Integer> en = new ArrayList<Integer>();
        for (int p = 1; p <= numPlayers; p++) {
            if (p != aiPlayer) en.add(p);
        }
        this.enemies = new int[en.size()];
        for (int i = 0; i < en.size(); i++) enemies[i] = en.get(i);
    }

    /** 返回 {row, col}，null表示无可下位置 */
    public int[] decide(GomokuBoard board) {
        if (board.isGameOver()) return null;

        int bestScore = -1;
        List<int[]> bestMoves = new ArrayList<int[]>();
        int blockR = -1, blockC = -1;      // 对方连五必挡点
        int block4R = -1, block4C = -1;    // 对方活四必挡点
        boolean hasStone = false;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board.get(r, c) != 0) continue;
                if (!nearStone(board, r, c, 2)) continue;
                hasStone = true;

                int attack = evaluatePos(board, r, c, aiPlayer);
                if (attack >= FIVE) return new int[]{r, c};   // 己方连五，直接赢

                // 防守评估：取所有对手中的最大威胁
                int defend = 0;
                for (int enemy : enemies) {
                    int d = evaluatePos(board, r, c, enemy);
                    if (d > defend) defend = d;
                }
                if (defend >= FIVE) { blockR = r; blockC = c; }
                if (defend >= LIVE_FOUR) { block4R = r; block4C = c; }

                // 综合评分：进攻为主，防守略低权重
                int score = attack + (int) (defend * 0.85);
                // 中心位置微加分（同等分数优先靠中间）
                int centerBonus = (7 - Math.abs(r - 7)) + (7 - Math.abs(c - 7));
                score = score * 100 + centerBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestMoves.clear();
                    bestMoves.add(new int[]{r, c});
                } else if (score == bestScore) {
                    bestMoves.add(new int[]{r, c});
                }
            }
        }

        // 优先级处理
        if (blockR >= 0) return new int[]{blockR, blockC};      // 必挡对方连五
        if (block4R >= 0 && bestScore < LIVE_FOUR * 100) {
            return new int[]{block4R, block4C};                  // 必挡对方活四（除非自己活四）
        }

        if (!hasStone || bestMoves.isEmpty()) {
            // 空盘下天元，否则找任意空位
            if (board.get(SIZE / 2, SIZE / 2) == 0) return new int[]{SIZE / 2, SIZE / 2};
            for (int r = 0; r < SIZE; r++)
                for (int c = 0; c < SIZE; c++)
                    if (board.get(r, c) == 0) return new int[]{r, c};
            return null;
        }
        return bestMoves.get(rnd.nextInt(bestMoves.size()));
    }

    /** 评估在(r,c)落子对玩家p的总价值（4个方向棋型分之和） */
    private int evaluatePos(GomokuBoard board, int r, int c, int p) {
        int total = 0;
        for (int[] d : DIRS) {
            total += evalDir(board, r, c, d[0], d[1], p);
        }
        return total;
    }

    /** 评估(r,c)在方向(dr,dc)上的棋型分 */
    private int evalDir(GomokuBoard board, int r, int c, int dr, int dc, int p) {
        int count = 1;  // 含自身
        int openBefore = 0, openAfter = 0;

        // 正方向延伸
        int nr = r + dr, nc = c + dc;
        while (inBoard(nr, nc) && board.get(nr, nc) == p) { count++; nr += dr; nc += dc; }
        if (inBoard(nr, nc) && board.get(nr, nc) == 0) openAfter = 1;

        // 反方向延伸
        nr = r - dr; nc = c - dc;
        while (inBoard(nr, nc) && board.get(nr, nc) == p) { count++; nr -= dr; nc -= dc; }
        if (inBoard(nr, nc) && board.get(nr, nc) == 0) openBefore = 1;

        int openEnds = openBefore + openAfter;
        if (count >= 5) return FIVE;
        if (count == 4) {
            if (openEnds == 2) return LIVE_FOUR;
            if (openEnds == 1) return RUSH_FOUR;
            return 0;
        }
        if (count == 3) {
            if (openEnds == 2) return LIVE_THREE;
            if (openEnds == 1) return SLEEP_THREE;
            return 0;
        }
        if (count == 2) {
            if (openEnds == 2) return LIVE_TWO;
            if (openEnds == 1) return SLEEP_TWO;
            return 0;
        }
        // count == 1
        if (openEnds == 2) return LIVE_ONE;
        if (openEnds == 1) return 5;
        return 0;
    }

    /** 检查(r,c)周围range格内是否有棋子 */
    private boolean nearStone(GomokuBoard board, int r, int c, int range) {
        for (int dr = -range; dr <= range; dr++) {
            for (int dc = -range; dc <= range; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (inBoard(nr, nc) && board.get(nr, nc) != 0) return true;
            }
        }
        return false;
    }

    private boolean inBoard(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }
}