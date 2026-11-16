import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 五子棋界面：15×15棋盘，黑先白后（三人模式加绿方），五连获胜。
 * 支持本地双人/三人和局域网联机（主机=黑方先手）。
 */
public class GomokuUI extends JFrame {
    private GomokuBoard board;
    private JLabel turnLabel;
    private JLabel statusLabel;

    // 聊天
    private JTextPane chatPane;
    private javax.swing.text.StyledDocument chatDoc;
    private JTextField chatInput;

    // 玩家信息面板
    private JPanel[] playerRows = new JPanel[3];
    private JLabel[] nickLabels = new JLabel[3];
    private JLabel specLabel;

    // 联机
    private NetPlay netPlay;
    private boolean online = false;
    private int myPlayer = 0;          // 0=本地, 1=黑方(主机), 2=白方, 3=绿方
    private int numPlayers = 2;        // 2人或3人
    private JDialog settlementDlg;

    // 人机对战
    private boolean vsAI = false;      // 人机模式
    private java.util.Map<Integer, GomokuAI> aiMap = new java.util.HashMap<Integer, GomokuAI>(); // AI玩家号→AI实例
    private javax.swing.Timer aiTimer;

    private static final Color BG        = new Color(20, 23, 32);
    private static final Color PANEL_BG  = new Color(30, 34, 46);
    private static final Color BLACK_TXT = new Color(235, 235, 240);
    private static final Color WHITE_TXT = new Color(168, 198, 255);
    private static final Color GOLD      = new Color(255, 215, 110);
    private static final Color SUB       = new Color(160, 168, 188);
    private static final Color BTN_BG    = new Color(52, 58, 76);
    private static final Color BTN_FG    = new Color(225, 228, 238);
    private static final Color BORDER    = new Color(58, 64, 82);

    public GomokuUI() {
        initUI();
        refresh();
    }

    /** 本地多人模式：numPlayers=2或3 */
    public GomokuUI(int numPlayers) {
        this.numPlayers = numPlayers;
        initUI();
        refresh();
    }

    /** 人机模式：aiPlayers为AI执子号数组（如{2}=二人AI白方，{2,3}=三人1人vs2电脑，{3}=三人2人vs1电脑） */
    public GomokuUI(int numPlayers, int[] aiPlayers) {
        this.numPlayers = numPlayers;
        if (aiPlayers != null && aiPlayers.length > 0) {
            this.vsAI = true;
            for (int p : aiPlayers) {
                aiMap.put(p, new GomokuAI(p, numPlayers));
            }
        }
        initUI();
        refresh();
    }

    /** 联机模式：主机=黑方（先手），客户端=白方 */
    public GomokuUI(NetPlay np, int player) {
        this.netPlay = np;
        this.myPlayer = player;
        this.online = true;
        initUI();
        netPlay.setListener(mkNetListener());
        refresh();
    }

    /** 联机三人模式：player=1/2/3 */
    public GomokuUI(NetPlay np, int player, int numPlayers) {
        this.netPlay = np;
        this.myPlayer = player;
        this.numPlayers = numPlayers;
        this.online = true;
        initUI();
        netPlay.setListener(mkNetListener());
        refresh();
    }

    private void initUI() {
        setTitle(numPlayers == 3 ? "五子棋（三人）" : "五子棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        setAppIcon();
        add(buildTop(), BorderLayout.NORTH);

        board = new GomokuBoard();
        board.setNumPlayers(numPlayers);
        board.setListener(new GomokuBoard.Listener() {
            public void onCellClick(int r, int c) { GomokuUI.this.onCellClick(r, c); }
        });
        add(board, BorderLayout.CENTER);
        add(buildRightPanel(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(getWidth() * 7 / 10, getHeight() * 7 / 10));
        SoundManager.switchBgm(2);

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
            if (online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (aiTimer != null) aiTimer.stop();
            board.reset();
            if (online && netPlay != null) netPlay.sendState("NEW");
            SoundManager.sfxGameStart();
            refresh();
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
        gbc.insets = new Insets(0, 16, 0, 8);
        gbc.anchor = GridBagConstraints.WEST;
        JLabel title = new JLabel("五子棋");
        title.setFont(new Font("微软雅黑", Font.BOLD, 17));
        title.setForeground(GOLD);
        gbc.gridx = 0; gbc.weightx = 0;
        top.add(title, gbc);

        turnLabel = new JLabel();
        turnLabel.setFont(new Font("微软雅黑", Font.BOLD, 15));
        gbc.gridx = 1; gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 0);
        top.add(turnLabel, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton homeBtn = mkBtn("主页");
        JButton ruleBtn = mkBtn("规则");
        JButton restartBtn = mkBtn("重开");
        btns.add(restartBtn);
        btns.add(ruleBtn);
        btns.add(homeBtn);
        gbc.gridx = 2; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 8, 0, 16);
        top.add(btns, gbc);

        homeBtn.addActionListener(e -> goHome());
        ruleBtn.addActionListener(e -> showRules());
        restartBtn.addActionListener(e -> {
            if (online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (aiTimer != null) aiTimer.stop();
            board.reset();
            if (online && netPlay != null) netPlay.sendState("NEW");
            SoundManager.sfxGameStart();
            refresh();
        });
        return top;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(PANEL_BG);
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(7, 16, 9, 16)));
        statusLabel = new JLabel("黑方先行，点击交叉点落子");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        statusLabel.setForeground(SUB);
        bottom.add(statusLabel, BorderLayout.CENTER);
        return bottom;
    }

    // ==================== 聊天 ====================

    /** 右侧面板：玩家信息（上）+ 聊天（下） */
    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 0));
        right.setBackground(PANEL_BG);
        right.setPreferredSize(new Dimension(216, 0));
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));
        right.add(buildPlayerInfoPanel(), BorderLayout.NORTH);
        right.add(buildChatPanel(), BorderLayout.CENTER);
        return right;
    }

    /** 玩家信息面板：各玩家昵称 + 观众人数 */
    private JPanel buildPlayerInfoPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 6, 0);
        gbc.anchor = GridBagConstraints.WEST;
        JLabel title = new JLabel("玩家");
        title.setFont(new Font("微软雅黑", Font.BOLD, 13));
        title.setForeground(GOLD);
        p.add(title, gbc);

        gbc.gridwidth = 2;
        for (int i = 0; i < 3; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setOpaque(false);
            JLabel dot = new JLabel("●");
            dot.setFont(new Font("微软雅黑", Font.BOLD, 12));
            dot.setForeground(playerColor(i + 1));
            nickLabels[i] = new JLabel(" ");
            nickLabels[i].setFont(new Font("微软雅黑", Font.PLAIN, 12));
            nickLabels[i].setForeground(BTN_FG);
            row.add(dot);
            row.add(nickLabels[i]);
            playerRows[i] = row;
            gbc.gridy = i + 1;
            gbc.insets = new Insets(0, 0, 0, 0);
            p.add(row, gbc);
        }

        gbc.gridy = 4;
        gbc.insets = new Insets(6, 0, 0, 0);
        specLabel = new JLabel(" ");
        specLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        specLabel.setForeground(SUB);
        p.add(specLabel, gbc);

        return p;
    }

    /** 更新玩家信息面板显示 */
    private void updatePlayerInfo() {
        for (int i = 0; i < 3; i++) {
            boolean show = (i < numPlayers);
            playerRows[i].setVisible(show);
            if (show) {
                String nick;
                if (online && netPlay != null) {
                    nick = netPlay.getPlayerNick(i + 1);
                    if (nick == null || nick.isEmpty()) nick = playerName(i + 1);
                    if (myPlayer == i + 1) nick += "（我）";
                } else if (vsAI && aiMap.containsKey(i + 1)) {
                    nick = "电脑";
                } else {
                    nick = playerName(i + 1);
                }
                nickLabels[i].setText(nick);
            }
        }
        if (online && netPlay != null) {
            int sc = netPlay.getSpectatorCount();
            specLabel.setText(sc > 0 ? "观众：" + sc + " 人" : " ");
            specLabel.setVisible(true);
        } else {
            specLabel.setVisible(false);
        }
    }

    private JPanel buildChatPanel() {
        JPanel chat = new JPanel(new BorderLayout(0, 0));
        chat.setBackground(PANEL_BG);

        JLabel title = new JLabel(" 聊天");
        title.setFont(new Font("微软雅黑", Font.BOLD, 13));
        title.setForeground(GOLD);
        title.setBackground(PANEL_BG);
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        title.setPreferredSize(new Dimension(0, 40));
        chat.add(title, BorderLayout.NORTH);

        chatPane = new JTextPane() {
            public boolean getScrollableTracksViewportWidth() { return true; }
        };
        chatPane.setEditable(false);
        chatPane.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        chatPane.setBackground(new Color(26, 29, 40));
        chatDoc = chatPane.getStyledDocument();
        JScrollPane sp = new JScrollPane(chatPane);
        sp.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        chat.add(sp, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(6, 0));
        input.setBackground(PANEL_BG);
        input.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        chatInput = new JTextField();
        chatInput.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        chatInput.setBackground(new Color(40, 44, 58));
        chatInput.setForeground(BTN_FG);
        chatInput.setCaretColor(BTN_FG);
        chatInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90, 98, 122), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        chatInput.addActionListener(e -> sendChat());
        input.add(chatInput, BorderLayout.CENTER);

        JButton sendBtn = mkBtn("发送");
        sendBtn.addActionListener(e -> sendChat());
        input.add(sendBtn, BorderLayout.EAST);
        chat.add(input, BorderLayout.SOUTH);
        return chat;
    }

    /** 玩家名称：1黑方 2白方 3绿方 */
    private String playerName(int p) {
        switch (p) {
            case 1: return "黑方";
            case 2: return "白方";
            case 3: return "绿方";
            default: return "?";
        }
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        text = text.replace("\n", " ").replace("\r", " ");
        String who;
        if (online) {
            who = "我";
            if (netPlay != null) {
                // 格式：玩家号:昵称:文本（0=观众）
                netPlay.sendChat(myPlayer + ":" + SoundManager.getNickname() + ":" + text);
            }
        } else {
            who = playerName(board.getCurrentPlayer());
        }
        Color c = online ? playerColor(myPlayer) : playerColor(board.getCurrentPlayer());
        appendChat(who, text, c);
        SoundManager.sfxChatSend();
    }

    private void appendChat(String who, String text) {
        Color c;
        if (who.equals("黑方")) c = BLACK_TXT;
        else if (who.equals("白方")) c = WHITE_TXT;
        else if (who.equals("绿方")) c = new Color(110, 220, 130);
        else c = GOLD;
        appendChat(who, text, c);
    }

    private void appendChat(String who, String text, Color c) {
        javax.swing.text.Style style = chatDoc.addStyle("s", null);
        javax.swing.text.StyleConstants.setForeground(style, c);
        try {
            chatDoc.insertString(chatDoc.getLength(), who + "：" + text + "\n", style);
        } catch (Exception e) { }
        chatPane.setCaretPosition(chatDoc.getLength());
    }

    private void onChatReceived(String msg) {
        // 联机消息格式 "玩家号:昵称:文本"（玩家号 0=观众）
        String[] p = msg.split(":", 3);
        if (p.length == 3) {
            try {
                int pn = Integer.parseInt(p[0]);
                Color c = pn == 0 ? SUB : playerColor(pn);
                appendChat(p[1], p[2], c);
                SoundManager.sfxChatReceive();
                return;
            } catch (NumberFormatException e) { }
        }
        appendChat("对方", msg);
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

    // ==================== 联机 ====================

    private void goHome() {
        if (aiTimer != null) { aiTimer.stop(); aiTimer = null; }
        if (netPlay != null) {
            NetPlay np = netPlay;
            netPlay = null;
            np.close();
        }
        online = false; myPlayer = 0;
        dispose();
        new HomeUI().setVisible(true);
    }

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { }
            public void onStateReceived(String state) { GomokuUI.this.onNetState(state); }
            public void onOpReceived(String op) { GomokuUI.this.onNetOp(op); }
            public void onChatReceived(String msg) { GomokuUI.this.onChatReceived(msg); }
            public void onDisconnected() { GomokuUI.this.onNetDisconnect(); }
        };
    }

    private void onNetState(String state) {
        if ("NEW".equals(state)) {
            board.reset();
            closeSettlement();
            refresh();
        } else if (state.startsWith("NICKS:") || state.startsWith("SPEC:")) {
            updatePlayerInfo();
        }
    }

    private void onNetOp(String op) {
        if (board.isGameOver()) return;
        String[] p = op.split(":");
        if ("PUT".equals(p[0])) {
            int r = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]);
            if (board.get(r, c) == 0) {
                board.put(r, c);
                SoundManager.sfxChessPlace();
            }
            refresh();
            if (board.isGameOver()) showGameOverDialog();
        }
    }

    private void onNetDisconnect() {
        if (netPlay == null) return;
        boolean wasOnline = online;
        NetPlay np = netPlay;
        netPlay = null;
        np.close();
        online = false; myPlayer = 0;
        closeSettlement();
        if (wasOnline) {
            SoundManager.sfxNetDisconnect();
            JOptionPane.showMessageDialog(this, "有玩家断开，回到本地模式", "联机", JOptionPane.WARNING_MESSAGE);
        }
        board.reset();
        refresh();
    }

    // ==================== 落子逻辑 ====================

    private void onCellClick(int r, int c) {
        if (board.isGameOver()) return;
        if (netPlay != null && !online) return;                    // 联机等待中不可操作
        if (online && board.getCurrentPlayer() != myPlayer) return; // 等对方落子
        if (vsAI && aiMap.containsKey(board.getCurrentPlayer())) return; // AI回合人类不可操作
        if (board.get(r, c) != 0) return;
        board.put(r, c);
        SoundManager.sfxChessPlace();
        if (online && netPlay != null) netPlay.sendOp("PUT:" + r + ":" + c);
        refresh();
        if (board.isGameOver()) showGameOverDialog();
        else maybeAiTurn();
    }

    // ==================== 人机对战 ====================

    /** 检查是否轮到AI，是则延迟触发AI落子 */
    private void maybeAiTurn() {
        if (!vsAI || board.isGameOver()) return;
        int cur = board.getCurrentPlayer();
        if (!aiMap.containsKey(cur)) return;   // 人类回合
        if (aiTimer != null) aiTimer.stop();
        aiTimer = new javax.swing.Timer(600, e -> doAiTurn());
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    /** 执行AI落子（三人模式可能连续多个AI回合） */
    private void doAiTurn() {
        if (!vsAI || board.isGameOver()) return;
        int cur = board.getCurrentPlayer();
        GomokuAI ai = aiMap.get(cur);
        if (ai == null) return;
        int[] move = ai.decide(board);
        if (move == null || board.get(move[0], move[1]) != 0) return;
        board.put(move[0], move[1]);
        SoundManager.sfxChessPlace();
        refresh();
        if (board.isGameOver()) showGameOverDialog();
        else maybeAiTurn();   // 下一个可能也是AI
    }

    // ==================== 刷新 ====================

    private Color playerColor(int p) {
        if (p == 1) return BLACK_TXT;
        if (p == 2) return WHITE_TXT;
        return new Color(110, 220, 130);
    }

    private void refresh() {
        if (board.isGameOver()) {
            String t;
            if (board.isDraw()) t = "平局";
            else t = playerName(board.getWinner()) + "胜";
            turnLabel.setText(t);
            turnLabel.setForeground(new Color(200, 200, 200));
            statusLabel.setText(board.isDraw() ? "棋盘已满，平局" : "五连达成！");
        } else if (online && myPlayer == 0) {
            // 观众
            turnLabel.setText("观战中  ·  " + playerName(board.getCurrentPlayer()) + "回合");
            turnLabel.setForeground(playerColor(board.getCurrentPlayer()));
            statusLabel.setText("你是观众，观战中...");
        } else if (online) {
            boolean myTurn = board.getCurrentPlayer() == myPlayer;
            turnLabel.setText((myTurn ? "你的回合" : "对方回合")
                    + "  ·  " + playerName(myPlayer));
            turnLabel.setForeground(playerColor(myPlayer));
            statusLabel.setText(myTurn ? "点击交叉点落子" : "等待对方落子...");
        } else if (vsAI) {
            int cur = board.getCurrentPlayer();
            boolean aiTurn = aiMap.containsKey(cur);
            int humanCount = numPlayers - aiMap.size();
            String turnText;
            if (aiTurn) turnText = "电脑思考中…";
            else if (humanCount == 1) turnText = "你的回合";
            else turnText = playerName(cur) + "回合";
            turnLabel.setText(turnText + "  ·  " + playerName(cur));
            turnLabel.setForeground(playerColor(cur));
            statusLabel.setText(aiTurn ? "等待电脑落子..." : "点击交叉点落子");
        } else {
            turnLabel.setText(playerName(board.getCurrentPlayer()) + "回合");
            turnLabel.setForeground(playerColor(board.getCurrentPlayer()));
        }
        updatePlayerInfo();
    }

    private void showGameOverDialog() {
        boolean win = false, lose = false;
        String title, sub;
        Color titleColor;
        if (board.isDraw()) {
            title = "平 局";
            sub = "棋盘已满，势均力敌";
            titleColor = new Color(215, 218, 228);
        } else if (online && myPlayer == 0) {
            // 观众
            title = playerName(board.getWinner()) + "胜利";
            sub = "五子连珠！";
            titleColor = playerColor(board.getWinner());
        } else if (online) {
            win = board.getWinner() == myPlayer;
            lose = !win;
            title = win ? "胜 利" : "失 败";
            sub = win ? "五子连珠，恭喜获胜！" : playerName(board.getWinner()) + "先连五子，再接再厉！";
            titleColor = win ? GOLD : new Color(205, 110, 110);
        } else if (vsAI) {
            int w = board.getWinner();
            if (aiMap.containsKey(w)) {
                // AI获胜 → 人类失败
                win = false; lose = true;
                title = "失 败";
                sub = "电脑先连五子，再接再厉！";
                titleColor = new Color(205, 110, 110);
            } else {
                // 人类获胜
                win = true; lose = false;
                title = "胜 利";
                sub = "五子连珠，恭喜获胜！";
                titleColor = GOLD;
            }
        } else {
            title = playerName(board.getWinner()) + "胜利";
            sub = "五子连珠！再来一局？";
            titleColor = playerColor(board.getWinner());
            win = true;
        }
        if (win && !lose) SoundManager.sfxWin();
        else if (lose) SoundManager.sfxLose();
        showSettlement(title, sub, titleColor, lose);
    }

    private void closeSettlement() {
        if (settlementDlg != null) {
            settlementDlg.dispose();
            settlementDlg = null;
        }
    }

    /** 胜利/失败结算页面 */
    private void showSettlement(final String title, String sub, final Color titleColor, final boolean lose) {
        final JDialog dlg = new JDialog(this, "游戏结束", true);
        dlg.setResizable(false);
        dlg.setUndecorated(true);
        settlementDlg = dlg;

        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(24, 27, 38));
                g2.fillRect(0, 0, getWidth(), getHeight());
                Color glowColor = lose ? new Color(160, 50, 50, 100) : new Color(255, 200, 90, 90);
                RadialGradientPaint glow = new RadialGradientPaint(
                        getWidth() / 2f, 80f, 170f,
                        new float[]{0f, 1f},
                        new Color[]{glowColor, new Color(0, 0, 0, 0)});
                g2.setPaint(glow);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setLayout(new BorderLayout(0, 0));
        panel.setPreferredSize(new Dimension(340, 280));
        panel.setBorder(BorderFactory.createLineBorder(new Color(80, 86, 108), 2));

        JLabel titleLb = new JLabel(title, SwingConstants.CENTER);
        titleLb.setFont(new Font("微软雅黑", Font.BOLD, 44));
        titleLb.setForeground(titleColor);
        titleLb.setBorder(BorderFactory.createEmptyBorder(46, 0, 6, 0));
        panel.add(titleLb, BorderLayout.NORTH);

        JLabel subLb = new JLabel(sub, SwingConstants.CENTER);
        subLb.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        subLb.setForeground(SUB);
        panel.add(subLb, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 26, 0));
        JButton againBtn = mkBtn("再来一局");
        JButton homeBtn2 = mkBtn("返回主页");
        againBtn.setPreferredSize(new Dimension(120, 38));
        homeBtn2.setPreferredSize(new Dimension(120, 38));
        btnPanel.add(againBtn);
        btnPanel.add(homeBtn2);
        panel.add(btnPanel, BorderLayout.SOUTH);

        againBtn.addActionListener(e -> {
            if (online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开，或等待主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            closeSettlement();
            if (aiTimer != null) aiTimer.stop();
            board.reset();
            if (online && netPlay != null) netPlay.sendState("NEW");
            SoundManager.sfxGameStart();
            refresh();
        });
        homeBtn2.addActionListener(e -> {
            closeSettlement();
            goHome();
        });

        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showRules() {
        String rules = "15×15棋盘，黑方先行，轮流在交叉点落子\n"
                + "\n先在横、竖、斜任一方向连成五子者获胜"
                + "\n棋盘下满仍无五连则为平局\n"
                + (numPlayers == 3
                    ? (vsAI
                        ? (aiMap.size() == 2
                            ? "\n三人人机：你执黑方先行，电脑执白方和绿方"
                            : "\n三人人机：黑方和白方由玩家操作，电脑执绿方")
                        : "\n三人模式：黑→白→绿轮流落子，先连五子者胜")
                    : (vsAI
                        ? "\n人机模式：你执黑方先行，电脑执白方"
                        : "\n联机：主机=黑方（先手），客户端=白方"))
                + "\n右侧聊天框可随时发消息";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setForeground(new Color(228, 231, 240));
        ta.setBackground(new Color(24, 27, 38));
        ta.setCaretColor(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(400, 240));
        JOptionPane.showMessageDialog(this, sp, "五子棋规则", JOptionPane.INFORMATION_MESSAGE);
    }
}