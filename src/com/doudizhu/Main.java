package com.doudizhu;

import com.doudizhu.engine.FourPlayerEngine;
import com.doudizhu.engine.GameEngine;
import com.doudizhu.ui.CardPainter;
import com.doudizhu.ui.GamePanel;
import com.doudizhu.ui.SoundPlayer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 斗地主游戏入口（融合版）：供游戏合集直接调用，无独立主页。
 * launchLocal(fourPlayer, onClosed) — 本地对战（3人AI / 4人2v2）
 * 联机对战由 HomeUI 统一房间体系接管（DdzHostAdapter/DdzRemoteController）。
 * onClosed 在斗地主窗口关闭后回调（用于返回游戏合集主页）。
 */
public class Main {

    /** 用户资料：由主页（HomeUI）在启动斗地主前设置，供引擎和面板读取 */
    public static String userNickname = "你";
    public static String userAvatarPath = null;   // null = 未设置，面板用默认头像

    /** 预加载资源（幂等） */
    private static void prepare() {
        CardPainter.preloadAsync();
        SoundPlayer.init();
    }

    /** 创建斗地主游戏窗口 */
    private static JFrame createFrame(String title, Runnable onClosed) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                SoundPlayer.shutdown();
                if (onClosed != null) onClosed.run();
            }
        });
        frame.setResizable(false);
        return frame;
    }

    private static void showCentered(JFrame frame) {
        frame.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(screen.width / 2 - frame.getWidth() / 2,
                screen.height / 2 - frame.getHeight() / 2);
        frame.setVisible(true);
    }

    /** 本地对战：fourPlayer=false 三人AI，true 四人2v2组队 */
    public static void launchLocal(boolean fourPlayer, Runnable onClosed) {
        prepare();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = createFrame(fourPlayer ? "欢乐斗地主·四人2v2" : "欢乐斗地主·单人对战", onClosed);
            GamePanel panel;
            if (fourPlayer) {
                FourPlayerEngine engine = new FourPlayerEngine();
                panel = new GamePanel(engine, false);
                frame.setContentPane(panel);
                showCentered(frame);
                panel.setOnBackToHome(frame::dispose);
                engine.startNewGame();
            } else {
                GameEngine engine = new GameEngine();
                panel = new GamePanel(engine, false);
                frame.setContentPane(panel);
                showCentered(frame);
                panel.setOnBackToHome(frame::dispose);
                engine.startNewGame();
            }
        });
    }

    /** 独立运行入口（不返回合集） */
    public static void main(String[] args) {
        launchLocal(false, null);
    }
}