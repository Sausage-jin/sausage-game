import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** 棋盘面板：格子按卡牌图片比例(2:3)竖版，纯点击操作，精致2D绘制。 */
public class BoardPanel extends JPanel {
    static final int CELL_W = 104, CELL_H = 156, GAP = 11, MARGIN = 26, CORNER = 13;
    static final Color BG_TOP = new Color(32, 36, 48), BG_BOT = new Color(20, 23, 32);
    static final Color Z_CELL = new Color(64, 58, 48);
    static final Color LINE = new Color(255, 255, 255, 30);
    static final Color HL_SEL = new Color(255, 190, 60), HL_MOVE = new Color(90, 230, 130);
    static final Color HL_ATK = new Color(255, 90, 90), HL_MERGE = new Color(255, 225, 100);
    static final Color HL_HOVER = new Color(255, 255, 255, 42);
    static final Color EDGE_RED = new Color(200, 60, 55), EDGE_BLU = new Color(60, 90, 200), EDGE_GLD = new Color(210, 160, 40);
    static final Color EDGE_NTR = new Color(160, 160, 170);   // 中立牌银灰边框
    static final Color HL_SWAP = new Color(100, 200, 255);    // 交换高亮（青色）

    private GameLogic game;
    private int baseW, baseH;                 // 基准尺寸（1:1缩放时）
    private double viewScale = 1.0;           // 当前等比缩放比例
    private int viewOffX = 0, viewOffY = 0;   // 居中偏移
    private int selRow = -1, selCol = -1, hoverRow = -1, hoverCol = -1;
    private boolean animating = false, flipApplied = false;
    private int flipRow = -1, flipCol = -1, flashRow = -1, flashCol = -1;
    private float flipPhase = 0f, flash = 0f;
    private Timer animTimer;
    private final Map<String, BufferedImage> images = new HashMap<String, BufferedImage>();

    private BufferedImage loadImage(String name) {
        BufferedImage img = images.get(name);
        if (img != null) return img;
        try {
            File f = new File("image/" + name);
            if (f.exists()) { img = ImageIO.read(f); images.put(name, img); }
        } catch (Exception e) { }
        return img;
    }

    /** 中立牌共用图片，双方牌按红蓝前缀 */
    private BufferedImage imageFor(Card card) {
        boolean p1 = card.getOwner() == 1;
        switch (card.getType()) {
            case LANTOU:         return loadImage(p1 ? "red_lantou.jpg" : "blue_lantou.jpg");
            case SHARENHU:       return loadImage(p1 ? "red_hu.jpg" : "blue_hu.jpg");
            case XIAOHAI:        return loadImage(p1 ? "red_xiaohai.jpg" : "blue_xiaohai.jpg");
            case GONGJI:         return loadImage(p1 ? "red_ji.jpg" : "blue_ji.jpg");
            case MIFENG:         return loadImage(p1 ? "red_feng.jpg" : "blue_feng.jpg");
            case LANTOUBEIQIANG: return loadImage(p1 ? "red_lantoubeiqiang.jpg" : "blue_lantoubeiqiang.jpg");
            case BUCHONGXIAOHAI: return loadImage(p1 ? "red_buchongxiaohai.jpg" : "blue_buchongxiaohai.jpg");
            case BAIQIANG:       return loadImage("baiqiang.jpg");
            case HEIQIANG:       return loadImage("heiqiang.jpg");
            case DASHU:          return loadImage("大树.jpg");
            case JUSHI:          return loadImage("巨石.jpg");
            case MAOMI:          return loadImage("猫咪.jpg");
            case WANGCAI:        return loadImage("wangcai.jpg");
            case BAIBUCHONG:     return loadImage("baibuchong.jpg");
            case HEIBUCHONG:     return loadImage("heibuchong.jpg");
        }
        return null;
    }

    public interface Listener {
        void onCellClick(int row, int col);
        void onFlipDone();
    }
    private Listener listener;

    public BoardPanel(GameLogic game) {
        this.game = game;
        baseW = MARGIN * 2 + GameLogic.COLS * CELL_W + (GameLogic.COLS - 1) * GAP;
        baseH = MARGIN * 2 + GameLogic.ROWS * CELL_H + (GameLogic.ROWS - 1) * GAP;
        setPreferredSize(new Dimension(baseW, baseH));
        setMinimumSize(new Dimension(baseW / 2, baseH / 2));
        setOpaque(true);
        setBackground(BG_BOT);
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int[] pos = cellAt(toLocalX(e.getX()), toLocalY(e.getY()));
                if (pos != null && listener != null) listener.onCellClick(pos[0], pos[1]);
            }
            public void mouseExited(MouseEvent e) { hoverRow = -1; hoverCol = -1; repaint(); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int[] pos = cellAt(toLocalX(e.getX()), toLocalY(e.getY()));
                int nr = pos == null ? -1 : pos[0], nc = pos == null ? -1 : pos[1];
                if (nr != hoverRow || nc != hoverCol) { hoverRow = nr; hoverCol = nc; repaint(); }
            }
        });
        animTimer = new Timer(16, e -> tick());
    }

    /** 窗口缩放：计算等比缩放比例与居中偏移 */
    private void updateViewTransform() {
        double sx = getWidth() / (double) baseW;
        double sy = getHeight() / (double) baseH;
        viewScale = Math.min(sx, sy);
        if (viewScale < 0.05) viewScale = 0.05;
        viewOffX = (int) ((getWidth() - baseW * viewScale) / 2.0);
        viewOffY = (int) ((getHeight() - baseH * viewScale) / 2.0);
    }

    /** 屏幕坐标转基准坐标（供鼠标命中判断） */
    private int toLocalX(int x) { return (int) ((x - viewOffX) / viewScale); }
    private int toLocalY(int y) { return (int) ((y - viewOffY) / viewScale); }

    public void setListener(Listener l) { this.listener = l; }
    public void setSelection(int r, int c) { selRow = r; selCol = c; repaint(); }
    public void clearSelection() { selRow = -1; selCol = -1; repaint(); }
    public boolean hasSelection() { return selRow >= 0; }

    public void animateFlip(int row, int col) {
        flipRow = row; flipCol = col; flipPhase = 0f; flipApplied = false;
        animating = true; animTimer.start();
    }

    public void animateBattle(int row, int col) {
        flashRow = row; flashCol = col; flash = 1f;
        if (!animating) { animating = true; animTimer.start(); }
    }

    private void tick() {
        boolean need = false;
        if (flipPhase < 1f && flipRow >= 0) {
            flipPhase += 0.08f;
            if (flipPhase >= 0.5f && !flipApplied) {
                flipApplied = true;
                game.flip(flipRow, flipCol);
                if (listener != null) listener.onFlipDone();
            }
            if (flipPhase >= 1f) { flipPhase = 1f; flipRow = -1; }
            need = true;
        }
        if (flash > 0f) {
            flash -= 0.06f;
            if (flash <= 0f) { flash = 0f; flashRow = -1; }
            need = true;
        }
        if (need) repaint();
        if (flipPhase >= 1f && flash <= 0f) { animating = false; animTimer.stop(); }
    }

    int cellX(int col) { return MARGIN + col * (CELL_W + GAP); }
    int cellY(int row) { return MARGIN + row * (CELL_H + GAP); }

    int[] cellAt(int x, int y) {
        for (int r = 0; r < GameLogic.ROWS; r++)
            for (int c = 0; c < GameLogic.COLS; c++)
                if (x >= cellX(c) && x < cellX(c) + CELL_W && y >= cellY(r) && y < cellY(r) + CELL_H)
                    return new int[]{r, c};
        return null;
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        updateViewTransform();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        drawBackground(g2);
        // 等比缩放绘制棋盘内容
        g2.translate(viewOffX, viewOffY);
        g2.scale(viewScale, viewScale);
        for (int r = 0; r < GameLogic.ROWS; r++)
            for (int c = 0; c < GameLogic.COLS; c++)
                drawCell(g2, r, c);
        drawHighlights(g2);
        for (int r = 0; r < GameLogic.ROWS; r++)
            for (int c = 0; c < GameLogic.COLS; c++)
                drawCard(g2, r, c);
        if (flash > 0f) drawFlash(g2);
    }

    private void drawBackground(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOT));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawCell(Graphics2D g2, int r, int c) {
        int x = cellX(c), y = cellY(r);
        RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, CELL_W, CELL_H, CORNER, CORNER);
        Color base = Z_CELL;
        g2.setPaint(new GradientPaint(x, y, brighten(base, 14), x, y + CELL_H, base));
        g2.fill(rect);
        g2.setColor(LINE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(rect);
        if (game.getCard(r, c) == null) {
            int cx = x + CELL_W / 2, cy = y + CELL_H / 2;
            g2.setColor(new Color(255, 255, 255, 22));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(cx - 8, cy - 8, 16, 16);
        }
    }

    private void drawHighlights(Graphics2D g2) {
        if (game.isGameOver()) return;
        if (hoverRow >= 0) strokeCell(g2, hoverRow, hoverCol, HL_HOVER, 2.5f);
        if (selRow < 0) return;
        glowCell(g2, selRow, selCol, HL_SEL);
        Card sel = game.getCard(selRow, selCol);
        if (sel == null) return;
        CardType st = sel.getType();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = selRow + d[0], nc = selCol + d[1];
            if (nr < 0 || nr >= GameLogic.ROWS || nc < 0 || nc >= GameLogic.COLS) continue;
            Card t = game.getCard(nr, nc);
            if (t == null) {
                strokeCell(g2, nr, nc, HL_MOVE, 3f);
            } else if (t.getOwner() == 0 && t.isFaceUp()) {
                // 目标为已翻开的中立牌（暗牌不显示交互高亮，避免泄露类型）
                CardType tt = t.getType();
                if (tt == CardType.JUSHI && (st == CardType.LANTOU || st == CardType.LANTOUBEIQIANG)) {
                    strokeCell(g2, nr, nc, HL_SWAP, 3f);       // 烂头/烂头背枪与巨石交换
                } else if (isMergePair(st, tt)) {
                    strokeCell(g2, nr, nc, HL_MERGE, 3f);      // 合成
                } else if (st == CardType.LANTOUBEIQIANG && (tt == CardType.MAOMI || tt == CardType.WANGCAI)) {
                    strokeCell(g2, nr, nc, HL_ATK, 3f);        // 烂头背枪杀猫咪/旺财
                }
            } else if (t.getOwner() != sel.getOwner()) {
                strokeCell(g2, nr, nc, HL_ATK, 3f);            // 敌方牌
            }
        }
    }

    /** 是否可合成对：烂头+洋枪 / 小孩+捕虫网 */
    static boolean isMergePair(CardType a, CardType b) {
        return (a == CardType.LANTOU && b.isQiang())
            || (a.isQiang() && b == CardType.LANTOU)
            || (a == CardType.XIAOHAI && b.isBuchong())
            || (a.isBuchong() && b == CardType.XIAOHAI);
    }
    private void drawCard(Graphics2D g2, int r, int c) {
        Card card = game.getCard(r, c);
        if (card == null) return;
        boolean isFlipping = (r == flipRow && c == flipCol);
        float scaleX = 1f;
        if (isFlipping) scaleX = flipPhase < 0.5f ? (1f - flipPhase * 2f) : ((flipPhase - 0.5f) * 2f);
        int x = cellX(c), y = cellY(r), cx = x + CELL_W / 2;
        Graphics2D gs = (Graphics2D) g2.create();
        if (isFlipping && scaleX < 1f) {
            gs.translate(cx, 0);
            gs.scale(Math.max(scaleX, 0.02f), 1f);
            gs.translate(-cx, 0);
        }
        boolean showFace = card.isFaceUp() && !(isFlipping && !flipApplied);
        if (showFace) drawCardFace(gs, card, x, y);
        else drawCardBack(gs, card, x, y);
        gs.dispose();
    }

    private void drawCardFace(Graphics2D g2, Card card, int x, int y) {
        boolean p1 = card.getOwner() == 1;
        boolean merged = card.getType() == CardType.LANTOUBEIQIANG
                      || card.getType() == CardType.BUCHONGXIAOHAI;
        boolean neutral = card.getOwner() == 0;
        Color edge = merged ? EDGE_GLD : (neutral ? EDGE_NTR : (p1 ? EDGE_RED : EDGE_BLU));
        drawShadow(g2, x, y);
        RoundRectangle2D rect = new RoundRectangle2D.Float(x + 2, y + 2, CELL_W - 4, CELL_H - 4, CORNER - 3, CORNER - 3);
        BufferedImage img = imageFor(card);
        if (img != null) {
            g2.setClip(rect);
            g2.drawImage(img, x + 2, y + 2, CELL_W - 4, CELL_H - 4, null);
            int barH = 18;
            g2.setPaint(new GradientPaint(0, y + CELL_H - 2 - barH, new Color(0, 0, 0, 130),
                                          0, y + CELL_H - 2, new Color(0, 0, 0, 190)));
            g2.fillRect(x + 2, y + CELL_H - 2 - barH, CELL_W - 4, barH);
            g2.setClip(null);
            String name = card.getType().getName();
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("微软雅黑", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, x + (CELL_W - fm.stringWidth(name)) / 2,
                          y + CELL_H - 2 - barH / 2 + fm.getAscent() / 2 - 1);
        } else {
            g2.setPaint(new GradientPaint(x, y, brighten(edge, 120), x, y + CELL_H, brighten(edge, 60)));
            g2.fill(rect);
            String name = card.getType().getName();
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("微软雅黑", Font.BOLD, 16));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, x + (CELL_W - fm.stringWidth(name)) / 2, y + CELL_H / 2 + fm.getAscent() / 2);
        }
        g2.setColor(edge);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(rect);
    }

    private void drawCardBack(Graphics2D g2, Card card, int x, int y) {
        drawShadow(g2, x, y);
        RoundRectangle2D rect = new RoundRectangle2D.Float(x + 2, y + 2, CELL_W - 4, CELL_H - 4, CORNER - 3, CORNER - 3);
        BufferedImage img = loadImage("kabei.jpg");
        if (img != null) {
            g2.setClip(rect);
            g2.drawImage(img, x + 2, y + 2, CELL_W - 4, CELL_H - 4, null);
            g2.setClip(null);
        } else {
            Color c1 = new Color(110, 80, 50);
            Color c2 = new Color(70, 50, 30);
            g2.setPaint(new GradientPaint(x, y, c1, x, y + CELL_H, c2));
            g2.fill(rect);
            int cx = x + CELL_W / 2, cy = y + CELL_H / 2, s = 20;
            g2.setColor(new Color(255, 255, 255, 70));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawPolygon(new int[]{cx, cx + s, cx, cx - s}, new int[]{cy - s, cy, cy + s, cy}, 4);
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillOval(cx - 3, cy - 3, 6, 6);
        }
        g2.setColor(EDGE_GLD);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(rect);
    }

    private void drawShadow(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fill(new RoundRectangle2D.Float(x + 4, y + 6, CELL_W - 4, CELL_H - 4, CORNER - 3, CORNER - 3));
    }

    private void drawFlash(Graphics2D g2) {
        if (flashRow < 0) return;
        g2.setColor(new Color(255, 255, 255, (int) (180 * flash)));
        g2.fill(new RoundRectangle2D.Float(cellX(flashCol), cellY(flashRow), CELL_W, CELL_H, CORNER, CORNER));
    }

    private void strokeCell(Graphics2D g2, int r, int c, Color color, float width) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new RoundRectangle2D.Float(cellX(c) + 1, cellY(r) + 1, CELL_W - 2, CELL_H - 2, CORNER, CORNER));
    }

    private void glowCell(Graphics2D g2, int r, int c, Color color) {
        int x = cellX(c), y = cellY(r);
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g2.setStroke(new BasicStroke(i * 3f));
            g2.draw(new RoundRectangle2D.Float(x + 1, y + 1, CELL_W - 2, CELL_H - 2, CORNER, CORNER));
        }
        strokeCell(g2, r, c, color, 3f);
    }

    private static Color brighten(Color c, int amt) {
        return new Color(Math.min(255, c.getRed() + amt),
                         Math.min(255, c.getGreen() + amt),
                         Math.min(255, c.getBlue() + amt));
    }
}