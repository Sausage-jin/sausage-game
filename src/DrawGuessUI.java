import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 你画我猜：两人轮流画/猜成语，支持本地双人和局域网联机。
 * 本地模式：两人共用一台电脑，轮次开始时弹窗出示题目给画者。
 * 联机模式：主机=玩家1，客户端=玩家2；主机控制流程与计时，
 *           笔划实时同步，题目仅发给画者。
 * 协议（ST=状态同步，OP=操作同步）：
 *   ST:DGSETUP:画时,猜时,轮数      主机→客户端 开局设置
 *   ST:DGTURN:轮次,画者            开始新一轮
 *   ST:DGIDIOM:成语                题目（仅发给画者客户端）
 *   ST:DGPHASE:0/1                 阶段切换 0=绘画 1=猜测
 *   ST:DGTIME:秒                   剩余时间同步
 *   ST:DGRESULT:1/0,成语           本轮结果与答案
 *   ST:DGEND                       游戏结束
 *   ST:DGSWITCH:模式号             联机切换游戏模式
 *   OP:DGDRAW:x1,y1,x2,y2,rgb,size 笔划段同步
 *   OP:DGCLEAR                     清空画板
 *   OP:DGGUESS:内容                 猜测内容（主机判断对错）
 *   OP:DGFINDRAW                   画者提前结束绘画（客户端→主机）
 *   OP:DGSWITCHREQ:模式号          请求切换模式（客户端→主机）
 */
public class DrawGuessUI extends JFrame {

    // ==================== 模式与联机 ====================
    private boolean online = false;
    private NetPlay netPlay;
    private int myPlayer = 0;      // 0=本地, 1=主机/玩家1, 2=客户端/玩家2

    // ==================== 设置 ====================
    private int drawTime = 60;     // 绘画时间（秒）
    private int guessTime = 30;    // 猜测时间（秒）
    private int totalTurns = 4;    // 总轮次（每人画 totalTurns/2 次）

    // ==================== 游戏状态 ====================
    private int currentTurn = 0;   // 当前轮次（1-based）
    private int drawer = 1;        // 当前画者 1或2
    private String currentIdiom = "";
    private int phase = -1;        // -1=未开始, 0=绘画, 1=猜测, 2=结果, 3=结束
    private int timeLeft = 0;
    private int correctCount = 0;  // 合作猜对题数
    private boolean gameStarted = false;
    private javax.swing.Timer timer;

    // ==================== UI组件 ====================
    private DrawingPanel canvas;
    private JLabel timerLabel, roundLabel, phaseLabel, roleLabel;
    private JLabel idiomLabel;
    private JTextField guessInput;
    private JButton sendBtn;
    private JTextPane chatPane;
    private StyledDocument chatDoc;
    private JPanel toolBar;
    private JButton finishDrawBtn;
    private JButton switchModeBtn;
    private JButton peekBtn;   // 本地模式：画者随时查看题目
    private java.util.List<JButton> colorBtns = new java.util.ArrayList<JButton>();
    private java.util.List<JButton> sizeBtns = new java.util.ArrayList<JButton>();
    private JButton eraserBtn;
    private JButton clearBtn;

    // ==================== 绘画状态 ====================
    private Color drawColor = Color.BLACK;
    private int brushSize = 2;
    private boolean erasing = false;

    // ==================== 配色 ====================
    private static final Color BG       = new Color(20, 23, 32);
    private static final Color PANEL_BG = new Color(30, 34, 46);
    private static final Color GOLD     = new Color(255, 215, 110);
    private static final Color SUB      = new Color(160, 168, 188);
    private static final Color BTN_BG   = new Color(52, 58, 76);
    private static final Color BTN_FG   = new Color(225, 228, 238);
    private static final Color BORDER   = new Color(58, 64, 82);
    private static final Color RED_C    = new Color(240, 100, 100);
    private static final Color GREEN_C  = new Color(110, 220, 130);
    private static final Color PLAYER1_C = new Color(255, 130, 130);
    private static final Color PLAYER2_C = new Color(130, 180, 255);
    private static final Color CANVAS_BG = Color.WHITE;

    private static final Color[] PALETTE = {
        Color.BLACK, Color.RED, Color.BLUE, new Color(0, 140, 0),
        Color.ORANGE, Color.MAGENTA, new Color(0, 160, 180), new Color(139, 69, 19)
    };
    private static final int[] BRUSH_SIZES = {1, 2, 3, 5, 7};

    /** 本地双人模式 */
    public DrawGuessUI() {
        initUI();
        SoundManager.switchBgm(4);   // 你画我猜背景音乐
        showSetupDialog();
    }

    /** 联机模式：player=1主机 / 2客户端 */
    public DrawGuessUI(NetPlay np, int player) {
        this.netPlay = np;
        this.myPlayer = player;
        this.online = true;
        initUI();
        SoundManager.switchBgm(4);   // 你画我猜背景音乐
        netPlay.setListener(mkNetListener());
        if (myPlayer == 1) {
            showSetupDialog();   // 主机设置后开局
        } else {
            appendChat("已连接主机，等待设置…", SUB);
        }
        updateInfo();
    }

    private void initUI() {
        setTitle("你画我猜");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        setAppIcon();

        add(buildTop(), BorderLayout.NORTH);

        canvas = new DrawingPanel();
        canvas.setPreferredSize(new Dimension(600, 420));
        JPanel canvasWrap = new JPanel(new GridBagLayout());
        canvasWrap.setBackground(BG);
        GridBagConstraints cgbc = new GridBagConstraints();
        cgbc.fill = GridBagConstraints.BOTH;
        cgbc.weightx = 1.0;
        cgbc.weighty = 1.0;
        canvasWrap.add(canvas, cgbc);
        add(canvasWrap, BorderLayout.CENTER);

        add(buildRight(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

        setSize(940, 660);
        setMinimumSize(new Dimension(760, 540));
        setLocationRelativeTo(null);
        setResizable(true);
        updateInfo();

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
            if (online && myPlayer == 2) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            startGame();
        } else if (c == 1) {
            showRules();
        } else if (c == 2) {
            UiKit.showGameSettings(this);
        } else if (c == 3) {
            goHome();
        }
    }

    private JPanel buildTop() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBackground(PANEL_BG);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        top.setPreferredSize(new Dimension(0, 52));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 16, 0, 10);
        gbc.anchor = GridBagConstraints.WEST;
        JLabel title = new JLabel("你画我猜");
        title.setFont(new Font("微软雅黑", Font.BOLD, 17));
        title.setForeground(GOLD);
        gbc.gridx = 0; gbc.weightx = 0;
        top.add(title, gbc);

        roundLabel = new JLabel("轮次 -/-");
        roundLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        roundLabel.setForeground(BTN_FG);
        gbc.gridx = 1; gbc.weightx = 0; gbc.insets = new Insets(0, 16, 0, 10);
        top.add(roundLabel, gbc);

        phaseLabel = new JLabel("等待开始");
        phaseLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        phaseLabel.setForeground(SUB);
        gbc.gridx = 2; gbc.weightx = 0; gbc.insets = new Insets(0, 4, 0, 10);
        top.add(phaseLabel, gbc);

        // 闹钟+倒计时 组合面板（紧挨显示，避免weightx导致分离）
        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        timerPanel.setOpaque(false);
        JLabel clockIcon = new JLabel();
        try {
            File cf = new File("image/naozhong.png");
            if (cf.exists()) {
                BufferedImage cimg = ImageIO.read(cf);
                clockIcon.setIcon(new ImageIcon(cimg.getScaledInstance(22, 22, Image.SCALE_SMOOTH)));
            }
        } catch (Exception e) { /* 图片缺失则不显示 */ }
        timerPanel.add(clockIcon);
        timerLabel = new JLabel("--");
        timerLabel.setFont(new Font("微软雅黑", Font.BOLD, 20));
        timerLabel.setForeground(GOLD);
        timerPanel.add(timerLabel);
        gbc.gridx = 3; gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 4, 0, 4);
        top.add(timerPanel, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        switchModeBtn = mkBtn("切换模式");
        switchModeBtn.setVisible(online);
        JButton homeBtn = mkBtn("主页");
        JButton ruleBtn = mkBtn("规则");
        btns.add(switchModeBtn);
        btns.add(ruleBtn);
        btns.add(homeBtn);
        gbc.gridx = 4; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 8, 0, 16);
        top.add(btns, gbc);

        homeBtn.addActionListener(e -> goHome());
        ruleBtn.addActionListener(e -> showRules());
        switchModeBtn.addActionListener(e -> onSwitchModeBtn());
        return top;
    }

    private JPanel buildRight() {
        JPanel right = new JPanel(new BorderLayout(0, 0));
        right.setBackground(PANEL_BG);
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));
        right.setPreferredSize(new Dimension(268, 0));

        // ---- 上：信息区 ----
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(PANEL_BG);
        info.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        roleLabel = new JLabel(" ");
        roleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        roleLabel.setForeground(SUB);
        roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(roleLabel);

        // 题目显示区：画者可见完整题目（醒目高亮），猜者只见遮罩
        JPanel idiomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        idiomPanel.setOpaque(false);
        idiomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        idiomLabel = new JLabel("题目：—");
        idiomLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        idiomLabel.setForeground(GOLD);
        idiomPanel.add(idiomLabel);
        info.add(idiomPanel);

        // 本地模式：画者可随时点击查看题目（联机画者直接显示无需按钮）
        peekBtn = mkBtn("看题目");
        peekBtn.setVisible(!online);
        peekBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        peekBtn.addActionListener(e -> {
            if (gameStarted && currentIdiom != null && !currentIdiom.isEmpty()) {
                showLocalIdiomDialog();
            }
        });
        info.add(peekBtn);

        right.add(info, BorderLayout.NORTH);

        // ---- 中：聊天/猜测记录 ----
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(24, 27, 38));
        chatPane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        chatDoc = chatPane.getStyledDocument();
        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        right.add(chatScroll, BorderLayout.CENTER);

        // ---- 下：输入区 ----
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(PANEL_BG);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        guessInput = new JTextField();
        guessInput.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        guessInput.setBackground(new Color(20, 23, 32));
        guessInput.setForeground(BTN_FG);
        guessInput.setCaretColor(BTN_FG);
        guessInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        guessInput.addActionListener(e -> submitInput());
        inputPanel.add(guessInput, BorderLayout.CENTER);

        sendBtn = mkBtn("发送");
        sendBtn.setPreferredSize(new Dimension(64, 34));
        sendBtn.addActionListener(e -> submitInput());
        inputPanel.add(sendBtn, BorderLayout.EAST);
        right.add(inputPanel, BorderLayout.SOUTH);

        return right;
    }

    /** 工具栏竖直分隔线（左侧留白 + 一根细线） */
    private JComponent vSep() {
        JPanel p = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(74, 82, 104));
                g2.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(14, 24));
        return p;
    }

    private JPanel buildBottom() {
        // 左：工具组（画笔 · 粗细 · 工具）  右：结束绘画
        toolBar = new JPanel(new BorderLayout(12, 0));
        toolBar.setBackground(PANEL_BG);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        left.setOpaque(false);

        JLabel lb = new JLabel("画笔");
        lb.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        lb.setForeground(SUB);
        left.add(lb);

        for (int i = 0; i < PALETTE.length; i++) {
            final int idx = i;
            JButton btn = new JButton();
            btn.setBackground(PALETTE[i]);
            btn.setPreferredSize(new Dimension(26, 26));
            btn.setFocusable(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                drawColor = PALETTE[idx];
                erasing = false;
                updateToolSelection();
                SoundManager.sfxDrawColor();
            });
            colorBtns.add(btn);
            left.add(btn);
        }

        left.add(vSep());

        JLabel sl = new JLabel("粗细");
        sl.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        sl.setForeground(SUB);
        left.add(sl);
        for (int i = 0; i < BRUSH_SIZES.length; i++) {
            final int idx = i;
            JButton btn = UiKit.make(String.valueOf(BRUSH_SIZES[i]), BTN_BG, BTN_FG);
            btn.setPreferredSize(new Dimension(40, 28));
            btn.addActionListener(e -> {
                brushSize = BRUSH_SIZES[idx];
                erasing = false;
                updateToolSelection();
                SoundManager.sfxSelect();
            });
            sizeBtns.add(btn);
            left.add(btn);
        }

        left.add(vSep());

        eraserBtn = mkBtn("橡皮");
        eraserBtn.setPreferredSize(new Dimension(62, 30));
        eraserBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        eraserBtn.addActionListener(e -> {
            erasing = !erasing;
            updateToolSelection();
            SoundManager.sfxDrawErase();
        });
        left.add(eraserBtn);

        clearBtn = mkBtn("清空");
        clearBtn.setPreferredSize(new Dimension(62, 30));
        clearBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        clearBtn.addActionListener(e -> clearCanvas());
        left.add(clearBtn);

        toolBar.add(left, BorderLayout.WEST);

        finishDrawBtn = mkBtn("结束绘画");
        finishDrawBtn.setPreferredSize(new Dimension(118, 32));
        finishDrawBtn.setFont(new Font("微软雅黑", Font.BOLD, 13));
        finishDrawBtn.setBackground(new Color(80, 140, 90));
        try {
            File jf = new File("image/jieshu.png");
            if (jf.exists()) {
                BufferedImage jimg = ImageIO.read(jf);
                finishDrawBtn.setIcon(new ImageIcon(jimg.getScaledInstance(18, 18, Image.SCALE_SMOOTH)));
                finishDrawBtn.setHorizontalTextPosition(SwingConstants.RIGHT);
            }
        } catch (Exception e) { /* 图片缺失则不显示 */ }
        finishDrawBtn.addActionListener(e -> {
            if (online && myPlayer == 2) requestFinishDraw();
            else finishDraw();
        });
        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        rightWrap.setOpaque(false);
        rightWrap.add(finishDrawBtn);
        toolBar.add(rightWrap, BorderLayout.EAST);

        updateToolSelection();
        return toolBar;
    }

    private JButton mkBtn(String text) {
        JButton b = UiKit.button(text);
        b.setFont(new Font("微软雅黑", Font.BOLD, 13));
        b.addActionListener(e -> SoundManager.sfxClick());
        return b;
    }

    private void setAppIcon() {
        try {
            File f = new File("image/home.png");
            if (f.exists()) setIconImage(ImageIO.read(f));
        } catch (Exception e) { }
    }

    // ==================== 设置对话框 ====================

    private void showSetupDialog() {
        final JDialog dlg = new JDialog(this, "你画我猜 - 游戏设置", true);
        dlg.setResizable(false);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(20, 26, 14, 26)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 4, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        Font lbFont = new Font("微软雅黑", Font.BOLD, 14);

        JLabel title = new JLabel("你画我猜 · 游戏设置");
        title.setFont(new Font("微软雅黑", Font.BOLD, 18));
        title.setForeground(GOLD);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);
        gbc.gridwidth = 1;

        JLabel dtLb = new JLabel("绘画时间（秒）：");
        dtLb.setFont(lbFont); dtLb.setForeground(BTN_FG);
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
        panel.add(dtLb, gbc);
        final JSpinner drawSpin = new JSpinner(new SpinnerNumberModel(60, 10, 300, 10));
        styleSpinner(drawSpin, lbFont);
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(drawSpin, gbc);

        JLabel gtLb = new JLabel("猜测时间（秒）：");
        gtLb.setFont(lbFont); gtLb.setForeground(BTN_FG);
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
        panel.add(gtLb, gbc);
        final JSpinner guessSpin = new JSpinner(new SpinnerNumberModel(30, 10, 300, 5));
        styleSpinner(guessSpin, lbFont);
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(guessSpin, gbc);

        JLabel rtLb = new JLabel("回合数（每人画几次）：");
        rtLb.setFont(lbFont); rtLb.setForeground(BTN_FG);
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE;
        panel.add(rtLb, gbc);
        final JSpinner roundSpin = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        styleSpinner(roundSpin, lbFont);
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(roundSpin, gbc);

        JButton startBtn = mkBtn("开始游戏");
        startBtn.setBackground(UiKit.GOLD);
        startBtn.setForeground(UiKit.DARK_FG);
        startBtn.setPreferredSize(new Dimension(150, 42));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER; gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(16, 4, 2, 4);
        panel.add(startBtn, gbc);

        startBtn.addActionListener(e -> {
            drawTime = (Integer) drawSpin.getValue();
            guessTime = (Integer) guessSpin.getValue();
            int rounds = (Integer) roundSpin.getValue();
            totalTurns = rounds * 2;
            dlg.dispose();
            startGame();
        });
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /** 美化数字输入框为暗色主题 */
    private void styleSpinner(JSpinner sp, Font f) {
        sp.setFont(f);
        sp.setBackground(new Color(20, 23, 32));
        sp.setForeground(BTN_FG);
        sp.setPreferredSize(new Dimension(110, 30));
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBackground(new Color(20, 23, 32));
            tf.setForeground(BTN_FG);
            tf.setCaretColor(BTN_FG);
            tf.setFont(f);
            tf.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
        }
    }

    // ==================== 游戏流程 ====================

    private void startGame() {
        gameStarted = true;
        correctCount = 0;
        currentTurn = 0;
        phase = -1;
        canvas.clearAll();
        if (online && myPlayer == 1) {
            netPlay.sendState("DGSETUP:" + drawTime + "," + guessTime + "," + totalTurns);
        }
        appendChat("游戏开始！共" + totalTurns + "轮，每人画" + (totalTurns / 2) + "次", GOLD);
        SoundManager.sfxGameStart();
        beginTurn();
    }

    /** 开始一轮（主机/本地调用） */
    private void beginTurn() {
        currentTurn++;
        if (currentTurn > totalTurns) { endGame(); return; }
        drawer = (currentTurn % 2 == 1) ? 1 : 2;
        currentIdiom = IdiomLibrary.pick();
        phase = -1;
        canvas.clearAll();
        if (online && myPlayer == 1) {
            netPlay.sendState("DGTURN:" + currentTurn + "," + drawer);
            if (drawer == 2) netPlay.sendToClient(0, "ST:DGIDIOM:" + currentIdiom);
        }
        if (!online) showLocalIdiomDialog();
        updateInfo();
        startDrawPhase();
    }

    /** 本地模式：弹窗出示题目给画者 */
    private void showLocalIdiomDialog() {
        SoundManager.sfxNotify();
        String who = nickOf(drawer);
        String other = nickOf(3 - drawer);
        JOptionPane.showMessageDialog(this,
            "第 " + currentTurn + "/" + totalTurns + " 轮\n"
            + who + " 作画，" + other + " 猜题\n\n"
            + "【" + other + " 请回避屏幕！】\n\n"
            + "题目：" + currentIdiom,
            "画者查看题目", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startDrawPhase() {
        phase = 0;
        timeLeft = drawTime;
        if (online && myPlayer == 1) netPlay.sendState("DGPHASE:0");
        restartTimer();
        updateInfo();
        appendChat("【绘画阶段】" + nickOf(drawer) + " 作画，" + drawTime + "秒", SUB);
        SoundManager.sfxTurnStart();
    }

    private void startGuessPhase() {
        phase = 1;
        timeLeft = guessTime;
        if (online && myPlayer == 1) netPlay.sendState("DGPHASE:1");
        restartTimer();
        updateInfo();
        appendChat("【猜测阶段】" + nickOf(3 - drawer) + " 请输入答案，" + guessTime + "秒", GOLD);
        guessInput.requestFocusInWindow();
        SoundManager.sfxTurnStart();
    }

    /** 画者提前结束绘画阶段，进入猜测阶段 */
    private void finishDraw() {
        if (!gameStarted || phase != 0) return;
        if (!online || myPlayer == drawer) {
            // 本地或联机画者本人：直接切换（主机切换会同步DGPHASE:1给客户端）
            appendChat("画者提前结束了绘画", SUB);
            startGuessPhase();
        }
        // 联机客户端画者：发送请求由主机切换（见 onNetOp DGFINDRAW）
    }

    /** 联机客户端画者请求提前结束绘画 → 主机收到后切换阶段 */
    private void requestFinishDraw() {
        if (!gameStarted || phase != 0) return;
        if (online && myPlayer == 2 && netPlay != null) {
            netPlay.sendOp("DGFINDRAW");
            appendChat("已请求提前结束绘画…", SUB);
        }
    }

    private void restartTimer() {
        if (timer != null) timer.stop();
        if (!online || myPlayer == 1) {
            timer = new javax.swing.Timer(1000, e -> tick());
            timer.start();
        }
        updateTimerLabel();
    }

    private void tick() {
        if (phase < 0 || phase >= 2) return;
        timeLeft--;
        if (online && myPlayer == 1) netPlay.sendState("DGTIME:" + timeLeft);
        updateTimerLabel();
        if (timeLeft > 0 && timeLeft <= 5) SoundManager.sfxTickTock();
        if (timeLeft <= 0) {
            if (phase == 0) startGuessPhase();
            else if (phase == 1) endTurn(false);
        }
    }

    /** 结束一轮（主机/本地调用） */
    private void endTurn(boolean correct) {
        phase = 2;
        if (timer != null) timer.stop();
        if (correct) correctCount++;
        if (online && myPlayer == 1) {
            netPlay.sendState("DGRESULT:" + (correct ? 1 : 0) + "," + currentIdiom);
        }
        if (correct) {
            appendChat("猜对了！答案是【" + currentIdiom + "】", GREEN_C);
            SoundManager.sfxGuessCorrect();
        } else {
            appendChat("时间到！答案是【" + currentIdiom + "】", RED_C);
            SoundManager.sfxGuessWrong();
        }
        updateInfo();
        if (!online || myPlayer == 1) {
            javax.swing.Timer t = new javax.swing.Timer(3000, e -> {
                if (currentTurn >= totalTurns) endGame();
                else beginTurn();
            });
            t.setRepeats(false);
            t.start();
        }
    }

    private void endGame() {
        phase = 3;
        if (timer != null) timer.stop();
        if (online && myPlayer == 1) {
            netPlay.sendState("DGEND");
        }
        showEndDialog();
    }

    private void showEndDialog() {
        updateInfo();
        if (correctCount * 2 >= totalTurns) SoundManager.sfxGameWin();
        else SoundManager.sfxDraw();
        String msg = "共完成 " + totalTurns + " 轮\n合作猜对 " + correctCount + " / " + totalTurns + " 题";

        final JDialog dlg = new JDialog(this, "游戏结束", true);
        dlg.setResizable(false);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(22, 30, 14, 30)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.insets = new Insets(4, 4, 4, 4);
        JLabel titleLb = new JLabel("游戏结束");
        titleLb.setFont(new Font("微软雅黑", Font.BOLD, 20));
        titleLb.setForeground(GOLD);
        gbc.gridy = 0; panel.add(titleLb, gbc);
        JLabel statLb = new JLabel(msg);
        statLb.setFont(new Font("微软雅黑", Font.BOLD, 15));
        statLb.setForeground(BTN_FG);
        gbc.gridy = 1; gbc.insets = new Insets(10, 4, 14, 4);
        panel.add(statLb, gbc);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setOpaque(false);
        JButton againBtn = mkBtn("再来一局");
        againBtn.setPreferredSize(new Dimension(110, 36));
        JButton homeBtn2 = mkBtn("回到主页");
        homeBtn2.setPreferredSize(new Dimension(110, 36));
        btnPanel.add(againBtn);
        btnPanel.add(homeBtn2);
        gbc.gridy = 2; panel.add(btnPanel, gbc);
        if (online && myPlayer == 2) againBtn.setEnabled(false);
        againBtn.addActionListener(e -> {
            dlg.dispose();
            if (!online || myPlayer == 1) startGame();
        });
        homeBtn2.addActionListener(e -> { dlg.dispose(); goHome(); });
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ==================== 猜测与聊天 ====================

    /** 输入框提交：猜测阶段且为猜者=猜测，否则=聊天 */
    private void submitInput() {
        String text = guessInput.getText().trim();
        if (text.isEmpty()) return;
        guessInput.setText("");
        text = text.replace("\n", " ").replace("\r", " ");
        boolean guessMode = gameStarted && phase == 1
                && (!online || (myPlayer > 0 && myPlayer != drawer));
        if (guessMode) submitGuess(text);
        else sendChat(text);
    }

    private void submitGuess(String text) {
        String nick = online ? SoundManager.getNickname() : nickOf(3 - drawer);
        Color c = online ? playerColor(myPlayer) : playerColor(3 - drawer);
        appendChat(nick + " 猜：" + text, c);
        if (online && netPlay != null) {
            netPlay.sendOp("DGGUESS:" + text);
            if (myPlayer == 1) checkGuess(text);
        } else {
            checkGuess(text);
        }
    }

    /** 判断猜测是否正确（主机或本地调用） */
    private void checkGuess(String text) {
        if (phase != 1) return;
        String t = text.replace(" ", "").replace("，", "").replace("、", "");
        if (t.equals(currentIdiom)) endTurn(true);
    }

    private void sendChat(String text) {
        String nick = online ? SoundManager.getNickname() : "我";
        Color c = online ? playerColor(myPlayer) : SUB;
        appendChat(nick + "：" + text, c);
        if (online && netPlay != null) {
            netPlay.sendChat(myPlayer + ":" + SoundManager.getNickname() + ":" + text);
        }
        SoundManager.sfxChatSend();
    }

    // ==================== 绘画同步 ====================

    private boolean canDraw() {
        if (!gameStarted || phase != 0) return false;
        if (!online) return true; // 本地：当前轮画者操作
        return myPlayer == drawer;
    }

    /** 本地画线（记录并发送） */
    private void drawLocal(int x1, int y1, int x2, int y2) {
        Color c = erasing ? CANVAS_BG : drawColor;
        int sz = erasing ? Math.max(brushSize * 3, 14) : brushSize;
        canvas.drawLine(x1, y1, x2, y2, c, sz);
        if (online && netPlay != null) {
            netPlay.sendOp("DGDRAW:" + x1 + "," + y1 + "," + x2 + "," + y2
                    + "," + c.getRGB() + "," + sz);
        }
    }

    /** 接收远程画线 */
    private void receiveDraw(String data) {
        try {
            String[] p = data.split(",");
            int x1 = Integer.parseInt(p[0]);
            int y1 = Integer.parseInt(p[1]);
            int x2 = Integer.parseInt(p[2]);
            int y2 = Integer.parseInt(p[3]);
            Color c = new Color(Integer.parseInt(p[4]));
            int sz = Integer.parseInt(p[5]);
            canvas.drawLine(x1, y1, x2, y2, c, sz);
        } catch (Exception e) { }
    }

    private void clearCanvas() {
        canvas.clearAll();
        if (online && netPlay != null) netPlay.sendOp("DGCLEAR");
        SoundManager.sfxDrawClear();
    }

    // ==================== 联机监听 ====================

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { }
            public void onSpectatorChanged(int n) { }
            public void onStateReceived(String msg) { onNetState(msg); }
            public void onOpReceived(String msg) { onNetOp(msg); }
            public void onChatReceived(String msg) { onNetChat(msg); }
            public void onDisconnected() { onNetDisconnect(); }
        };
    }

    private void onNetState(String msg) {
        // msg 已去除 ST: 前缀，形如 DGSETUP:60,30,4
        if (!msg.startsWith("DG")) return;
        String[] parts = msg.split(":", 2);
        String cmd = parts[0];
        String data = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case "DGSETUP": {
                String[] p = data.split(",");
                drawTime = Integer.parseInt(p[0]);
                guessTime = Integer.parseInt(p[1]);
                totalTurns = Integer.parseInt(p[2]);
                gameStarted = true;   // 客户端标记游戏已开始（showIdiom/canDraw依赖此标志）
                appendChat("游戏设置：画" + drawTime + "秒/猜" + guessTime + "秒/共" + totalTurns + "轮", GOLD);
                break;
            }
            case "DGTURN": {
                String[] p = data.split(",");
                currentTurn = Integer.parseInt(p[0]);
                drawer = Integer.parseInt(p[1]);
                phase = -1;
                canvas.clearAll();
                updateInfo();
                break;
            }
            case "DGIDIOM": {
                currentIdiom = data;
                updateInfo();   // 立即刷新，画者无需等待阶段切换即可看到题目
                break;
            }
            case "DGPHASE": {
                phase = Integer.parseInt(data);
                if (phase == 0) {
                    timeLeft = drawTime;
                    appendChat("【绘画阶段】" + nickOf(drawer) + " 作画，" + drawTime + "秒", SUB);
                } else if (phase == 1) {
                    timeLeft = guessTime;
                    appendChat("【猜测阶段】请输入答案，" + guessTime + "秒", GOLD);
                    guessInput.requestFocusInWindow();
                }
                SoundManager.sfxTurnStart();
                updateInfo();
                break;
            }
            case "DGTIME": {
                timeLeft = Integer.parseInt(data);
                updateTimerLabel();
                break;
            }
            case "DGRESULT": {
                String[] p = data.split(",", 2);
                boolean ok = "1".equals(p[0]);
                String answer = p.length > 1 ? p[1] : "";
                phase = 2;
                if (timer != null) timer.stop();
                if (ok) {
                    correctCount++;
                    appendChat("猜对了！答案是【" + answer + "】", GREEN_C);
                    SoundManager.sfxGuessCorrect();
                } else {
                    appendChat("时间到！答案是【" + answer + "】", RED_C);
                    SoundManager.sfxGuessWrong();
                }
                updateInfo();
                break;
            }
            case "DGEND": {
                phase = 3;
                if (timer != null) timer.stop();
                showEndDialog();
                break;
            }
            case "DGSWITCH": {
                try {
                    int mode = Integer.parseInt(data.trim());
                    if (mode >= 1 && mode <= 5 && mode != 3) {
                        appendChat("切换到【" + modeName(mode) + "】", GOLD);
                        switchToMode(mode);
                    }
                } catch (Exception e) { }
                break;
            }
        }
    }

    private void onNetOp(String msg) {
        // msg 已去除 OP: 前缀，形如 DGDRAW:x1,y1,x2,y2,rgb,size
        if (!msg.startsWith("DG")) return;
        String[] parts = msg.split(":", 2);
        String cmd = parts[0];
        String data = parts.length > 1 ? parts[1] : "";
        switch (cmd) {
            case "DGDRAW": receiveDraw(data); break;
            case "DGCLEAR": canvas.clearAll(); break;
            case "DGGUESS": {
                // 主机收到客户端的猜测，判断并显示
                appendChat(nickOf(2) + " 猜：" + data, playerColor(2));
                if (myPlayer == 1) checkGuess(data);
                break;
            }
            case "DGFINDRAW": {
                // 主机收到客户端画者的"提前结束绘画"请求
                if (myPlayer == 1 && gameStarted && phase == 0) {
                    appendChat("画者提前结束了绘画", SUB);
                    startGuessPhase();
                }
                break;
            }
            case "DGSWITCHREQ": {
                // 主机收到客户端的切换模式请求
                if (myPlayer == 1) {
                    int reqMode = 1;
                    try { reqMode = Integer.parseInt(data.trim()); } catch (Exception e) { }
                    if (reqMode < 1 || reqMode > 5 || reqMode == 3) break;
                    int c = JOptionPane.showConfirmDialog(this,
                            "对方想切换到【" + modeName(reqMode) + "】，同意吗？",
                            "模式切换请求", JOptionPane.YES_NO_OPTION);
                    if (c == JOptionPane.YES_OPTION) {
                        netPlay.sendState("DGSWITCH:" + reqMode);
                        switchToMode(reqMode);
                    } else {
                        sendChat("拒绝了模式切换请求");
                    }
                }
                break;
            }
        }
    }

    private void onNetChat(String msg) {
        // 格式 玩家号:昵称:文本（已去除 CH: 前缀）
        try {
            String[] p = msg.split(":", 3);
            int who = Integer.parseInt(p[0]);
            String nick = p.length > 1 ? p[1] : "玩家" + who;
            String text = p.length > 2 ? p[2] : "";
            appendChat(nick + "：" + text, who == 1 ? PLAYER1_C : PLAYER2_C);
            SoundManager.sfxChatReceive();
        } catch (Exception e) { }
    }

    private void onNetDisconnect() {
        if (timer != null) timer.stop();
        netPlay = null;
        online = false;
        SoundManager.sfxNetDisconnect();
        JOptionPane.showMessageDialog(this, "对方已断开连接", "联机中断",
                JOptionPane.WARNING_MESSAGE);
        goHome();
    }

    // ==================== 界面更新 ====================

    private void updateInfo() {
        roundLabel.setText("第 " + Math.max(currentTurn, 0) + "/" + totalTurns + " 轮");
        String ph = "等待开始";
        if (phase == 0) ph = "绘画阶段";
        else if (phase == 1) ph = "猜测阶段";
        else if (phase == 2) ph = "本轮结束";
        else if (phase == 3) ph = "游戏结束";
        phaseLabel.setText(ph);

        String role;
        if (!gameStarted) role = "等待开始";
        else if (!online) role = "本回合画者：" + nickOf(drawer);
        else role = (myPlayer == drawer) ? "你是画者" : "你是猜者";
        roleLabel.setText(role);

        // 题目显示：联机画者直接醒目显示完整题目；其余情况遮罩
        boolean showIdiom = online && gameStarted && myPlayer == drawer
                && currentIdiom != null && !currentIdiom.isEmpty();
        if (!gameStarted || currentIdiom == null || currentIdiom.isEmpty()) {
            idiomLabel.setText("题目：—");
            idiomLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
            idiomLabel.setForeground(GOLD);
        } else if (showIdiom) {
            idiomLabel.setText("题目：" + currentIdiom);
            idiomLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
            idiomLabel.setForeground(GOLD);
        } else {
            int len = currentIdiom.length();
            String masked = "";
            for (int i = 0; i < len; i++) masked += "○";
            idiomLabel.setText("题目：" + masked + "（" + len + "字）");
            idiomLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
            idiomLabel.setForeground(GOLD);
        }
        updateControls();
        updateTimerLabel();
    }

    private void updateTimerLabel() {
        int t = Math.max(timeLeft, 0);
        timerLabel.setText(t + "s");
        if (phase == 0 || phase == 1) {
            if (t <= 10) timerLabel.setForeground(RED_C);
            else if (t <= 20) timerLabel.setForeground(GOLD);
            else timerLabel.setForeground(BTN_FG);
        } else {
            timerLabel.setForeground(SUB);
        }
    }

    private void updateControls() {
        boolean canDraw = canDraw();
        for (JButton b : colorBtns) b.setEnabled(canDraw);
        for (JButton b : sizeBtns) b.setEnabled(canDraw);
        eraserBtn.setEnabled(canDraw);
        clearBtn.setEnabled(canDraw);
        // 结束绘画：绘画阶段且当前用户是画者时可用
        finishDrawBtn.setEnabled(gameStarted && phase == 0
                && (!online || myPlayer == drawer));
        // 看题目按钮：本地模式游戏进行中可用
        peekBtn.setEnabled(gameStarted && currentIdiom != null && !currentIdiom.isEmpty());
        // 猜测输入：本地模式始终可输入；联机猜者可猜
        boolean canType = !gameStarted || phase != 0 || (online && myPlayer != drawer);
        guessInput.setEnabled(canType);
        sendBtn.setEnabled(canType);
    }

    private void updateToolSelection() {
        for (int i = 0; i < colorBtns.size(); i++) {
            JButton b = colorBtns.get(i);
            boolean sel = drawColor.equals(PALETTE[i]) && !erasing;
            b.setBorder(BorderFactory.createMatteBorder(
                sel ? 3 : 1, 1, sel ? 3 : 1, 1, GOLD));
        }
        for (int i = 0; i < sizeBtns.size(); i++) {
            JButton b = sizeBtns.get(i);
            boolean sel = brushSize == BRUSH_SIZES[i];
            b.setBackground(sel ? GOLD : BTN_BG);
            b.setForeground(sel ? UiKit.DARK_FG : BTN_FG);
        }
        eraserBtn.setBackground(erasing ? GOLD : BTN_BG);
        eraserBtn.setForeground(erasing ? UiKit.DARK_FG : BTN_FG);
    }

    private String nickOf(int p) {
        if (online && netPlay != null) {
            String n = netPlay.getPlayerNick(p);
            if (n != null && !n.isEmpty()) return n;
        }
        if (p == 1) return "玩家1";
        if (p == 2) return "玩家2";
        return "玩家" + p;
    }

    private Color playerColor(int p) {
        return p == 1 ? PLAYER1_C : PLAYER2_C;
    }

    private void appendChat(String text, Color c) {
        try {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, c);
            StyleConstants.setFontFamily(attrs, "微软雅黑");
            StyleConstants.setFontSize(attrs, 13);
            chatDoc.insertString(chatDoc.getLength(), text + "\n", attrs);
            chatPane.setCaretPosition(chatDoc.getLength());
        } catch (Exception e) { }
    }

    private void goHome() {
        if (timer != null) timer.stop();
        if (netPlay != null) {
            NetPlay np = netPlay;
            netPlay = null;
            np.close();
        }
        online = false;
        dispose();
        new HomeUI().setVisible(true);
    }

    // ==================== 联机切换游戏模式 ====================

    /** 切换模式按钮：主机直接切换并通知客户端；客户端发请求给主机 */
    private void onSwitchModeBtn() {
        if (!online || netPlay == null) return;
        String[] opts = {"烂头背枪", "五子棋双人", "俄罗斯方块", "你画我猜"};
        int c = JOptionPane.showOptionDialog(this, "选择要切换的游戏模式（当前游戏将结束）",
                "切换游戏模式", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, opts, opts[0]);
        if (c < 0) return;
        int mode = (c == 0) ? 1 : (c == 1) ? 2 : (c == 2) ? 4 : 5;
        if (myPlayer == 1) {
            netPlay.sendState("DGSWITCH:" + mode);
            switchToMode(mode);
        } else {
            netPlay.sendOp("DGSWITCHREQ:" + mode);
            appendChat("已向主机请求切换到【" + modeName(mode) + "】…", SUB);
        }
    }

    /** 切换到指定游戏模式（保留NetPlay连接） */
    private void switchToMode(int mode) {
        if (timer != null) timer.stop();
        NetPlay np = netPlay;
        netPlay = null;
        online = false;
        dispose();
        if (mode == 2) {
            new GomokuUI(np, myPlayer).setVisible(true);
        } else if (mode == 4) {
            new TetrisUI(np, myPlayer).setVisible(true);
        } else if (mode == 5) {
            new DrawGuessUI(np, myPlayer).setVisible(true);
        } else {
            new GameUI(np, myPlayer).setVisible(true);
        }
    }

    private String modeName(int mode) {
        switch (mode) {
            case 1: return "烂头背枪";
            case 2: return "五子棋双人";
            case 3: return "五子棋三人";
            case 4: return "俄罗斯方块";
            case 5: return "你画我猜";
            default: return "未知模式";
        }
    }

    private void showRules() {
        String rules = "【你画我猜 · 游戏规则】\n\n"
            + "1. 每轮系统随机出一个四字成语作为题目。\n"
            + "2. 画者根据题目在画板上作画，不能写字或拼音。\n"
            + "3. 猜者在聊天框输入答案，答对即本轮成功。\n"
            + "4. 绘画时间结束后进入猜测阶段，猜者继续答题。\n"
            + "   画者也可点击下方\"结束绘画\"提前进入猜测阶段。\n"
            + "5. 回合结束后画者与猜者互换，进行下一轮。\n"
            + "6. 所有轮次结束后显示合作成绩。\n\n"
            + "提示：联机时画者右侧直接显示题目；\n"
            + "联机可点右上角\"切换模式\"换游戏。";
        JOptionPane.showMessageDialog(this, rules, "游戏规则",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== 画板 ====================

    @SuppressWarnings("serial")
    private class DrawingPanel extends JPanel {
        private BufferedImage img;
        private int lastX = -1, lastY = -1;
        private boolean drawing = false;

        DrawingPanel() {
            setBackground(CANVAS_BG);
            setOpaque(true);
            setBorder(BorderFactory.createLineBorder(BORDER, 2));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (!canDraw()) return;
                    drawing = true;
                    lastX = e.getX();
                    lastY = e.getY();
                    drawLocal(lastX, lastY, lastX, lastY);
                }
                public void mouseReleased(MouseEvent e) {
                    drawing = false;
                    lastX = lastY = -1;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (!drawing || !canDraw()) return;
                    int x = e.getX(), y = e.getY();
                    drawLocal(lastX, lastY, x, y);
                    lastX = x;
                    lastY = y;
                }
            });
        }

        private void ensureImage() {
            int w = Math.max(getWidth(), 10);
            int h = Math.max(getHeight(), 10);
            if (img == null || img.getWidth() != w || img.getHeight() != h) {
                BufferedImage old = img;
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(CANVAS_BG);
                g.fillRect(0, 0, w, h);
                if (old != null) g.drawImage(old, 0, 0, null);
                g.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            ensureImage();
            g.drawImage(img, 0, 0, null);
        }

        void drawLine(int x1, int y1, int x2, int y2, Color c, int size) {
            ensureImage();
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c);
            g.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
            g.dispose();
            repaint();
        }

        void clearAll() {
            if (img == null) return;
            Graphics2D g = img.createGraphics();
            g.setColor(CANVAS_BG);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.dispose();
            repaint();
        }
    }
}