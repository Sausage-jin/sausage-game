package com.doudizhu.ui;

import com.doudizhu.model.Card;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * 扑克牌绘制器：使用 Graphics2D 精细绘制牌面、牌背与阴影。
 */
public class CardPainter {

    public static final int CARD_W = 68;    // 与图片 2:3 比例一致，避免拉伸
    public static final int CARD_H = 102;
    public static final int SMALL_W = 43;
    public static final int SMALL_H = 64;
    public static final int OVERLAP = 34;   // 手牌水平重叠
    public static final int SELECT_OFFSET = 22;

    private static final float CORNER = 10f;
    private static final Color CARD_FACE = new Color(252, 252, 248);
    private static final Color CARD_EDGE = new Color(190, 190, 185);
    private static final Color SHADOW = new Color(0, 0, 0, 70);

    // ==================== 图片资源 ====================

    /** 预缩放后的图片缓存（key=文件名） */
    private static final ConcurrentHashMap<String, BufferedImage> IMG_CACHE = new ConcurrentHashMap<>();
    /** 已解析的图片目录；null 表示未找到，使用自绘回退 */
    private static volatile String imgDir;
    /** 图片预缩放目标高度（2 倍最大显示高度，兼顾清晰与内存） */
    private static final int IMG_TARGET_H = 210;

    static {
        imgDir = resolveImageDir();
    }

    /** 探测图片目录（兼容不同启动位置） */
    private static String resolveImageDir() {
        String[] candidates = {
                "../image", "image",                       // 工作目录 = doudizhu / 工作区根
                "../image/poker_deck", "image/poker_deck"  // poker_deck 子目录（内容相同）
        };
        for (String dir : candidates) {
            if (new File(dir, "Card_Back.jpg").exists()) return dir;
        }
        return null;
    }

    /** 加载并预缩放图片到缓存；失败返回 null（回退自绘） */
    private static BufferedImage loadImage(String name) {
        if (imgDir == null) return null;
        BufferedImage cached = IMG_CACHE.get(name);
        if (cached != null) return cached;
        try {
            File f = new File(imgDir, name);
            if (!f.exists()) return null;
            BufferedImage full = ImageIO.read(f);
            if (full == null) return null;
            int tw = Math.max(1, Math.round(IMG_TARGET_H * full.getWidth() / (float) full.getHeight()));
            // SCALE_SMOOTH 对 7 倍降采样采用区域平均，质量远优于单步双线性
            Image smooth = full.getScaledInstance(tw, IMG_TARGET_H, Image.SCALE_SMOOTH);
            BufferedImage scaled = new BufferedImage(tw, IMG_TARGET_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(smooth, 0, 0, null);
            g2.dispose();
            BufferedImage prev = IMG_CACHE.putIfAbsent(name, scaled);
            return prev != null ? prev : scaled;
        } catch (IOException e) {
            return null;
        }
    }

    /** 异步并行预加载全部 81 张图片，避免游戏中首次绘制卡顿 */
    public static void preloadAsync() {
        List<String> names = new ArrayList<>();
        String[] ranks = {"Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Jack", "Queen", "King", "Ace", "Two"};
        String[] suits = {"Spades", "Hearts", "Diamonds", "Clubs", "Circles", "Triangles"};
        for (String r : ranks) {
            for (String s : suits) {
                names.add(r + "_of_" + s + ".jpg");
            }
        }
        names.add("Joker_Red.jpg");
        names.add("Joker_Black.jpg");
        names.add("Card_Back.jpg");
        AtomicInteger cursor = new AtomicInteger();
        int workers = Math.min(4, names.size());
        for (int t = 0; t < workers; t++) {
            Thread worker = new Thread(() -> {
                int i;
                while ((i = cursor.getAndIncrement()) < names.size()) {
                    loadImage(names.get(i));
                }
            }, "CardImagePreload-" + t);
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** Card → 图片文件名（如 Ace_of_Hearts.jpg / Joker_Red.jpg） */
    private static String imageFileName(Card card) {
        if (card.isJoker()) {
            return card.getRank() == 17 ? "Joker_Red.jpg" : "Joker_Black.jpg";
        }
        return rankToName(card.getRank()) + "_of_" + suitToName(card.getSuit()) + ".jpg";
    }

    private static String rankToName(int rank) {
        switch (rank) {
            case 3: return "Three";   case 4: return "Four";    case 5: return "Five";
            case 6: return "Six";     case 7: return "Seven";   case 8: return "Eight";
            case 9: return "Nine";    case 10: return "Ten";    case 11: return "Jack";
            case 12: return "Queen";  case 13: return "King";   case 14: return "Ace";
            case 15: return "Two";    default: return "";
        }
    }

    private static String suitToName(Card.Suit suit) {
        switch (suit) {
            case SPADE: return "Spades";
            case HEART: return "Hearts";
            case DIAMOND: return "Diamonds";
            case CLUB: return "Clubs";
            case CIRCLE: return "Circles";
            case TRIANGLE: return "Triangles";
            default: return "";
        }
    }

    /** 绘制正面扑克牌 */
    public static void drawCard(Graphics2D g, Card card, int x, int y) {
        drawCard(g, card, x, y, CARD_W, CARD_H, 1f);
    }

    /** 绘制小尺寸正面牌（底牌展示等） */
    public static void drawSmallCard(Graphics2D g, Card card, int x, int y) {
        drawCard(g, card, x, y, SMALL_W, SMALL_H, 0.65f);
    }

    private static void drawCard(Graphics2D g, Card card, int x, int y, int w, int h, float scale) {
        RenderingHints old = setup(g);
        BufferedImage img = loadImage(imageFileName(card));
        if (img != null) {
            // 阴影
            g.setColor(SHADOW);
            g.fill(new RoundRectangle2D.Float(x + 2, y + 3, w, h, CORNER, CORNER));
            // 圆角裁剪绘制图片牌面
            Shape oldClip = g.getClip();
            g.setClip(new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER));
            g.drawImage(img, x, y, w, h, null);
            g.setClip(oldClip);
            // 细边框增强轮廓
            g.setColor(CARD_EDGE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER));
        } else {
            // 回退：自绘牌面
            RoundRectangle2D shadowRect = new RoundRectangle2D.Float(x + 2, y + 3, w, h, CORNER, CORNER);
            g.setColor(SHADOW);
            g.fill(shadowRect);
            RoundRectangle2D cardRect = new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER);
            GradientParser face = new GradientParser(x, y, w, h);
            g.setPaint(face);
            g.fill(cardRect);
            g.setColor(CARD_EDGE);
            g.setStroke(new BasicStroke(1f));
            g.draw(cardRect);
            // 内描边（精致感）
            RoundRectangle2D inner = new RoundRectangle2D.Float(x + 3, y + 3, w - 6, h - 6, CORNER - 2, CORNER - 2);
            g.setColor(new Color(230, 230, 225, 120));
            g.draw(inner);

            Color ink = card.getSuit().color;
            if (card.isJoker()) {
                drawJokerFace(g, card, x, y, w, h, scale);
            } else {
                drawStandardFace(g, card, x, y, w, h, scale, ink);
            }
        }
        restore(g, old);
    }

    private static void drawStandardFace(Graphics2D g, Card card, int x, int y,
                                         int w, int h, float scale, Color ink) {
        String rankTxt = card.getCornerText();
        String suitSym = card.getSuit().symbol;
        float cornerSize = 15f * scale;
        float suitSize = 12f * scale;
        float centerSize = 30f * scale;

        // 左上角：点数 + 花色
        g.setColor(ink);
        g.setFont(g.getFont().deriveFont(Font.BOLD, cornerSize));
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (int)(6 * scale);
        int ty = y + (int)(6 * scale) + fm.getAscent();
        g.drawString(rankTxt, tx, ty);
        g.setFont(g.getFont().deriveFont(Font.BOLD, suitSize));
        g.drawString(suitSym, tx + 1, ty + (int)(13 * scale));

        // 中央大花色
        g.setFont(g.getFont().deriveFont(Font.BOLD, centerSize));
        FontMetrics fm2 = g.getFontMetrics();
        int cx = x + w / 2 - fm2.stringWidth(suitSym) / 2;
        int cy = y + h / 2 + fm2.getAscent() / 2 - 2;
        g.drawString(suitSym, cx, cy);

        // 右下角（旋转180°）
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(Math.PI, x + w / 2.0, y + h / 2.0);
        g2.setColor(ink);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, cornerSize));
        FontMetrics fm3 = g2.getFontMetrics();
        int tx2 = x + (int)(6 * scale);
        int ty2 = y + (int)(6 * scale) + fm3.getAscent();
        g2.drawString(rankTxt, tx2, ty2);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, suitSize));
        g2.drawString(suitSym, tx2 + 1, ty2 + (int)(13 * scale));
        g2.dispose();
    }

    private static void drawJokerFace(Graphics2D g, Card card, int x, int y,
                                      int w, int h, float scale) {
        boolean big = card.getRank() == 17;
        Color main = big ? new Color(200, 30, 40) : new Color(50, 50, 55);
        Color accent = big ? new Color(255, 215, 0) : new Color(160, 160, 170);

        // 顶部 "JOKER" 竖排
        g.setColor(main);
        float fs = 11f * scale;
        g.setFont(g.getFont().deriveFont(Font.BOLD, fs));
        String label = big ? "BIG" : "SMALL";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + w / 2 - fm.stringWidth(label) / 2, y + (int)(16 * scale));

        // 中央大五角星
        float starSize = 26f * scale;
        int cx = x + w / 2;
        int cy = y + h / 2 + (int)(4 * scale);
        drawStar(g, cx, cy, starSize, main, accent);

        // 底部 "王"
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f * scale));
        String wang = big ? "大王" : "小王";
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(wang, x + w / 2 - fm2.stringWidth(wang) / 2, y + h - (int)(8 * scale));
    }

    /** 绘制五角星 */
    public static void drawStar(Graphics2D g, int cx, int cy, float size, Color fill, Color outline) {
        Polygon star = new Polygon();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? size : size * 0.42;
            star.addPoint((int)(cx + r * Math.cos(angle)), (int)(cy - r * Math.sin(angle)));
        }
        g.setColor(fill);
        g.fill(star);
        g.setColor(outline);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(star);
    }

    /** 绘制牌背 */
    public static void drawCardBack(Graphics2D g, int x, int y) {
        drawCardBack(g, x, y, CARD_W, CARD_H);
    }

    public static void drawCardBack(Graphics2D g, int x, int y, int w, int h) {
        RenderingHints old = setup(g);
        BufferedImage img = loadImage("Card_Back.png");
        if (img != null) {
            // 阴影
            g.setColor(SHADOW);
            g.fill(new RoundRectangle2D.Float(x + 2, y + 3, w, h, CORNER, CORNER));
            // 圆角裁剪绘制图片牌背
            Shape oldClip = g.getClip();
            g.setClip(new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER));
            g.drawImage(img, x, y, w, h, null);
            g.setClip(oldClip);
            g.setColor(CARD_EDGE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER));
        } else {
            // 回退：自绘牌背
            // 阴影
            g.setColor(SHADOW);
            g.fill(new RoundRectangle2D.Float(x + 2, y + 3, w, h, CORNER, CORNER));
            // 主体渐变（深红）
            GradientParser gp = new GradientParser(x, y, w, h,
                    new Color(150, 30, 40), new Color(90, 15, 25));
            g.setPaint(gp);
            g.fill(new RoundRectangle2D.Float(x, y, w, h, CORNER, CORNER));
            // 金色边框
            g.setColor(new Color(255, 215, 0, 200));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3, h - 3, CORNER - 1, CORNER - 1));
            // 内框
            g.setColor(new Color(255, 215, 0, 100));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(x + 5, y + 5, w - 10, h - 10, CORNER - 3, CORNER - 3));
            // 中央菱形装饰
            int cx = x + w / 2, cy = y + h / 2;
            int ds = Math.min(w, h) / 5;
            Polygon diamond = new Polygon();
            diamond.addPoint(cx, cy - ds);
            diamond.addPoint(cx + ds, cy);
            diamond.addPoint(cx, cy + ds);
            diamond.addPoint(cx - ds, cy);
            g.setColor(new Color(255, 215, 0, 180));
            g.fill(diamond);
            g.setColor(new Color(255, 240, 180));
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float)(ds * 0.9)));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("斗", cx - fm.stringWidth("斗") / 2, cy + fm.getAscent() / 2 - 1);
        }
        restore(g, old);
    }

    /** 绘制迷你牌背（对手手牌） */
    public static void drawMiniBack(Graphics2D g, int x, int y) {
        drawCardBack(g, x, y, 26, 36);
    }

    // ==================== 工具 ====================

    private static RenderingHints setup(Graphics2D g) {
        RenderingHints old = (RenderingHints) g.getRenderingHints().clone();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return old;
    }

    private static void restore(Graphics2D g, RenderingHints old) {
        g.setRenderingHints(old);
    }

    /** 牌面渐变（内部类避免与 GradientPaint 命名冲突） */
    private static class GradientParser extends GradientPaint {
        GradientParser(int x, int y, int w, int h) {
            super(x, y, new Color(255, 255, 252), x, y + h, CARD_FACE);
        }
        GradientParser(int x, int y, int w, int h, Color c1, Color c2) {
            super(x, y, c1, x, y + h, c2);
        }
    }
}