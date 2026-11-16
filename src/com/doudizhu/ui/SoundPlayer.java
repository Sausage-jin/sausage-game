package com.doudizhu.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * 音效播放器：通过常驻 PowerShell 进程调用 Windows winmm.dll 的 mciSendString 播放 mp3。
 * 新音效会打断旧音效（单 alias 复用），适合游戏语音场景。
 * 所有播放请求提交到独立线程，绝不阻塞 UI 线程。
 */
public class SoundPlayer {

    private static volatile Process proc;
    private static volatile PrintWriter writer;
    private static volatile boolean initFailed = false;
    private static String musicDir;

    /** 单线程执行器：串行发送播放命令，避免并发写入管道 */
    private static final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sound-player");
        t.setDaemon(true);
        return t;
    });

    static {
        musicDir = resolveMusicDir();
        Runtime.getRuntime().addShutdownHook(new Thread(SoundPlayer::shutdown, "sound-shutdown"));
    }

    /** 探测 music 目录（兼容工作目录 = doudizhu / 工作区根） */
    private static String resolveMusicDir() {
        String[] candidates = {"../music", "music"};
        for (String d : candidates) {
            if (new File(d, "21_rocket.mp3").exists()) {
                return new File(d).getAbsolutePath();
            }
        }
        return null;
    }

    /** 启动常驻 PowerShell 播放进程（幂等，重复调用安全） */
    public static synchronized void init() {
        if (initFailed || musicDir == null) return;
        if (proc != null && proc.isAlive()) return;
        try {
            // PowerShell 脚本：循环读取 stdin 路径 → close 旧 → open 新 → play（异步不等待）
            String script =
                "[Console]::InputEncoding=[System.Text.Encoding]::UTF8\n" +
                "Add-Type -MemberDefinition '" +
                "[DllImport(\"winmm.dll\", CharSet=CharSet.Auto)]" +
                "public static extern int mciSendString(string c, System.Text.StringBuilder b, int s, IntPtr h);" +
                "' -Name M -Namespace W\n" +
                "while($true){" +
                "$p=[Console]::In.ReadLine();" +
                "if($null -eq $p -or $p -eq 'QUIT'){break};" +
                "[W.M]::mciSendString('close snd',$null,0,[IntPtr]::Zero)|Out-Null;" +
                "[W.M]::mciSendString('open \"'+$p+'\" alias snd',$null,0,[IntPtr]::Zero)|Out-Null;" +
                "[W.M]::mciSendString('play snd',$null,0,[IntPtr]::Zero)|Out-Null" +
                "}\n" +
                "[W.M]::mciSendString('close snd',$null,0,[IntPtr]::Zero)|Out-Null";
            String encoded = Base64.getEncoder()
                    .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-EncodedCommand", encoded);
            pb.redirectErrorStream(true);
            proc = pb.start();
            writer = new PrintWriter(new OutputStreamWriter(
                    proc.getOutputStream(), StandardCharsets.UTF_8), true);
            // 消费进程输出，防止管道缓冲区满导致阻塞
            Thread drain = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    while (r.readLine() != null) { /* 丢弃输出 */ }
                } catch (IOException ignored) {}
            }, "sound-drain");
            drain.setDaemon(true);
            drain.start();
        } catch (Exception e) {
            initFailed = true;
            proc = null;
            writer = null;
        }
    }

    /** 播放指定音效文件（如 "21_rocket.mp3"）；进程未就绪则先初始化 */
    public static void play(String fileName) {
        if (musicDir == null || initFailed) return;
        final String path = new File(musicDir, fileName).getAbsolutePath();
        exec.submit(() -> {
            if (proc == null || !proc.isAlive()) {
                init();
                if (writer == null) return;
            }
            writer.println(path);
            writer.flush();
        });
    }

    /** 延迟播放（毫秒），用于春天语音后再播结果语音 */
    public static void playDelayed(String fileName, long delayMs) {
        exec.schedule(() -> play(fileName), delayMs, TimeUnit.MILLISECONDS);
    }

    /** 关闭播放进程 */
    public static void shutdown() {
        exec.submit(() -> {
            try {
                if (writer != null) {
                    writer.println("QUIT");
                    writer.flush();
                }
            } catch (Exception ignored) {}
            if (proc != null) proc.destroyForcibly();
        });
    }
}