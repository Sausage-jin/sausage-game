import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏核心逻辑：6行6列棋盘，双人对战+中立资源
 * 双方各5张暗牌（烂头/杀人虎/小孩/公鸡/蜜蜂）+10张中立暗牌随机铺开；
 * 中立资源：大树×2、巨石×2、猫咪、旺财、白/黑洋枪、白/黑捕虫网。
 * 所有牌开局均为牌背，需翻开后才能行动。
 * 白牌双方均可移动，黑牌不可移动（但可参与合成）。
 */
public class GameLogic {
    public static final int ROWS = 6;
    public static final int COLS = 6;

    private Card[][] board;
    private int currentPlayer;   // 1 或 2
    private boolean gameOver;
    private int winner;          // 0=平局, 1或2=获胜玩家
    private String lastAction;

    /** 连续交换检查：同一烂头不能连续两回合与巨石交换 */
    private int lastSwapPlayer = 0;
    private int lastSwapRow = -1, lastSwapCol = -1;

    /** 击杀关系表：key能击杀value集合中的任意卡 */
    private static final Map<CardType, Set<CardType>> KILL_MAP = new HashMap<CardType, Set<CardType>>();
    static {
        KILL_MAP.put(CardType.LANTOUBEIQIANG, EnumSet.of(CardType.SHARENHU, CardType.LANTOU, CardType.MAOMI, CardType.WANGCAI));
        KILL_MAP.put(CardType.BUCHONGXIAOHAI, EnumSet.of(CardType.MIFENG, CardType.GONGJI));
        KILL_MAP.put(CardType.SHARENHU, EnumSet.of(CardType.XIAOHAI, CardType.LANTOU, CardType.BUCHONGXIAOHAI));
        KILL_MAP.put(CardType.XIAOHAI, EnumSet.of(CardType.GONGJI));
        KILL_MAP.put(CardType.GONGJI, EnumSet.of(CardType.MIFENG));
        KILL_MAP.put(CardType.MIFENG, EnumSet.of(CardType.LANTOU, CardType.LANTOUBEIQIANG));
    }

    public GameLogic() {
        reset();
    }

    /** 初始化：双方各5张暗牌+10张中立暗牌，混合随机铺在棋盘上（36格取20格） */
    public void reset() {
        board = new Card[ROWS][COLS];
        currentPlayer = 1;
        gameOver = false;
        winner = 0;
        lastSwapPlayer = 0; lastSwapRow = -1; lastSwapCol = -1;
        lastAction = "红方先手，点暗牌翻开或己方/中立明牌行动";

        List<Card> all = new ArrayList<Card>();
        // 10张中立明牌
        all.add(new Card(CardType.DASHU, 0));
        all.add(new Card(CardType.DASHU, 0));
        all.add(new Card(CardType.JUSHI, 0));
        all.add(new Card(CardType.JUSHI, 0));
        all.add(new Card(CardType.MAOMI, 0));
        all.add(new Card(CardType.WANGCAI, 0));
        all.add(new Card(CardType.BAIQIANG, 0));
        all.add(new Card(CardType.HEIQIANG, 0));
        all.add(new Card(CardType.BAIBUCHONG, 0));
        all.add(new Card(CardType.HEIBUCHONG, 0));

        // 双方各5张暗牌
        for (int p = 1; p <= 2; p++) {
            List<CardType> types = new ArrayList<CardType>(Arrays.asList(
                    CardType.LANTOU, CardType.SHARENHU, CardType.XIAOHAI,
                    CardType.GONGJI, CardType.MIFENG));
            Collections.shuffle(types);
            for (CardType t : types) all.add(new Card(t, p));
        }
        Collections.shuffle(all);

        // 随机选20个格子放牌，其余留空
        List<Integer> positions = new ArrayList<Integer>();
        for (int i = 0; i < ROWS * COLS; i++) positions.add(i);
        Collections.shuffle(positions);
        for (int i = 0; i < all.size(); i++) {
            int pos = positions.get(i);
            board[pos / COLS][pos % COLS] = all.get(i);
        }
    }

    public Card getCard(int row, int col) {
        return board[row][col];
    }

    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public String getLastAction() { return lastAction; }

    /** 统计某玩家存活卡牌数（中立牌不计入） */
    public int countAlive(int player) {
        int count = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c] != null && board[r][c].getOwner() == player) count++;
        return count;
    }

    /** 翻牌：翻开任意一张暗牌（可能翻到对方的牌），消耗本回合 */
    public boolean flip(int row, int col) {
        if (gameOver) return false;
        Card card = board[row][col];
        if (card == null || card.isFaceUp()) return false;
        card.setFaceUp(true);
        String ownerPrefix = card.getOwner() == 0 ? "中立"
                : (card.getOwner() != currentPlayer ? playerName(card.getOwner()) : "");
        lastAction = playerName(currentPlayer) + "翻开 " + ownerPrefix + card.getType().getName();
        clearSwapIfMine();
        endTurnCheck();
        if (!gameOver) nextTurn();
        return true;
    }

    /**
     * 移动/对战/交换：己方明牌或中立可动明牌向相邻一格行动
     * 空格→移动；敌方牌→对战；巨石+烂头/烂头背枪→交换位置；
     * 中立牌（非巨石）→仅当可击杀时允许（烂头背枪杀猫咪/旺财）
     */
    public boolean move(int fromRow, int fromCol, int toRow, int toCol) {
        if (gameOver) return false;
        if (!isAdjacent(fromRow, fromCol, toRow, toCol)) return false;
        Card card = board[fromRow][fromCol];
        if (card == null || !card.isFaceUp()) return false;
        // 可操作检查：己方明牌 或 中立可动明牌
        if (card.getOwner() != currentPlayer) {
            if (card.getOwner() != 0 || !card.getType().isMovable()) return false;
        }
        if (!card.getType().isMovable()) return false; // 大树/巨石/黑牌不可动
        Card target = board[toRow][toCol];
        if (target != null && target.getOwner() == currentPlayer) return false; // 不能移到己方牌上

        if (target == null) {
            // 移动到空格
            board[toRow][toCol] = card;
            board[fromRow][fromCol] = null;
            lastAction = playerName(currentPlayer) + "移动 " + card.getType().getName();
            clearSwapIfMine();
        } else if (target.getType() == CardType.JUSHI && target.isFaceUp()
                && (card.getType() == CardType.LANTOU || card.getType() == CardType.LANTOUBEIQIANG)) {
            // 烂头/烂头背枪 与巨石交换位置（巨石需已翻开）
            if (currentPlayer == lastSwapPlayer && fromRow == lastSwapRow && fromCol == lastSwapCol) {
                return false; // 不能连续交换
            }
            board[fromRow][fromCol] = target;  // 巨石移到烂头原位
            board[toRow][toCol] = card;        // 烂头移到巨石原位
            lastSwapPlayer = currentPlayer;
            lastSwapRow = toRow;
            lastSwapCol = toCol;
            lastAction = playerName(currentPlayer) + card.getType().getName() + "与巨石交换位置";
        } else if (target.getOwner() == 0) {
            // 目标为其他中立牌（需已翻开）：仅当可击杀时允许
            if (!target.isFaceUp() || !canKill(card.getType(), target.getType())) return false;
            board[toRow][toCol] = card;
            board[fromRow][fromCol] = null;
            lastAction = card.getType().getName() + " 击杀 " + target.getType().getName();
            clearSwapIfMine();
        } else {
            // 敌方牌 → 对战（暗牌翻开应战）
            StringBuilder sb = new StringBuilder();
            if (!target.isFaceUp()) {
                target.setFaceUp(true);
                sb.append(target.getType().getName()).append("应战，");
            }
            boolean attKills = canKill(card.getType(), target.getType());
            boolean defKills = canKill(target.getType(), card.getType());

            // 特殊规则：杀人虎与烂头背枪、烂头背枪互相对战，先手（进攻方）胜
            boolean firstStrike = (card.getType() == CardType.SHARENHU && target.getType() == CardType.LANTOUBEIQIANG)
                               || (card.getType() == CardType.LANTOUBEIQIANG && target.getType() == CardType.SHARENHU)
                               || (card.getType() == CardType.LANTOUBEIQIANG && target.getType() == CardType.LANTOUBEIQIANG);
            if (firstStrike) {
                attKills = true;
                defKills = false;
            }

            if (attKills) {
                board[toRow][toCol] = card;
                board[fromRow][fromCol] = null;
                sb.append(card.getType().getName()).append(" 击杀 ").append(target.getType().getName());
                if (firstStrike) sb.append("（先手胜）");
                if (target.getType() == CardType.LANTOU) {
                    gameOver = true;
                    winner = currentPlayer;
                    sb.append("，斩首胜利！");
                } else if (target.getType() == CardType.LANTOUBEIQIANG) {
                    gameOver = true;
                    winner = currentPlayer;
                    sb.append("，击杀烂头背枪，胜利！");
                }
            } else if (defKills) {
                board[fromRow][fromCol] = null;
                sb.append(card.getType().getName()).append(" 被 ").append(target.getType().getName()).append(" 反杀");
                if (card.getType() == CardType.LANTOU) {
                    gameOver = true;
                    winner = target.getOwner();
                    sb.append("，烂头阵亡！");
                } else if (card.getType() == CardType.LANTOUBEIQIANG) {
                    gameOver = true;
                    winner = target.getOwner();
                    sb.append("，烂头背枪阵亡！");
                }
            } else {
                sb.append("平局退回");
            }
            lastAction = sb.toString();
            clearSwapIfMine();
        }

        endTurnCheck();
        if (!gameOver) nextTurn();
        return true;
    }

    /**
     * 合成：己方明牌+相邻中立明牌
     * 烂头+洋枪→烂头背枪；小孩+捕虫网→捕虫男孩
     * 合成结果放在第二张牌的位置
     */
    public boolean synthesize(int row1, int col1, int row2, int col2) {
        if (gameOver) return false;
        if (!isAdjacent(row1, col1, row2, col2)) return false;
        Card c1 = board[row1][col1];
        Card c2 = board[row2][col2];
        if (c1 == null || c2 == null) return false;
        if (!c1.isFaceUp() || !c2.isFaceUp()) return false;

        CardType t1 = c1.getType(), t2 = c2.getType();
        CardType mergedType = null;
        int mRow = -1, mCol = -1;

        if (c1.getOwner() == currentPlayer && c2.getOwner() == 0) {
            if (t1 == CardType.LANTOU && t2.isQiang()) {
                mergedType = CardType.LANTOUBEIQIANG; mRow = row2; mCol = col2;
            } else if (t1 == CardType.XIAOHAI && t2.isBuchong()) {
                mergedType = CardType.BUCHONGXIAOHAI; mRow = row2; mCol = col2;
            }
        } else if (c2.getOwner() == currentPlayer && c1.getOwner() == 0) {
            if (t2 == CardType.LANTOU && t1.isQiang()) {
                mergedType = CardType.LANTOUBEIQIANG; mRow = row1; mCol = col1;
            } else if (t2 == CardType.XIAOHAI && t1.isBuchong()) {
                mergedType = CardType.BUCHONGXIAOHAI; mRow = row1; mCol = col1;
            }
        }
        if (mergedType == null) return false;

        board[row1][col1] = null;
        board[row2][col2] = null;
        Card merged = new Card(mergedType, currentPlayer);
        merged.setFaceUp(true);
        board[mRow][mCol] = merged;
        lastAction = playerName(currentPlayer) + "合成 " + mergedType.getName();
        clearSwapIfMine();

        endTurnCheck();
        if (!gameOver) nextTurn();
        return true;
    }

    /** 当前玩家执行了非交换操作后，清除其连续交换记录 */
    private void clearSwapIfMine() {
        if (lastSwapPlayer == currentPlayer) {
            lastSwapPlayer = 0; lastSwapRow = -1; lastSwapCol = -1;
        }
    }

    private boolean canKill(CardType attacker, CardType defender) {
        Set<CardType> kills = KILL_MAP.get(attacker);
        return kills != null && kills.contains(defender);
    }

    /** 公共查询：attacker能否击杀defender（供AI使用） */
    public static boolean canKillType(CardType attacker, CardType defender) {
        Set<CardType> kills = KILL_MAP.get(attacker);
        return kills != null && kills.contains(defender);
    }

    /** 公共查询：对战是否为先手胜特殊对（杀人虎vs烂头背枪、烂头背枪vs烂头背枪） */
    public static boolean isFirstStrike(CardType attacker, CardType defender) {
        return (attacker == CardType.SHARENHU && defender == CardType.LANTOUBEIQIANG)
            || (attacker == CardType.LANTOUBEIQIANG && defender == CardType.SHARENHU)
            || (attacker == CardType.LANTOUBEIQIANG && defender == CardType.LANTOUBEIQIANG);
    }

    /** 公共查询：指定位置的牌是否可与巨石交换（非连续交换限制） */
    public boolean canSwapWithRock(int fromRow, int fromCol) {
        return !(currentPlayer == lastSwapPlayer && fromRow == lastSwapRow && fromCol == lastSwapCol);
    }

    private boolean isAdjacent(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2) == 1;
    }

    private void nextTurn() {
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
    }

    /** 回合结束统一检查：某方仅剩烂头 → 输 */
    private void endTurnCheck() {
        if (gameOver) return;
        for (int p = 1; p <= 2; p++) {
            int count = 0;
            boolean hasLantou = false;
            boolean onlyLantou = true;
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    Card card = board[r][c];
                    if (card != null && card.getOwner() == p) {
                        count++;
                        if (card.getType() == CardType.LANTOU) hasLantou = true;
                        else onlyLantou = false;
                    }
                }
            }
            if (count == 1 && hasLantou && onlyLantou) {
                gameOver = true;
                winner = (p == 1) ? 2 : 1;
                lastAction = playerName(p) + "仅剩烂头，" + playerName(winner) + "胜";
                return;
            }
        }
    }

    /** 序列化完整状态（用于联机同步） */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(currentPlayer).append('|').append(gameOver).append('|').append(winner).append('|')
          .append(lastAction == null ? "" : lastAction.replace("|", "\u4e28"))
          .append('|').append(lastSwapPlayer).append(':').append(lastSwapRow).append(':').append(lastSwapCol);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append('|');
                Card card = board[r][c];
                if (card == null) sb.append('-');
                else sb.append(card.getOwner()).append(':').append(card.getType().ordinal())
                       .append(':').append(card.isFaceUp() ? 1 : 0);
            }
        }
        return sb.toString();
    }

    /** 反序列化恢复状态 */
    public void deserialize(String s) {
        String[] parts = s.split("\\|", -1);
        currentPlayer = Integer.parseInt(parts[0]);
        gameOver = Boolean.parseBoolean(parts[1]);
        winner = Integer.parseInt(parts[2]);
        lastAction = parts[3].replace("\u4e28", "|");
        String[] swap = parts[4].split(":", -1);
        lastSwapPlayer = Integer.parseInt(swap[0]);
        lastSwapRow = Integer.parseInt(swap[1]);
        lastSwapCol = Integer.parseInt(swap[2]);
        board = new Card[ROWS][COLS];
        int idx = 5;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String cell = parts[idx++];
                if (!"-".equals(cell)) {
                    String[] f = cell.split(":");
                    Card card = new Card(CardType.values()[Integer.parseInt(f[1])], Integer.parseInt(f[0]));
                    card.setFaceUp("1".equals(f[2]));
                    board[r][c] = card;
                }
            }
        }
    }

    private String playerName(int p) {
        return p == 1 ? "红方" : "蓝方";
    }
}