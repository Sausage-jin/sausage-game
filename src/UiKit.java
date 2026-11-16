import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;

/**
 * UI 美化工具：统一的圆角按钮（自带 hover 变亮 / 按压变暗 / 顶部高光）。
 * 纯视觉组件，不改变任何游戏逻辑与文字。
 *
 * 关键点：自绘背景读取按钮的 getBackground()，
 * 因此运行时再 setBackground(...)（如绿色“结束绘画”、金色主按钮）依然生效。
 */
public final class UiKit {

    private UiKit() {}

    // 与各界面一致的暗色主题
    public static final Color BTN_BG = new Color(52, 58, 76);
    public static final Color BTN_FG = new Color(228, 231, 240);
    public static final Color GOLD   = new Color(255, 215, 110);
    public static final Color DARK_FG = new Color(34, 36, 46);
    public static final Color BORDER = new Color(110, 120, 148);

    private static final int ARC = 14;   // 圆角半径

    /** 普通按钮（深色背景、浅色字） */
    public static JButton button(String text) {
        return make(text, BTN_BG, BTN_FG);
    }

    /** 主操作按钮（金色背景、深色字） */
    public static JButton primary(String text) {
        return make(text, GOLD, DARK_FG);
    }

    /** 指定背景/前景的圆角按钮 */
    public static JButton make(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("微软雅黑", Font.BOLD, 13));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(5, 14, 5, 14));
        b.setUI(new RoundedUI());
        return b;
    }

    /** 自绘圆角背景的 ButtonUI：hover 变亮、按压变暗、顶部柔和高光、细描边 */
    private static final class RoundedUI extends BasicButtonUI {
        @Override
        public void update(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = b.getModel();
            Color base = b.getBackground();
            Color fill;
            if (!b.isEnabled())        fill = shade(base, 0.72f);
            else if (m.isPressed())    fill = shade(base, 0.80f);
            else if (m.isRollover())   fill = shade(base, 1.16f);
            else                       fill = base;

            int w = c.getWidth(), h = c.getHeight();
            // 圆角底色
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            // 顶部高光，制造一点立体层次
            g2.setColor(new Color(255, 255, 255, 20));
            g2.fillRoundRect(1, 1, w - 2, Math.max(1, h / 2 - 1), ARC, ARC);
            // 细描边
            g2.setColor(shade(base, 0.55f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.dispose();

            super.update(g, c);   // 交给基类绘制文字与图标
        }

        private static Color shade(Color c, float f) {
            return new Color(clamp((int) (c.getRed() * f)),
                             clamp((int) (c.getGreen() * f)),
                             clamp((int) (c.getBlue() * f)),
                             c.getAlpha());
        }

        private static int clamp(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
        }
    }

    // ==================== 统一弹窗（白底蓝按钮竖排，与主页风格一致） ====================

    /**
     * 竖排按钮选择对话框：按钮从上到下竖向排列，返回选中索引（-1=关闭窗口）。
     * 风格与 HomeUI.showVerticalOptions 一致：白底、蓝底白字按钮。
     */
    public static int showVerticalOptions(Window owner, String title, String message, String[] options) {
        final int[] result = {-1};
        final JDialog dlg = new JDialog();
        dlg.setTitle(title);
        dlg.setModal(true);
        dlg.setResizable(false);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

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
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return result[0];
    }

    /**
     * 游戏内设置对话框：背景音乐/游戏音效开关+音量滑块。
     * 白底风格，与主页设置弹窗一致。
     */
    public static void showGameSettings(Window owner) {
        final JDialog dlg = new JDialog();
        dlg.setTitle("游戏设置");
        dlg.setModal(true);
        dlg.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(22, 26, 16, 26)));

        Font cbFont = new Font("微软雅黑", Font.BOLD, 13);
        Font lbFont = new Font("微软雅黑", Font.PLAIN, 12);
        Color subColor = new Color(120, 120, 120);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ---- 背景音乐 ----
        final JCheckBox musicCb = new JCheckBox("背景音乐");
        musicCb.setSelected(SoundManager.isMusicOn());
        musicCb.setFont(cbFont);
        musicCb.setForeground(new Color(50, 50, 50));
        musicCb.setBackground(Color.WHITE);
        musicCb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(musicCb, gbc);

        final JLabel musicVolLb = new JLabel("音量 " + SoundManager.getMusicVolume());
        musicVolLb.setFont(lbFont);
        musicVolLb.setForeground(subColor);
        musicVolLb.setPreferredSize(new Dimension(60, 20));
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
        sfxCb.setForeground(new Color(50, 50, 50));
        sfxCb.setBackground(Color.WHITE);
        sfxCb.setFocusable(false);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(sfxCb, gbc);

        final JLabel sfxVolLb = new JLabel("音量 " + SoundManager.getSfxVolume());
        sfxVolLb.setFont(lbFont);
        sfxVolLb.setForeground(subColor);
        sfxVolLb.setPreferredSize(new Dimension(60, 20));
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(sfxVolLb, gbc);

        final JSlider sfxSlider = new JSlider(0, 100, SoundManager.getSfxVolume());
        sfxSlider.setOpaque(false);
        sfxSlider.setPreferredSize(new Dimension(150, 26));
        sfxSlider.setEnabled(SoundManager.isSfxOn());
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 1;
        panel.add(sfxSlider, gbc);

        // ---- 确定按钮 ----
        JButton okBtn = new JButton("确 定");
        okBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        okBtn.setFocusable(false);
        okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okBtn.setBackground(new Color(70, 130, 220));
        okBtn.setForeground(Color.WHITE);
        okBtn.setPreferredSize(new Dimension(200, 36));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weightx = 1;
        gbc.insets = new Insets(14, 4, 2, 4);
        panel.add(okBtn, gbc);

        musicCb.addActionListener(e -> musicSlider.setEnabled(musicCb.isSelected()));
        sfxCb.addActionListener(e -> sfxSlider.setEnabled(sfxCb.isSelected()));
        musicSlider.addChangeListener(e -> musicVolLb.setText("音量 " + musicSlider.getValue()));
        sfxSlider.addChangeListener(e -> sfxVolLb.setText("音量 " + sfxSlider.getValue()));
        okBtn.addActionListener(e -> {
            SoundManager.setMusicOn(musicCb.isSelected());
            SoundManager.setMusicVolume(musicSlider.getValue());
            SoundManager.setSfxOn(sfxCb.isSelected());
            SoundManager.setSfxVolume(sfxSlider.getValue());
            dlg.dispose();
        });

        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
