package com.doudizhu.ui;

import com.doudizhu.engine.FourPlayerEngine;
import com.doudizhu.engine.GameEngine;
import com.doudizhu.engine.IGameController;
import com.doudizhu.engine.RuleEngine;
import com.doudizhu.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

/**
 * 游戏主面板：全自绘精美界面。
 * 布局：左侧上家 / 右侧下家 / 顶部底牌 / 中央出牌区 / 底部手牌与按钮。
 */
public class GamePanel extends JPanel implements GameEngine.GameListener {

    private final IGameController engine;
    private final boolean multiplayer;  // 是否联机模式（隐藏提示按钮）
    private final boolean fourPlayer;   // 是否四人 2v2 模式
    private Runnable onBackToHome;      // 返回主页回调
    private final Set<Card> selected = new LinkedHashSet<>();
    private final Map<Integer, CardPlay> lastPlays = new HashMap<>();
    private final Map<Integer, String> passLabels = new HashMap<>();
    private final Map<Integer, Boolean> bidShown = new HashMap<>();
    private final Map<Integer, Boolean> doubleShown = new HashMap<>();   // 加倍结果
    private final Map<Integer, Card> guessShown = new HashMap<>();       // 四人：每人亮出的牌
    private int[] teams = null;          // 四人：分组结果
    private int scoreDelta = 0;   // 本局积分变化
    private String gameDetail = "";  // 结算明细

    private String centerMessage = "";
    private boolean showGameOver = false;
    private String gameOverText = "";
    private String gameOverSubText = "";
    private int hoverCardIdx = -1;
    // 音效状态
    private int lastNonPassPlayer = -1;   // 最后一个出牌（非过牌）的玩家，用于判断压牌
    private final Random soundRand = new Random();
    private final int[] playedRankCount = new int[18];  // 已出牌按 rank 计数（index=rank, 3~17），用于记牌器

    // 按钮区域（自绘）
    private final List<GameButton> buttons = new ArrayList<>();

    private static final int W = 1200, H = 780;
    private static final Color TABLE_DARK = new Color(12, 70, 40);
    private static final Color TABLE_LIGHT = new Color(28, 110, 65);
    private static final Color GOLD = new Color(255, 215, 0);

    /** 桌布背景图（image/card_table_white.png），加载失败回退自绘绿呢面 */
    private static BufferedImage tableImg;
    private static boolean tableImgTried = false;

    /** 懒加载桌布图（按窗口尺寸预缩放，失败返回 null） */
    private static BufferedImage loadTableImage() {
        if (tableImgTried) return tableImg;
        tableImgTried = true;
        String[] candidates = {"image/card_table_white.png", "../image/card_table_white.png"};
        for (String p : candidates) {
            File f = new File(p);
            if (!f.exists()) continue;
            try {
                BufferedImage full = ImageIO.read(f);
                if (full == null) continue;
                Image smooth = full.getScaledInstance(W, H, Image.SCALE_SMOOTH);
                BufferedImage scaled = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = scaled.createGraphics();
                g2.drawImage(smooth, 0, 0, null);
                g2.dispose();
                tableImg = scaled;
                break;
            } catch (IOException e) { /* 尝试下一个候选 */ }
        }
        return tableImg;
    }

    /** 头像图缓存：key=文件路径，value=预缩放方形图（80×80） */
    private static final java.util.Map<String, BufferedImage> AVATAR_CACHE = new java.util.HashMap<>();
    private static final int AVATAR_SIZE = 80;

    /** 加载头像图（文件系统 → classpath），预缩放为方形，失败返回 null */
    private static BufferedImage loadAvatarImage(String path) {
        if (path == null) return null;
        BufferedImage cached = AVATAR_CACHE.get(path);
        if (cached != null) return cached;
        // 文件系统
        String[] candidates = {path, "../" + path};
        for (String p : candidates) {
            File f = new File(p);
            if (!f.exists()) continue;
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    BufferedImage scaled = scaleSquare(img, AVATAR_SIZE);
                    AVATAR_CACHE.put(path, scaled);
                    return scaled;
                }
            } catch (IOException ignored) { }
        }
        // classpath（jar 内运行）
        try {
            URL url = GamePanel.class.getResource("/" + path);
            if (url != null) {
                BufferedImage img = ImageIO.read(url);
                if (img != null) {
                    BufferedImage scaled = scaleSquare(img, AVATAR_SIZE);
                    AVATAR_CACHE.put(path, scaled);
                    return scaled;
                }
            }
        } catch (Exception ignored) { }
        AVATAR_CACHE.put(path, null);   // 标记已尝试，避免重复加载
        return null;
    }

    /** 将图片等比缩放并居中裁剪为 size×size 方形 */
    private static BufferedImage scaleSquare(BufferedImage src, int size) {
        int sw = src.getWidth(), sh = src.getHeight();
        int side = Math.min(sw, sh);
        BufferedImage crop = src.getSubimage((sw - side) / 2, (sh - side) / 2, side, side);
        Image smooth = crop.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.drawImage(smooth, 0, 0, null);
        g2.dispose();
        return out;
    }

    /** 按玩家 ID 获取头像图：玩家0=用户头像，其余=人机头像(renji.png) */
    private static BufferedImage getAvatarFor(int playerId) {
        if (playerId == 0) {
            return loadAvatarImage(com.doudizhu.Main.userAvatarPath);
        }
        return loadAvatarImage("image/renji.png");
    }

    static class GameButton {
        String text;
        Rectangle rect;
        Color normalBg, hoverBg, pressedBg;
        boolean hover, pressed, visible, enabled;
        Runnable action;

        GameButton(String text, Color bg, Runnable action) {
            this.text = text;
            this.normalBg = bg;
            this.hoverBg = bg.brighter();
            this.pressedBg = bg.darker();
            this.action = action;
            this.visible = false;
            this.enabled = true;
            this.rect = new Rectangle();
        }
    }

    public GamePanel(IGameController engine, boolean multiplayer) {
        this.engine = engine;
        this.multiplayer = multiplayer;
        this.fourPlayer = engine.getPlayers().size() == 4;
        engine.addListener(this);
        setPreferredSize(new Dimension(W, H));
        setLayout(null);
        setFocusable(true);

        // 创建按钮
        buttons.add(makeBtn("出 牌", new Color(200, 60, 50), () -> doPlay()));
        buttons.add(makeBtn("不 要", new Color(110, 110, 120), () -> engine.submitPlay(null)));
        buttons.add(makeBtn("提 示", new Color(50, 130, 200), () -> doHint()));
        buttons.add(makeBtn("叫地主", new Color(220, 160, 40), () -> engine.humanBid(true)));
        buttons.add(makeBtn("不 叫", new Color(110, 110, 120), () -> engine.humanBid(false)));
        buttons.add(makeBtn("加 倍", new Color(220, 80, 40), () -> engine.humanDouble(true)));
        buttons.add(makeBtn("不加倍", new Color(110, 110, 120), () -> engine.humanDouble(false)));
        buttons.add(makeBtn("再来一局", new Color(40, 160, 90), () -> engine.startNewGame()));
        buttons.add(makeBtn("返回主页", new Color(110, 110, 120), () -> {
            if (onBackToHome != null) onBackToHome.run();
        }));
        buttons.add(makeBtn("亮 牌", new Color(220, 160, 40), () -> doGuess()));  // idx=9 四人猜牌用

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleClick(e.getX(), e.getY()); }
            @Override public void mouseMoved(MouseEvent e) { handleMove(e.getX(), e.getY()); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // ESC 键：弹出游戏菜单
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escMenu");
        getActionMap().put("escMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showEscMenu(); }
        });
    }

    /** ESC 菜单：重新开始 / 查看规则 / 游戏设置 / 返回主页 */
    private void showEscMenu() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        String[] opts = {"重新开始", "查看规则", "游戏设置", "返回主页"};
        int c = uiKitOptions(owner, "游戏菜单", opts);
        if (c == 0) {
            engine.startNewGame();
        } else if (c == 1) {
            showRules();
        } else if (c == 2) {
            uiKitSettings(owner);
        } else if (c == 3) {
            if (onBackToHome != null) onBackToHome.run();
        }
        requestFocusInWindow();
    }

    /** 反射调用默认包 UiKit.showVerticalOptions 统一竖排选项弹窗，返回索引（-1=关闭） */
    private int uiKitOptions(Window owner, String title, String[] options) {
        try {
            Class<?> uiKit = Class.forName("UiKit");
            Object r = uiKit.getMethod("showVerticalOptions", Window.class, String.class, String.class, String[].class)
                    .invoke(null, owner, title, null, options);
            return r instanceof Integer ? (Integer) r : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 反射调用默认包 UiKit.showGameSettings 统一游戏设置弹窗 */
    private void uiKitSettings(Window owner) {
        try {
            Class.forName("UiKit").getMethod("showGameSettings", Window.class).invoke(null, owner);
        } catch (Exception e) { }
    }

    /** 斗地主规则弹窗 */
    private void showRules() {
        String rules = "【斗地主规则】\n\n"
                + "三人游戏，一副54张牌，每人17张，留3张底牌\n"
                + "叫地主阶段：可叫地主或不叫，地主获得3张底牌\n"
                + "地主先出牌，按逆时针顺序轮流出牌\n\n"
                + "牌型大小：火箭（双王）> 炸弹 > 一般牌型\n"
                + "一般牌型：单牌、对子、三张、三带一/二\n"
                + "          顺子（5+）、连对（3+对）、飞机\n"
                + "          四带二等\n\n"
                + "胜负：地主先出完牌则地主胜，任一农民先出完则农民胜\n"
                + "春天：地主完胜（农民一张未出），积分×2\n"
                + "反春天：地主只出一手牌即被农民打完，积分×2\n\n"
                + "联机模式支持人机玩家补位";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setForeground(new Color(228, 231, 240));
        ta.setBackground(new Color(24, 27, 38));
        ta.setCaretColor(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(420, 340));
        JOptionPane.showMessageDialog(this, sp, "斗地主规则", JOptionPane.INFORMATION_MESSAGE);
    }

    public void setOnBackToHome(Runnable r) { this.onBackToHome = r; }

    private GameButton makeBtn(String text, Color bg, Runnable action) {
        GameButton b = new GameButton(text, bg, action);
        return b;
    }

    // ==================== 交互处理 ====================

    private void handleClick(int mx, int my) {
        // 按钮优先
        for (GameButton btn : buttons) {
            if (btn.visible && btn.enabled && btn.rect.contains(mx, my)) {
                btn.action.run();
                repaint();
                return;
            }
        }
        // 手牌选择（仅出牌阶段且轮到自己）
        if (engine.getState() == GameEngine.State.PLAYING
                && engine.getCurrentPlayer() == 0) {
            int idx = cardAt(mx, my);
            if (idx >= 0) {
                Card c = engine.getPlayer(0).getHand().get(idx);
                if (selected.contains(c)) selected.remove(c);
                else selected.add(c);
                repaint();
            }
        }
        // 猜牌阶段：只选一张牌亮出
        else if (engine.getState() == GameEngine.State.GUESSING
                && engine.getCurrentPlayer() == 0) {
            int idx = cardAt(mx, my);
            if (idx >= 0) {
                Card c = engine.getPlayer(0).getHand().get(idx);
                selected.clear();
                selected.add(c);
                repaint();
            }
        }
    }

    private void handleMove(int mx, int my) {
        int old = hoverCardIdx;
        hoverCardIdx = -1;
        for (GameButton btn : buttons) {
            btn.hover = btn.visible && btn.enabled && btn.rect.contains(mx, my);
        }
        if (engine.getState() == GameEngine.State.PLAYING
                && engine.getCurrentPlayer() == 0) {
            hoverCardIdx = cardAt(mx, my);
        }
        if (engine.getState() == GameEngine.State.GUESSING
                && engine.getCurrentPlayer() == 0) {
            hoverCardIdx = cardAt(mx, my);
        }
        if (old != hoverCardIdx || buttons.stream().anyMatch(b -> b.hover)) repaint();
    }

    /** 判断鼠标点击了哪张手牌（从右往左检测，右侧牌在上层） */
    private int cardAt(int mx, int my) {
        List<Card> hand = engine.getPlayer(0).getHand();
        if (hand.isEmpty()) return -1;
        int[] pos = handLayout(hand);
        int startX = pos[0], y = pos[1];
        for (int i = hand.size() - 1; i >= 0; i--) {
            int cx = startX + i * CardPainter.OVERLAP;
            boolean isSel = selected.contains(hand.get(i));
            int cy = y - (isSel ? CardPainter.SELECT_OFFSET : 0);
            if (mx >= cx && mx <= cx + CardPainter.CARD_W
                    && my >= cy && my <= cy + CardPainter.CARD_H) {
                return i;
            }
        }
        return -1;
    }

    /** 手牌起始 X 与 Y */
    private int[] handLayout(List<Card> hand) {
        int totalW = (hand.size() - 1) * CardPainter.OVERLAP + CardPainter.CARD_W;
        int startX = (W - totalW) / 2;
        int y = H - CardPainter.CARD_H - 58;
        return new int[]{startX, y};
    }

    // ==================== 出牌操作 ====================

    private void doPlay() {
        if (selected.isEmpty()) return;
        List<Card> cards = new ArrayList<>(selected);
        CardPlay play = RuleEngine.analyze(cards);
        if (play == null || play.getType() == CardType.INVALID) {
            centerMessage = "牌型不合法！";
            repaint();
            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override public void run() {
                    SwingUtilities.invokeLater(() -> { centerMessage = ""; repaint(); });
                }
            }, 1200);
            return;
        }
        // 跟牌时校验能否压过
        CardPlay last = engine.getLastPlay();
        if (last != null && engine.getLastPlayer() != 0 && !play.canBeat(last)) {
            centerMessage = "压不过上家！";
            repaint();
            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override public void run() {
                    SwingUtilities.invokeLater(() -> { centerMessage = ""; repaint(); });
                }
            }, 1200);
            return;
        }
        selected.clear();
        engine.submitPlay(play);
    }

    private void doHint() {
        CardPlay hint = engine.getHint(new ArrayList<>(selected));
        if (hint == null) {
            centerMessage = "没有能出的牌";
            repaint();
            return;
        }
        selected.clear();
        selected.addAll(hint.getCards());
        repaint();
    }

    /** 四人猜牌：亮出选中的一张牌 */
    private void doGuess() {
        if (engine.getState() != GameEngine.State.GUESSING
                || engine.getCurrentPlayer() != 0) return;
        if (selected.size() != 1) {
            centerMessage = "请选择一张牌亮出";
            repaint();
            return;
        }
        Card c = selected.iterator().next();
        selected.clear();
        if (engine instanceof FourPlayerEngine) {
            ((FourPlayerEngine) engine).submitGuess(c);
        }
    }

    // ==================== 引擎回调 ====================

    @Override public void onStateChanged(GameEngine.State state) {
        if (state == GameEngine.State.BIDDING) {
            selected.clear(); lastPlays.clear(); passLabels.clear();
            bidShown.clear(); doubleShown.clear(); showGameOver = false;
            centerMessage = ""; scoreDelta = 0; gameDetail = "";
            java.util.Arrays.fill(playedRankCount, 0);
        }
        if (state == GameEngine.State.GUESSING) {
            selected.clear(); lastPlays.clear(); passLabels.clear();
            bidShown.clear(); doubleShown.clear(); showGameOver = false;
            guessShown.clear(); teams = null;
            centerMessage = ""; scoreDelta = 0; gameDetail = "";
            java.util.Arrays.fill(playedRankCount, 0);
        }
        updateButtons();
        repaint();
    }

    @Override public void onHandUpdated(int playerId) {
        if (playerId == 0) { selected.clear(); }
        repaint();
    }

    @Override public void onBidTurn(int playerId) {
        centerMessage = playerId == 0 ? "请选择是否叫地主" :
                engine.getPlayer(playerId).getName() + " 正在考虑...";
        updateButtons();
        repaint();
    }

    @Override public void onBidResult(int playerId, boolean called) {
        bidShown.put(playerId, called);
        centerMessage = engine.getPlayer(playerId).getName() + (called ? " 叫地主！" : " 不叫");
        SoundPlayer.play(called ? "01_call_landlord.mp3" : "02_pass_bid.mp3");
        repaint();
    }

    @Override public void onLandlordDecided(int landlordId) {
        String name = engine.getPlayer(landlordId).getName();
        centerMessage = name + " 成为地主！";
        lastPlays.clear(); passLabels.clear();
        updateButtons();
        repaint();
    }

    @Override public void onDoubleTurn(int playerId) {
        centerMessage = playerId == 0 ? "是否加倍？" :
                engine.getPlayer(playerId).getName() + " 考虑加倍...";
        updateButtons();
        repaint();
    }

    @Override public void onDoubleResult(int playerId, boolean doubled) {
        doubleShown.put(playerId, doubled);
        centerMessage = engine.getPlayer(playerId).getName() + (doubled ? " 加倍！" : " 不加倍");
        SoundPlayer.play(doubled ? "05_double.mp3" : "06_no_double.mp3");
        repaint();
    }

    @Override public void onPlayTurn(int playerId) {
        // 新一轮首出（上一手已被两家跳过清空）→ 重置压牌追踪
        if (engine.getLastPlay() == null) lastNonPassPlayer = -1;
        if (playerId != 0) {
            centerMessage = engine.getPlayer(playerId).getName() + " 出牌中...";
        } else {
            boolean leading = engine.getLastPlay() == null || engine.getLastPlayer() == 0;
            centerMessage = leading ? "请出牌" : "请跟牌或选择不要";
        }
        // 清除该玩家之前的"不要"标记
        passLabels.remove(playerId);
        updateButtons();
        repaint();
    }

    @Override public void onPlay(int playerId, CardPlay play) {
        lastPlays.put(playerId, play);
        passLabels.remove(playerId);
        centerMessage = "";
        for (Card c : play.getCards()) playedRankCount[c.getRank()]++;   // 记牌器：累计已出牌
        playPlaySound(playerId, play);
        repaint();
    }

    /** 出牌音效：炸弹/王炸始终播特效音；压牌播压牌语音；首出播牌型语音 */
    private void playPlaySound(int playerId, CardPlay play) {
        CardType t = play.getType();
        boolean covering = lastNonPassPlayer != -1 && lastNonPassPlayer != playerId;
        lastNonPassPlayer = playerId;
        if (t == CardType.ROCKET) {
            SoundPlayer.play("21_rocket.mp3");
            return;
        }
        if (t == CardType.BOMB || t == CardType.SIX_BOMB) {
            SoundPlayer.play("20_bomb.mp3");
            return;
        }
        if (covering) {
            String[] covers = {"25_bigger.mp3", "26_cover.mp3", "27_crush.mp3"};
            SoundPlayer.play(covers[soundRand.nextInt(covers.length)]);
            return;
        }
        String file;
        switch (t) {
            case SINGLE:          file = "10_single.mp3"; break;
            case PAIR:            file = "11_pair.mp3"; break;
            case TRIPLE:          file = "12_three_of_a_kind.mp3"; break;
            case TRIPLE_ONE:      file = "13_three_with_one.mp3"; break;
            case TRIPLE_TWO:      file = "14_three_with_two.mp3"; break;
            case STRAIGHT:        file = "15_straight.mp3"; break;
            case DOUBLE_STRAIGHT: file = "16_consecutive_pairs.mp3"; break;
            case PLANE:           file = "17_airplane.mp3"; break;
            case PLANE_ONE:
            case PLANE_TWO:       file = "18_airplane_with_wings.mp3"; break;
            case FOUR_TWO:
            case FOUR_TWO_PAIR:   file = "19_four_with_two.mp3"; break;
            default:              return;
        }
        SoundPlayer.play(file);
    }

    @Override public void onPass(int playerId) {
        passLabels.put(playerId, "不要");
        lastPlays.remove(playerId);
        centerMessage = "";
        String[] passes = {"22_pass.mp3", "23_no_play.mp3", "24_cannot_beat.mp3"};
        SoundPlayer.play(passes[soundRand.nextInt(passes.length)]);
        repaint();
    }

    // ==================== 四人模式回调 ====================

    @Override public void onGuessTurn(int playerId) {
        centerMessage = playerId == 0 ? "请选一张牌亮出（大牌分组）" :
                engine.getPlayer(playerId).getName() + " 正在选牌...";
        updateButtons();
        repaint();
    }

    @Override public void onGuessResult(int playerId, Card card) {
        guessShown.put(playerId, card);
        centerMessage = engine.getPlayer(playerId).getName() + " 亮出 " + card.getDisplayText();
        repaint();
    }

    @Override public void onTeamsDecided(int[] teams) {
        this.teams = teams.clone();
        StringBuilder big = new StringBuilder(), small = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String name = engine.getPlayer(i).getName();
            if (teams[i] == 0) big.append(name).append(" ");
            else small.append(name).append(" ");
        }
        centerMessage = "大牌组: " + big + " 小牌组: " + small;
        lastPlays.clear(); passLabels.clear();
        updateButtons();
        repaint();
    }

    @Override public void onGameOverTeam(int winnerTeam, boolean bigTeamWon, int multiplier) {
        // 显示由 onGameOver 统一处理
    }

    @Override public void onGameOver(int winnerId, boolean landlordWon,
                                      int multiplier, int baseScore, boolean spring) {
        Player me = engine.getPlayer(0);
        boolean iWon;
        if (fourPlayer && teams != null) {
            iWon = (teams[0] == 0) == landlordWon;   // 四人模式：按分组判断（landlordWon=大牌组胜）
        } else {
            iWon = (me.getRole() == Player.Role.LANDLORD) == landlordWon;
        }
        showGameOver = true;
        gameOverText = iWon ? "胜 利！" : "失 败";
        if (spring) gameOverText += (landlordWon ? "  春天！" : "  反春天！");

        // 计算本局积分变化
        int total = baseScore * multiplier;
        boolean amLandlord = me.getRole() == Player.Role.LANDLORD;
        if (iWon) {
            scoreDelta = amLandlord ? total * 2 : total;
        } else {
            scoreDelta = amLandlord ? -total * 2 : -total;
        }

        // 明细
        StringBuilder sb = new StringBuilder();
        sb.append("底分 ").append(baseScore);
        sb.append("  ·  倍数 ×").append(multiplier);
        if (spring) sb.append("  ·  春天×2");
        sb.append("  ·  ").append(iWon ? "+" : "").append(scoreDelta).append("分");
        gameDetail = sb.toString();

        gameOverSubText = String.format("%s获胜 · %s",
                landlordWon ? "地主" : "农民", gameDetail);
        // 结算音效：春天/反春天先播，2.6 秒后接胜利/失败语音
        if (spring) {
            SoundPlayer.play(landlordWon ? "30_spring.mp3" : "31_anti_spring.mp3");
            SoundPlayer.playDelayed(iWon ? "28_i_win.mp3" : "29_you_lose.mp3", 2600);
        } else {
            SoundPlayer.play(iWon ? "28_i_win.mp3" : "29_you_lose.mp3");
        }
        updateButtons();
        repaint();
    }

    /** 根据状态刷新按钮可见性 */
    private void updateButtons() {
        GameEngine.State st = engine.getState();
        for (GameButton b : buttons) b.visible = false;
        if (st == GameEngine.State.BIDDING && engine.getCurrentPlayer() == 0) {
            buttons.get(3).visible = true; // 叫地主
            buttons.get(4).visible = true; // 不叫
        } else if (st == GameEngine.State.DOUBLING && engine.getCurrentPlayer() == 0) {
            buttons.get(5).visible = true; // 加倍
            buttons.get(6).visible = true; // 不加倍
        } else if (st == GameEngine.State.GUESSING && engine.getCurrentPlayer() == 0) {
            buttons.get(9).visible = true; // 亮牌（四人猜牌）
        } else if (st == GameEngine.State.PLAYING && engine.getCurrentPlayer() == 0) {
            buttons.get(0).visible = true; // 出牌
            buttons.get(1).visible = true; // 不要
            if (!multiplayer) buttons.get(2).visible = true; // 提示（联机模式隐藏）
            // 首出时隐藏"不要"
            if (engine.getLastPlay() == null || engine.getLastPlayer() == 0) {
                buttons.get(1).visible = false;
            }
        } else if (st == GameEngine.State.GAME_OVER) {
            buttons.get(7).visible = true; // 再来一局
            buttons.get(8).visible = true; // 返回主页
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        drawTable(g2);
        if (fourPlayer) {
            drawAIPlayer(g2, 2, W / 2 - 35, 12, false);   // 上（北·老王）
            drawAIPlayer(g2, 1, W - 95, 130, true);        // 右（东·小美）
            drawAIPlayer(g2, 3, 25, 130, false);           // 左（西·阿强）
        } else {
            drawAIPlayer(g2, 2, 25, 25, false);    // 左侧上家
            drawAIPlayer(g2, 1, W - 95, 25, true); // 右侧下家
        }
        drawBottomCards(g2);
        drawCardCounter(g2);
        drawPlayAreas(g2);
        drawCenterMessage(g2);
        drawHand(g2);
        drawSelfInfo(g2);
        drawButtons(g2);
        if (showGameOver) drawGameOver(g2);
        g2.dispose();
    }

    /** 绘制桌面：优先绘制桌布图片，缺失时回退绿呢面 */
    private void drawTable(Graphics2D g) {
        BufferedImage img = loadTableImage();
        if (img != null) {
            g.drawImage(img, 0, 0, null);
            // 半透明深色遮罩，保证文字/牌面可读性
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRect(0, 0, W, H);
            // 金色双层边框
            g.setColor(new Color(180, 140, 40, 200));
            g.setStroke(new BasicStroke(4f));
            g.drawRoundRect(8, 8, W - 16, H - 16, 22, 22);
            g.setColor(new Color(255, 215, 0, 90));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(15, 15, W - 30, H - 30, 16, 16);
            return;
        }
        // 回退：绿呢面渐变
        RadialGradientPaint rgp = new RadialGradientPaint(
                new Point2D.Float(W / 2f, H / 2f), W * 0.72f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{TABLE_LIGHT, TABLE_DARK, new Color(8, 45, 28)});
        g.setPaint(rgp);
        g.fillRect(0, 0, W, H);
        // 金色双层边框
        g.setColor(new Color(180, 140, 40, 200));
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(8, 8, W - 16, H - 16, 22, 22);
        g.setColor(new Color(255, 215, 0, 90));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(15, 15, W - 30, H - 30, 16, 16);
        // 中央装饰椭圆
        g.setColor(new Color(255, 255, 255, 14));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(W / 2 - 210, H / 2 - 140, 420, 280);
        g.setColor(new Color(255, 255, 255, 8));
        g.drawOval(W / 2 - 240, H / 2 - 160, 480, 320);
    }

    /** 绘制圆形头像：优先绘制头像图片（圆形裁剪），无图时回退名字首字 */
    private void drawAvatar(Graphics2D g, int cx, int cy, int r, int playerId, String name,
                            boolean active, boolean isLandlord) {
        // 激活光环
        if (active) {
            for (int i = 3; i >= 1; i--) {
                g.setColor(new Color(255, 220, 80, 40));
                g.fillOval(cx - r - i * 4, cy - r - i * 4, (r + i * 4) * 2, (r + i * 4) * 2);
            }
        }
        BufferedImage avatar = getAvatarFor(playerId);
        if (avatar != null) {
            // 圆形裁剪绘制头像图片
            Shape oldClip = g.getClip();
            g.setClip(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
            g.drawImage(avatar, cx - r, cy - r, r * 2, r * 2, null);
            g.setClip(oldClip);
        } else {
            // 回退：头像底色渐变 + 名字首字
            GradientPaint gp = new GradientPaint(cx - r, cy - r, new Color(90, 160, 220),
                    cx + r, cy + r, new Color(40, 80, 140));
            g.setPaint(gp);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float)(r * 0.85)));
            FontMetrics fm = g.getFontMetrics();
            String ch = name.substring(0, 1);
            g.drawString(ch, cx - fm.stringWidth(ch) / 2, cy + fm.getAscent() / 2 - 2);
        }
        // 边框
        g.setColor(active ? GOLD : new Color(200, 200, 200, 150));
        g.setStroke(new BasicStroke(active ? 3f : 2f));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        // 地主皇冠
        if (isLandlord) {
            drawCrown(g, cx, cy - r - 6, r * 0.55f);
        }
    }

    /** 绘制小皇冠 */
    private void drawCrown(Graphics2D g, int cx, int cy, float size) {
        Polygon crown = new Polygon();
        crown.addPoint((int)(cx - size), (int)(cy + size * 0.5));
        crown.addPoint((int)(cx - size), (int)(cy - size * 0.3));
        crown.addPoint((int)(cx - size * 0.5), (int)(cy + size * 0.1));
        crown.addPoint(cx, (int)(cy - size * 0.6));
        crown.addPoint((int)(cx + size * 0.5), (int)(cy + size * 0.1));
        crown.addPoint((int)(cx + size), (int)(cy - size * 0.3));
        crown.addPoint((int)(cx + size), (int)(cy + size * 0.5));
        g.setColor(GOLD);
        g.fillPolygon(crown);
        g.setColor(new Color(180, 130, 20));
        g.setStroke(new BasicStroke(1.2f));
        g.drawPolygon(crown);
    }

    /** 绘制 AI 玩家区域 */
    private void drawAIPlayer(Graphics2D g, int playerId, int ax, int ay, boolean rightSide) {
        Player p = engine.getPlayer(playerId);
        boolean active = engine.getCurrentPlayer() == playerId
                && (engine.getState() == GameEngine.State.PLAYING
                    || engine.getState() == GameEngine.State.BIDDING
                    || engine.getState() == GameEngine.State.DOUBLING
                    || engine.getState() == GameEngine.State.GUESSING);
        boolean isLandlord = engine.getLandlordId() == playerId;
        int cx = ax + 35, cy = ay + 35;

        drawAvatar(g, cx, cy, 32, playerId, p.getName(), active, isLandlord);

        // 名字
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
        FontMetrics fm = g.getFontMetrics();
        String nm = p.getName();
        g.drawString(nm, cx - fm.stringWidth(nm) / 2, ay + 80);

        // 身份标签
        if (fourPlayer && teams != null) {
            String role = teams[playerId] == 0 ? "大牌组" : "小牌组";
            Color rc = teams[playerId] == 0 ? new Color(220, 60, 50) : new Color(50, 100, 200);
            g.setColor(rc);
            int rw = fm.stringWidth(role) + 14;
            int rx = cx - rw / 2;
            g.fillRoundRect(rx, ay + 86, rw, 20, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm2 = g.getFontMetrics();
            g.drawString(role, cx - fm2.stringWidth(role) / 2, ay + 100);
        } else if (engine.getLandlordId() >= 0) {
            String role = isLandlord ? "地主" : "农民";
            Color rc = isLandlord ? new Color(220, 60, 50) : new Color(60, 140, 80);
            g.setColor(rc);
            int rw = fm.stringWidth(role) + 14;
            int rx = cx - rw / 2;
            g.fillRoundRect(rx, ay + 86, rw, 20, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm2 = g.getFontMetrics();
            g.drawString(role, cx - fm2.stringWidth(role) / 2, ay + 100);
        }

        // 剩余牌数
        int cnt = p.getCardCount();
        g.setColor(new Color(255, 240, 200));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        String cntTxt = cnt + " 张";
        FontMetrics fm3 = g.getFontMetrics();
        g.drawString(cntTxt, cx - fm3.stringWidth(cntTxt) / 2, ay + 122);

        // 迷你牌背扇形展示（最多显示 8 张）
        int showCnt = Math.min(cnt, 8);
        int dir = rightSide ? -1 : 1;
        // 左侧：牌在头像右边；右侧：牌在头像左边，避免遮挡
        int bx = rightSide ? ax - 27 : ax + 70;
        for (int i = 0; i < showCnt; i++) {
            CardPainter.drawMiniBack(g, bx + dir * i * 14, ay + 8);
        }

        // 叫地主结果
        if (engine.getState() == GameEngine.State.BIDDING && bidShown.containsKey(playerId)) {
            boolean called = bidShown.get(playerId);
            String bidTxt = called ? "叫地主" : "不叫";
            g.setColor(called ? GOLD : new Color(200, 200, 200));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm4 = g.getFontMetrics();
            g.drawString(bidTxt, cx - fm4.stringWidth(bidTxt) / 2, ay + 145);
        }

        // 加倍结果（加倍阶段及出牌阶段显示）
        GameEngine.State st = engine.getState();
        if ((st == GameEngine.State.DOUBLING || st == GameEngine.State.PLAYING)
                && doubleShown.containsKey(playerId)) {
            boolean dbl = doubleShown.get(playerId);
            String dblTxt = dbl ? "加倍" : "不加倍";
            g.setColor(dbl ? new Color(255, 100, 60) : new Color(180, 180, 180));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm5 = g.getFontMetrics();
            g.drawString(dblTxt, cx - fm5.stringWidth(dblTxt) / 2, ay + 145);
        }

        // 四人猜牌：显示亮出的牌
        if (st == GameEngine.State.GUESSING && guessShown.containsKey(playerId)) {
            Card gc = guessShown.get(playerId);
            CardPainter.drawSmallCard(g, gc, cx - CardPainter.SMALL_W / 2, ay + 130);
        }
    }

    /** 绘制底牌 */
    private void drawBottomCards(Graphics2D g) {
        List<Card> bottoms = engine.getBottomCards();
        if (bottoms.isEmpty()) return;
        int gap = 6;
        int totalW = 3 * CardPainter.SMALL_W + 2 * gap;
        int sx = fourPlayer ? 400 : 460;  // 中间稍微靠左；四人模式避开顶部 player2 头像(565起)
        int sy = 12;
        boolean revealed = engine.getLandlordId() >= 0;
        // 底牌标签
        g.setColor(new Color(255, 230, 150, 200));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("底牌", sx - 34, sy + CardPainter.SMALL_H / 2 + 4);
        for (int i = 0; i < 3; i++) {
            int x = sx + i * (CardPainter.SMALL_W + gap);
            if (revealed) CardPainter.drawSmallCard(g, bottoms.get(i), x, sy);
            else CardPainter.drawCardBack(g, x, sy, CardPainter.SMALL_W, CardPainter.SMALL_H);
        }
    }

    /** 绘制记牌器：顶部显示每种点数剩余张数（总数-已出-已翻底牌，含自己手牌） */
    private void drawCardCounter(Graphics2D g) {
        int[] ranks = {3,4,5,6,7,8,9,10,11,12,13,14,15,16,17};
        int perRank = fourPlayer ? 6 : 4;   // 3~15 每种总数（四人6花色/三人4花色）
        boolean revealed = engine.getLandlordId() >= 0;

        int[] bottomCount = new int[18];
        if (revealed) for (Card c : engine.getBottomCards()) bottomCount[c.getRank()]++;

        int x0 = 640, y0 = 12, itemW = 22, cardW = 20, cardH = 28;
        int panelW = ranks.length * itemW + 6;
        // 面板背景
        g.setColor(new Color(20, 40, 20, 160));
        g.fillRoundRect(x0 - 5, y0 - 5, panelW, cardH + 26, 8, 8);

        for (int i = 0; i < ranks.length; i++) {
            int rank = ranks[i];
            int total = rank >= 16 ? 1 : perRank;
            int remaining = total - playedRankCount[rank] - bottomCount[rank];
            if (remaining < 0) remaining = 0;
            int cx = x0 + i * itemW;

            // 小牌图标
            g.setColor(Color.WHITE);
            g.fillRoundRect(cx, y0, cardW, cardH, 4, 4);
            g.setColor(new Color(70, 70, 70));
            g.drawRoundRect(cx, y0, cardW, cardH, 4, 4);

            // 点数文字
            String text;
            Color textColor;
            if (rank == 16)      { text = "小"; textColor = Color.BLACK; }
            else if (rank == 17) { text = "大"; textColor = Color.RED; }
            else if (rank == 15) { text = "2";  textColor = Color.RED; }
            else if (rank == 14) { text = "A";  textColor = Color.BLACK; }
            else if (rank == 13) { text = "K";  textColor = Color.BLACK; }
            else if (rank == 12) { text = "Q";  textColor = Color.BLACK; }
            else if (rank == 11) { text = "J";  textColor = Color.BLACK; }
            else                 { text = String.valueOf(rank); textColor = Color.BLACK; }
            g.setColor(textColor);
            g.setFont(g.getFont().deriveFont(Font.BOLD, text.length() > 1 ? 9f : 11f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, cx + (cardW - fm.stringWidth(text)) / 2, y0 + cardH / 2 + fm.getAscent() / 2 - 1);

            // 剩余数字
            g.setColor(remaining > 0 ? new Color(255, 215, 60) : new Color(110, 110, 110));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
            String num = String.valueOf(remaining);
            fm = g.getFontMetrics();
            g.drawString(num, cx + (cardW - fm.stringWidth(num)) / 2, y0 + cardH + 14);
        }
    }

    /** 绘制出牌区 */
    private void drawPlayAreas(Graphics2D g) {
        if (fourPlayer) {
            drawPlayFor(g, 2, W / 2, 155);       // 上（北·老王）
            drawPlayFor(g, 1, W - 210, 280);     // 右（东·小美）
            drawPlayFor(g, 3, 210, 280);         // 左（西·阿强）
            drawPlayFor(g, 0, W / 2, 360);       // 下（自己）
        } else {
            drawPlayFor(g, 2, 235, 205);        // 上家（左）
            drawPlayFor(g, 0, W / 2, 320);      // 自己（中）
            drawPlayFor(g, 1, W - 235, 205);    // 下家（右）
        }
    }

    private void drawPlayFor(Graphics2D g, int playerId, int centerX, int y) {
        if (passLabels.containsKey(playerId)) {
            String txt = "不要";
            g.setFont(g.getFont().deriveFont(Font.BOLD, 22f));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(txt) + 28;
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRoundRect(centerX - tw / 2, y + 8, tw, 36, 18, 18);
            g.setColor(new Color(255, 130, 110));
            g.drawString(txt, centerX - fm.stringWidth(txt) / 2, y + 33);
        } else if (lastPlays.containsKey(playerId)) {
            CardPlay play = lastPlays.get(playerId);
            List<Card> cards = play.getCards();
            int overlap = 22;
            int totalW = (cards.size() - 1) * overlap + CardPainter.SMALL_W;
            int sx = centerX - totalW / 2;
            for (int i = 0; i < cards.size(); i++) {
                CardPainter.drawSmallCard(g, cards.get(i), sx + i * overlap, y);
            }
            // 牌型标签
            g.setColor(new Color(255, 230, 150, 170));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g.getFontMetrics();
            String label = play.getType().label;
            g.drawString(label, centerX - fm.stringWidth(label) / 2,
                    y + CardPainter.SMALL_H + 16);
        }
    }

    /** 绘制中央状态消息 */
    private void drawCenterMessage(Graphics2D g) {
        if (centerMessage.isEmpty()) return;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 20f));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(centerMessage) + 40;
        int x = W / 2 - tw / 2, y = 250;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(x, y, tw, 38, 19, 19);
        g.setColor(new Color(255, 240, 200));
        g.drawString(centerMessage, W / 2 - fm.stringWidth(centerMessage) / 2, y + 26);
    }

    /** 绘制手牌 */
    private void drawHand(Graphics2D g) {
        List<Card> hand = engine.getPlayer(0).getHand();
        if (hand.isEmpty()) return;
        int[] pos = handLayout(hand);
        int startX = pos[0], y = pos[1];
        boolean canSelect = engine.getState() == GameEngine.State.PLAYING
                && engine.getCurrentPlayer() == 0;
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            int cx = startX + i * CardPainter.OVERLAP;
            boolean isSel = selected.contains(c);
            boolean isHover = (i == hoverCardIdx && canSelect);
            int cy = y - (isSel ? CardPainter.SELECT_OFFSET : 0)
                       - (isHover && !isSel ? 8 : 0);
            CardPainter.drawCard(g, c, cx, cy);
            // 选中高亮边框
            if (isSel) {
                g.setColor(new Color(255, 215, 0, 200));
                g.setStroke(new BasicStroke(2.5f));
                g.drawRoundRect(cx - 1, cy - 1, CardPainter.CARD_W + 2,
                        CardPainter.CARD_H + 2, 11, 11);
            }
        }
    }

    /** 绘制自己信息（左下角） */
    private void drawSelfInfo(Graphics2D g) {
        Player me = engine.getPlayer(0);
        boolean active = engine.getCurrentPlayer() == 0
                && (engine.getState() == GameEngine.State.PLAYING
                    || engine.getState() == GameEngine.State.BIDDING
                    || engine.getState() == GameEngine.State.DOUBLING
                    || engine.getState() == GameEngine.State.GUESSING);
        boolean isLandlord = engine.getLandlordId() == 0;
        drawAvatar(g, 50, H - 100, 30, 0, me.getName(), active, isLandlord);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
        g.drawString(me.getName(), 90, H - 115);
        if (fourPlayer && teams != null) {
            String role = teams[0] == 0 ? "大牌组" : "小牌组";
            Color rc = teams[0] == 0 ? new Color(220, 60, 50) : new Color(50, 100, 200);
            g.setColor(rc);
            g.fillRoundRect(90, H - 108, 56, 20, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(role, 97, H - 93);
        } else if (engine.getLandlordId() >= 0) {
            String role = isLandlord ? "地主" : "农民";
            Color rc = isLandlord ? new Color(220, 60, 50) : new Color(60, 140, 80);
            g.setColor(rc);
            g.fillRoundRect(90, H - 108, 46, 20, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(role, 101, H - 93);
        }
        g.setColor(new Color(255, 240, 200));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.drawString("积分 " + me.getScore(), 90, H - 78);
        // 自己的加倍结果
        GameEngine.State st = engine.getState();
        if ((st == GameEngine.State.DOUBLING || st == GameEngine.State.PLAYING)
                && doubleShown.containsKey(0)) {
            boolean dbl = doubleShown.get(0);
            String dblTxt = dbl ? "加倍" : "不加倍";
            Color dc = dbl ? new Color(255, 100, 60) : new Color(180, 180, 180);
            g.setColor(dc);
            g.fillRoundRect(142, H - 108, 56, 20, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
            g.drawString(dblTxt, 151, H - 93);
        }
        // 倍数面板（右侧中部，下移避开右上角玩家）
        if (st == GameEngine.State.DOUBLING || st == GameEngine.State.PLAYING) {
            int mult = engine.getMultiplier();
            // 面板背景
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(W - 150, 370, 130, 46, 10, 10);
            g.setColor(new Color(255, 200, 60));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
            g.drawString("底分 " + engine.getBaseScore(), W - 138, 388);
            g.setColor(new Color(255, 100, 80));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 20f));
            g.drawString("×" + mult, W - 68, 407);
        }
    }

    /** 绘制底部按钮 */
    private void drawButtons(Graphics2D g) {
        List<GameButton> vis = new ArrayList<>();
        for (GameButton b : buttons) if (b.visible) vis.add(b);
        if (vis.isEmpty()) return;
        int bw = 110, bh = 42, gap = 18;
        int totalW = vis.size() * bw + (vis.size() - 1) * gap;
        int sx = (W - totalW) / 2;
        // 按钮位于手牌上方（考虑选中上移后的最高位置，留 10px 间隙）
        int by = H - CardPainter.CARD_H - 58 - CardPainter.SELECT_OFFSET - bh - 10;
        for (int i = 0; i < vis.size(); i++) {
            GameButton btn = vis.get(i);
            btn.rect.setBounds(sx + i * (bw + gap), by, bw, bh);
            drawButton(g, btn);
        }
    }

    private void drawButton(Graphics2D g, GameButton btn) {
        Rectangle r = btn.rect;
        Color bg = btn.hover ? btn.hoverBg : btn.normalBg;
        // 阴影
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(r.x + 2, r.y + 3, r.width, r.height, 12, 12);
        // 主体渐变
        GradientPaint gp = new GradientPaint(r.x, r.y, bg.brighter(),
                r.x, r.y + r.height, bg.darker());
        g.setPaint(gp);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);
        // 边框
        g.setColor(btn.hover ? GOLD : new Color(255, 255, 255, 100));
        g.setStroke(new BasicStroke(btn.hover ? 2f : 1.2f));
        g.drawRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 11, 11);
        // 文字
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 17f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(btn.text, r.x + r.width / 2 - fm.stringWidth(btn.text) / 2,
                r.y + r.height / 2 + fm.getAscent() / 2 - 2);
    }

    /** 绘制游戏结束遮罩 */
    private void drawGameOver(Graphics2D g) {
        // 半透明遮罩
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, W, H);
        // 中央面板
        int pw = 460, ph = fourPlayer ? 310 : 280;
        int px = W / 2 - pw / 2, py = H / 2 - ph / 2 - 40;
        g.setColor(new Color(20, 60, 40, 235));
        g.fillRoundRect(px, py, pw, ph, 20, 20);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(px + 3, py + 3, pw - 6, ph - 6, 17, 17);
        // 大标题
        boolean win = gameOverText.startsWith("胜");
        g.setColor(win ? new Color(255, 220, 80) : new Color(255, 110, 90));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 46f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(gameOverText, W / 2 - fm.stringWidth(gameOverText) / 2, py + 65);
        // 积分变化（醒目）
        String deltaStr = (scoreDelta >= 0 ? "+" : "") + scoreDelta + " 分";
        g.setColor(scoreDelta >= 0 ? new Color(100, 255, 130) : new Color(255, 120, 100));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 30f));
        FontMetrics fmD = g.getFontMetrics();
        g.drawString(deltaStr, W / 2 - fmD.stringWidth(deltaStr) / 2, py + 108);
        // 明细行
        g.setColor(new Color(255, 240, 210));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(gameDetail, W / 2 - fm2.stringWidth(gameDetail) / 2, py + 140);
        // 分隔线
        g.setColor(new Color(255, 255, 255, 60));
        g.drawLine(px + 40, py + 158, px + pw - 40, py + 158);
        // 积分列表
        int yStep = 26;
        int y0 = py + 182;
        int num = fourPlayer ? 4 : 3;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 15f));
        for (int i = 0; i < num; i++) {
            Player p = engine.getPlayers().get(i);
            int ty = y0 + i * yStep;
            // 名字
            g.setColor(new Color(220, 220, 200));
            g.drawString(p.getName(), px + 100, ty);
            // 身份/队伍
            String role;
            Color roleColor;
            if (fourPlayer && teams != null) {
                role = teams[i] == 0 ? "大牌组" : "小牌组";
                roleColor = teams[i] == 0 ? new Color(255, 120, 90) : new Color(120, 160, 255);
            } else {
                boolean isLL = p.getRole() == Player.Role.LANDLORD;
                role = isLL ? "地主" : "农民";
                roleColor = isLL ? new Color(255, 120, 90) : new Color(120, 220, 140);
            }
            g.setColor(roleColor);
            g.drawString(role, px + 160, ty);
            // 积分
            String sc = String.valueOf(p.getScore());
            g.setColor(p.getScore() >= 0 ? new Color(255, 230, 150) : new Color(255, 150, 130));
            FontMetrics fmS = g.getFontMetrics();
            g.drawString(sc, px + pw - 100 - fmS.stringWidth(sc) / 2, ty);
        }
    }
}