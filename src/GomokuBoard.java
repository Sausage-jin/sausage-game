import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/** 五子棋棋盘面板：15×15交叉点，木色棋盘，黑白绿棋子，点击落子，五连高亮。
 *  支持2人或3人模式：1黑 2白 3绿。 */
public class GomokuBoard extends JPanel {
    static final int SIZE = 15;
    static final int CELL = 36;
    static final int MARGIN = 34;
    static final int PIECE_R = 15;

    static final Color WOOD_TOP = new Color(224, 186, 110);
    static final Color WOOD_BOT = new Color(196, 154, 82);
    static final Color LINE = new Color(92, 60, 28);
    static final Color LAST_MARK = new Color(232, 72, 60);
    static final Color WIN_LINE = new Color(232, 72, 60);

    static final int DRAW = -1;

    private int[][] board = new int[SIZE][SIZE]; // 0空 1黑 2白 3绿
    private int baseSize;                        // 基准尺寸
    private double viewScale = 1.0;              // 等比缩放比例
    private int viewOffX = 0, viewOffY = 0;      // 居中偏移
    private int numPlayers = 2; // 2人或3人模式
    private int current = 1;  // 当前执子方
    private int winner = 0;   // 0进行中 1黑胜 2白胜 3绿胜 DRAW(-1)平局
    private int lastR = -1, lastC = -1;
    private List<int[]> winLine = null;
    private int hoverR = -1, hoverC = -1;

    public interface Listener {
        void onCellClick(int r, int c);
    }
    private Listener listener;

    public GomokuBoard() {
        baseSize = MARGIN * 2 + (SIZE - 1) * CELL;
        setPreferredSize(new Dimension(baseSize, baseSize));
        setMinimumSize(new Dimension(baseSize / 2, baseSize / 2));
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int[] pos = posAt(toLocalX(e.getX()), toLocalY(e.getY()));
                if (pos != null && listener != null) listener.onCellClick(pos[0], pos[1]);
            }
            public void mouseExited(MouseEvent e) { hoverR = -1; hoverC = -1; repaint(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int[] pos = posAt(toLocalX(e.getX()), toLocalY(e.getY()));
                int nr = pos == null ? -1 : pos[0], nc = pos == null ? -1 : pos[1];
                if (nr != hoverR || nc != hoverC) { hoverR = nr; hoverC = nc; repaint(); }
            }
        });
    }

    /** 窗口缩放：计算等比缩放比例与居中偏移 */
    private void updateViewTransform() {
        double s = Math.min(getWidth() / (double) baseSize, getHeight() / (double) baseSize);
        viewScale = s < 0.05 ? 0.05 : s;
        viewOffX = (int) ((getWidth() - baseSize * viewScale) / 2.0);
        viewOffY = (int) ((getHeight() - baseSize * viewScale) / 2.0);
    }

    /** 屏幕坐标转基准坐标（供鼠标命中判断） */
    private int toLocalX(int x) { return (int) ((x - viewOffX) / viewScale); }
    private int toLocalY(int y) { return (int) ((y - viewOffY) / viewScale); }

    public void setListener(Listener l) { this.listener = l; }
    public void setNumPlayers(int n) { this.numPlayers = n; reset(); }
    public int getNumPlayers() { return numPlayers; }
    public int get(int r, int c) { return board[r][c]; }
    public int getCurrentPlayer() { return current; }
    public int getWinner() { return winner; }
    public boolean isGameOver() { return winner != 0; }
    public boolean isDraw() { return winner == DRAW; }

    public void reset() {
        board = new int[SIZE][SIZE];
        current = 1; winner = 0;
        lastR = -1; lastC = -1;
        winLine = null;
        repaint();
    }

    /** 落子并判断胜负 */
    public void put(int r, int c) {
        if (board[r][c] != 0 || winner != 0) return;
        board[r][c] = current;
        lastR = r; lastC = c;
        winLine = checkWin(r, c);
        if (winLine != null) winner = current;
        else if (isFull()) winner = DRAW;
        else current = current % numPlayers + 1;
        repaint();
    }

    private boolean isFull() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (board[r][c] == 0) return false;
        return true;
    }

    /** 检查(r,c)落子后是否形成五连，返回连子坐标列表 */
    private List<int[]> checkWin(int r, int c) {
        int me = board[r][c];
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            List<int[]> line = new ArrayList<int[]>();
            line.add(new int[]{r, c});
            for (int s = 1; s < 5; s++) {
                int nr = r + d[0] * s, nc = c + d[1] * s;
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE || board[nr][nc] != me) break;
                line.add(new int[]{nr, nc});
            }
            for (int s = 1; s < 5; s++) {
                int nr = r - d[0] * s, nc = c - d[1] * s;
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE || board[nr][nc] != me) break;
                line.add(0, new int[]{nr, nc});
            }
            if (line.size() >= 5) return line;
        }
        return null;
    }

    private int px(int c) { return MARGIN + c * CELL; }
    private int py(int r) { return MARGIN + r * CELL; }

    private int[] posAt(int x, int y) {
        int c = Math.round((x - MARGIN) / (float) CELL);
        int r = Math.round((y - MARGIN) / (float) CELL);
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) return null;
        if (Math.abs(x - px(c)) > CELL / 2 || Math.abs(y - py(r)) > CELL / 2) return null;
        return new int[]{r, c};
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawWood(g2);                       // 背景铺满整个面板
        updateViewTransform();              // 等比缩放棋盘内容
        g2.translate(viewOffX, viewOffY);
        g2.scale(viewScale, viewScale);
        drawGrid(g2);
        drawStars(g2);
        drawHover(g2);
        drawPieces(g2);
        drawLastMark(g2);
        drawWinLine(g2);
    }

    private void drawWood(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, WOOD_TOP, getWidth(), getHeight(), WOOD_BOT));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(LINE);
        g2.setStroke(new BasicStroke(1.1f));
        for (int i = 0; i < SIZE; i++) {
            g2.drawLine(px(0), py(i), px(SIZE - 1), py(i));
            g2.drawLine(px(i), py(0), px(i), py(SIZE - 1));
        }
        g2.setStroke(new BasicStroke(2.2f));
        g2.drawRect(px(0), py(0), (SIZE - 1) * CELL, (SIZE - 1) * CELL);
    }

    private void drawStars(Graphics2D g2) {
        int mid = SIZE / 2, off = 3;
        int[][] stars = {{mid, mid}, {off, off}, {off, SIZE - 1 - off},
                         {SIZE - 1 - off, off}, {SIZE - 1 - off, SIZE - 1 - off}};
        g2.setColor(LINE);
        for (int[] s : stars) g2.fillOval(px(s[1]) - 4, py(s[0]) - 4, 8, 8);
    }

    private void drawHover(Graphics2D g2) {
        if (hoverR < 0 || isGameOver() || board[hoverR][hoverC] != 0) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        drawPiece(g2, px(hoverC), py(hoverR), current);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawPieces(Graphics2D g2) {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (board[r][c] != 0) drawPiece(g2, px(c), py(r), board[r][c]);
    }

    private void drawPiece(Graphics2D g2, int x, int y, int color) {
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(x - PIECE_R + 2, y - PIECE_R + 3, PIECE_R * 2, PIECE_R * 2);
        Color c1, c2, border, hl;
        if (color == 1) {          // 黑
            c1 = new Color(78, 78, 86);   c2 = new Color(18, 18, 24);
            border = new Color(8, 8, 12); hl = new Color(255, 255, 255, 50);
        } else if (color == 2) {   // 白
            c1 = new Color(252, 252, 254); c2 = new Color(190, 190, 202);
            border = new Color(155, 155, 170); hl = new Color(255, 255, 255, 160);
        } else {                   // 绿
            c1 = new Color(110, 220, 130); c2 = new Color(20, 130, 60);
            border = new Color(15, 95, 40); hl = new Color(255, 255, 255, 100);
        }
        g2.setPaint(new RadialGradientPaint(x - PIECE_R / 3f, y - PIECE_R / 3f, PIECE_R * 1.5f,
                new float[]{0f, 1f}, new Color[]{c1, c2}));
        g2.fillOval(x - PIECE_R, y - PIECE_R, PIECE_R * 2, PIECE_R * 2);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(x - PIECE_R, y - PIECE_R, PIECE_R * 2, PIECE_R * 2);
        g2.setColor(hl);
        g2.fillOval(x - PIECE_R / 2 - 2, y - PIECE_R / 2 - 3, PIECE_R / 2 + 4, PIECE_R / 2 + 2);
    }

    private void drawLastMark(Graphics2D g2) {
        if (lastR < 0 || winLine != null) return;
        g2.setColor(LAST_MARK);
        g2.fillOval(px(lastC) - 3, py(lastR) - 3, 6, 6);
    }

    private void drawWinLine(Graphics2D g2) {
        if (winLine == null || winLine.isEmpty()) return;
        int[] first = winLine.get(0);
        int[] last = winLine.get(winLine.size() - 1);
        g2.setColor(new Color(WIN_LINE.getRed(), WIN_LINE.getGreen(), WIN_LINE.getBlue(), 200));
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(px(first[1]), py(first[0]), px(last[1]), py(last[0]));
        g2.setStroke(new BasicStroke(2.5f));
        for (int[] p : winLine)
            g2.drawOval(px(p[1]) - PIECE_R - 2, py(p[0]) - PIECE_R - 2, PIECE_R * 2 + 4, PIECE_R * 2 + 4);
    }
}