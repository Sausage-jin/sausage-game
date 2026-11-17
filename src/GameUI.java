import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 游戏界面：纯点击操作，无模式切换。
 * 点任意暗牌=翻牌；点己方明牌=选中；选中后点相邻格=移动/对战/合成。
 * 支持局域网联机：主机=红方，客户端=蓝方。
 */
public class GameUI extends JFrame {
    private GameLogic game;
    private BoardPanel boardPanel;
    private JLabel turnLabel;
    private JLabel statusLabel;
    private int selRow = -1, selCol = -1;
    private boolean animLock = false;

    // 聊天
    private JTextPane chatPane;
    private javax.swing.text.StyledDocument chatDoc;
    private JTextField chatInput;

    // 玩家信息面板
    private JPanel[] playerRows = new JPanel[2];
    private JLabel[] nickLabels = new JLabel[2];
    private JLabel specLabel;

    // 联机
    private NetPlay netPlay;
    private boolean online = false;
    private int myPlayer = 0;          // 0=本地双人, 1=红方(主机), 2=蓝方(客户端)
    private boolean remoteOp = false;  // 正在执行远程操作（不回发）
    private int lastFlipRow = -1, lastFlipCol = -1;
    private static final int NET_PORT = 3721;
    private JDialog settlementDlg;     // 结算弹窗（主机重开时自动关闭）

    // 人机对战
    private boolean vsAI = false;      // 是否人机模式（人类=红方1，AI=蓝方2）
    private GameAI ai;
    private javax.swing.Timer aiTimer;

    private static final Color BG       = new Color(20, 23, 32);
    private static final Color PANEL_BG = new Color(30, 34, 46);
    private static final Color P1_TEXT  = new Color(255, 110, 100);
    private static final Color P2_TEXT  = new Color(110, 155, 255);
    private static final Color GOLD     = new Color(255, 215, 110);
    private static final Color SUB      = new Color(160, 168, 188);
    private static final Color BTN_BG   = new Color(52, 58, 76);
    private static final Color BTN_FG   = new Color(225, 228, 238);
    private static final Color BORDER   = new Color(58, 64, 82);
    private static final Color XIAOKAI  = new Color(110, 220, 170);  // 小开（机器人提示）

    public GameUI() {
        game = new GameLogic();
        initUI();
        refresh();
    }

    /** 人机对战模式：人类执红方(1)，AI执蓝方(2) */
    public GameUI(boolean vsAI) {
        game = new GameLogic();
        this.vsAI = vsAI;
        if (vsAI) this.ai = new GameAI(2);
        initUI();
        refresh();
    }

    /** 联机模式：由主页建立连接后进入，主机=红方，客户端=蓝方 */
    public GameUI(NetPlay np, int player) {
        game = new GameLogic();
        this.netPlay = np;
        this.myPlayer = player;
        this.online = true;
        initUI();
        netPlay.setListener(mkNetListener());
        if (myPlayer == 1) {
            game.reset();
            selRow = -1; selCol = -1; animLock = false;
            boardPanel.clearSelection();
            netPlay.sendState(game.serialize());
        }
        refresh();
    }

    private void initUI() {
        setTitle("烂头背枪");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        setAppIcon();
        SoundManager.switchBgm(1);   // 烂头背枪背景音乐
        add(buildTop(), BorderLayout.NORTH);

        boardPanel = new BoardPanel(game);
        boardPanel.setListener(new BoardPanel.Listener() {
            public void onCellClick(int row, int col) { GameUI.this.onCellClick(row, col); }
            public void onFlipDone() { GameUI.this.onFlipDone(); }
        });
        add(boardPanel, BorderLayout.CENTER);
        add(buildRightPanel(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension((int) (getWidth() * 0.7), (int) (getHeight() * 0.7)));
        setLocationRelativeTo(null);
        setResizable(true);

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
            if (netPlay != null && online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (aiTimer != null) aiTimer.stop();
            game.reset();
            selRow = -1; selCol = -1; animLock = false;
            boardPanel.clearSelection();
            if (netPlay != null && online) netPlay.sendState(game.serialize());
            SoundManager.sfxCardDeal();
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
        JLabel title = new JLabel("烂头背枪");
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
            if (netPlay != null && online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            game.reset();
            selRow = -1; selCol = -1; animLock = false;
            boardPanel.clearSelection();
            if (netPlay != null && online) netPlay.sendState(game.serialize());
            SoundManager.sfxCardDeal();
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
        statusLabel = new JLabel(" ");
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

    /** 玩家信息面板：红蓝双方昵称 + 观众人数 */
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

        Color[] dots = { P1_TEXT, P2_TEXT };
        String[] names = { "红方", "蓝方" };
        for (int i = 0; i < 2; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setOpaque(false);
            JLabel dot = new JLabel("●");
            dot.setFont(new Font("微软雅黑", Font.BOLD, 12));
            dot.setForeground(dots[i]);
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

        gbc.gridy = 3;
        gbc.insets = new Insets(6, 0, 0, 0);
        specLabel = new JLabel(" ");
        specLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        specLabel.setForeground(SUB);
        p.add(specLabel, gbc);

        return p;
    }

    /** 更新玩家信息面板显示 */
    private void updatePlayerInfo() {
        String[] names = { "红方", "蓝方" };
        for (int i = 0; i < 2; i++) {
            String nick;
            if (online && netPlay != null) {
                nick = netPlay.getPlayerNick(i + 1);
                if (nick == null || nick.isEmpty()) nick = names[i];
                if (myPlayer == i + 1) nick += "（我）";
            } else if (vsAI && i == 1) {
                nick = "电脑";
            } else {
                nick = names[i];
            }
            nickLabels[i].setText(nick);
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

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        text = text.replace("\n", " ").replace("\r", " ");
        String who;
        Color c;
        if (online) {
            who = "我";
            c = myPlayer == 0 ? SUB : (myPlayer == 1 ? P1_TEXT : P2_TEXT);
            if (netPlay != null) {
                // 格式：玩家号:昵称:文本（0=观众）
                netPlay.sendChat(myPlayer + ":" + SoundManager.getNickname() + ":" + text);
            }
        } else {
            boolean p1 = game.getCurrentPlayer() == 1;
            who = p1 ? "红方" : "蓝方";
            c = p1 ? P1_TEXT : P2_TEXT;
        }
        appendChat(who, text, c);
        SoundManager.sfxChatSend();
    }

    private void appendChat(String who, String text) {
        Color c = who.equals("红方") ? P1_TEXT : who.equals("蓝方") ? P2_TEXT : GOLD;
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

    /** 小开发言：在聊天框显示机器人提示 */
    private void xiaokaiSay(String text) {
        appendChat("小开", text, XIAOKAI);
        SoundManager.sfxChatReceive();
    }

    /** 操作失败：小开解释原因 */
    private void onActionFailed() {
        String hint = game.getLastErrorHint();
        if (hint != null) {
            xiaokaiSay(hint);
            game.clearLastErrorHint();
        }
        cancelSel();
        refresh();
    }

    private void onChatReceived(String msg) {
        // 联机消息格式 "玩家号:昵称:文本"（玩家号 0=观众）
        String[] p = msg.split(":", 3);
        if (p.length == 3) {
            try {
                int pn = Integer.parseInt(p[0]);
                Color c = pn == 0 ? SUB : (pn == 1 ? P1_TEXT : P2_TEXT);
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

    /** 设置窗口图标 */
    private void setAppIcon() {
        try {
            File f = new File("image/home.png");
            if (f.exists()) {
                BufferedImage icon = ImageIO.read(f);
                setIconImage(icon);
            }
        } catch (Exception e) { /* 无图标不影响运行 */ }
    }

    // ==================== 联机 ====================

    /** 返回主页：断开联机，关闭游戏窗口，重新打开主页 */
    private void goHome() {
        if (aiTimer != null) { aiTimer.stop(); aiTimer = null; }
        if (netPlay != null) {
            NetPlay np = netPlay;
            netPlay = null;
            np.close();
        }
        online = false; myPlayer = 0; remoteOp = false; animLock = false;
        dispose();
        new HomeUI().setVisible(true);
    }

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { /* 连接由主页处理 */ }
            public void onStateReceived(String state) { GameUI.this.onNetState(state); }
            public void onOpReceived(String op) { GameUI.this.onNetOp(op); }
            public void onChatReceived(String msg) { GameUI.this.onChatReceived(msg); }
            public void onDisconnected() { GameUI.this.onNetDisconnect(); }
        };
    }

    private void onNetState(String state) {
        if (state.startsWith("NICKS:") || state.startsWith("SPEC:")) {
            updatePlayerInfo();
            return;
        }
        game.deserialize(state);
        online = true;
        selRow = -1; selCol = -1; animLock = false;
        remoteOp = false;
        closeSettlement();
        boardPanel.clearSelection();
        refresh();
    }

    private void onNetOp(String op) {
        if (game.isGameOver()) return;
        String[] p = op.split(":");
        if ("FLIP".equals(p[0])) {
            remoteOp = true;
            animLock = true;
            SoundManager.sfxCardFlip();
            boardPanel.animateFlip(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } else if ("MOVE".equals(p[0])) {
            game.move(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                      Integer.parseInt(p[3]), Integer.parseInt(p[4]));
            SoundManager.sfxCardEat();
            boardPanel.animateBattle(Integer.parseInt(p[3]), Integer.parseInt(p[4]));
            cancelSel();
            refresh();
            if (game.isGameOver()) showGameOverDialog();
        } else if ("SYN".equals(p[0])) {
            game.synthesize(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                            Integer.parseInt(p[3]), Integer.parseInt(p[4]));
            SoundManager.sfxCardMerge();
            cancelSel();
            refresh();
            if (game.isGameOver()) showGameOverDialog();
        }
    }

    private void onNetDisconnect() {
        if (netPlay == null) return;
        boolean wasOnline = online;
        NetPlay np = netPlay;
        netPlay = null;
        np.close();
        online = false; myPlayer = 0; remoteOp = false; animLock = false;
        closeSettlement();
        if (wasOnline) {
            SoundManager.sfxNetDisconnect();
            JOptionPane.showMessageDialog(this, "对方已断开，回到本地模式", "联机", JOptionPane.WARNING_MESSAGE);
        }
        game.reset();
        selRow = -1; selCol = -1;
        boardPanel.clearSelection();
        refresh();
    }

    // ==================== 点击逻辑（纯点击，无模式） ====================

    private void onCellClick(int row, int col) {
        if (game.isGameOver() || animLock) return;
        if (netPlay != null && !online) return;           // 联机等待中不可操作
        if (online && game.getCurrentPlayer() != myPlayer) return; // 等对方行动
        if (vsAI && game.getCurrentPlayer() == 2) return; // AI回合，人类不可操作
        Card card = game.getCard(row, col);

        if (selRow == -1) {
            if (card == null) return;
            if (!card.isFaceUp()) {
                animLock = true;
                remoteOp = false;
                lastFlipRow = row; lastFlipCol = col;
                SoundManager.sfxCardFlip();
                boardPanel.animateFlip(row, col);
            } else if (card.getOwner() == game.getCurrentPlayer()
                    || (card.getOwner() == 0 && card.getType().isMovable())) {
                selRow = row; selCol = col;
                boardPanel.setSelection(row, col); SoundManager.sfxCardSelect();
            } else if (card.getOwner() == 0 && !card.getType().isMovable()) {
                xiaokaiSay("他们没有腿");
            }
            return;
        }

        // 已选中
        if (selRow == row && selCol == col) { cancelSel(); return; }
        Card selCard = game.getCard(selRow, selCol);
        if (selCard == null) { cancelSel(); return; }
        int cur = game.getCurrentPlayer();

        if (isAdjacent(selRow, selCol, row, col)) {
            if (card == null) {
                game.move(selRow, selCol, row, col);
                sendOpIfOnline("MOVE:" + selRow + ":" + selCol + ":" + row + ":" + col);
                SoundManager.sfxCardMove();
                afterAction();
            } else if (card.isFaceUp()
                    && BoardPanel.isMergePair(selCard.getType(), card.getType())
                    && ((selCard.getOwner() == cur && card.getOwner() == 0)
                     || (selCard.getOwner() == 0 && card.getOwner() == cur))) {
                // 合成：己方烂头+中立洋枪 / 己方小孩+中立捕虫网
                game.synthesize(selRow, selCol, row, col);
                sendOpIfOnline("SYN:" + selRow + ":" + selCol + ":" + row + ":" + col);
                SoundManager.sfxCardMerge();
                afterAction();
            } else if (card.isFaceUp()
                    && ((selCard.getOwner() == cur && card.getOwner() == 0)
                     || (selCard.getOwner() == 0 && card.getOwner() == cur))
                    && (card.getType().isQiang() || card.getType().isBuchong()
                     || selCard.getType().isQiang() || selCard.getType().isBuchong())
                    && !BoardPanel.isMergePair(selCard.getType(), card.getType())) {
                // 合成组合不符
                xiaokaiSay("你想的美");
            } else if (card.getOwner() == cur) {
                if (card.isFaceUp()) {
                    selRow = row; selCol = col;
                    boardPanel.setSelection(row, col); SoundManager.sfxCardSelect();
                } else {
                    cancelSel();
                }
            } else {
                // 敌方牌→对战；中立牌→巨石交换/击杀猫咪旺财
                if (game.move(selRow, selCol, row, col)) {
                    sendOpIfOnline("MOVE:" + selRow + ":" + selCol + ":" + row + ":" + col);
                    SoundManager.sfxBattle();
                    SoundManager.sfxCardEat();
                    boardPanel.animateBattle(row, col);
                    afterAction();
                } else {
                    onActionFailed();
                }
            }
        } else {
            if (card != null && card.isFaceUp()
                    && (card.getOwner() == cur
                     || (card.getOwner() == 0 && card.getType().isMovable()))) {
                selRow = row; selCol = col;
                boardPanel.setSelection(row, col); SoundManager.sfxCardSelect();
            } else {
                cancelSel();
            }
        }
    }

    private void sendOpIfOnline(String op) {
        if (online && netPlay != null && !remoteOp) netPlay.sendOp(op);
    }

    private void cancelSel() {
        selRow = -1; selCol = -1;
        boardPanel.clearSelection();
    }

    private void afterAction() {
        cancelSel();
        refresh();
        if (game.isGameOver()) showGameOverDialog();
        else maybeAiTurn();
    }

    private void onFlipDone() {
        animLock = false;
        if (online && netPlay != null && !remoteOp) {
            netPlay.sendOp("FLIP:" + lastFlipRow + ":" + lastFlipCol);
        }
        remoteOp = false;
        cancelSel();
        refresh();
        if (game.isGameOver()) showGameOverDialog();
        else maybeAiTurn();
    }

    // ==================== 人机对战 ====================

    /** 检查是否轮到AI，是则延迟触发AI行动 */
    private void maybeAiTurn() {
        if (!vsAI || game.isGameOver() || animLock) return;
        if (game.getCurrentPlayer() != 2) return;
        if (aiTimer != null) aiTimer.stop();
        aiTimer = new javax.swing.Timer(800, e -> doAiTurn());
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    /** 执行AI回合 */
    private void doAiTurn() {
        if (!vsAI || game.isGameOver() || animLock) return;
        if (game.getCurrentPlayer() != 2) return;
        int[] action = ai.decide(game);
        if (action == null) return; // 无可行动作（极端情况）
        switch (action[0]) {
            case GameAI.FLIP:
                // 走翻牌动画流程（与人类翻牌一致）
                animLock = true;
                SoundManager.sfxCardFlip();
                boardPanel.animateFlip(action[1], action[2]);
                break;
            case GameAI.MOVE: {
                Card target = game.getCard(action[3], action[4]);
                boolean wasEnemy = target != null && target.isFaceUp() && target.getOwner() == 1;
                boolean wasNeutralKill = target != null && target.isFaceUp() && target.getOwner() == 0;
                if (game.move(action[1], action[2], action[3], action[4])) {
                    if (wasEnemy || wasNeutralKill) {
                        SoundManager.sfxCardEat();
                        boardPanel.animateBattle(action[3], action[4]);
                    }
                    afterAction();
                } else {
                    // move失败（如连续交换限制），重新决策
                    maybeAiTurn();
                }
                break;
            }
            case GameAI.SYN:
                if (game.synthesize(action[1], action[2], action[3], action[4])) {
                    SoundManager.sfxCardMerge();
                    afterAction();
                } else {
                    maybeAiTurn();
                }
                break;
        }
    }

    private boolean isAdjacent(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2) == 1;
    }

    // ==================== 刷新 ====================

    private void refresh() {
        if (game.isGameOver()) {
            String t = game.getWinner() == 0 ? "平局"
                    : (game.getWinner() == 1 ? "红方胜" : "蓝方胜");
            turnLabel.setText(t);
            turnLabel.setForeground(new Color(200, 200, 200));
        } else if (online && myPlayer == 0) {
            // 观众
            boolean p1 = game.getCurrentPlayer() == 1;
            turnLabel.setText("观战中  ·  " + (p1 ? "红方回合" : "蓝方回合"));
            turnLabel.setForeground(p1 ? P1_TEXT : P2_TEXT);
        } else if (online) {
            boolean myTurn = game.getCurrentPlayer() == myPlayer;
            turnLabel.setText((myTurn ? "你的回合" : "对方回合")
                    + "  ·  " + (myPlayer == 1 ? "红方" : "蓝方"));
            turnLabel.setForeground(myPlayer == 1 ? P1_TEXT : P2_TEXT);
        } else if (vsAI) {
            boolean myTurn = game.getCurrentPlayer() == 1;
            turnLabel.setText((myTurn ? "你的回合" : "电脑思考中…")
                    + "  ·  " + game.countAlive(game.getCurrentPlayer()) + "张");
            turnLabel.setForeground(myTurn ? P1_TEXT : P2_TEXT);
        } else {
            boolean p1 = game.getCurrentPlayer() == 1;
            turnLabel.setText((p1 ? "红方回合" : "蓝方回合") + "  ·  "
                    + game.countAlive(game.getCurrentPlayer()) + "张");
            turnLabel.setForeground(p1 ? P1_TEXT : P2_TEXT);
        }
        statusLabel.setText(game.getLastAction());
        updatePlayerInfo();
    }

    private void showGameOverDialog() {
        boolean win = false, lose = false;
        String title, sub;
        Color titleColor;
        if (game.getWinner() == 0) {
            title = "平 局";
            sub = "势均力敌，再战一局？";
            titleColor = new Color(215, 218, 228);
        } else if (online && myPlayer == 0) {
            // 观众
            title = game.getWinner() == 1 ? "红方胜利" : "蓝方胜利";
            sub = "对局结束！";
            titleColor = game.getWinner() == 1 ? P1_TEXT : P2_TEXT;
        } else if (online) {
            win = game.getWinner() == myPlayer;
            lose = !win;
            title = win ? "胜 利" : "失 败";
            sub = win ? "恭喜你击败了对手！" : "再接再厉，下次一定！";
            titleColor = win ? GOLD : new Color(205, 110, 110);
        } else if (vsAI) {
            win = game.getWinner() == 1;
            lose = !win;
            title = win ? "胜 利" : "失 败";
            sub = win ? "恭喜你击败了电脑！" : "电脑获胜，再接再厉！";
            titleColor = win ? GOLD : new Color(205, 110, 110);
        } else {
            title = game.getWinner() == 1 ? "红方胜利" : "蓝方胜利";
            sub = "再来一局？";
            titleColor = game.getWinner() == 1 ? P1_TEXT : P2_TEXT;
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
            game.reset();
            selRow = -1; selCol = -1; animLock = false;
            boardPanel.clearSelection();
            if (online && netPlay != null) netPlay.sendState(game.serialize());
            SoundManager.sfxCardDeal();
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
        String rules = "【棋盘】6行6列，共36格\n"
                + "开局随机铺20张牌（牌背朝上）：\n"
                + "  红方5张：烂头、杀人虎、小孩、公鸡、蜜蜂\n"
                + "  蓝方5张：烂头、杀人虎、小孩、公鸡、蜜蜂\n"
                + "  中立10张：大树×2、巨石×2、猫咪、旺财、\n"
                + "            白洋枪、黑洋枪、白捕虫网、黑捕虫网\n"
                + "  其余16格为空地\n"
                + "\n【基本操作】每回合只能执行一个动作：\n"
                + "  1. 翻牌：点任意牌背=翻开（可能翻到对方或中立的牌）\n"
                + "  2. 选中：点己方明牌或中立可动明牌=选中（金色高亮）\n"
                + "  3. 行动：选中后点相邻格\n"
                + "     空地（绿框）=移动过去\n"
                + "     敌方牌（红框）=发起对战\n"
                + "     巨石（青框）=烂头/烂头背枪与巨石交换位置\n"
                + "     猫咪/旺财（红框）=烂头背枪将其击杀\n"
                + "     洋枪/捕虫网（金框）=合成新卡牌\n"
                + "\n【中立资源】（银灰边框，需先翻开）\n"
                + "  大树：障碍物，不可移动，不可击杀\n"
                + "  巨石：不可主动移动；烂头/烂头背枪可与其交换位置\n"
                + "        （同一个烂头不能连续两回合交换）\n"
                + "  猫咪/旺财：双方均可移动；烂头背枪可击杀\n"
                + "  白洋枪/白捕虫网：双方均可移动，可参与合成\n"
                + "  黑洋枪/黑捕虫网：不可移动，但可参与合成\n"
                + "\n【合成】己方牌+相邻中立牌，结果放在中立牌位置：\n"
                + "  烂头 + 洋枪（白/黑）= 烂头背枪（金色边框）\n"
                + "  小孩 + 捕虫网（白/黑）= 捕虫男孩（金色边框）\n"
                + "\n【击杀链】\n"
                + "  烂头背枪 击杀 杀人虎、烂头、猫咪、旺财\n"
                + "  捕虫男孩 击杀 蜜蜂、公鸡\n"
                + "  杀人虎   击杀 小孩、烂头、捕虫男孩\n"
                + "  小孩     击杀 公鸡\n"
                + "  公鸡     击杀 蜜蜂\n"
                + "  蜜蜂     击杀 烂头、烂头背枪\n"
                + "  特殊：杀人虎vs烂头背枪、烂头背枪vs烂头背枪，先手（进攻方）胜\n"
                + "  其余无法互杀的对战=平局，双方退回原位\n"
                + "\n【胜利条件】\n"
                + "  1. 斩首：击杀敌方烂头或烂头背枪\n"
                + "  2. 困毙：敌方仅剩烂头一张牌\n"
                + "\n【联机】主页点\"联机对战\"创建房间或加入房间\n"
                + "  主机=红方，客户端=蓝方，右侧聊天框可互发消息";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(new Color(255, 255, 245));
        ta.setForeground(Color.BLACK);
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(480, 460));
        JOptionPane.showMessageDialog(this, sp, "规则", JOptionPane.INFORMATION_MESSAGE);
    }
}
