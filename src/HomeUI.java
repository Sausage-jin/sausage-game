import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import javax.imageio.ImageIO;

/**
 * 主页：blackground.jpg背景，菜单含开始游戏/联机对战/规则/退出。
 * 开始游戏可选模式：烂头背枪 / 五子棋（双人） / 五子棋（三人）。
 * 联机房间在主页设置：创建房间（主机）或加入房间（客户端），
 * 主机在房间界面选择模式（可随时切换），点开始后双方/三方自动进入游戏。
 */
public class HomeUI extends JFrame {
    private NetPlay netPlay;
    private int myPlayer = 0;   // 0=未联机, 1=主机, 2=客户端, 3=观众
    private int netMode = 0;    // 0=未定, 1=烂头背枪, 2=五子棋双人, 3=五子棋三人, 4=俄罗斯方块, 5=你画我猜, 6=斗地主3人, 7=小鸟飞翔, 8=斗地主4人
    private int netPlayerNum = 0; // 三人/四人模式客户端的实际玩家号(2/3/4)
    private JLabel statusLabel;
    private static final int NET_PORT = 3721;

    // 房间等待对话框
    private JDialog roomDlg;
    private JRadioButton modeLtbqBtn, modeGomoku2Btn, modeGomoku3Btn, modeTetrisBtn, modeDrawGuessBtn, modeDdzBtn, modeBirdBtn, modeDdz4Btn;
    private JLabel roomConnLabel;
    private JLabel roomSpecLabel;
    private JButton startBtn;
    private JPanel roomCardsPanel;   // 玩家信息卡容器（动态重建）

    // 性别显示映射：索引0-4 → {图标路径, 文字}
    private static final String[][] GENDER_INFO = {
        {null, "保密"},
        {"image/boy.png", "男"},
        {"image/gril.png", "女"},
        {"image/kuaxingbie.png", "跨性别"},
        {"image/no people.png", "非人"}
    };

    private static final Color BG     = new Color(20, 23, 32);
    private static final Color GOLD   = new Color(255, 215, 110);
    private static final Color SUB    = new Color(170, 176, 196);
    private static final Color BTN_BG = new Color(38, 42, 58);
    private static final Color BTN_FG = new Color(228, 231, 240);
    private static final Color BORDER = new Color(96, 104, 128);
    private static final Color PANEL_BG = new Color(30, 34, 46);

    private JButton musicBtn;
    private JButton settingBtn;
    private JButton nickBtn;
    private ImageIcon musicIconOn;
    private ImageIcon musicIconOff;

    public HomeUI() {
        SoundManager.init();
        SoundManager.switchBgm(0);   // 主页背景音乐
        loadMusicIcons();
        initUI();
    }

    private void initUI() {
        setTitle("烂头背枪");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setAppIcon();

        BackgroundPanel bg = new BackgroundPanel();
        bg.setLayout(new BorderLayout());
        setContentPane(bg);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        bg.add(center, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("烂头背枪", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 38));
        title.setForeground(GOLD);
        title.setPreferredSize(new Dimension(240, 56));
        gbc.gridy = 0;
        gbc.insets = new Insets(24, 0, 28, 0);
        center.add(title, gbc);

        String[] texts = {"开始游戏", "联机对战", "游戏规则", "退出游戏"};
        JButton[] btns = new JButton[texts.length];
        for (int i = 0; i < texts.length; i++) {
            btns[i] = mkMenuBtn(texts[i]);
            btns[i].setBackground(UiKit.GOLD);    // 全部金底
            btns[i].setForeground(UiKit.DARK_FG); // 黑字
            gbc.gridy = i + 1;
            gbc.insets = new Insets(7, 0, 7, 0);
            center.add(btns[i], gbc);
        }

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        statusLabel.setForeground(SUB);
        statusLabel.setPreferredSize(new Dimension(320, 20));
        gbc.gridy = texts.length + 1;
        gbc.insets = new Insets(18, 0, 14, 0);
        center.add(statusLabel, gbc);

        btns[0].addActionListener(e -> startLocal());
        btns[1].addActionListener(e -> showNetDialog());
        btns[2].addActionListener(e -> showRules());
        btns[3].addActionListener(e -> System.exit(0));

        // 右下角：昵称 + 音乐开关 + 设置
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JLabel copyrightLb = new JLabel("© 2026 Sausage-jin · 仅供非商业使用 · 玩法受保护");
        copyrightLb.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        copyrightLb.setForeground(new Color(140, 140, 150));
        copyrightLb.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        south.add(copyrightLb, BorderLayout.WEST);

        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        corner.setOpaque(false);
        nickBtn = mkIconBtn(loadIcon("image/user.png", 30), "昵称");
        musicBtn = mkIconBtn(musicIconOn, "背景音乐");
        settingBtn = mkIconBtn(loadIcon("image/seeting.png", 30), "设置");
        corner.add(nickBtn);
        corner.add(musicBtn);
        corner.add(settingBtn);
        south.add(corner, BorderLayout.EAST);
        bg.add(south, BorderLayout.SOUTH);

        updateMusicBtn();
        musicBtn.addActionListener(e -> toggleMusic());
        settingBtn.addActionListener(e -> showSettings());
        nickBtn.addActionListener(e -> showNicknameDialog());

        setSize(640, 480);
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(null);
        setResizable(true);

        // ESC 键：弹窗确认退出
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "escExit");
        getRootPane().getActionMap().put("escExit", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int r = UiKit.showVerticalOptions(HomeUI.this, "退出游戏", "确定要退出游戏吗？",
                        new String[]{"确定", "取消"});
                if (r == 0) System.exit(0);
            }
        });

        // 启动时若设置了全屏，窗口首次显示后自动进入全屏
        if (SoundManager.isFullscreen()) {
            setUndecorated(true);
            addWindowListener(new WindowAdapter() {
                public void windowOpened(WindowEvent e) {
                    GraphicsDevice gd = getGraphicsConfiguration().getDevice();
                    if (gd.isFullScreenSupported()) gd.setFullScreenWindow(HomeUI.this);
                }
            });
        }
    }

    // ==================== 设置 ====================

    private ImageIcon loadIcon(String path, int size) {
        try {
            File f = new File(path);
            if (f.exists()) {
                BufferedImage img = ImageIO.read(f);
                return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
            }
        } catch (Exception e) { }
        return null;
    }

    private void loadMusicIcons() {
        try {
            File f = new File("image/music.png");
            if (f.exists()) {
                BufferedImage img = ImageIO.read(f);
                Image on = img.getScaledInstance(30, 30, Image.SCALE_SMOOTH);
                musicIconOn = new ImageIcon(on);
                BufferedImage dim = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = dim.createGraphics();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2.drawImage(on, 0, 0, null);
                g2.dispose();
                musicIconOff = new ImageIcon(dim);
            }
        } catch (Exception e) { }
    }

    private JButton mkIconBtn(Icon icon, String tip) {
        JButton b = UiKit.make("", new Color(44, 50, 66), BTN_FG);
        b.setIcon(icon);
        b.setToolTipText(tip);
        b.setMargin(new Insets(4, 4, 4, 4));
        b.setPreferredSize(new Dimension(42, 42));
        b.addActionListener(e -> SoundManager.sfxClick());
        return b;
    }

    private void toggleMusic() {
        SoundManager.setMusicOn(!SoundManager.isMusicOn());
        updateMusicBtn();
    }

    private void updateMusicBtn() {
        if (musicIconOn != null && musicIconOff != null) {
            musicBtn.setIcon(SoundManager.isMusicOn() ? musicIconOn : musicIconOff);
        }
        musicBtn.setToolTipText(SoundManager.isMusicOn() ? "背景音乐：开（点击关闭）" : "背景音乐：关（点击开启）");
    }

    private void showSettings() {
        final JDialog dlg = new JDialog(this, "设置", true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(22, 26, 16, 26)));

        Font cbFont = new Font("微软雅黑", Font.BOLD, 14);
        Font lbFont = new Font("微软雅黑", Font.PLAIN, 12);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ---- 背景音乐 ----
        final JCheckBox musicCb = new JCheckBox("背景音乐");
        musicCb.setSelected(SoundManager.isMusicOn());
        musicCb.setFont(cbFont);
        musicCb.setForeground(BTN_FG);
        musicCb.setBackground(PANEL_BG);
        musicCb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(musicCb, gbc);

        final JLabel musicVolLb = new JLabel("音量 " + SoundManager.getMusicVolume());
        musicVolLb.setFont(lbFont);
        musicVolLb.setForeground(SUB);
        musicVolLb.setPreferredSize(new Dimension(64, 20));
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(musicVolLb, gbc);

        final JSlider musicSlider = new JSlider(0, 100, SoundManager.getMusicVolume());
        musicSlider.setOpaque(false);
        musicSlider.setPreferredSize(new Dimension(150, 26));
        musicSlider.setEnabled(SoundManager.isMusicOn());
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 1;
        panel.add(musicSlider, gbc);

        // ---- 游戏音效 ----
        final JCheckBox sfxCb = new JCheckBox("游戏音效");
        sfxCb.setSelected(SoundManager.isSfxOn());
        sfxCb.setFont(cbFont);
        sfxCb.setForeground(BTN_FG);
        sfxCb.setBackground(PANEL_BG);
        sfxCb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(sfxCb, gbc);

        final JLabel sfxVolLb = new JLabel("音量 " + SoundManager.getSfxVolume());
        sfxVolLb.setFont(lbFont);
        sfxVolLb.setForeground(SUB);
        sfxVolLb.setPreferredSize(new Dimension(64, 20));
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(sfxVolLb, gbc);

        final JSlider sfxSlider = new JSlider(0, 100, SoundManager.getSfxVolume());
        sfxSlider.setOpaque(false);
        sfxSlider.setPreferredSize(new Dimension(150, 26));
        sfxSlider.setEnabled(SoundManager.isSfxOn());
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 1;
        panel.add(sfxSlider, gbc);

        // ---- 游戏速度 ----
        final JLabel spdLb = new JLabel("游戏速度");
        spdLb.setFont(cbFont);
        spdLb.setForeground(BTN_FG);
        spdLb.setBackground(PANEL_BG);
        spdLb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(spdLb, gbc);

        final JLabel spdValLb = new JLabel(SoundManager.getGameSpeed() + "%");
        spdValLb.setFont(lbFont);
        spdValLb.setForeground(SUB);
        spdValLb.setPreferredSize(new Dimension(64, 20));
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(spdValLb, gbc);

        final JSlider spdSlider = new JSlider(50, 500, SoundManager.getGameSpeed());
        spdSlider.setOpaque(false);
        spdSlider.setPreferredSize(new Dimension(150, 26));
        gbc.gridx = 2; gbc.gridy = 2; gbc.weightx = 1;
        panel.add(spdSlider, gbc);

        // ---- 全屏模式 ----
        final JCheckBox fsCb = new JCheckBox("全屏模式");
        fsCb.setSelected(SoundManager.isFullscreen());
        fsCb.setFont(cbFont);
        fsCb.setForeground(BTN_FG);
        fsCb.setBackground(PANEL_BG);
        fsCb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(fsCb, gbc);

        final JLabel fsHint = new JLabel("切换窗口/全屏显示");
        fsHint.setFont(lbFont);
        fsHint.setForeground(SUB);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1;
        panel.add(fsHint, gbc);
        gbc.gridwidth = 1;

        // ---- 关闭 ----
        JButton closeBtn = mkMenuBtn("关 闭");
        closeBtn.setPreferredSize(new Dimension(130, 40));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER; gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(18, 4, 2, 4);
        panel.add(closeBtn, gbc);

        // ---- 事件 ----
        musicCb.addActionListener(e -> {
            SoundManager.setMusicOn(musicCb.isSelected());
            musicSlider.setEnabled(musicCb.isSelected());
            updateMusicBtn();
        });
        musicSlider.addChangeListener(e -> {
            int v = musicSlider.getValue();
            SoundManager.setMusicVolume(v);
            musicVolLb.setText("音量 " + v);
        });
        sfxCb.addActionListener(e -> {
            SoundManager.setSfxOn(sfxCb.isSelected());
            sfxSlider.setEnabled(sfxCb.isSelected());
        });
        sfxSlider.addChangeListener(e -> {
            int v = sfxSlider.getValue();
            SoundManager.setSfxVolume(v);
            sfxVolLb.setText("音量 " + v);
            if (!sfxSlider.getValueIsAdjusting() && sfxCb.isSelected()) {
                SoundManager.sfxEat();   // 试听
            }
        });
        spdSlider.addChangeListener(e -> {
            int v = spdSlider.getValue();
            SoundManager.setGameSpeed(v);
            spdValLb.setText(v + "%");
        });
        fsCb.addActionListener(e -> SoundManager.setFullscreen(fsCb.isSelected()));
        closeBtn.addActionListener(e -> dlg.dispose());

        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showNicknameDialog() {
        final JDialog dlg = new JDialog(this, "用户设置", true);
        dlg.setResizable(false);
        dlg.setContentPane(buildUserPanel(dlg));
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /** 用户设置面板：白色背景，含昵称输入与性别选择 */
    private JPanel buildUserPanel(final JDialog dlg) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(20, 24, 16, 24)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ---- 昵称 ----
        JLabel nickLb = new JLabel("昵称（联机时显示给其他玩家）");
        nickLb.setFont(new Font("微软雅黑", Font.BOLD, 13));
        nickLb.setForeground(new Color(50, 50, 50));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4; gbc.weightx = 1;
        panel.add(nickLb, gbc);

        final JTextField nickField = new JTextField(SoundManager.getNickname());
        nickField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        nickField.setForeground(new Color(30, 30, 30));
        nickField.setBackground(Color.WHITE);
        nickField.setCaretColor(new Color(30, 30, 30));
        nickField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 160, 160), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 4; gbc.weightx = 1;
        gbc.insets = new Insets(2, 4, 12, 4);
        panel.add(nickField, gbc);

        // ---- 性别 ----
        JLabel genderLb = new JLabel("选择性别");
        genderLb.setFont(new Font("微软雅黑", Font.BOLD, 13));
        genderLb.setForeground(new Color(50, 50, 50));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4; gbc.weightx = 1;
        gbc.insets = new Insets(4, 4, 6, 4);
        panel.add(genderLb, gbc);

        // 性别选项：图标+文字按钮
        String[][] genders = {
            {"image/boy.png", "男", "1"},
            {"image/gril.png", "女", "2"},
            {"image/kuaxingbie.png", "跨性别", "3"},
            {"image/no people.png", "非人", "4"}
        };
        final JButton[] genderBtns = new JButton[genders.length];
        final int curGender = SoundManager.getGender();
        gbc.gridwidth = 1; gbc.weightx = 0.25;
        gbc.fill = GridBagConstraints.NONE;
        for (int i = 0; i < genders.length; i++) {
            final int idx = i;
            final int gVal = Integer.parseInt(genders[i][2]);
            JButton gb = new JButton(genders[i][1]);
            gb.setFont(new Font("微软雅黑", Font.BOLD, 12));
            gb.setFocusable(false);
            gb.setCursor(new Cursor(Cursor.HAND_CURSOR));
            gb.setPreferredSize(new Dimension(76, 68));
            gb.setVerticalTextPosition(SwingConstants.BOTTOM);
            gb.setHorizontalTextPosition(SwingConstants.CENTER);
            // 加载性别图标
            ImageIcon icon = loadIcon(genders[i][0], 36);
            if (icon != null) gb.setIcon(icon);
            // 选中状态样式
            final Color selectedBg = new Color(220, 235, 255);
            final Color selectedBd = new Color(80, 140, 220);
            final Color normalBg = new Color(245, 245, 245);
            final Color normalBd = new Color(200, 200, 200);
            if (curGender == gVal) {
                gb.setBackground(selectedBg);
                gb.setBorder(BorderFactory.createLineBorder(selectedBd, 2));
            } else {
                gb.setBackground(normalBg);
                gb.setBorder(BorderFactory.createLineBorder(normalBd, 1));
            }
            gb.setForeground(new Color(40, 40, 40));
            gb.setOpaque(true);
            gb.addActionListener(e -> {
                for (JButton b : genderBtns) {
                    b.setBackground(normalBg);
                    b.setBorder(BorderFactory.createLineBorder(normalBd, 1));
                }
                gb.setBackground(selectedBg);
                gb.setBorder(BorderFactory.createLineBorder(selectedBd, 2));
            });
            genderBtns[i] = gb;
            gbc.gridx = i; gbc.gridy = 3;
            gbc.insets = new Insets(2, 3, 12, 3);
            panel.add(gb, gbc);
        }

        // ---- 头像 ----
        JLabel avatarLb = new JLabel("选择头像");
        avatarLb.setFont(new Font("微软雅黑", Font.BOLD, 13));
        avatarLb.setForeground(new Color(50, 50, 50));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 6, 4);
        panel.add(avatarLb, gbc);

        final JButton[] avatarBtns = new JButton[SoundManager.AVATAR_FILES.length];
        final int curAvatar = SoundManager.getAvatar();
        final Color avSelBg = new Color(220, 235, 255);
        final Color avSelBd = new Color(80, 140, 220);
        final Color avNorBg = new Color(245, 245, 245);
        final Color avNorBd = new Color(200, 200, 200);
        int avCols = 3;
        for (int i = 0; i < SoundManager.AVATAR_FILES.length; i++) {
            final int idx = i;
            JButton ab = new JButton();
            ab.setFocusable(false);
            ab.setCursor(new Cursor(Cursor.HAND_CURSOR));
            ab.setPreferredSize(new Dimension(64, 64));
            ImageIcon aIcon = loadIcon(SoundManager.AVATAR_FILES[i], 52);
            if (aIcon != null) ab.setIcon(aIcon);
            if (curAvatar == i) {
                ab.setBackground(avSelBg);
                ab.setBorder(BorderFactory.createLineBorder(avSelBd, 2));
            } else {
                ab.setBackground(avNorBg);
                ab.setBorder(BorderFactory.createLineBorder(avNorBd, 1));
            }
            ab.setOpaque(true);
            ab.addActionListener(e -> {
                for (JButton b : avatarBtns) {
                    b.setBackground(avNorBg);
                    b.setBorder(BorderFactory.createLineBorder(avNorBd, 1));
                }
                ab.setBackground(avSelBg);
                ab.setBorder(BorderFactory.createLineBorder(avSelBd, 2));
            });
            avatarBtns[i] = ab;
            gbc.gridx = i % avCols; gbc.gridy = 5 + i / avCols; gbc.gridwidth = 1; gbc.weightx = 0.33;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(2, 3, 2, 3);
            panel.add(ab, gbc);
        }

        // ---- 确认按钮 ----
        JButton okBtn = new JButton("确 认");
        okBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        okBtn.setFocusable(false);
        okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okBtn.setBackground(new Color(70, 130, 220));
        okBtn.setForeground(Color.WHITE);
        okBtn.setPreferredSize(new Dimension(140, 38));
        okBtn.addActionListener(e -> {
            SoundManager.setNickname(nickField.getText());
            for (int i = 0; i < genderBtns.length; i++) {
                if (genderBtns[i].getBackground().equals(new Color(220, 235, 255))) {
                    SoundManager.setGender(Integer.parseInt(genders[i][2]));
                    break;
                }
            }
            for (int i = 0; i < avatarBtns.length; i++) {
                if (avatarBtns[i].getBackground().equals(avSelBg)) {
                    SoundManager.setAvatar(i);
                    break;
                }
            }
            statusLabel.setText("昵称：" + SoundManager.getNickname());
            dlg.dispose();
        });
        int lastAvRow = 5 + (SoundManager.AVATAR_FILES.length - 1) / avCols;
        gbc.gridx = 0; gbc.gridy = lastAvRow + 1; gbc.gridwidth = 4; gbc.weightx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(10, 4, 4, 4);
        panel.add(okBtn, gbc);

        return panel;
    }

    private JButton mkMenuBtn(String text) {
        JButton b = UiKit.button(text);
        b.setFont(new Font("微软雅黑", Font.BOLD, 18));
        b.setPreferredSize(new Dimension(240, 52));
        b.addActionListener(e -> SoundManager.sfxClick());
        return b;
    }

    private void setAppIcon() {
        try {
            File f = new File("image/home.png");
            if (f.exists()) setIconImage(ImageIO.read(f));
        } catch (Exception e) { }
    }

    /**
     * 竖排按钮选择对话框：按钮从上到下竖向排列，返回选中索引（-1=关闭窗口）
     */
    private int showVerticalOptions(String title, String message, String[] options) {
        final int[] result = {-1};
        final JDialog dlg = new JDialog(this, title, true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(18, 22, 14, 22)));

        if (message != null && !message.isEmpty()) {
            JLabel msgLb = new JLabel(message);
            msgLb.setFont(new Font("微软雅黑", Font.BOLD, 14));
            msgLb.setForeground(new Color(50, 50, 50));
            msgLb.setBorder(BorderFactory.createEmptyBorder(0, 4, 12, 4));
            panel.add(msgLb, BorderLayout.NORTH);
        }

        JPanel btnPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        btnPanel.setOpaque(false);
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            JButton btn = new JButton(options[i]);
            btn.setFont(new Font("微软雅黑", Font.BOLD, 14));
            btn.setFocusable(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setBackground(new Color(70, 130, 220));
            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(200, 38));
            btn.addActionListener(e -> {
                result[0] = idx;
                dlg.dispose();
            });
            btnPanel.add(btn);
        }
        panel.add(btnPanel, BorderLayout.CENTER);

        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result[0];
    }

    private void startLocal() {
        String[] opts = {"烂头背枪", "五子棋", "双人俄罗斯方块", "你画我猜", "小鸟飞翔", "斗地主", "取消"};
        int c = showVerticalOptions("开始游戏", "选择游戏模式", opts);
        if (c == 0) {
            // 烂头背枪：选择双人/人机
            String[] sub = {"双人", "人机", "取消"};
            int sc = showVerticalOptions("烂头背枪", "选择模式", sub);
            if (sc == 0) {
                GameUI gui = new GameUI();
                gui.setVisible(true);
                dispose();
            } else if (sc == 1) {
                GameUI gui = new GameUI(true);
                gui.setVisible(true);
                dispose();
            }
        } else if (c == 1) {
            // 五子棋：选择双人/人机/三人
            String[] sub = {"双人", "人机", "三人", "取消"};
            int sc = showVerticalOptions("五子棋", "选择模式", sub);
            if (sc == 0) {
                GomokuUI gui = new GomokuUI(2);
                gui.setVisible(true);
                dispose();
            } else if (sc == 1) {
                GomokuUI gui = new GomokuUI(2, new int[]{2});
                gui.setVisible(true);
                dispose();
            } else if (sc == 2) {
                String[] sub3 = {"1人（对战2台电脑）", "2人（对战1台电脑）", "3人（纯本地）", "取消"};
                int sc3 = showVerticalOptions("五子棋（三人）", "几人参与？", sub3);
                if (sc3 == 0) {
                    GomokuUI gui = new GomokuUI(3, new int[]{2, 3});
                    gui.setVisible(true);
                    dispose();
                } else if (sc3 == 1) {
                    GomokuUI gui = new GomokuUI(3, new int[]{3});
                    gui.setVisible(true);
                    dispose();
                } else if (sc3 == 2) {
                    GomokuUI gui = new GomokuUI(3);
                    gui.setVisible(true);
                    dispose();
                }
            }
        } else if (c == 2) {
            TetrisUI gui = new TetrisUI();
            gui.setVisible(true);
            dispose();
        } else if (c == 3) {
            DrawGuessUI gui = new DrawGuessUI();
            gui.setVisible(true);
            dispose();
        } else if (c == 4) {
            String[] sub = {"双人合作（共控一鸟）", "双人双鸟（各自一鸟）", "取消"};
            int sc = showVerticalOptions("小鸟飞翔", "选择模式", sub);
            if (sc == 0) {
                BirdGame gui = new BirdGame();
                gui.setVisible(true);
                dispose();
            } else if (sc == 1) {
                BirdGame gui = new BirdGame(true);
                gui.setVisible(true);
                dispose();
            }
        } else if (c == 5) {
            String[] sub = {"单人对战（3人AI）", "四人对战（2v2组队）", "取消"};
            int sc = showVerticalOptions("斗地主", "选择模式", sub);
            if (sc == 0 || sc == 1) {
                SoundManager.switchBgm(5);   // 斗地主背景音乐
                com.doudizhu.Main.userNickname = SoundManager.getNickname();
                com.doudizhu.Main.userAvatarPath = SoundManager.AVATAR_FILES[SoundManager.getAvatar()];
                com.doudizhu.Main.launchLocal(sc == 1, () -> {
                    SoundManager.switchBgm(0);   // 回主页背景音乐
                    new HomeUI().setVisible(true);
                });
                dispose();
            }
        }
    }

    // ==================== 联机房间 ====================

    private void showNetDialog() {
        if (netPlay != null) {
            int r = JOptionPane.showConfirmDialog(this, "当前正在联机，是否取消并返回？", "联机",
                    JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) cancelNet();
            return;
        }
        String[] opts = {"创建房间", "加入房间", "取消"};
        int c = showVerticalOptions("联机对战", "选择联机方式（局域网）", opts);
        if (c == 0) {
            startHost();
        } else if (c == 1) {
            joinGame();
        }
    }

    /** 主机创建房间：监听最多2个客户端，弹房间界面等主机选模式并开始 */
    private void startHost() {
        try {
            netPlay = new NetPlay();
            netPlay.setMyNick(SoundManager.getNickname());
            netPlay.setMyAvatar(SoundManager.getAvatar());
            netPlay.setMyGender(SoundManager.getGender());
            myPlayer = 1;
            netMode = 0; // 待主机在房间里选定
            netPlay.hostMulti(NET_PORT, 3, mkNetListener());  // 最多3个客户端（四人斗地主）
            statusLabel.setText("房间已创建，等待加入  IP:" + getLocalIP() + "  端口:" + NET_PORT);
            showHostRoom();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "创建失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            netPlay = null; myPlayer = 0; netMode = 0; netPlayerNum = 0;
        }
    }

    private void joinGame() {
        String ip = JOptionPane.showInputDialog(this, "输入主机IP：", "加入房间", JOptionPane.PLAIN_MESSAGE);
        if (ip == null || ip.trim().isEmpty()) return;
        // 统一以玩家身份加入，房间内可切换为观众
        try {
            netPlay = new NetPlay();
            netPlay.setMyNick(SoundManager.getNickname());
            netPlay.setMyAvatar(SoundManager.getAvatar());
            netPlay.setMyGender(SoundManager.getGender());
            myPlayer = 2;
            netPlay.connect(ip.trim(), NET_PORT, mkNetListener(), false);
            statusLabel.setText("已连接，等待主机确认模式...");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "连接失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            netPlay = null; myPlayer = 0; netMode = 0; netPlayerNum = 0;
        }
    }

    private NetPlay.NetListener mkNetListener() {
        return new NetPlay.NetListener() {
            public void onConnected() { HomeUI.this.onNetConnected(); }
            public void onStateReceived(String state) { HomeUI.this.onNetModeReceived(state); }
            public void onOpReceived(String op) { }
            public void onChatReceived(String msg) { }
            public void onDisconnected() { HomeUI.this.onNetDisconnect(); }
            public void onSpectatorChanged(int count) { HomeUI.this.updateRoomStatus(); }
        };
    }

    private void onNetConnected() {
        SoundManager.sfxNetConnect();
        if (myPlayer == 1) {
            // 主机：有客户端连入，刷新房间界面人数
            updateRoomStatus();
        } else if (myPlayer == 2) {
            // 客户端：显示房间等待界面，等主机选模式并开始
            showClientRoom();
        } else if (myPlayer == 3) {
            // 观众：显示观众等待界面
            showSpectatorRoom();
        }
    }

    /** 客户端/观众收到主机发来的模式/玩家号，进入对应游戏 */
    private void onNetModeReceived(String state) {
        if (myPlayer != 2 && myPlayer != 3) return;
        if ("FULL".equals(state)) {
            if (myPlayer == 2) {
                closeRoomDlg();
                cancelNet();
                JOptionPane.showMessageDialog(this, "房间已满", "提示", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        if (state.startsWith("NICKS:")) {
            // 收到主机广播的玩家信息，刷新房间信息卡
            updateRoomStatus();
            return;
        }
        if (state.startsWith("ROLE:")) {
            // 主机确认角色切换
            String role = state.substring(5).trim();
            if ("SPEC".equals(role)) {
                myPlayer = 3;
                closeRoomDlg();
                showSpectatorRoom();
            } else if ("PLAYER".equals(role)) {
                myPlayer = 2;
                closeRoomDlg();
                showClientRoom();
            } else if ("FULL".equals(role)) {
                JOptionPane.showMessageDialog(this, "玩家槽位已满，无法切换回玩家", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (state.startsWith("MODE:")) {
            int mode = 1;
            try { mode = Integer.parseInt(state.substring(5).trim()); } catch (Exception e) { }
            netMode = mode;
            if (myPlayer == 3) {
                if (mode == 6 || mode == 8) {
                    JOptionPane.showMessageDialog(this, "斗地主暂不支持观战", "提示", JOptionPane.INFORMATION_MESSAGE);
                    cancelNet();
                    return;
                }
                enterGame(mode, 0);  // 观众进入观战
                return;
            }
            if (mode != 3 && mode != 6 && mode != 8) {
                enterGame(mode, 2);  // 双人模式客户端固定为白方(2)
            }
            // 三人/四人模式（五子棋三人/斗地主）：等待 PLAYER:n 消息确定玩家号
        } else if (state.startsWith("PLAYER:") && myPlayer == 2) {
            int p = 2;
            try { p = Integer.parseInt(state.substring(7).trim()); } catch (Exception e) { }
            netPlayerNum = p;
            if (netMode == 3 || netMode == 6 || netMode == 8) {
                enterGame(netMode, p);
            }
        }
    }

    private void enterGame(int mode, int player) {
        closeRoomDlg();
        NetPlay np = netPlay;
        if (mode == 6 || mode == 8) {
            enterDdzGame(np, player, mode);
        } else if (mode == 7) {
            BirdGame gui = new BirdGame(np, player);
            gui.setVisible(true);
        } else if (mode == 3) {
            GomokuUI gui = new GomokuUI(np, player, 3);
            gui.setVisible(true);
        } else if (mode == 2) {
            GomokuUI gui = new GomokuUI(np, player);
            gui.setVisible(true);
        } else if (mode == 4) {
            TetrisUI gui = new TetrisUI(np, player);
            gui.setVisible(true);
        } else if (mode == 5) {
            DrawGuessUI gui = new DrawGuessUI(np, player);
            gui.setVisible(true);
        } else {
            GameUI gui = new GameUI(np, player);
            gui.setVisible(true);
        }
        dispose();
    }

    /** 斗地主联机：主机用引擎+适配器，客户端用远程控制器；mode 6=三人 8=四人 */
    private void enterDdzGame(NetPlay np, int player, int mode) {
        SoundManager.switchBgm(5);   // 斗地主背景音乐
        com.doudizhu.Main.userNickname = SoundManager.getNickname();
        com.doudizhu.Main.userAvatarPath = SoundManager.AVATAR_FILES[SoundManager.getAvatar()];
        com.doudizhu.ui.CardPainter.preloadAsync();
        com.doudizhu.ui.SoundPlayer.init();
        boolean fourP = (mode == 8);
        JFrame frame = new JFrame((fourP ? "四人斗地主" : "欢乐斗地主") + "·联机" + (player == 1 ? "(房主)" : ""));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                com.doudizhu.ui.SoundPlayer.shutdown();
                np.close();
                SoundManager.switchBgm(0);   // 回主页背景音乐
                new HomeUI().setVisible(true);
            }
        });
        frame.setResizable(false);
        if (player == 1) {
            if (fourP) {
                // 四人模式：主机用 FourPlayerEngine + Ddz4HostAdapter
                com.doudizhu.engine.FourPlayerEngine engine = new com.doudizhu.engine.FourPlayerEngine();
                engine.setOnlineMode(true);
                java.util.Set<Integer> botSeats = new java.util.HashSet<>();
                if (np.isSeatBot(2)) botSeats.add(1);
                if (np.isSeatBot(3)) botSeats.add(2);
                if (np.isSeatBot(4)) botSeats.add(3);
                if (!botSeats.isEmpty()) engine.setBotSeats(botSeats);
                new Ddz4HostAdapter(np, engine);
                com.doudizhu.ui.GamePanel panel = new com.doudizhu.ui.GamePanel(engine, true);
                panel.setOnBackToHome(frame::dispose);
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                engine.startNewGame();
            } else {
                // 三人模式：主机用 GameEngine + DdzHostAdapter
                com.doudizhu.engine.GameEngine engine = new com.doudizhu.engine.GameEngine();
                engine.setOnlineMode(true);
                java.util.Set<Integer> botSeats = new java.util.HashSet<>();
                if (np.isSeatBot(2)) botSeats.add(1);
                if (np.isSeatBot(3)) botSeats.add(2);
                if (!botSeats.isEmpty()) engine.setBotSeats(botSeats);
                new DdzHostAdapter(np, engine);
                com.doudizhu.ui.GamePanel panel = new com.doudizhu.ui.GamePanel(engine, true);
                panel.setOnBackToHome(frame::dispose);
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                engine.startNewGame();
            }
        } else {
            if (fourP) {
                // 四人客户端：Ddz4RemoteController 镜像主机状态
                Ddz4RemoteController controller = new Ddz4RemoteController(np, player);
                com.doudizhu.ui.GamePanel panel = new com.doudizhu.ui.GamePanel(controller, true);
                panel.setOnBackToHome(frame::dispose);
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
            } else {
                // 三人客户端：DdzRemoteController 镜像主机状态
                DdzRemoteController controller = new DdzRemoteController(np, player);
                com.doudizhu.ui.GamePanel panel = new com.doudizhu.ui.GamePanel(controller, true);
                panel.setOnBackToHome(frame::dispose);
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
            }
        }
    }

    private void onNetDisconnect() {
        if (netPlay == null) return;
        closeRoomDlg();
        netPlay.close();
        netPlay = null;
        myPlayer = 0;
        netMode = 0;
        netPlayerNum = 0;
        statusLabel.setText("连接已断开");
        SoundManager.sfxNetDisconnect();
        JOptionPane.showMessageDialog(this, "连接断开", "联机", JOptionPane.WARNING_MESSAGE);
    }

    private void cancelNet() {
        closeRoomDlg();
        if (netPlay == null) return;
        netPlay.close();
        netPlay = null;
        myPlayer = 0;
        netMode = 0;
        netPlayerNum = 0;
        statusLabel.setText(" ");
    }

    // ==================== 房间等待界面 ====================

    /** 主机房间界面：选模式、看人数、开始游戏 */
    private void showHostRoom() {
        final JDialog dlg = new JDialog(this, "游戏房间（主机）", true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(16, 20, 12, 20)));

        Font titleFont = new Font("微软雅黑", Font.BOLD, 18);
        Font lbFont = new Font("微软雅黑", Font.BOLD, 14);
        Font subFont = new Font("微软雅黑", Font.PLAIN, 12);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridwidth = 1;

        JLabel title = new JLabel("游 戏 房 间");
        title.setFont(titleFont);
        title.setForeground(GOLD);
        gbc.gridy = 0;
        panel.add(title, gbc);

        JLabel ipLb = new JLabel("IP：" + getLocalIP() + "  端口：" + NET_PORT);
        ipLb.setFont(subFont);
        ipLb.setForeground(SUB);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 4, 8, 4);
        panel.add(ipLb, gbc);

        // 玩家信息卡区域（由 updateRoomStatus 动态重建：主机+客户端+观众）
        roomCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomCardsPanel.setOpaque(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 4, 8, 4);
        panel.add(roomCardsPanel, gbc);

        // 可折叠：游戏模式选择（点击展开，竖排紧密排列）
        modeLtbqBtn = mkRadio("烂头背枪（2人）");
        modeGomoku2Btn = mkRadio("五子棋·双人（2人）");
        modeGomoku3Btn = mkRadio("五子棋·三人（3人）");
        modeTetrisBtn = mkRadio("俄罗斯方块（2人）");
        modeDrawGuessBtn = mkRadio("你画我猜（2人）");
        modeDdzBtn = mkRadio("斗地主（3人）");
        modeDdz4Btn = mkRadio("斗地主·四人2v2（4人）");
        modeBirdBtn = mkRadio("小鸟飞翔（2人）");
        ButtonGroup grp = new ButtonGroup();
        grp.add(modeLtbqBtn);
        grp.add(modeGomoku2Btn);
        grp.add(modeGomoku3Btn);
        grp.add(modeTetrisBtn);
        grp.add(modeDrawGuessBtn);
        grp.add(modeDdzBtn);
        grp.add(modeDdz4Btn);
        grp.add(modeBirdBtn);
        modeLtbqBtn.setSelected(true);

        JPanel modeContent = new JPanel(new GridLayout(0, 1, 0, 2));
        modeContent.setOpaque(false);
        modeContent.add(modeLtbqBtn);
        modeContent.add(modeGomoku2Btn);
        modeContent.add(modeGomoku3Btn);
        modeContent.add(modeTetrisBtn);
        modeContent.add(modeDrawGuessBtn);
        modeContent.add(modeDdzBtn);
        modeContent.add(modeDdz4Btn);
        modeContent.add(modeBirdBtn);
        JPanel modeSection = mkCollapseSection("游戏模式（点击选择）", modeContent, false);
        gbc.gridy = 3;
        gbc.insets = new Insets(2, 4, 6, 4);
        panel.add(modeSection, gbc);

        ActionListener modeListener = e -> updateRoomStatus();
        modeLtbqBtn.addActionListener(modeListener);
        modeGomoku2Btn.addActionListener(modeListener);
        modeGomoku3Btn.addActionListener(modeListener);
        modeTetrisBtn.addActionListener(modeListener);
        modeDrawGuessBtn.addActionListener(modeListener);
        modeDdzBtn.addActionListener(modeListener);
        modeDdz4Btn.addActionListener(modeListener);
        modeBirdBtn.addActionListener(modeListener);

        roomConnLabel = new JLabel(" ");
        roomConnLabel.setFont(lbFont);
        roomConnLabel.setForeground(SUB);
        gbc.gridy = 4;
        gbc.insets = new Insets(6, 4, 2, 4);
        panel.add(roomConnLabel, gbc);

        roomSpecLabel = new JLabel(" ");
        roomSpecLabel.setFont(subFont);
        roomSpecLabel.setForeground(SUB);
        gbc.gridy = 5;
        gbc.insets = new Insets(0, 4, 8, 4);
        panel.add(roomSpecLabel, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        startBtn = mkMenuBtn("开始游戏");
        startBtn.setPreferredSize(new Dimension(120, 38));
        JButton cancelBtn = mkMenuBtn("取消");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        btnPanel.add(startBtn);
        btnPanel.add(cancelBtn);
        gbc.gridy = 6;
        gbc.insets = new Insets(2, 4, 4, 4);
        panel.add(btnPanel, gbc);

        startBtn.addActionListener(e -> startGameFromRoom());
        cancelBtn.addActionListener(e -> {
            closeRoomDlg();
            cancelNet();
        });

        roomDlg = dlg;   // 所有组件就绪后再注册，避免提前回调空指针
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        updateRoomStatus();
        dlg.setVisible(true);
    }

    /** 客户端房间界面：显示玩家信息卡，等待主机选模式并开始 */
    private void showClientRoom() {
        final JDialog dlg = new JDialog(this, "游戏房间", true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(16, 20, 12, 20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);

        JLabel title = new JLabel("游 戏 房 间");
        title.setFont(new Font("微软雅黑", Font.BOLD, 18));
        title.setForeground(GOLD);
        gbc.gridy = 0;
        panel.add(title, gbc);

        JLabel okLb = new JLabel("已连接主机！");
        okLb.setFont(new Font("微软雅黑", Font.BOLD, 14));
        okLb.setForeground(BTN_FG);
        gbc.gridy = 1;
        gbc.insets = new Insets(4, 4, 6, 4);
        panel.add(okLb, gbc);

        // 玩家信息卡区域（收到 NICKS 后由 rebuildClientCards 刷新）
        roomCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomCardsPanel.setOpaque(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 4, 8, 4);
        panel.add(roomCardsPanel, gbc);

        JLabel waitLb = new JLabel("等待主机选择模式并开始游戏…");
        waitLb.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        waitLb.setForeground(SUB);
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 4, 10, 4);
        panel.add(waitLb, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setOpaque(false);
        JButton toSpecBtn = mkMenuBtn("切换为观众");
        toSpecBtn.setPreferredSize(new Dimension(120, 38));
        JButton cancelBtn = mkMenuBtn("取消");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        btnPanel.add(toSpecBtn);
        btnPanel.add(cancelBtn);
        gbc.gridy = 4;
        gbc.insets = new Insets(2, 4, 4, 4);
        panel.add(btnPanel, gbc);

        toSpecBtn.addActionListener(e -> {
            if (netPlay != null) netPlay.requestSpectator();
        });
        cancelBtn.addActionListener(e -> {
            closeRoomDlg();
            cancelNet();
        });

        roomDlg = dlg;
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        rebuildClientCards();   // 先显示自己的卡，收到NICKS后再补主机卡
        dlg.setVisible(true);
    }

    /** 观众房间界面：显示玩家信息卡和观众身份，等待主机开始 */
    private void showSpectatorRoom() {
        if (roomDlg != null && roomDlg.isVisible()) return; // 已显示则不重复弹
        final JDialog dlg = new JDialog(this, "游戏房间（观众）", true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(16, 20, 12, 20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);

        JLabel title = new JLabel("游 戏 房 间");
        title.setFont(new Font("微软雅黑", Font.BOLD, 18));
        title.setForeground(GOLD);
        gbc.gridy = 0;
        panel.add(title, gbc);

        JLabel specLb = new JLabel("你是观众");
        specLb.setFont(new Font("微软雅黑", Font.BOLD, 14));
        specLb.setForeground(new Color(110, 220, 130));
        gbc.gridy = 1;
        gbc.insets = new Insets(4, 4, 6, 4);
        panel.add(specLb, gbc);

        // 玩家信息卡区域
        roomCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomCardsPanel.setOpaque(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 4, 8, 4);
        panel.add(roomCardsPanel, gbc);

        JLabel waitLb = new JLabel("等待主机选择模式并开始游戏…");
        waitLb.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        waitLb.setForeground(SUB);
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 4, 10, 4);
        panel.add(waitLb, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.setOpaque(false);
        // 房间内切换来的观众可切换回玩家（JOIN:SPECTATOR 直连观众不可）
        boolean canSwitch = netPlay != null && netPlay.isRoleSpectator();
        if (canSwitch) {
            JButton toPlayerBtn = mkMenuBtn("切换为玩家");
            toPlayerBtn.setPreferredSize(new Dimension(120, 38));
            toPlayerBtn.addActionListener(e -> {
                if (netPlay != null) netPlay.requestPlayer();
            });
            btnPanel.add(toPlayerBtn);
        }
        JButton cancelBtn = mkMenuBtn("取消");
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        btnPanel.add(cancelBtn);
        gbc.gridy = 4;
        gbc.insets = new Insets(2, 4, 4, 4);
        panel.add(btnPanel, gbc);

        cancelBtn.addActionListener(e -> {
            closeRoomDlg();
            cancelNet();
        });

        roomDlg = dlg;
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        rebuildClientCards();
        dlg.setVisible(true);
    }

    private JRadioButton mkRadio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        rb.setForeground(BTN_FG);
        rb.setBackground(PANEL_BG);
        rb.setFocusable(false);
        // 隐藏单选圆圈，用选中高亮代替
        Icon empty = new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {}
            public int getIconWidth() { return 0; }
            public int getIconHeight() { return 0; }
        };
        rb.setIcon(empty);
        rb.setSelectedIcon(empty);
        rb.setBorderPainted(false);
        rb.setContentAreaFilled(false);
        rb.setOpaque(true);
        rb.addItemListener(e -> {
            boolean sel = rb.isSelected();
            rb.setBackground(sel ? new Color(52, 58, 76) : PANEL_BG);
            rb.setForeground(sel ? GOLD : BTN_FG);
        });
        return rb;
    }

    /** 玩家信息卡：角色标签→昵称→头像→性别，竖排紧凑卡片 */
    private JPanel mkPlayerCard(String nick, int avatarIdx, int genderIdx, String roleTag, Color tagColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(38, 42, 58));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        card.setPreferredSize(new Dimension(104, 148));
        card.setMaximumSize(new Dimension(104, 148));

        JLabel tagLb = new JLabel(roleTag);
        tagLb.setFont(new Font("微软雅黑", Font.BOLD, 11));
        tagLb.setForeground(tagColor);
        tagLb.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(tagLb);

        JLabel nameLb = new JLabel(nick == null || nick.isEmpty() ? "玩家" : nick);
        nameLb.setFont(new Font("微软雅黑", Font.BOLD, 13));
        nameLb.setForeground(BTN_FG);
        nameLb.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(nameLb);

        int av = Math.max(0, Math.min(SoundManager.AVATAR_FILES.length - 1, avatarIdx));
        ImageIcon avIcon = loadIcon(SoundManager.AVATAR_FILES[av], 48);
        JLabel avLb = avIcon != null ? new JLabel(avIcon) : new JLabel("头像");
        avLb.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        avLb.setForeground(SUB);
        avLb.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(avLb);
        card.add(Box.createVerticalStrut(2));

        JPanel gPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        gPanel.setOpaque(false);
        gPanel.setMaximumSize(new Dimension(100, 22));
        int gd = Math.max(0, Math.min(4, genderIdx));
        if (GENDER_INFO[gd][0] != null) {
            ImageIcon gIcon = loadIcon(GENDER_INFO[gd][0], 16);
            if (gIcon != null) gPanel.add(new JLabel(gIcon));
        }
        JLabel gLb = new JLabel(GENDER_INFO[gd][1]);
        gLb.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        gLb.setForeground(SUB);
        gPanel.add(gLb);
        card.add(gPanel);

        return card;
    }

    /** 等待加入的可点击占位卡：点击加号添加人机玩家 */
    private JPanel mkWaitingCard(String roleTag, final ActionListener onAddBot) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(34, 38, 52));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 66, 86), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        card.setPreferredSize(new Dimension(104, 148));
        card.setMaximumSize(new Dimension(104, 148));

        JLabel tagLb = new JLabel(roleTag);
        tagLb.setFont(new Font("微软雅黑", Font.BOLD, 11));
        tagLb.setForeground(SUB);
        tagLb.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(tagLb);

        JLabel waitLb = new JLabel("点击加人机");
        waitLb.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        waitLb.setForeground(SUB);
        waitLb.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(waitLb);

        JButton plusBtn = new JButton("＋");
        plusBtn.setFont(new Font("微软雅黑", Font.BOLD, 36));
        plusBtn.setForeground(new Color(120, 130, 160));
        plusBtn.setBackground(new Color(42, 46, 62));
        plusBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        plusBtn.setFocusable(false);
        plusBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        plusBtn.setBorderPainted(false);
        plusBtn.setContentAreaFilled(false);
        plusBtn.setOpaque(false);
        plusBtn.addActionListener(onAddBot);
        card.add(plusBtn);

        return card;
    }

    /** 人机玩家信息卡：显示人机信息+删除按钮 */
    private JPanel mkBotCard(String nick, int avatarIdx, int genderIdx,
                             String roleTag, final ActionListener onRemove) {
        JPanel card = mkPlayerCard(nick, avatarIdx, genderIdx, roleTag, new Color(180, 140, 255));
        // 在卡片底部加删除按钮
        JButton delBtn = new JButton("✕ 移除");
        delBtn.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        delBtn.setForeground(new Color(200, 100, 100));
        delBtn.setBackground(new Color(50, 40, 50));
        delBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        delBtn.setFocusable(false);
        delBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        delBtn.setBorderPainted(false);
        delBtn.setContentAreaFilled(false);
        delBtn.setOpaque(false);
        delBtn.addActionListener(onRemove);
        card.add(Box.createVerticalStrut(2));
        card.add(delBtn);
        return card;
    }

    /** 可折叠区域：点击标题栏往下展开/收起竖排选项，不弹新窗口 */
    private JPanel mkCollapseSection(String title, JComponent content, boolean expanded) {
        JPanel section = new JPanel(new BorderLayout(0, 0));
        section.setOpaque(false);

        final JButton header = new JButton((expanded ? "▾ " : "▸ ") + title);
        header.setFont(new Font("微软雅黑", Font.BOLD, 13));
        header.setForeground(BTN_FG);
        header.setBackground(new Color(42, 46, 62));
        header.setFocusable(false);
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setHorizontalAlignment(SwingConstants.LEFT);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(3, 18, 2, 6));
        wrap.add(content, BorderLayout.CENTER);
        wrap.setVisible(expanded);

        header.addActionListener(e -> {
            boolean vis = !wrap.isVisible();
            wrap.setVisible(vis);
            header.setText((vis ? "▾ " : "▸ ") + title);
            if (roomDlg != null) {
                roomDlg.pack();
                roomDlg.setLocationRelativeTo(HomeUI.this);
            }
        });

        section.add(header, BorderLayout.NORTH);
        section.add(wrap, BorderLayout.CENTER);
        return section;
    }

    /** 刷新房间人数显示、玩家信息卡与开始按钮状态 */
    private void updateRoomStatus() {
        if (roomDlg == null || netPlay == null) return;
        if (myPlayer == 1) {
            // 主机：重建信息卡 + 更新人数
            rebuildHostCards();
            int need = getModeNeed();
            int effPlayers = netPlay.getEffectivePlayerCount();
            int bots = netPlay.getBotCount();
            int total = effPlayers + bots;
            roomConnLabel.setText("已连接玩家：" + total + " / " + need
                    + (total < need ? "，等待加入…" : "，可以开始！"));
            roomConnLabel.setForeground(total >= need ? GOLD : SUB);
            int specs = netPlay.getSpectatorCount();
            roomSpecLabel.setText(specs > 0 ? "观众：" + specs + " 人" : " ");
            startBtn.setEnabled(total >= need);
        } else {
            // 客户端/观众：刷新信息卡
            rebuildClientCards();
        }
    }

    /** 当前所选模式需要的客户端玩家数（不含主机） */
    private int getModeNeed() {
        if (modeDdz4Btn != null && modeDdz4Btn.isSelected()) return 3;
        if (modeGomoku3Btn != null && modeGomoku3Btn.isSelected()) return 2;
        if (modeDdzBtn != null && modeDdzBtn.isSelected()) return 2;
        return 1;
    }

    /** 主机端：重建玩家信息卡（主机+有效客户端+人机+等待槽位+观众） */
    private void rebuildHostCards() {
        if (roomCardsPanel == null || netPlay == null || modeGomoku3Btn == null) return;
        roomCardsPanel.removeAll();
        // 主机卡
        roomCardsPanel.add(mkPlayerCard(SoundManager.getNickname(),
                SoundManager.getAvatar(), SoundManager.getGender(), "主机", GOLD));
        // 有效（非观众）客户端卡
        int effPlayers = netPlay.getEffectivePlayerCount();
        for (int i = 0; i < effPlayers; i++) {
            String nick = netPlay.getPlayerNick(i + 2);
            roomCardsPanel.add(mkPlayerCard(nick,
                    netPlay.getPlayerAvatar(i + 2), netPlay.getPlayerGender(i + 2),
                    "玩家" + (i + 2), new Color(120, 180, 255)));
        }
        // 人机卡（带删除按钮）
        int botCount = netPlay.getBotCount();
        for (int i = 0; i < botCount; i++) {
            roomCardsPanel.add(mkBotCard(netPlay.getBotNick(i),
                    netPlay.getBotAvatar(i), netPlay.getBotGender(i),
                    "人机", new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            netPlay.removeBot();
                            updateRoomStatus();
                        }
                    }));
        }
        // 等待槽位（可点击加人机）
        int need = getModeNeed();
        for (int i = effPlayers + botCount; i < need; i++) {
            roomCardsPanel.add(mkWaitingCard("玩家" + (i + 2), new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int r = netPlay.addBot();
                    if (r < 0) {
                        JOptionPane.showMessageDialog(roomDlg, "人机数量已达上限",
                                "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                    updateRoomStatus();
                }
            }));
        }
        // 观众卡（含观众角色客户端）
        for (String[] info : netPlay.getSpectatorInfos()) {
            int av = 0, gd = 0;
            try { av = Integer.parseInt(info[1]); } catch (Exception e) { }
            try { gd = Integer.parseInt(info[2]); } catch (Exception e) { }
            roomCardsPanel.add(mkPlayerCard(info[0], av, gd, "观众", new Color(110, 220, 130)));
        }
        roomCardsPanel.revalidate();
        roomCardsPanel.repaint();
        if (roomDlg != null) {
            roomDlg.pack();
            roomDlg.setLocationRelativeTo(this);
        }
    }

    /** 客户端/观众端：重建玩家信息卡（自己+主机+其他玩家） */
    private void rebuildClientCards() {
        if (roomCardsPanel == null || netPlay == null) return;
        roomCardsPanel.removeAll();
        if (myPlayer == 2) {
            roomCardsPanel.add(mkPlayerCard(SoundManager.getNickname(),
                    SoundManager.getAvatar(), SoundManager.getGender(), "我", new Color(120, 180, 255)));
        } else if (myPlayer == 3) {
            roomCardsPanel.add(mkPlayerCard(SoundManager.getNickname(),
                    SoundManager.getAvatar(), SoundManager.getGender(), "我·观众", new Color(110, 220, 130)));
        }
        // 主机卡（收到NICKS后才有）
        String hNick = netPlay.getPlayerNick(1);
        if (hNick != null) {
            roomCardsPanel.add(mkPlayerCard(hNick,
                    netPlay.getPlayerAvatar(1), netPlay.getPlayerGender(1), "主机", GOLD));
        }
        // 其他玩家卡
        String p2 = netPlay.getPlayerNick(2);
        if (p2 != null && myPlayer != 2) {
            roomCardsPanel.add(mkPlayerCard(p2,
                    netPlay.getPlayerAvatar(2), netPlay.getPlayerGender(2), "玩家2", new Color(120, 180, 255)));
        }
        String p3 = netPlay.getPlayerNick(3);
        if (p3 != null && myPlayer != 3) {
            roomCardsPanel.add(mkPlayerCard(p3,
                    netPlay.getPlayerAvatar(3), netPlay.getPlayerGender(3), "玩家3", new Color(120, 180, 255)));
        }
        String p4 = netPlay.getPlayerNick(4);
        if (p4 != null) {
            roomCardsPanel.add(mkPlayerCard(p4,
                    netPlay.getPlayerAvatar(4), netPlay.getPlayerGender(4), "玩家4", new Color(120, 180, 255)));
        }
        roomCardsPanel.revalidate();
        roomCardsPanel.repaint();
        if (roomDlg != null) {
            roomDlg.pack();
            roomDlg.setLocationRelativeTo(this);
        }
    }

    /** 主机点开始游戏：发送模式给客户端，多方进入 */
    private void startGameFromRoom() {
        if (netPlay == null) return;
        int mode = modeDdz4Btn.isSelected() ? 8 : modeDdzBtn.isSelected() ? 6 : modeBirdBtn.isSelected() ? 7
                : modeGomoku3Btn.isSelected() ? 3 : modeGomoku2Btn.isSelected() ? 2
                : modeTetrisBtn.isSelected() ? 4 : modeDrawGuessBtn.isSelected() ? 5 : 1;
        int need = (mode == 3 || mode == 6) ? 2 : (mode == 8) ? 3 : 1;
        int effPlayers = netPlay.getEffectivePlayerCount();
        int bots = netPlay.getBotCount();
        if (effPlayers + bots < need) {
            JOptionPane.showMessageDialog(roomDlg, "等待玩家加入…", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // 仅斗地主（3人/4人）支持人机补位
        if (bots > 0 && mode != 6 && mode != 8) {
            JOptionPane.showMessageDialog(roomDlg, "该模式暂不支持人机补位，请先移除人机", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        netPlay.stopAccept(); // 不再接受新连接
        netMode = mode;
        netPlay.broadcastRoomInfo(); // 开局前同步昵称和观众数
        if (mode == 3 || mode == 6 || mode == 8) {
            // 三人/四人模式：按有效玩家顺序分配玩家号
            netPlay.sendState("MODE:" + mode);
            java.util.List<Integer> pi = netPlay.getPlayerClientIndices();
            for (int i = 0; i < pi.size(); i++) {
                netPlay.sendToClient(pi.get(i), "ST:PLAYER:" + (i + 2));
            }
            closeRoomDlg();
            enterGame(mode, 1);
        } else {
            // 双人模式：若多余有效玩家客户端已连入，通知并踢出（观众角色不踢）
            if (effPlayers > 1) {
                java.util.List<Integer> pi = netPlay.getPlayerClientIndices();
                for (int i = 1; i < pi.size(); i++) {
                    netPlay.sendToClient(pi.get(i), "ST:FULL");
                    netPlay.kickClient(pi.get(i));
                }
            }
            netPlay.sendState("MODE:" + mode);
            closeRoomDlg();
            enterGame(mode, 1);
        }
    }

    private void closeRoomDlg() {
        if (roomDlg != null) {
            JDialog d = roomDlg;
            roomDlg = null;
            d.dispose();
        }
    }

    private static String getLocalIP() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "未知"; }
    }

    // ==================== 规则 ====================

    private void showRules() {
        String[] opts = {"烂头背枪", "五子棋", "俄罗斯方块", "你画我猜", "小鸟飞翔", "斗地主", "取消"};
        int c = showVerticalOptions("游戏规则", "查看哪个游戏的规则？", opts);
        if (c == 0) showRulesLtbq();
        else if (c == 1) showRulesGomoku();
        else if (c == 2) showRulesTetris();
        else if (c == 3) showRulesDrawGuess();
        else if (c == 4) showRulesBird();
        else if (c == 5) showRulesDoudizhu();
    }

    private void showRulesBird() {
        String rules = "【双人合作】两人共控同一只小鸟，躲避右侧飞来的障碍！\n"
                + "\n玩家1（橙色）：按住 F 键 小鸟向上飞"
                + "\n玩家2（青色）：按住 ↓ 键 小鸟向下飞"
                + "\n两人同时按则原地不动——必须沟通配合！\n"
                + "\n【双人双鸟】各自控制一只小鸟，比拼分数！\n"
                + "\n玩家1（橙色鸟）：按 F 向上飞  ·  按 J 向下飞"
                + "\n玩家2（青色鸟）：按 ↑ 向上飞  ·  按 ↓ 向下飞"
                + "\n撞到障碍该鸟淘汰，两鸟全灭游戏结束，分数高者获胜\n"
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

    private void showRulesDoudizhu() {
        String rules = "欢乐斗地主：经典扑克对战，支持单机AI、四人2v2组队和局域网联机！\n"
                + "\n【三人模式】54张牌，3人各17张，留3张底牌"
                + "\n叫地主流程：叫/不叫 → 抢/不抢 → 加倍/不加倍 → 明牌/不明牌"
                + "\n地主拿3张底牌共20张，先出牌；2农民配合对抗地主\n"
                + "\n【四人模式】4人各13张牌，2v2组队对战\n"
                + "\n【牌型大小】"
                + "\n  王炸（双王）最大，可打一切"
                + "\n  炸弹（四张同点）次之，可打非炸弹牌型"
                + "\n  同牌型比点数：3<4<5<...<K<A<2<小王<大王\n"
                + "\n【可出牌型】"
                + "\n  单张  ·  对子  ·  三张  ·  三带一  ·  三带二"
                + "\n  顺子（5张以上连续）  ·  连对（3对以上连续）"
                + "\n  飞机（2组以上连续三张）  ·  飞机带翅膀"
                + "\n  四带两对  ·  炸弹  ·  王炸\n"
                + "\n【春天】地主出完牌农民一张未出=春天"
                + "\n【反春天】地主只出了一手牌即被农民打完\n"
                + "\n【联机】创建房间后把IP告诉好友，好友加入房间输入IP即可";
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

    private void showRulesDrawGuess() {
        String rules = "你画我猜：两人轮流画成语和猜成语\n"
                + "\n每轮系统随机出一个四字成语作为题目"
                + "\n画者根据题目在画板上作画（不能写字或拼音）"
                + "\n猜者在下方输入框输入答案，答对即本轮成功\n"
                + "\n流程：绘画阶段 → 猜测阶段 → 公布答案 → 互换角色"
                + "\n绘画时间结束后进入猜测阶段，猜者继续答题"
                + "\n画者也可点击\"结束绘画\"提前进入猜测阶段"
                + "\n所有轮次结束后显示合作成绩\n"
                + "\n画者工具：8种颜色、4档笔刷粗细、橡皮擦、清空"
                + "\n本地模式：两人共用一台电脑，轮次开始时弹窗出示题目"
                + "\n联机模式：主机=玩家1，客户端=玩家2，笔划实时同步"
                + "\n联机时画者右侧直接显示题目，可点\"切换模式\"换游戏"
                + "\n右侧聊天框可随时发消息";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(new Color(24, 27, 38));
        ta.setForeground(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(400, 300));
        JOptionPane.showMessageDialog(this, sp, "你画我猜规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRulesTetris() {
        String rules = "双人共享14×22棋盘，两人同时控制各自的方块下落\n"
                + "\n玩家1（橙色）：A/D 左右移动，W/S 旋转"
                + "\n玩家2（青色）：←/→ 左右移动，↑/↓ 旋转\n"
                + "\n两种颜色的方块混合填满一整行即可消除"
                + "\n方块匀速下落，不能加速，只能左右移动和旋转"
                + "\n每消10行下落速度略微加快\n"
                + "\nP键暂停/继续"
                + "\n方块堆到顶部无法生成新方块则游戏结束\n"
                + "\n【联机】主机=橙方(WASD)，客户端=青方(方向键)"
                + "\n右侧聊天框可互发消息";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(new Color(24, 27, 38));
        ta.setForeground(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(400, 280));
        JOptionPane.showMessageDialog(this, sp, "俄罗斯方块规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRulesGomoku() {
        String rules = "15×15棋盘，黑方先行，轮流在交叉点落子\n"
                + "\n先在横、竖、斜任一方向连成五子者获胜"
                + "\n棋盘下满仍无五连则为平局\n"
                + "\n双人模式：黑白轮流落子"
                + "\n人机模式：你执黑方先行，电脑执白方"
                + "\n三人模式：黑→白→绿轮流落子，先连五子者胜"
                + "\n三人人机：可选1人对战2台电脑，或2人对战1台电脑\n"
                + "\n联机双人：主机=黑方（先手），客户端=白方"
                + "\n联机三人：主机=黑方，先加入=白方，后加入=绿方"
                + "\n右侧聊天框可随时发消息";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(new Color(24, 27, 38));
        ta.setForeground(new Color(228, 231, 240));
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(400, 300));
        JOptionPane.showMessageDialog(this, sp, "五子棋规则", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRulesLtbq() {
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
                + "  主机=红方，客户端=蓝方，右侧聊天框可互发消息\n"
                + "\n【人机】选择\"烂头背枪（人机）\"与电脑对战\n"
                + "  你执红方先行，电脑执蓝方";
        JTextArea ta = new JTextArea(rules);
        ta.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setBackground(new Color(255, 255, 245));
        ta.setForeground(Color.BLACK);
        ta.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(480, 460));
        JOptionPane.showMessageDialog(this, sp, "烂头背枪规则", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 背景面板：绘制home.png（等比缩放铺满+暗色遮罩） */
    private class BackgroundPanel extends JPanel {
        private BufferedImage img;
        BackgroundPanel() {
            try {
                File f = new File("image/blackground.jpg");
                if (!f.exists()) f = new File("image/home.png");
                if (f.exists()) img = ImageIO.read(f);
            } catch (Exception e) { }
            setOpaque(true);
            setBackground(BG);
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                float sx = (float) getWidth() / img.getWidth();
                float sy = (float) getHeight() / img.getHeight();
                float s = Math.max(sx, sy);
                int w = (int) (img.getWidth() * s), h = (int) (img.getHeight() * s);
                int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
                g2.drawImage(img, x, y, w, h, null);
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(12, 14, 24, 110));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}