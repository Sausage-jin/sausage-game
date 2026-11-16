import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Random;

/**
 * 小鸟飞翔：两人合作控制同一只小鸟，在宇宙中躲避陨石、飞碟、地球和星球，
 * 并收集金珠、金币(+1分)和钱袋(+2分)奖励分数。
 * 玩家1：F键 控制小鸟向上
 * 玩家2：↓方向键 控制小鸟向下
 * 一个只管上升、一个只管下降，两人必须默契配合！
 * P键暂停，R键重开。
 */
public class BirdGame extends JFrame {
    private static final int GW = 920, GH = 520;

    // ---- 小鸟 ----
    private int bird1X = 200, bird1Y = GH / 2;
    private int bird2X = 260, bird2Y = GH / 2;   // 双鸟模式：玩家2的鸟
    private static final int BIRD_W = 52, BIRD_H = 44;
    private static final int MOVE_SPD = 10;

    /** 全局速度倍率（来自设置，1.0=正常） */
    private double spdMul() { return SoundManager.getSpeedMul(); }
    private boolean keyF = false, keyDown = false, keyJ = false, keyUp = false;
    private Image birdImg, bird2Img, meteorImg, ufoImg, bgImg, earthImg, planetImg, pearlImg, coinImg, pouchImg;
    private double bird1Tilt = 0, bird2Tilt = 0;
    private int flap1Anim = 0, flap2Anim = 0;

    // ---- 双鸟模式 ----
    private boolean versusMode = false;       // true=双人双鸟，false=双人合作单鸟
    private boolean bird1Alive = true, bird2Alive = true;
    private int score1 = 0, score2 = 0;       // 双鸟模式各自得分

    // ---- 陨石 / 飞碟 / 粒子 / 星空 ----
    private final ArrayList<Spike> spikes = new ArrayList<>();
    private final ArrayList<int[]> particles = new ArrayList<>();
    private final ArrayList<int[]> clouds = new ArrayList<>();
    private int spawnCD = 50;
    private final ArrayList<Reward> rewards = new ArrayList<>();
    private int rewardCD = 120;

    // ---- 状态 ----
    private int score = 0, best = 0;
    private boolean over = false, paused = false, begun = false;
    private int shake = 0;
    private int frame = 0;

    // ---- 联机 ----
    private NetPlay netPlay = null;
    private int myPlayer = 0;       // 联机：1=主机 2=客户端 0=观众
    private boolean online = false;

    private Timer timer;
    private GamePanel gamePanel;
    private JLabel scoreLabel, bestLabel;
    private final Random rnd = new Random();
    private JDialog overDlg = null;   // 游戏结束对话框（联机时可编程关闭）

    // ---- 配色 ----
    private static final Color BG       = new Color(18, 21, 32);
    private static final Color PANEL_BG = new Color(30, 34, 46);
    private static final Color GOLD     = new Color(255, 215, 110);
    private static final Color SUB      = new Color(160, 168, 188);
    private static final Color BTN_BG   = new Color(52, 58, 76);
    private static final Color BTN_FG   = new Color(225, 228, 238);
    private static final Color BORDER   = new Color(58, 64, 82);
    private static final Color P1_C     = new Color(255, 140, 70);
    private static final Color P2_C     = new Color(80, 200, 220);
    private static final Color SPIKE_C  = new Color(225, 75, 85);
    private static final Color SPIKE_D  = new Color(160, 40, 52);

    static class Spike {
        int x, y, w, h, speed, type;  // type: 0=陨石, 1=飞碟, 2=地球, 3=星球
        boolean scored;
        double rot;        // 当前旋转角度（陨石/地球/星球使用）
        double rotSpd;     // 旋转速度
        double baseY, waveAmp, wavePhase;  // 飞碟波动飞行
        Spike(int x, int y, int w, int h, int sp, int t) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.speed = sp; this.type = t;
            if (t == 1) {       // 飞碟：不旋转，上下波动
                baseY = y;
                waveAmp = 20 + Math.random() * 25;
                wavePhase = Math.random() * Math.PI * 2;
            } else {            // 陨石/地球/星球：旋转
                rot = Math.random() * Math.PI * 2;
                if (t == 0) rotSpd = 0.012 + Math.random() * 0.02;        // 陨石
                else if (t == 2) rotSpd = 0.004 + Math.random() * 0.005;  // 地球极慢
                else rotSpd = 0.006 + Math.random() * 0.008;              // 星球
            }
        }
    }

    static class Reward {
        int x, y, w, h, speed, type;  // type: 0=金珠(+1), 1=金币(+1), 2=钱袋(+2)
        int value;
        double bob, bobSpd;           // 上下浮动动画
        Reward(int x, int y, int w, int h, int sp, int t) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.speed = sp; this.type = t;
            value = (t == 2) ? 2 : 1;
            bob = Math.random() * Math.PI * 2;
            bobSpd = 0.04 + Math.random() * 0.03;
        }
    }

    public BirdGame() {
        this(false);
    }

    /** versusMode: false=双人合作（单鸟），true=双人双鸟（各自控制一只鸟） */
    public BirdGame(boolean versusMode) {
        this.versusMode = versusMode;
        loadImages();
        initUI();
        initClouds();
        newGame(false);
    }

    /** 联机构造器：player 1=主机(玩家1·F键上升) 2=客户端(玩家2·↓键下降) 0=观众 */
    public BirdGame(NetPlay np, int player) {
        this.netPlay = np;
        this.myPlayer = player;
        this.online = true;
        this.versusMode = false;   // 联机为双人合作模式
        loadImages();
        initUI();
        initClouds();
        if (netPlay != null) netPlay.setListener(mkNetListener());
        newGame(false);
    }

    private void loadImages() {
        try {
            File f = new File("image/bird.png");
            if (f.exists()) birdImg = ImageIO.read(f);
            f = new File("image/bird2.png");
            if (f.exists()) bird2Img = ImageIO.read(f);
            f = new File("image/yunshi (1).png");
            if (f.exists()) meteorImg = ImageIO.read(f);
            f = new File("image/feidie.png");
            if (f.exists()) ufoImg = ImageIO.read(f);
            f = new File("image/yuzhou.jpg");
            if (f.exists()) bgImg = ImageIO.read(f);
            f = new File("image/diqiu.png");
            if (f.exists()) earthImg = ImageIO.read(f);
            f = new File("image/xinqiu.png");
            if (f.exists()) planetImg = ImageIO.read(f);
            f = new File("image/jinzhu.png");
            if (f.exists()) pearlImg = ImageIO.read(f);
            f = new File("image/jinbi.png");
            if (f.exists()) coinImg = ImageIO.read(f);
            f = new File("image/qiandai.png");
            if (f.exists()) pouchImg = ImageIO.read(f);
        } catch (Exception e) { }
    }

    private void initUI() {
        setTitle("小鸟飞翔");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        setAppIcon();
        add(buildTop(), BorderLayout.NORTH);
        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        SoundManager.switchBgm(1);

        // ESC 键：弹出游戏菜单
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "escMenu");
        getRootPane().getActionMap().put("escMenu", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { showEscMenu(); }
        });
    }

    /** ESC 菜单：重新开始 / 查看规则 / 游戏设置 / 返回主页 */
    private void showEscMenu() {
        int c = UiKit.showVerticalOptions(this, "游戏菜单", null,
                new String[]{"重新开始", "查看规则", "游戏设置", "返回主页"});
        if (c == 0) {
            newGame(true);
        } else if (c == 1) {
            showRules();
        } else if (c == 2) {
            UiKit.showGameSettings(this);
        } else if (c == 3) {
            goHome();
            return;
        }
        gamePanel.requestFocusInWindow();
    }

    private JPanel buildTop() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBackground(PANEL_BG);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        top.setPreferredSize(new Dimension(0, 56));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0; gbc.insets = new Insets(0, 16, 0, 8); gbc.anchor = GridBagConstraints.WEST;
        JLabel title = new JLabel(online
                ? (myPlayer == 1 ? "小鸟飞翔 · 联机(玩家1·F上升)" : myPlayer == 2 ? "小鸟飞翔 · 联机(玩家2·↓下降)" : "小鸟飞翔 · 观战")
                : (versusMode ? "小鸟飞翔 · 双人双鸟" : "小鸟飞翔 · 双人合作"));
        title.setFont(new Font("微软雅黑", Font.BOLD, 17));
        title.setForeground(GOLD);
        gbc.gridx = 0; gbc.weightx = 0;
        top.add(title, gbc);

        // 得分
        JPanel scorePnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scorePnl.setOpaque(false);
        scoreLabel = new JLabel("得分 0");
        scoreLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        scoreLabel.setForeground(GOLD);
        scorePnl.add(scoreLabel);
        gbc.gridx = 1; gbc.insets = new Insets(0, 20, 0, 8);
        top.add(scorePnl, gbc);

        // 最佳
        JPanel bestPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        bestPnl.setOpaque(false);
        bestLabel = new JLabel("最佳 0");
        bestLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        bestLabel.setForeground(new Color(180, 190, 210));
        bestPnl.add(bestLabel);
        gbc.gridx = 2;
        top.add(bestPnl, gbc);

        // 奖励图例
        JLabel legend = new JLabel("金珠·金币 +1   钱袋 +2");
        legend.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        legend.setForeground(new Color(200, 170, 90));
        gbc.gridx = 3; gbc.insets = new Insets(0, 16, 0, 8);
        top.add(legend, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton homeBtn = mkBtn("主页");
        JButton ruleBtn = mkBtn("规则");
        JButton restartBtn = mkBtn("重开");
        btns.add(restartBtn); btns.add(ruleBtn); btns.add(homeBtn);
        gbc.gridx = 4; gbc.weightx = 1; gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 8, 0, 16);
        top.add(btns, gbc);

        homeBtn.addActionListener(e -> goHome());
        ruleBtn.addActionListener(e -> { showRules(); gamePanel.requestFocusInWindow(); });
        restartBtn.addActionListener(e -> { newGame(true); gamePanel.requestFocusInWindow(); });
        return top;
    }

    private void initClouds() {
        for (int i = 0; i < 6; i++) {
            clouds.add(new int[]{rnd.nextInt(GW), 30 + rnd.nextInt(GH - 100),
                    40 + rnd.nextInt(60), rnd.nextInt(3) + 1});
        }
    }

    private void newGame(boolean immediate) {
        spikes.clear();
        rewards.clear();
        particles.clear();
        score = 0; score1 = 0; score2 = 0;
        over = false; paused = false;
        bird1Y = GH / 2; bird1Tilt = 0; flap1Anim = 0;
        bird2Y = GH / 2; bird2Tilt = 0; flap2Anim = 0;
        bird1Alive = true; bird2Alive = true;
        if (versusMode) { bird1X = 170; bird2X = 260; }
        else { bird1X = 200; }
        spawnCD = 60; rewardCD = 120 + rnd.nextInt(80); frame = 0; shake = 0;
        keyF = false; keyDown = false; keyJ = false; keyUp = false;
        begun = immediate;
        if (timer != null) timer.stop();
        timer = new Timer(20, e -> tick());
        timer.start();
        updateLabels();
        gamePanel.requestFocusInWindow();
    }

    private void tick() {
        frame++;
        updateClouds();
        if (online && myPlayer != 1) {
            // 客户端/观众：不运行游戏逻辑，只更新本地装饰并重绘（状态由主机同步）
            updateParticles();
            gamePanel.repaint();
            return;
        }
        if (over) { updateParticles(); gamePanel.repaint(); sendStateIfOnline(); return; }
        if (paused || !begun) { gamePanel.repaint(); return; }
        updateBird();
        spawnCD--;
        if (spawnCD <= 0) spawnSpike();
        rewardCD--;
        if (rewardCD <= 0) spawnReward();
        updateSpikes();
        updateRewards();
        updateParticles();
        checkCollisions();
        gamePanel.repaint();
        updateLabels();
        sendStateIfOnline();
    }

    private void updateBird() {
        int spd = Math.max(1, (int) Math.round(MOVE_SPD * spdMul()));
        if (versusMode) {
            if (bird1Alive) updateOneBird(1, spd);   // 玩家1：F上 J下
            if (bird2Alive) updateOneBird(2, spd);   // 玩家2：↑上 ↓下
        } else {
            updateOneBird(1, spd);                    // 合作模式：F上 ↓下
        }
    }

    private void updateOneBird(int which, int spd) {
        int vy = 0;
        if (which == 1) {
            if (keyF) vy -= spd;
            if (versusMode ? keyJ : keyDown) vy += spd;
            bird1Y += vy;
            if (bird1Y < BIRD_H / 2 + 8) bird1Y = BIRD_H / 2 + 8;
            if (bird1Y > GH - BIRD_H / 2 - 8) bird1Y = GH - BIRD_H / 2 - 8;
            double target = vy < 0 ? -0.45 : vy > 0 ? 0.45 : 0;
            bird1Tilt += (target - bird1Tilt) * 0.25;
            if (vy != 0 && frame % 6 == 0) flap1Anim = 6;
            if (flap1Anim > 0) flap1Anim--;
        } else {
            if (keyUp) vy -= spd;
            if (keyDown) vy += spd;
            bird2Y += vy;
            if (bird2Y < BIRD_H / 2 + 8) bird2Y = BIRD_H / 2 + 8;
            if (bird2Y > GH - BIRD_H / 2 - 8) bird2Y = GH - BIRD_H / 2 - 8;
            double target = vy < 0 ? -0.45 : vy > 0 ? 0.45 : 0;
            bird2Tilt += (target - bird2Tilt) * 0.25;
            if (vy != 0 && frame % 6 == 0) flap2Anim = 6;
            if (flap2Anim > 0) flap2Anim--;
        }
    }

    private void spawnSpike() {
        int diff = Math.min(score / 8, 6);
        int speed = Math.max(1, (int) Math.round((8 + diff + rnd.nextInt(4)) * spdMul()));
        int r = rnd.nextInt(100);
        if (r < 26 || diff == 0) {
            // 单颗陨石
            int size = 36 + rnd.nextInt(16);
            int y = 50 + rnd.nextInt(GH - 110 - size);
            spikes.add(new Spike(GW + 40, y, size, size, speed, 0));
            spawnCD = Math.max(20, 48 - diff * 4) + rnd.nextInt(16);
        } else if (r < 46) {
            // 单个飞碟（上下波动）
            int y = 60 + rnd.nextInt(GH - 140);
            spikes.add(new Spike(GW + 50, y, 58, 32, speed, 1));
            spawnCD = Math.max(22, 52 - diff * 4) + rnd.nextInt(16);
        } else if (r < 62) {
            // 陨石群：两颗错落飞来
            int s1 = 36 + rnd.nextInt(14);
            int s2 = 36 + rnd.nextInt(14);
            int y1 = 50 + rnd.nextInt(GH / 2 - 60);
            int y2 = GH / 2 + rnd.nextInt(Math.max(10, GH / 2 - 70 - s2));
            spikes.add(new Spike(GW + 40, y1, s1, s1, speed, 0));
            spikes.add(new Spike(GW + 40 + 70 + rnd.nextInt(50), y2, s2, s2, speed - 1, 0));
            spawnCD = Math.max(26, 60 - diff * 4) + rnd.nextInt(18);
        } else if (r < 76) {
            // 地球：大体积慢速旋转
            int size = 66 + rnd.nextInt(22);
            int y = 40 + rnd.nextInt(Math.max(10, GH - 100 - size));
            spikes.add(new Spike(GW + 60, y, size, size, speed - 2, 2));
            spawnCD = Math.max(30, 64 - diff * 4) + rnd.nextInt(18);
        } else if (r < 88) {
            // 星球：带光环旋转
            int size = 50 + rnd.nextInt(18);
            int y = 40 + rnd.nextInt(Math.max(10, GH - 100 - size));
            spikes.add(new Spike(GW + 60, y, size, size, speed - 1, 3));
            spawnCD = Math.max(30, 64 - diff * 4) + rnd.nextInt(18);
        } else {
            // 混合编队：飞碟 + 陨石
            int uy = 60 + rnd.nextInt(GH / 2 - 40);
            int ms = 38 + rnd.nextInt(14);
            int my = GH / 2 + 30 + rnd.nextInt(Math.max(10, GH / 2 - 80 - ms));
            spikes.add(new Spike(GW + 50, uy, 58, 32, speed, 1));
            spikes.add(new Spike(GW + 50 + 90 + rnd.nextInt(60), my, ms, ms, speed, 0));
            spawnCD = Math.max(32, 70 - diff * 4) + rnd.nextInt(20);
        }
    }

    private void updateSpikes() {
        for (int i = spikes.size() - 1; i >= 0; i--) {
            Spike s = spikes.get(i);
            s.x -= s.speed;
            if (s.type == 1) {
                s.wavePhase += 0.06;                   // 飞碟波动相位推进
                s.y = (int) (s.baseY + Math.sin(s.wavePhase) * s.waveAmp);
            } else {
                s.rot += s.rotSpd;                     // 陨石/地球/星球旋转
            }
            if (!s.scored && s.x + s.w < bird1X - BIRD_W / 2) {
                s.scored = true;
                if (versusMode) {
                    if (bird1Alive) score1++;
                    if (bird2Alive) score2++;
                } else {
                    score++;
                    if (score % 10 == 0) SoundManager.sfxScore();
                }
            }
            if (s.x + s.w < -40) spikes.remove(i);
        }
    }

    private void spawnReward() {
        int diff = Math.min(score / 8, 6);
        int speed = 7 + diff + rnd.nextInt(3);
        int r = rnd.nextInt(100);
        int type = (r < 40) ? 0 : (r < 80) ? 1 : 2;   // 金珠40% 金币40% 钱袋20%
        int size = (type == 2) ? 34 : 28;
        int y = 50 + rnd.nextInt(GH - 110 - size);
        rewards.add(new Reward(GW + 40, y, size, size, speed, type));
        rewardCD = 140 + rnd.nextInt(100);
    }

    private void updateRewards() {
        for (int i = rewards.size() - 1; i >= 0; i--) {
            Reward rw = rewards.get(i);
            rw.x -= rw.speed;
            rw.bob += rw.bobSpd;                        // 浮动动画
            if (rw.x + rw.w < -30) { rewards.remove(i); continue; }
            Rectangle rr = new Rectangle(rw.x + 2, rw.y + 2, rw.w - 4, rw.h - 4);
            boolean eaten = false;
            if (!versusMode) {
                Rectangle bird = new Rectangle(bird1X - 15, bird1Y - 12, 30, 24);
                if (bird.intersects(rr)) { score += rw.value; eaten = true; }
            } else {
                if (bird1Alive) {
                    Rectangle b1 = new Rectangle(bird1X - 15, bird1Y - 12, 30, 24);
                    if (b1.intersects(rr)) { score1 += rw.value; eaten = true; }
                }
                if (!eaten && bird2Alive) {
                    Rectangle b2 = new Rectangle(bird2X - 15, bird2Y - 12, 30, 24);
                    if (b2.intersects(rr)) { score2 += rw.value; eaten = true; }
                }
            }
            if (eaten) {
                SoundManager.sfxCoin();
                for (int k = 0; k < 14; k++) {          // 金色粒子爆发
                    particles.add(new int[]{rw.x + rw.w / 2, rw.y + rw.h / 2,
                            rnd.nextInt(10) - 5, rnd.nextInt(10) - 8, 18 + rnd.nextInt(14)});
                }
                rewards.remove(i);
            }
        }
    }

    private void checkCollisions() {
        if (!versusMode) {
            if (checkBirdHit(bird1X, bird1Y)) gameOver();
            return;
        }
        // 双鸟模式：各自检测，撞到的淘汰
        if (bird1Alive && checkBirdHit(bird1X, bird1Y)) killBird(1);
        if (bird2Alive && checkBirdHit(bird2X, bird2Y)) killBird(2);
        if (!bird1Alive && !bird2Alive) gameOver();
    }

    private void killBird(int which) {
        int bx = (which == 1) ? bird1X : bird2X;
        int by = (which == 1) ? bird1Y : bird2Y;
        if (which == 1) bird1Alive = false; else bird2Alive = false;
        shake = 10;
        for (int i = 0; i < 24; i++) {
            particles.add(new int[]{bx, by, rnd.nextInt(14) - 7,
                    rnd.nextInt(14) - 10, 20 + rnd.nextInt(20)});
        }
        SoundManager.sfxBirdHit();
    }

    private boolean checkBirdHit(int bx, int by) {
        Rectangle bird = new Rectangle(bx - 15, by - 12, 30, 24);
        for (Spike s : spikes) {
            Rectangle sr;
            if (s.type == 1) {   // 飞碟：扁宽碰撞盒
                sr = new Rectangle(s.x + 6, s.y + 6, s.w - 12, s.h - 12);
            } else {             // 陨石/地球/星球：近似圆形碰撞盒（略缩小更宽容）
                int pad = s.w / 7;
                sr = new Rectangle(s.x + pad, s.y + pad, s.w - pad * 2, s.h - pad * 2);
            }
            if (bird.intersects(sr)) return true;
        }
        return false;
    }

    private void gameOver() {
        over = true;
        int finalScore = versusMode ? Math.max(score1, score2) : score;
        if (finalScore > best) best = finalScore;
        shake = 14;
        if (!versusMode) {
            for (int i = 0; i < 36; i++) {
                particles.add(new int[]{bird1X, bird1Y, rnd.nextInt(14) - 7,
                        rnd.nextInt(14) - 10, 25 + rnd.nextInt(25)});
            }
        }
        SoundManager.sfxLose();
        Timer t = new Timer(900, e -> showOverDlg());
        t.setRepeats(false);
        t.start();
    }

    private void showOverDlg() {
        if (!over) return;
        closeOverDlg();
        String msg;
        if (versusMode) {
            msg = "玩家1得分：" + score1 + "    玩家2得分：" + score2 + "\n";
            if (score1 > score2) msg += "玩家1获胜！";
            else if (score2 > score1) msg += "玩家2获胜！";
            else msg += "平局！";
            int finalScore = Math.max(score1, score2);
            if (finalScore >= best && finalScore > 0) msg += "\n新纪录！";
        } else {
            msg = "得分：" + score + "    最佳：" + best;
            if (score >= best && score > 0) msg += "\n新纪录！";
        }
        final JDialog dlg = new JDialog(this, "小鸟坠落", true);
        dlg.setResizable(false);
        overDlg = dlg;
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(24, 27, 38));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 86, 108), 2),
            BorderFactory.createEmptyBorder(24, 28, 18, 28)));
        JLabel titleLb = new JLabel("小鸟坠落", SwingConstants.CENTER);
        titleLb.setFont(new Font("微软雅黑", Font.BOLD, 26));
        titleLb.setForeground(GOLD);
        panel.add(titleLb, BorderLayout.NORTH);
        JLabel msgLb = new JLabel("<html><div style='text-align:center;'>" + msg.replace("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
        msgLb.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        msgLb.setForeground(SUB);
        msgLb.setBorder(BorderFactory.createEmptyBorder(12, 0, 16, 0));
        panel.add(msgLb, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        JButton againBtn = mkBtn(online && myPlayer != 1 ? "等待主机重开" : "再来一局");
        againBtn.setPreferredSize(new Dimension(130, 36));
        JButton homeBtn = mkBtn("返回主页");
        homeBtn.setPreferredSize(new Dimension(110, 36));
        if (online && myPlayer == 0) {
            btnPanel.add(homeBtn);
        } else {
            btnPanel.add(againBtn);
            btnPanel.add(homeBtn);
        }
        panel.add(btnPanel, BorderLayout.SOUTH);
        againBtn.addActionListener(e -> {
            closeOverDlg();
            if (online && myPlayer == 2 && netPlay != null) netPlay.sendOp("BIRDNEW");
            else { newGame(true); if (online) sendStateIfOnline(); }
        });
        homeBtn.addActionListener(e -> { closeOverDlg(); goHome(); });
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void closeOverDlg() {
        if (overDlg != null) { overDlg.dispose(); overDlg = null; }
    }

    private void updateParticles() {
        for (int i = particles.size() - 1; i >= 0; i--) {
            int[] p = particles.get(i);
            p[0] += p[2]; p[1] += p[3]; p[3] += 1; p[4]--;
            if (p[4] <= 0) particles.remove(i);
        }
    }

    private void updateClouds() {
        for (int[] c : clouds) {
            c[0] -= c[3];
            if (c[0] < -c[2] * 2) { c[0] = GW + c[2]; c[1] = 30 + rnd.nextInt(GH - 100); }
        }
    }

    private void togglePause() {
        if (over || !begun) return;
        paused = !paused;
    }

    private void updateLabels() {
        if (versusMode) {
            scoreLabel.setText("P1 " + score1 + "   P2 " + score2);
        } else {
            scoreLabel.setText("得分 " + score);
        }
        bestLabel.setText("最佳 " + best);
    }

    private void goHome() {
        if (timer != null) timer.stop();
        closeOverDlg();
        if (online && netPlay != null) { netPlay.close(); netPlay = null; }
        SoundManager.switchBgm(0);
        dispose();
        new HomeUI().setVisible(true);
    }

    private void showRules() {
        String rules;
        if (versusMode) {
            rules = "双人双鸟模式：各自控制一只小鸟，比拼谁活得更久、分数更高！\n"
                + "\n玩家1（橙色鸟）：按 F 向上飞  ·  按 J 向下飞"
                + "\n玩家2（青色鸟）：按 ↑ 向上飞  ·  按 ↓ 向下飞\n"
                + "\n撞到障碍该玩家小鸟淘汰，两只鸟都被淘汰则游戏结束"
                + "\n各自计分，结算时分数高者获胜！\n"
                + "\n障碍类型："
                + "\n  陨石：缓慢旋转的岩石，从右侧随机高度飞来"
                + "\n  飞碟：上下波动飞行的外星飞船，轨迹难测"
                + "\n  地球：巨大蓝色星球，极慢旋转，体积大要早躲"
                + "\n  星球：带光环的星球，旋转前进，注意走位"
                + "\n  陨石群/混合编队：多颗障碍错落袭来，考验走位\n"
                + "\n奖励道具（吃到加分！）："
                + "\n  金珠 +1分  ·  金币 +1分  ·  钱袋 +2分\n"
                + "\n每躲过一个障碍得1分，分数越高障碍越快越密"
                + "\nP键暂停/继续，R键重新开始";
        } else {
            rules = "两人合作控制同一只小鸟，在宇宙中躲避右侧飞来的障碍！\n"
                + "\n玩家1（橙色）：按住 F 键 小鸟向上飞"
                + "\n玩家2（青色）：按住 ↓ 键 小鸟向下飞"
                + "\n两人同时按则原地不动——必须沟通配合！\n"
                + "\n障碍类型："
                + "\n  陨石：缓慢旋转的岩石，从右侧随机高度飞来"
                + "\n  飞碟：上下波动飞行的外星飞船，轨迹难测"
                + "\n  地球：巨大蓝色星球，极慢旋转，体积大要早躲"
                + "\n  星球：带光环的星球，旋转前进，注意走位"
                + "\n  陨石群/混合编队：多颗障碍错落袭来，考验配合\n"
                + "\n奖励道具（吃到加分！）："
                + "\n  金珠 +1分  ·  金币 +1分  ·  钱袋 +2分\n"
                + "\n每躲过一个障碍得1分，分数越高障碍越快越密"
                + "\nP键暂停/继续，R键重新开始";
        }
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setForeground(new Color(228, 231, 240));
        ta.setBackground(new Color(24, 27, 38));
        ta.setCaretColor(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(420, 320));
        JOptionPane.showMessageDialog(this, sp, "小鸟飞翔规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private JButton mkBtn(String text) {
        JButton b = UiKit.button(text);
        b.setFont(new Font("微软雅黑", Font.BOLD, 12));
        b.addActionListener(e -> SoundManager.sfxClick());
        return b;
    }

    private void setAppIcon() {
        try {
            File f = new File("image/home.png");
            if (f.exists()) setIconImage(ImageIO.read(f));
        } catch (Exception e) { }
    }

    private void handleKey(int k, boolean pressed) {
        if (online) {
            handleKeyOnline(k, pressed);
            return;
        }
        // 拍翅音效：按键边沿（松开再按才重复，避免按住连发）
        if (k == KeyEvent.VK_F)    { if (pressed && !keyF) SoundManager.sfxBirdFlap(); keyF = pressed; }
        if (k == KeyEvent.VK_DOWN) { if (pressed && !keyDown) SoundManager.sfxBirdFlap(); keyDown = pressed; }
        if (k == KeyEvent.VK_J)    { if (pressed && !keyJ) SoundManager.sfxBirdFlap(); keyJ = pressed; }
        if (k == KeyEvent.VK_UP)   { if (pressed && !keyUp) SoundManager.sfxBirdFlap(); keyUp = pressed; }
        if (!pressed) return;
        if (k == KeyEvent.VK_P) togglePause();
        if ((k == KeyEvent.VK_R || k == KeyEvent.VK_SPACE) && over) newGame(true);
        if (!begun && !over) { begun = true; SoundManager.sfxGameStart(); }
    }

    /** 联机按键：主机=玩家1(F上升)，客户端=玩家2(↓下降)，观众无操作 */
    private void handleKeyOnline(int k, boolean pressed) {
        if (myPlayer == 1) {
            // 主机：本地控制 F 键（上升），↓ 键也允许本地按（合作模式）
            if (k == KeyEvent.VK_F)    { if (pressed && !keyF) SoundManager.sfxBirdFlap(); keyF = pressed; }
            if (k == KeyEvent.VK_DOWN) { if (pressed && !keyDown) SoundManager.sfxBirdFlap(); keyDown = pressed; }
            if (!pressed) return;
            if (k == KeyEvent.VK_P) { togglePause(); sendStateIfOnline(); }
            if ((k == KeyEvent.VK_R || k == KeyEvent.VK_SPACE) && over) newGame(true);
            if (!begun && !over) { begun = true; SoundManager.sfxGameStart(); sendStateIfOnline(); }
        } else if (myPlayer == 2 && netPlay != null) {
            // 客户端：↓ 键发操作给主机
            if (k == KeyEvent.VK_DOWN) {
                if (pressed && !keyDown) SoundManager.sfxBirdFlap();
                keyDown = pressed;
                netPlay.sendOp("BIRDK:D:" + (pressed ? 1 : 0));
                return;
            }
            if (!pressed) return;
            if (k == KeyEvent.VK_P) netPlay.sendOp("BIRDP");
            if ((k == KeyEvent.VK_R || k == KeyEvent.VK_SPACE) && over) netPlay.sendOp("BIRDNEW");
            if (!begun && !over) netPlay.sendOp("BIRDSTART");
        }
        // 观众（myPlayer==0）：无操作
    }

    // ==================== 联机 ====================

    /** 主机发送状态同步 */
    private void sendStateIfOnline() {
        if (online && netPlay != null && myPlayer == 1) {
            netPlay.sendState("BSTATE:" + serializeState());
        }
    }

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { }
            public void onStateReceived(String state) { BirdGame.this.onNetState(state); }
            public void onOpReceived(String op) { BirdGame.this.onNetOp(op); }
            public void onChatReceived(String msg) { }
            public void onDisconnected() { BirdGame.this.onNetDisconnect(); }
        };
    }

    /** 客户端/观众接收主机状态 */
    private void onNetState(String state) {
        if (!state.startsWith("BSTATE:")) return;
        boolean wasOver = over;
        deserializeState(state.substring(7));
        updateLabels();
        if (gamePanel != null) gamePanel.repaint();
        if (over && !wasOver) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { showOverDlg(); }
            });
        } else if (!over && wasOver) {
            closeOverDlg();
        }
    }

    /** 主机接收客户端操作 */
    private void onNetOp(String op) {
        if (!online || myPlayer != 1 || netPlay == null) return;
        String[] p = op.split(":", -1);
        if ("BIRDK".equals(p[0]) && p.length >= 3) {
            boolean pressed = "1".equals(p[1].trim());
            if (pressed && !keyDown) SoundManager.sfxBirdFlap();
            keyDown = pressed;
        } else if ("BIRDP".equals(p[0])) {
            togglePause();
            sendStateIfOnline();
        } else if ("BIRDNEW".equals(p[0])) {
            if (over) newGame(true);
        } else if ("BIRDSTART".equals(p[0])) {
            if (!begun && !over) { begun = true; SoundManager.sfxGameStart(); sendStateIfOnline(); }
        }
    }

    private void onNetDisconnect() {
        if (netPlay == null) return;
        NetPlay np = netPlay;
        netPlay = null;
        np.close();
        online = false; myPlayer = 0;
        if (timer != null) timer.stop();
        closeOverDlg();
        SoundManager.sfxNetDisconnect();
        JOptionPane.showMessageDialog(this, "对方已断开，回到本地模式", "联机", JOptionPane.WARNING_MESSAGE);
        newGame(false);
    }

    /** 序列化游戏状态：y|tilt|flap|score|over|paused|begun|S:障碍列表|R:奖励列表 */
    private String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(bird1Y).append('|').append(bird1Tilt).append('|').append(flap1Anim)
          .append('|').append(score).append('|').append(over ? 1 : 0).append('|')
          .append(paused ? 1 : 0).append('|').append(begun ? 1 : 0).append('|');
        sb.append("S:");
        for (int i = 0; i < spikes.size(); i++) {
            Spike s = spikes.get(i);
            if (i > 0) sb.append(';');
            sb.append(s.x).append(',').append(s.y).append(',').append(s.w).append(',')
              .append(s.h).append(',').append(s.type).append(',').append(s.rot);
        }
        sb.append("|R:");
        for (int i = 0; i < rewards.size(); i++) {
            Reward rw = rewards.get(i);
            if (i > 0) sb.append(';');
            sb.append(rw.x).append(',').append(rw.y).append(',').append(rw.w).append(',')
              .append(rw.h).append(',').append(rw.type).append(',').append(rw.bob);
        }
        return sb.toString();
    }

    /** 反序列化主机状态到本地镜像 */
    private void deserializeState(String data) {
        String[] parts = data.split("\\|", -1);
        if (parts.length < 8) return;
        try {
            bird1Y = Integer.parseInt(parts[0].trim());
            bird1Tilt = Double.parseDouble(parts[1].trim());
            flap1Anim = Integer.parseInt(parts[2].trim());
            score = Integer.parseInt(parts[3].trim());
            over = "1".equals(parts[4].trim());
            paused = "1".equals(parts[5].trim());
            begun = "1".equals(parts[6].trim());
        } catch (Exception e) { return; }
        // 障碍列表
        spikes.clear();
        if (parts.length > 7 && parts[7].startsWith("S:")) {
            String spData = parts[7].substring(2);
            if (!spData.isEmpty()) {
                for (String sp : spData.split(";")) {
                    String[] f = sp.split(",", -1);
                    if (f.length >= 6) {
                        try {
                            Spike s = new Spike(0, 0, 0, 0, 0, Integer.parseInt(f[4].trim()));
                            s.x = Integer.parseInt(f[0].trim());
                            s.y = Integer.parseInt(f[1].trim());
                            s.w = Integer.parseInt(f[2].trim());
                            s.h = Integer.parseInt(f[3].trim());
                            s.rot = Double.parseDouble(f[5].trim());
                            spikes.add(s);
                        } catch (Exception e) { }
                    }
                }
            }
        }
        // 奖励列表
        rewards.clear();
        if (parts.length > 8 && parts[8].startsWith("R:")) {
            String rwData = parts[8].substring(2);
            if (!rwData.isEmpty()) {
                for (String rp : rwData.split(";")) {
                    String[] f = rp.split(",", -1);
                    if (f.length >= 6) {
                        try {
                            Reward rw = new Reward(0, 0, 0, 0, 0, Integer.parseInt(f[4].trim()));
                            rw.x = Integer.parseInt(f[0].trim());
                            rw.y = Integer.parseInt(f[1].trim());
                            rw.w = Integer.parseInt(f[2].trim());
                            rw.h = Integer.parseInt(f[3].trim());
                            rw.bob = Double.parseDouble(f[5].trim());
                            rewards.add(rw);
                        } catch (Exception e) { }
                    }
                }
            }
        }
    }

    // ==================== 游戏画面 ====================

    class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(GW, GH));
            setBackground(BG);
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { handleKey(e.getKeyCode(), true); }
                @Override public void keyReleased(KeyEvent e) { handleKey(e.getKeyCode(), false); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                    if (!begun && !over) {
                        if (online && myPlayer == 2 && netPlay != null) {
                            netPlay.sendOp("BIRDSTART");
                        } else if (!online || myPlayer == 1) {
                            begun = true; SoundManager.sfxGameStart();
                            if (online) sendStateIfOnline();
                        }
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ox = 0, oy = 0;
            if (shake > 0) { ox = rnd.nextInt(10) - 5; oy = rnd.nextInt(10) - 5; shake--; }
            g2.translate(ox, oy);
            drawBackground(g2);
            drawClouds(g2);
            for (Spike s : spikes) drawSpike(g2, s);
            for (Reward rw : rewards) drawReward(g2, rw);
            if (!over) {
                if (!versusMode) {
                    drawBird(g2, bird1X, bird1Y, bird1Tilt, flap1Anim, birdImg, P1_C);
                } else {
                    if (bird1Alive) drawBird(g2, bird1X, bird1Y, bird1Tilt, flap1Anim, birdImg, P1_C);
                    if (bird2Alive) drawBird(g2, bird2X, bird2Y, bird2Tilt, flap2Anim, bird2Img, P2_C);
                }
            }
            drawParticles(g2);
            drawBigScore(g2);
            if (!begun && !over) drawStartHint(g2);
            if (paused && !over) drawPauseOverlay(g2);
            g2.translate(-ox, -oy);
        }

        private void drawBackground(Graphics2D g2) {
            if (bgImg != null) {
                g2.drawImage(bgImg, 0, 0, GW, GH, null);
            } else {
                GradientPaint gp = new GradientPaint(0, 0, new Color(24, 28, 44), 0, GH, new Color(14, 16, 26));
                g2.setPaint(gp);
                g2.fillRect(0, 0, GW, GH);
            }
            // 流动星尘（叠加在背景图上，增加动感）
            g2.setColor(new Color(255, 255, 255, 50));
            for (int i = 0; i < 30; i++) {
                int sx = (i * 163 + frame / 2) % GW;
                int sy = (i * 97 + 13) % GH;
                int sz = i % 4 == 0 ? 2 : 1;
                g2.fillOval(sx, sy, sz, sz);
            }
        }

        private void drawClouds(Graphics2D g2) {
            // 星空闪烁层（缓慢漂移的亮星）
            for (int[] c : clouds) {
                int tw = (frame / 8 + c[2]) % 6;
                int a = tw < 3 ? 90 + tw * 40 : 210 - tw * 20;
                g2.setColor(new Color(255, 255, 240, Math.max(40, a)));
                int sz = c[2] / 22 + 1;
                g2.fillOval(c[0], c[1], sz + 1, sz + 1);
                if (sz > 1) {  // 十字星芒
                    g2.setColor(new Color(255, 255, 240, a / 2));
                    g2.fillRect(c[0] - 2, c[1] + sz / 2, sz + 5, 1);
                    g2.fillRect(c[0] + sz / 2, c[1] - 2, 1, sz + 5);
                }
            }
        }

        private void drawSpike(Graphics2D g2, Spike s) {
            if (s.type == 1) {
                // 飞碟：不旋转，底部光晕
                if (ufoImg != null) {
                    g2.setColor(new Color(80, 220, 255, 40));
                    g2.fillOval(s.x + 4, s.y + s.h - 4, s.w - 8, 10);
                    g2.drawImage(ufoImg, s.x, s.y, s.w, s.h, null);
                } else {
                    g2.setColor(new Color(160, 180, 200));
                    g2.fillOval(s.x, s.y, s.w, s.h / 2 + 4);
                    g2.setColor(new Color(90, 200, 230, 120));
                    g2.fillOval(s.x + s.w / 4, s.y + s.h / 2, s.w / 2, s.h / 2);
                }
                return;
            }
            // 陨石/地球/星球：旋转绘制
            AffineTransform old = g2.getTransform();
            g2.translate(s.x + s.w / 2.0, s.y + s.h / 2.0);
            g2.rotate(s.rot);
            Image img = null;
            Color fillC = null, spotC = null;
            if (s.type == 0) { img = meteorImg; fillC = new Color(128,118,108); spotC = new Color(90,82,74); }
            else if (s.type == 2) { img = earthImg; fillC = new Color(70,130,200); spotC = new Color(40,90,60); }
            else { img = planetImg; fillC = new Color(200,150,90); spotC = new Color(160,110,60); }
            if (img != null) {
                g2.drawImage(img, -s.w / 2, -s.h / 2, s.w, s.h, null);
            } else {
                g2.setColor(fillC);
                g2.fillOval(-s.w / 2, -s.h / 2, s.w, s.h);
                g2.setColor(spotC);
                g2.fillOval(-s.w / 5, -s.h / 6, s.w / 3, s.h / 4);
            }
            g2.setTransform(old);
            // 地球/星球外圈淡光晕（不随旋转）
            if (s.type >= 2) {
                g2.setColor(new Color(120, 180, 255, 30));
                g2.fillOval(s.x - 4, s.y - 4, s.w + 8, s.h + 8);
            }
        }

        private void drawReward(Graphics2D g2, Reward rw) {
            int dy = (int) (Math.sin(rw.bob) * 5);      // 浮动偏移
            int cx = rw.x + rw.w / 2, cy = rw.y + rw.h / 2 + dy;
            // 发光底晕
            g2.setColor(new Color(255, 215, 110, 45));
            g2.fillOval(cx - rw.w / 2 - 5, cy - rw.h / 2 - 5, rw.w + 10, rw.h + 10);
            Image img = null;
            Color fc = null;
            if (rw.type == 0) { img = pearlImg; fc = new Color(255,240,200); }
            else if (rw.type == 1) { img = coinImg; fc = new Color(255,200,60); }
            else { img = pouchImg; fc = new Color(190,150,90); }
            if (img != null) {
                g2.drawImage(img, rw.x, rw.y + dy, rw.w, rw.h, null);
            } else {
                g2.setColor(fc);
                g2.fillOval(rw.x, rw.y + dy, rw.w, rw.h);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 11));
                String t = rw.type == 2 ? "+2" : "+1";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, cx - fm.stringWidth(t) / 2, cy + 4);
            }
        }

        private void drawBird(Graphics2D g2, int bx, int by, double tilt, int flap, Image img, Color bodyC) {
            AffineTransform old = g2.getTransform();
            g2.translate(bx, by);
            g2.rotate(tilt);
            if (img != null) {
                int iw = img.getWidth(null), ih = img.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double sc = Math.min((double) BIRD_W / iw, (double) BIRD_H / ih);
                    int dw = (int) (iw * sc), dh = (int) (ih * sc);
                    g2.drawImage(img, -dw / 2, -dh / 2, dw, dh, null);
                }
            } else {
                g2.setColor(bodyC);
                g2.fillOval(-BIRD_W / 2, -BIRD_H / 2, BIRD_W, BIRD_H);
                int wingY = flap > 0 ? -8 : 2;
                g2.setColor(bodyC.darker());
                g2.fillOval(-18, wingY, 26, 14);
                g2.setColor(Color.WHITE);
                g2.fillOval(2, -14, 13, 13);
                g2.setColor(Color.BLACK);
                g2.fillOval(6, -11, 6, 6);
                g2.setColor(new Color(255, 190, 60));
                g2.fillPolygon(new int[]{BIRD_W / 2 - 4, BIRD_W / 2 + 12, BIRD_W / 2 - 4},
                        new int[]{-5, 2, 9}, 3);
            }
            g2.setTransform(old);
        }

        private void drawParticles(Graphics2D g2) {
            for (int[] p : particles) {
                int a = Math.min(255, p[4] * 10);
                g2.setColor(new Color(255, 140 + p[4] % 80, 60, a));
                g2.fillOval(p[0] - 3, p[1] - 3, 6, 6);
            }
        }

        private void drawBigScore(Graphics2D g2) {
            g2.setFont(new Font("微软雅黑", Font.BOLD, 42));
            g2.setColor(new Color(255, 255, 255, 45));
            String s = versusMode ? (score1 + "  :  " + score2) : String.valueOf(score);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, GW / 2 - fm.stringWidth(s) / 2, 66);
        }

        private void drawStartHint(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, GW, GH);
            g2.setFont(new Font("微软雅黑", Font.BOLD, 28));
            g2.setColor(GOLD);
            FontMetrics fm = g2.getFontMetrics();
            String s = "点击屏幕或按任意键开始";
            g2.drawString(s, GW / 2 - fm.stringWidth(s) / 2, GH / 2 - 20);
            g2.setFont(new Font("微软雅黑", Font.PLAIN, 15));
            g2.setColor(SUB);
            s = online
                    ? (myPlayer == 1 ? "你是玩家1：按 F 上升  ·  对方按 ↓ 下降  ·  默契配合躲避障碍"
                       : myPlayer == 2 ? "你是玩家2：按 ↓ 下降  ·  对方按 F 上升  ·  默契配合躲避障碍"
                       : "观战中  ·  玩家1按 F 上升  ·  玩家2按 ↓ 下降")
                    : (versusMode
                       ? "玩家1按 F 上 J 下  ·  玩家2按 ↑ 上 ↓ 下  ·  躲避陨石飞碟星球  ·  吃金珠金币钱袋加分"
                       : "玩家1按 F 上升  ·  玩家2按 ↓ 下降  ·  躲避陨石飞碟星球  ·  吃金珠金币钱袋加分");
            fm = g2.getFontMetrics();
            g2.drawString(s, GW / 2 - fm.stringWidth(s) / 2, GH / 2 + 22);
        }

        private void drawPauseOverlay(Graphics2D g2) {
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, GW, GH);
            g2.setFont(new Font("微软雅黑", Font.BOLD, 32));
            g2.setColor(GOLD);
            FontMetrics fm = g2.getFontMetrics();
            String s = "已暂停";
            g2.drawString(s, GW / 2 - fm.stringWidth(s) / 2, GH / 2);
            g2.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            g2.setColor(SUB);
            s = "按 P 继续";
            fm = g2.getFontMetrics();
            g2.drawString(s, GW / 2 - fm.stringWidth(s) / 2, GH / 2 + 34);
        }
    }
}