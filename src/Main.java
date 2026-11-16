import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;

/**
 * 程序入口
 */
public class Main {

    // 全局暗色主题配色（与各UI一致）
    private static final Color THEME_BG     = new Color(24, 27, 38);
    private static final Color THEME_PANEL  = new Color(30, 34, 46);
    private static final Color THEME_FIELD  = new Color(20, 23, 32);
    private static final Color THEME_FG     = new Color(228, 231, 240);
    private static final Color THEME_SUB    = new Color(170, 176, 196);
    private static final Color THEME_BTN_BG = new Color(52, 58, 76);
    private static final Color THEME_BORDER = new Color(96, 104, 128);
    private static final Color THEME_SEL    = new Color(90, 110, 160);

    /** 设置全局UI默认值，让JOptionPane等小窗口呈现统一暗色主题 */
    private static void applyDarkTheme() {
        UIManager.put("OptionPane.background", THEME_BG);
        UIManager.put("OptionPane.messageForeground", THEME_FG);
        UIManager.put("OptionPane.messageDialog.border",
                BorderUIResource.getEtchedBorderUIResource());
        UIManager.put("Panel.background", THEME_BG);
        UIManager.put("Label.foreground", THEME_FG);
        UIManager.put("Label.font", new Font("微软雅黑", Font.PLAIN, 13));

        UIManager.put("Button.background", THEME_BTN_BG);
        UIManager.put("Button.foreground", THEME_FG);
        UIManager.put("Button.font", new Font("微软雅黑", Font.BOLD, 13));
        UIManager.put("Button.border", new BorderUIResource(
                new EmptyBorder(5, 14, 5, 14)));
        UIManager.put("Button.focus", new Color(80, 90, 120));
        UIManager.put("Button.select", new Color(70, 78, 100));

        UIManager.put("CheckBox.background", THEME_BG);
        UIManager.put("CheckBox.foreground", THEME_FG);
        UIManager.put("CheckBox.font", new Font("微软雅黑", Font.BOLD, 13));
        UIManager.put("CheckBox.focus", THEME_BG);

        UIManager.put("RadioButton.background", THEME_BG);
        UIManager.put("RadioButton.foreground", THEME_FG);
        UIManager.put("RadioButton.font", new Font("微软雅黑", Font.PLAIN, 13));
        UIManager.put("RadioButton.focus", THEME_BG);

        UIManager.put("TextField.background", THEME_FIELD);
        UIManager.put("TextField.foreground", THEME_FG);
        UIManager.put("TextField.caretForeground", THEME_FG);
        UIManager.put("TextField.font", new Font("微软雅黑", Font.PLAIN, 13));

        UIManager.put("TextArea.background", THEME_FIELD);
        UIManager.put("TextArea.foreground", THEME_FG);
        UIManager.put("TextArea.caretForeground", THEME_FG);
        UIManager.put("TextArea.font", new Font("微软雅黑", Font.PLAIN, 13));

        UIManager.put("ScrollPane.background", THEME_BG);
        UIManager.put("ScrollPane.border", new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));
        UIManager.put("Viewport.background", THEME_BG);

        UIManager.put("ComboBox.background", THEME_BTN_BG);
        UIManager.put("ComboBox.foreground", THEME_FG);
        UIManager.put("ComboBox.selectionBackground", THEME_SEL);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);

        UIManager.put("Slider.background", THEME_BG);
        UIManager.put("Slider.foreground", new Color(255, 215, 110));

        UIManager.put("OptionPane.buttonAreaBorder",
                new BorderUIResource.EmptyBorderUIResource(6, 8, 6, 8));
    }

    public static void main(String[] args) {
        // 使用跨平台外观，确保按钮背景色在所有系统下正常显示
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            applyDarkTheme();
        } catch (Exception e) {
            // 忽略，使用默认外观
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new HomeUI().setVisible(true);
            }
        });
    }
}