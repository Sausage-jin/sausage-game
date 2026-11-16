import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.Random;

/**
 * 双人俄罗斯方块：两人共享同一棋盘，各自控制不同颜色的方块同时下落。
 * 不同颜色的方块混合填满一行即可消除。
 * 玩家1（橙）：A/D 左右移动，W 旋转，S 加速下落。
 * 玩家2（青）：←/→ 左右移动，↑ 旋转，↓ 加速下落。
 * P键暂停。
 */
public class TetrisUI extends JFrame {
    private static final int COLS = 14, ROWS = 22, CELL = 28;

    // grid[r][c] = 0空, 1玩家1(橙), 2玩家2(青)
    private int[][] grid = new int[ROWS][COLS];

    private Piece p1, p2;
    private int next1 = -1, next2 = -1;
    private int score = 0, totalLines = 0;
    private boolean over = false, paused = false;

    private Timer timer;
    private BoardPanel boardPanel;
    private JLabel statusLabel, scoreLabel, linesLabel;

    private Random rnd = new Random();

    // 联机
    private NetPlay netPlay;
    private boolean online = false;
    private int myPlayer = 0;          // 0=本地双人, 1=主机(橙), 2=客户端(青), 3=观众
    private boolean remoteOp = false;  // 正在处理远程操作
    private JDialog gameOverDlg;

    // 聊天
    private JTextPane chatPane;
    private javax.swing.text.StyledDocument chatDoc;
    private JTextField chatInput;

    private static final Color BG       = new Color(20, 23, 32);
    private static final Color PANEL_BG = new Color(30, 34, 46);
    private static final Color GOLD     = new Color(255, 215, 110);
    private static final Color SUB      = new Color(160, 168, 188);
    private static final Color BTN_BG   = new Color(52, 58, 76);
    private static final Color BTN_FG   = new Color(225, 228, 238);
    private static final Color BORDER   = new Color(58, 64, 82);
    private static final Color P1_COLOR = new Color(255, 130, 60);   // 橙
    private static final Color P2_COLOR = new Color(80, 200, 220);   // 青

    /** 方块（7种tetromino） */
    static class Piece {
        int type, rot, row, col;
        Piece(int t, int r, int row, int col) { type = t; rot = r; this.row = row; this.col = col; }

        int[][] mat() {
            int[][] m = shape(type);
            for (int i = 0; i < rot; i++) m = rotCW(m);
            return m;
        }

        static int[][] shape(int t) {
            switch (t) {
                case 0: return new int[][]{{1,1,1,1}};           // I
                case 1: return new int[][]{{1,1},{1,1}};         // O
                case 2: return new int[][]{{0,1,0},{1,1,1}};     // T
                case 3: return new int[][]{{0,1,1},{1,1,0}};     // S
                case 4: return new int[][]{{1,1,0},{0,1,1}};     // Z
                case 5: return new int[][]{{1,0,0},{1,1,1}};     // J
                default: return new int[][]{{0,0,1},{1,1,1}};    // L
            }
        }

        static int[][] rotCW(int[][] m) {
            int r = m.length, c = m[0].length;
            int[][] n = new int[c][r];
            for (int i = 0; i < r; i++)
                for (int j = 0; j < c; j++)
                    n[j][r - 1 - i] = m[i][j];
            return n;
        }
    }

    public TetrisUI() {
        initUI();
        newGame();
    }

    /** 联机模式：主机=玩家1(橙)，客户端=玩家2(青)，观众只观战 */
    public TetrisUI(NetPlay np, int player) {
        this.netPlay = np;
        this.myPlayer = player;
        this.online = true;
        initUI();
        netPlay.setListener(mkNetListener());
        if (myPlayer == 1) {
            newGame();
            sendStateIfOnline();
        } else {
            statusLabel.setText("等待主机开始...");
        }
    }

    private void initUI() {
        setTitle("双人俄罗斯方块");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);
        setAppIcon();
        add(buildTop(), BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildBottom(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(getWidth() * 7 / 10, getHeight() * 7 / 10));
        SoundManager.switchBgm(3);

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
            newGame();
            if (online) sendStateIfOnline();
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
        JLabel title = new JLabel("双人俄罗斯方块");
        title.setFont(new Font("微软雅黑", Font.BOLD, 17));
        title.setForeground(GOLD);
        gbc.gridx = 0; gbc.weightx = 0;
        top.add(title, gbc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton homeBtn = mkBtn("主页");
        JButton ruleBtn = mkBtn("规则");
        JButton restartBtn = mkBtn("重开");
        btns.add(restartBtn);
        btns.add(ruleBtn);
        btns.add(homeBtn);
        gbc.gridx = 1; gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 8, 0, 16);
        top.add(btns, gbc);

        homeBtn.addActionListener(e -> goHome());
        ruleBtn.addActionListener(e -> showRules());
        restartBtn.addActionListener(e -> {
            if (online && myPlayer != 1) {
                JOptionPane.showMessageDialog(this, "联机模式请由主机重开", "提示", JOptionPane.INFORMATION_MESSAGE);
                boardPanel.requestFocusInWindow();
                return;
            }
            newGame();
            sendStateIfOnline();
        });
        return top;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel(new BorderLayout(0, 0));
        side.setBackground(PANEL_BG);
        side.setPreferredSize(new Dimension(online ? 220 : 180, 0));
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(PANEL_BG);
        info.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        if (online) {
            JLabel myTitle;
            if (myPlayer == 1) {
                myTitle = new JLabel("你（橙方·主机）");
                myTitle.setForeground(P1_COLOR);
            } else if (myPlayer == 2) {
                myTitle = new JLabel("你（青方）");
                myTitle.setForeground(P2_COLOR);
            } else {
                myTitle = new JLabel("观众模式");
                myTitle.setForeground(GOLD);
            }
            myTitle.setFont(new Font("微软雅黑", Font.BOLD, 14));
            myTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(myTitle);
            info.add(Box.createVerticalStrut(3));
            String ctrl = myPlayer == 1 ? "A/D 移动  W 旋转  S 加速"
                         : myPlayer == 2 ? "←/→ 移动  ↑ 旋转  ↓ 加速"
                         : "只观战，不可操作";
            JLabel c1 = new JLabel(ctrl);
            c1.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            c1.setForeground(SUB);
            c1.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(c1);
            info.add(Box.createVerticalStrut(12));
        } else {
            JLabel t1 = new JLabel("玩家1（橙）");
            t1.setFont(new Font("微软雅黑", Font.BOLD, 14));
            t1.setForeground(P1_COLOR);
            t1.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(t1);
            info.add(Box.createVerticalStrut(3));
            JLabel c1 = new JLabel("A/D 移动  W 旋转  S 加速");
            c1.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            c1.setForeground(SUB);
            c1.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(c1);
            info.add(Box.createVerticalStrut(12));

            JLabel t2 = new JLabel("玩家2（青）");
            t2.setFont(new Font("微软雅黑", Font.BOLD, 14));
            t2.setForeground(P2_COLOR);
            t2.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(t2);
            info.add(Box.createVerticalStrut(3));
            JLabel c2 = new JLabel("←/→ 移动  ↑ 旋转  ↓ 加速");
            c2.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            c2.setForeground(SUB);
            c2.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(c2);
            info.add(Box.createVerticalStrut(16));
        }

        scoreLabel = new JLabel("分数：0");
        scoreLabel.setFont(new Font("微软雅黑", Font.BOLD, 15));
        scoreLabel.setForeground(GOLD);
        scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(scoreLabel);
        info.add(Box.createVerticalStrut(5));
        linesLabel = new JLabel("行数：0");
        linesLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        linesLabel.setForeground(SUB);
        linesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(linesLabel);

        side.add(info, BorderLayout.NORTH);

        if (online) {
            side.add(buildChatPanel(), BorderLayout.CENTER);
        }

        JLabel pauseLb = new JLabel(online ? "  P 暂停  点击棋盘聚焦" : "  P 暂停/继续");
        pauseLb.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        pauseLb.setForeground(SUB);
        pauseLb.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));
        side.add(pauseLb, BorderLayout.SOUTH);

        return side;
    }

    private JPanel buildChatPanel() {
        JPanel chat = new JPanel(new BorderLayout(0, 4));
        chat.setBackground(PANEL_BG);
        chat.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(24, 27, 38));
        chatPane.setForeground(new Color(228, 231, 240));
        chatPane.setCaretColor(new Color(228, 231, 240));
        chatDoc = chatPane.getStyledDocument();
        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setPreferredSize(new Dimension(180, 130));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        chat.add(scroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(4, 0));
        input.setBackground(PANEL_BG);
        chatInput = new JTextField();
        chatInput.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        chatInput.setBackground(new Color(24, 27, 38));
        chatInput.setForeground(new Color(228, 231, 240));
        chatInput.setCaretColor(new Color(228, 231, 240));
        chatInput.addActionListener(e -> sendChat());
        input.add(chatInput, BorderLayout.CENTER);
        JButton sendBtn = mkBtn("发送");
        sendBtn.addActionListener(e -> sendChat());
        input.add(sendBtn, BorderLayout.EAST);
        chat.add(input, BorderLayout.SOUTH);

        return chat;
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

    // ==================== 棋盘渲染 ====================

    private class BoardPanel extends JPanel {
        private int baseW = COLS * CELL, baseH = ROWS * CELL;
        private double viewScale = 1.0;
        private int viewOffX = 0, viewOffY = 0;

        BoardPanel() {
            setPreferredSize(new Dimension(baseW, baseH));
            setMinimumSize(new Dimension(baseW / 2, baseH / 2));
            setBackground(new Color(24, 27, 38));
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) { onKey(e.getKeyCode()); }
            });
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { requestFocusInWindow(); }
            });
        }

        private void updateViewTransform() {
            double s = Math.min(getWidth() / (double) baseW, getHeight() / (double) baseH);
            viewScale = s < 0.05 ? 0.05 : s;
            viewOffX = (int) ((getWidth() - baseW * viewScale) / 2.0);
            viewOffY = (int) ((getHeight() - baseH * viewScale) / 2.0);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = baseW, h = baseH;
            updateViewTransform();
            g2.translate(viewOffX, viewOffY);
            g2.scale(viewScale, viewScale);

            // 网格线
            g2.setColor(new Color(38, 42, 56));
            for (int c = 0; c <= COLS; c++) g2.drawLine(c * CELL, 0, c * CELL, h);
            for (int r = 0; r <= ROWS; r++) g2.drawLine(0, r * CELL, w, r * CELL);

            // 中线（虚线标记两方区域）
            g2.setColor(new Color(70, 76, 100));
            Stroke oldStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5f, 5f}, 0f));
            g2.drawLine(w / 2, 0, w / 2, h);
            g2.setStroke(oldStroke);

            // 已固定方块
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (grid[r][c] != 0) {
                        drawCell(g2, c * CELL, r * CELL, grid[r][c] == 1 ? P1_COLOR : P2_COLOR);
                    }
                }
            }

            // 活动方块
            if (p1 != null) drawPiece(g2, p1, P1_COLOR);
            if (p2 != null) drawPiece(g2, p2, P2_COLOR);

            // 暂停遮罩
            if (paused) {
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fillRect(0, 0, w, h);
                g2.setColor(GOLD);
                g2.setFont(new Font("微软雅黑", Font.BOLD, 28));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "已暂停";
                g2.drawString(txt, (w - fm.stringWidth(txt)) / 2, h / 2);
            }
        }

        private void drawPiece(Graphics2D g2, Piece p, Color color) {
            int[][] m = p.mat();
            for (int r = 0; r < m.length; r++) {
                for (int c = 0; c < m[r].length; c++) {
                    if (m[r][c] != 0) {
                        drawCell(g2, (p.col + c) * CELL, (p.row + r) * CELL, color);
                    }
                }
            }
        }

        private void drawCell(Graphics2D g2, int x, int y, Color color) {
            g2.setColor(color);
            g2.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
            g2.setColor(color.brighter());
            g2.fillRect(x + 1, y + 1, CELL - 2, 3);
            g2.fillRect(x + 1, y + 1, 3, CELL - 2);
            g2.setColor(color.darker());
            g2.fillRect(x + 1, y + CELL - 4, CELL - 2, 3);
            g2.fillRect(x + CELL - 4, y + 1, 3, CELL - 2);
        }
    }

    // ==================== 游戏逻辑 ====================

    private void newGame() {
        grid = new int[ROWS][COLS];
        score = 0; totalLines = 0; over = false; paused = false;
        p1 = null; p2 = null;
        next1 = rnd.nextInt(7);
        next2 = rnd.nextInt(7);
        p1 = spawn(1);
        p2 = spawn(2);
        if (timer != null) timer.stop();
        if (!online || myPlayer == 1) {
            timer = new Timer(600, e -> tick());
            timer.start();
        }
        boardPanel.repaint();
        updateLabels();
        boardPanel.requestFocusInWindow();
        SoundManager.sfxGameStart();
    }

    /** 每个 tick：两个方块各尝试下移一格 */
    private void tick() {
        if (over || paused) return;

        // 方块1下移
        if (p1 != null) {
            if (canPlace(p1, p1.row + 1, p1.col, p2)) {
                p1.row++;
            } else if (!canPlace(p1, p1.row + 1, p1.col, null)) {
                // 被固定方块/底部挡住 → 锁定（被p2挡住则等待）
                lock(p1);
                p1 = spawn(1);
                if (p1 == null) { endGame(); return; }
            }
        }

        // 方块2下移
        if (p2 != null) {
            if (canPlace(p2, p2.row + 1, p2.col, p1)) {
                p2.row++;
            } else if (!canPlace(p2, p2.row + 1, p2.col, null)) {
                lock(p2);
                p2 = spawn(2);
                if (p2 == null) { endGame(); return; }
            }
        }

        clearLines();
        boardPanel.repaint();
        updateLabels();
        sendStateIfOnline();
    }

    /** 检查方块能否放在(row,col)：不越界、不碰固定方块、不碰另一个活动方块 */
    private boolean canPlace(Piece p, int row, int col, Piece other) {
        int[][] m = p.mat();
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < m[r].length; c++) {
                if (m[r][c] == 0) continue;
                int gr = row + r, gc = col + c;
                if (gr < 0 || gr >= ROWS || gc < 0 || gc >= COLS) return false;
                if (grid[gr][gc] != 0) return false;
                if (other != null && cellOccupied(other, gr, gc)) return false;
            }
        }
        return true;
    }

    private boolean cellOccupied(Piece p, int gr, int gc) {
        int[][] m = p.mat();
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < m[r].length; c++) {
                if (m[r][c] != 0 && p.row + r == gr && p.col + c == gc) return true;
            }
        }
        return false;
    }

    /** 锁定方块到grid */
    private void lock(Piece p) {
        int player = (p == p1) ? 1 : 2;
        int[][] m = p.mat();
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < m[r].length; c++) {
                if (m[r][c] != 0 && p.row + r >= 0 && p.row + r < ROWS && p.col + c >= 0 && p.col + c < COLS) {
                    grid[p.row + r][p.col + c] = player;
                }
            }
        }
        SoundManager.sfxTetrisLock();
    }

    /** 生成新方块：玩家1在左侧(col=2)，玩家2在右侧 */
    private Piece spawn(int player) {
        int type = (player == 1) ? next1 : next2;
        int next = rnd.nextInt(7);
        if (player == 1) next1 = next; else next2 = next;

        int[][] m = Piece.shape(type);
        int w = m[0].length;
        int col = (player == 1) ? 2 : COLS - 2 - w;
        Piece p = new Piece(type, 0, 0, col);
        Piece other = (player == 1) ? p2 : p1;
        if (!canPlace(p, 0, col, other)) return null; // 堆到顶部，游戏结束
        return p;
    }

    /** 消除填满的行（混合颜色也可消除） */
    private void clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) { full = false; break; }
            }
            if (full) {
                cleared++;
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(grid[rr - 1], 0, grid[rr], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) grid[0][c] = 0;
                r++; // 当前行已下移，重新检查
            }
        }
        if (cleared > 0) {
            totalLines += cleared;
            int[] pts = {0, 100, 300, 500, 800};
            score += pts[Math.min(cleared, 4)];
            SoundManager.sfxTetrisClear();
            // 缓慢加速：每消10行速度加快
            if (totalLines % 10 == 0 && timer.getDelay() > 300) {
                timer.setDelay(timer.getDelay() - 50);
            }
        }
    }

    private void endGame() {
        over = true;
        if (timer != null) timer.stop();
        SoundManager.sfxLose();
        sendStateIfOnline();
        boardPanel.repaint();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { showGameOverDialog(); }
        });
    }

    // ==================== 联机 ====================

    /** 序列化完整状态（主机→客户端/观众） */
    private String serializeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(score).append('|').append(totalLines).append('|')
          .append(over).append('|').append(paused).append('|')
          .append(timer != null ? timer.getDelay() : 600).append('|');
        if (p1 != null) sb.append(p1.type).append(',').append(p1.rot).append(',').append(p1.row).append(',').append(p1.col);
        else sb.append("-1,0,0,0");
        sb.append('|');
        if (p2 != null) sb.append(p2.type).append(',').append(p2.rot).append(',').append(p2.row).append(',').append(p2.col);
        else sb.append("-1,0,0,0");
        sb.append('|');
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                sb.append(grid[r][c]);
        return sb.toString();
    }

    /** 反序列化恢复状态（客户端/观众） */
    private void deserializeState(String s) {
        String[] parts = s.split("\\|", -1);
        int oldLines = totalLines;
        score = Integer.parseInt(parts[0]);
        totalLines = Integer.parseInt(parts[1]);
        over = Boolean.parseBoolean(parts[2]);
        paused = Boolean.parseBoolean(parts[3]);
        String[] pp1 = parts[5].split(",");
        int t1 = Integer.parseInt(pp1[0]);
        p1 = t1 >= 0 ? new Piece(t1, Integer.parseInt(pp1[1]), Integer.parseInt(pp1[2]), Integer.parseInt(pp1[3])) : null;
        String[] pp2 = parts[6].split(",");
        int t2 = Integer.parseInt(pp2[0]);
        p2 = t2 >= 0 ? new Piece(t2, Integer.parseInt(pp2[1]), Integer.parseInt(pp2[2]), Integer.parseInt(pp2[3])) : null;
        String gridStr = parts[7];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                grid[r][c] = gridStr.charAt(r * COLS + c) - '0';
        if (totalLines > oldLines) SoundManager.sfxTetrisClear();
    }

    /** 主机发送状态同步 */
    private void sendStateIfOnline() {
        if (online && netPlay != null && myPlayer == 1) {
            netPlay.sendState("TSTATE:" + serializeState());
        }
    }

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { }
            public void onStateReceived(String state) { TetrisUI.this.onNetState(state); }
            public void onOpReceived(String op) { TetrisUI.this.onNetOp(op); }
            public void onChatReceived(String msg) { TetrisUI.this.onChatReceived(msg); }
            public void onDisconnected() { TetrisUI.this.onNetDisconnect(); }
        };
    }

    private void onNetState(String state) {
        if (state.startsWith("NICKS:") || state.startsWith("SPEC:")) return;
        if (!state.startsWith("TSTATE:")) return;
        boolean wasOver = over;
        deserializeState(state.substring(7));
        boardPanel.repaint();
        updateLabels();
        if (over && !wasOver) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { showGameOverDialog(); }
            });
        } else if (!over && wasOver) {
            closeGameOverDlg();
        }
    }

    /** 主机接收客户端操作，应用到玩家2（青） */
    private void onNetOp(String op) {
        if (!online || myPlayer != 1 || over) return;
        String[] p = op.split(":");
        remoteOp = true;
        try {
            if ("TIN".equals(p[0]) && p.length >= 2) {
                String key = p[1];
                if ("L".equals(key)) moveH(p2, -1, p1);
                else if ("R".equals(key)) moveH(p2, 1, p1);
                else if ("ROT".equals(key)) tryRotate(p2, 1, p1);
                else if ("DROP".equals(key)) softDrop(p2, p1);
                boardPanel.repaint();
                updateLabels();
                sendStateIfOnline();
            } else if ("TPAUSE".equals(p[0])) {
                paused = !paused;
                boardPanel.repaint();
                updateLabels();
                sendStateIfOnline();
            }
        } finally {
            remoteOp = false;
        }
    }

    private void onNetDisconnect() {
        if (netPlay == null) return;
        NetPlay np = netPlay;
        netPlay = null;
        np.close();
        online = false; myPlayer = 0; remoteOp = false;
        if (timer != null) timer.stop();
        closeGameOverDlg();
        SoundManager.sfxNetDisconnect();
        JOptionPane.showMessageDialog(this, "对方已断开，回到本地模式", "联机", JOptionPane.WARNING_MESSAGE);
        newGame();
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        text = text.replace("\n", " ").replace("\r", " ");
        if (netPlay != null) {
            netPlay.sendChat(myPlayer + ":" + SoundManager.getNickname() + ":" + text);
        }
        appendChat("我", text, myPlayer == 1 ? P1_COLOR : myPlayer == 2 ? P2_COLOR : GOLD);
        SoundManager.sfxChatSend();
        boardPanel.requestFocusInWindow();
    }

    private void appendChat(String who, String text, Color c) {
        if (chatDoc == null) return;
        javax.swing.text.Style style = chatDoc.addStyle("s", null);
        javax.swing.text.StyleConstants.setForeground(style, c);
        try {
            chatDoc.insertString(chatDoc.getLength(), who + "：" + text + "\n", style);
        } catch (Exception e) { }
        chatPane.setCaretPosition(chatDoc.getLength());
    }

    private void onChatReceived(String msg) {
        String[] p = msg.split(":", 3);
        if (p.length == 3) {
            try {
                int pn = Integer.parseInt(p[0]);
                Color c = pn == 0 ? SUB : (pn == 1 ? P1_COLOR : P2_COLOR);
                appendChat(p[1], p[2], c);
                SoundManager.sfxChatReceive();
                return;
            } catch (NumberFormatException e) { }
        }
        appendChat("对方", msg, GOLD);
    }

    // ==================== 键盘控制 ====================

    private void onKey(int code) {
        if (over) return;
        if (code == KeyEvent.VK_P) {
            if (online) {
                if (myPlayer == 1) {
                    paused = !paused;
                    sendStateIfOnline();
                } else if (myPlayer == 2 && netPlay != null) {
                    netPlay.sendOp("TPAUSE");
                }
            } else {
                paused = !paused;
            }
            boardPanel.repaint();
            updateLabels();
            return;
        }
        if (paused) return;

        if (online) {
            if (myPlayer == 1) {
                // 主机控制玩家1（橙）：WASD
                switch (code) {
                    case KeyEvent.VK_A: moveH(p1, -1, p2); break;
                    case KeyEvent.VK_D: moveH(p1, 1, p2); break;
                    case KeyEvent.VK_W: tryRotate(p1, 1, p2); break;
                    case KeyEvent.VK_S: softDrop(p1, p2); break;
                }
                sendStateIfOnline();
            } else if (myPlayer == 2 && netPlay != null) {
                // 客户端发送操作给主机：方向键
                switch (code) {
                    case KeyEvent.VK_LEFT:  netPlay.sendOp("TIN:L"); break;
                    case KeyEvent.VK_RIGHT: netPlay.sendOp("TIN:R"); break;
                    case KeyEvent.VK_UP:    netPlay.sendOp("TIN:ROT"); break;
                    case KeyEvent.VK_DOWN:  netPlay.sendOp("TIN:DROP"); break;
                }
            }
            // 观众：无操作
        } else {
            // 本地双人模式
            switch (code) {
                case KeyEvent.VK_A: moveH(p1, -1, p2); break;
                case KeyEvent.VK_D: moveH(p1, 1, p2); break;
                case KeyEvent.VK_W: tryRotate(p1, 1, p2); break;
                case KeyEvent.VK_S: softDrop(p1, p2); break;
                case KeyEvent.VK_LEFT:  moveH(p2, -1, p1); break;
                case KeyEvent.VK_RIGHT: moveH(p2, 1, p1); break;
                case KeyEvent.VK_UP:    tryRotate(p2, 1, p1); break;
                case KeyEvent.VK_DOWN:  softDrop(p2, p1); break;
            }
        }
        boardPanel.repaint();
    }

    private void moveH(Piece p, int dc, Piece other) {
        if (p == null) return;
        if (canPlace(p, p.row, p.col + dc, other)) { p.col += dc; SoundManager.sfxTetrisMove(); }
    }

    /** 加速下落：方块下移一格，无法下移则锁定并生成新方块 */
    private void softDrop(Piece p, Piece other) {
        if (p == null) return;
        if (canPlace(p, p.row + 1, p.col, other)) {
            p.row++;
            SoundManager.sfxTetrisDrop();
            return;
        }
        if (!canPlace(p, p.row + 1, p.col, null)) {
            lock(p);
            if (p == p1) {
                p1 = spawn(1);
                if (p1 == null) { endGame(); return; }
            } else {
                p2 = spawn(2);
                if (p2 == null) { endGame(); return; }
            }
            clearLines();
            updateLabels();
        }
    }

    /** 旋转（带简单wall kick：原位、左1、右1、上1） */
    private void tryRotate(Piece p, int dir, Piece other) {
        if (p == null) return;
        int oldRot = p.rot;
        p.rot = (p.rot + dir + 4) % 4;
        int[][] offsets = {{0, 0}, {0, -1}, {0, 1}, {-1, 0}};
        for (int[] off : offsets) {
            if (canPlace(p, p.row + off[0], p.col + off[1], other)) {
                p.row += off[0];
                p.col += off[1];
                SoundManager.sfxTetrisRotate();
                return;
            }
        }
        p.rot = oldRot; // 无法旋转，回退
    }

    // ==================== 辅助 ====================

    private void updateLabels() {
        scoreLabel.setText("分数：" + score);
        linesLabel.setText("行数：" + totalLines);
        if (paused) statusLabel.setText("已暂停（按P继续）");
        else if (online) statusLabel.setText("联机对战中 · 两种颜色混合填满一行即可消除");
        else statusLabel.setText("两种颜色混合填满一行即可消除！");
    }

    private void showGameOverDialog() {
        closeGameOverDlg();
        final JDialog dlg = new JDialog(this, "游戏结束", true);
        dlg.setResizable(false);
        gameOverDlg = dlg;

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(24, 27, 38));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 86, 108), 2),
            BorderFactory.createEmptyBorder(24, 28, 18, 28)));

        JLabel titleLb = new JLabel("游戏结束", SwingConstants.CENTER);
        titleLb.setFont(new Font("微软雅黑", Font.BOLD, 28));
        titleLb.setForeground(GOLD);
        panel.add(titleLb, BorderLayout.NORTH);

        JLabel scoreLb = new JLabel("总分：" + score + "    消除：" + totalLines + " 行", SwingConstants.CENTER);
        scoreLb.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        scoreLb.setForeground(SUB);
        scoreLb.setBorder(BorderFactory.createEmptyBorder(10, 0, 16, 0));
        panel.add(scoreLb, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        JButton againBtn = mkBtn(online && myPlayer != 1 ? "等待主机重开" : "再来一局");
        againBtn.setPreferredSize(new Dimension(130, 36));
        JButton homeBtn = mkBtn("返回主页");
        homeBtn.setPreferredSize(new Dimension(110, 36));
        btnPanel.add(againBtn);
        btnPanel.add(homeBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        againBtn.addActionListener(e -> {
            if (online && myPlayer != 1) {
                closeGameOverDlg();
            } else {
                closeGameOverDlg();
                newGame();
                sendStateIfOnline();
            }
        });
        homeBtn.addActionListener(e -> {
            closeGameOverDlg();
            goHome();
        });

        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void closeGameOverDlg() {
        if (gameOverDlg != null) {
            gameOverDlg.dispose();
            gameOverDlg = null;
        }
    }

    private void showRules() {
        String rules = "双人共享14×22棋盘，两人同时控制各自的方块下落\n"
                + "\n玩家1（橙色）：A/D 左右移动，W 旋转，S 加速下落"
                + "\n玩家2（青色）：←/→ 左右移动，↑ 旋转，↓ 加速下落\n"
                + "\n两种颜色的方块混合填满一整行即可消除"
                + "\n按加速键方块下移一格，无法下移则锁定"
                + "\n每消10行下落速度略微加快\n"
                + "\nP键暂停/继续"
                + "\n方块堆到顶部无法生成新方块则游戏结束\n"
                + "\n【联机】主机=橙方(WASD)，客户端=青方(方向键)"
                + "\n右侧聊天框可互发消息，点击棋盘可恢复操控";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setForeground(new Color(228, 231, 240));
        ta.setBackground(new Color(24, 27, 38));
        ta.setCaretColor(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(400, 280));
        JOptionPane.showMessageDialog(this, sp, "俄罗斯方块规则", JOptionPane.INFORMATION_MESSAGE);
        boardPanel.requestFocusInWindow();
    }

    private void goHome() {
        if (timer != null) timer.stop();
        closeGameOverDlg();
        if (netPlay != null) {
            NetPlay np = netPlay;
            netPlay = null;
            np.close();
        }
        online = false; myPlayer = 0;
        dispose();
        new HomeUI().setVisible(true);
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
}