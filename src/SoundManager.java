import javax.sound.sampled.*;
import java.io.*;

/**
 * 音频管理：多背景音乐循环播放、音效单次播放、音量控制、设置持久化。
 * 背景音乐：0=主页 1=烂头背枪 2=五子棋 3=俄罗斯方块 4=你画我猜 5=斗地主
 * 纯 javax.sound.sampled 实现，播放 music/ 下的 wav 文件。
 * 所有方法均可安全调用（音频不可用时静默失败，不影响游戏）。
 */
public class SoundManager {

    // ==================== 设置项 ====================
    private static boolean musicOn = true;
    private static int musicVolume = 60;      // 0-100
    private static boolean sfxOn = true;
    private static int sfxVolume = 80;        // 0-100
    private static String nickname = "玩家";
    private static int gender = 0;   // 0=未设置, 1=男, 2=女, 3=跨性别, 4=非人
    private static int avatar = 0;   // 头像索引 0-5（对应image目录touxiang系列）
    private static int gameSpeed = 100;    // 游戏速度百分比 50-200，100=正常
    private static boolean fullscreen = false;  // 全屏模式
    private static final String SETTINGS_FILE = "settings.properties";

    // ==================== 音频资源 ====================
    private static Sound[] bgms = new Sound[6];  // 0=主页 1=烂头背枪 2=五子棋 3=俄罗斯方块 4=你画我猜 5=斗地主
    private static int currentBgm = 0;
    /** 全部音效：key=音效名（对应 music/<name>.wav），统一加载/调音量/播放 */
    private static final java.util.Map<String, Sound> sfx = new java.util.LinkedHashMap<>();
    private static boolean loaded = false;
    private static boolean initStarted = false;

    /** 所有音效文件名（不含扩展名），初始化时统一加载 */
    private static final String[] SFX_NAMES = {
        // 通用 UI
        "ui-click", "ui-confirm", "ui-cancel", "ui-select",
        // 通用游戏流程
        "game-start", "game-win", "game-lose", "game-draw", "turn-start",
        // 小鸟飞翔
        "bird-flap", "bird-hit", "coin-pickup", "score-point",
        // 烂头背枪
        "card-flip", "card-move", "card-eat", "card-merge", "card-select",
        "card-deal", "battle-attack",
        // 五子棋
        "chess-place",
        // 俄罗斯方块
        "tetris-move", "tetris-rotate", "tetris-drop", "tetris-lock", "tetris-clear",
        // 你画我猜
        "draw-color", "draw-erase", "draw-clear", "guess-correct", "guess-wrong", "tick-tock",
        // 聊天 / 联网
        "chat-send", "chat-receive", "net-connect", "net-disconnect", "notify"
    };

    /** 单个音频：Clip + 音量控制 */
    private static class Sound {
        Clip clip;
        FloatControl gain;

        Sound(String path) {
            clip = loadClip(path);
            if (clip != null) {
                try { gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN); }
                catch (Exception e) { gain = null; }
            }
        }

        void play() {
            if (clip == null) return;
            try {
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            } catch (Exception e) { }
        }

        void loop() {
            if (clip == null) return;
            try {
                clip.setFramePosition(0);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            } catch (Exception e) { }
        }

        void stop() {
            if (clip == null) return;
            try { clip.stop(); } catch (Exception e) { }
        }

        void setDb(float db) {
            if (gain == null) return;
            try {
                db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
                gain.setValue(db);
            } catch (Exception e) { }
        }
    }

    private static Clip loadClip(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            AudioInputStream ais = AudioSystem.getAudioInputStream(f);
            Clip c = AudioSystem.getClip();
            c.open(ais);
            ais.close();
            return c;
        } catch (Exception e) { return null; }
    }

    /** 0-100 线性音量转分贝 */
    private static float volToDb(int vol) {
        if (vol <= 0) return -80f;
        return (float) (20.0 * Math.log10(vol / 100.0));
    }

    // ==================== 初始化 ====================

    /** 在程序启动时调用一次（重复调用安全），后台线程加载音频并按设置播放背景音乐 */
    public static void init() {
        if (initStarted) return;
        initStarted = true;
        loadSettings();
        Thread t = new Thread(() -> {
            bgms[0] = new Sound("music/background-music.wav");
            bgms[1] = new Sound("music/lantoubeiqiang-background.wav");
            bgms[2] = new Sound("music/wuziqibackgroung.wav");
            bgms[3] = new Sound("music/eluosibackground.wav");
            bgms[4] = new Sound("music/nihuawocaibackground.wav");
            bgms[5] = new Sound("music/doudizhu.wav");
            for (String name : SFX_NAMES) {
                sfx.put(name, new Sound("music/" + name + ".wav"));
            }
            loaded = true;
            applyAllVolumes();
            if (musicOn && bgms[currentBgm] != null) bgms[currentBgm].loop();
        }, "audio-load");
        t.setDaemon(true);
        t.start();
    }

    private static void applyAllVolumes() {
        if (!loaded) return;
        float mDb = musicOn ? volToDb(musicVolume) : -80f;
        float sDb = sfxOn ? volToDb(sfxVolume) : -80f;
        for (Sound s : bgms) if (s != null) s.setDb(mDb);
        for (Sound s : sfx.values()) if (s != null) s.setDb(sDb);
    }

    // ==================== 背景音乐 ====================

    /** 切换背景音乐：0=主页 1=烂头背枪 2=五子棋 3=俄罗斯方块 4=你画我猜 5=斗地主 */
    public static void switchBgm(int which) {
        if (which < 0 || which >= bgms.length) return;
        if (which == currentBgm) return;
        if (loaded) {
            if (bgms[currentBgm] != null) bgms[currentBgm].stop();
        }
        currentBgm = which;
        if (loaded && musicOn && bgms[currentBgm] != null) bgms[currentBgm].loop();
    }

    public static void startBgm() {
        if (loaded && musicOn && bgms[currentBgm] != null) bgms[currentBgm].loop();
    }

    public static void stopBgm() {
        if (loaded && bgms[currentBgm] != null) bgms[currentBgm].stop();
    }

    // ==================== 音效 ====================

    /** 通用播放：按音效名播放（安全调用，未加载/静音时静默） */
    private static void play(String name) {
        if (!loaded || !sfxOn) return;
        Sound s = sfx.get(name);
        if (s != null) s.play();
    }

    // ---- 兼容旧接口（映射到更贴切的新音效） ----
    public static void sfxFlip()  { play("card-flip"); }
    public static void sfxEat()   { play("card-eat"); }
    public static void sfxMerge() { play("card-merge"); }
    public static void sfxWin()   { play("game-win"); }
    public static void sfxLose()  { play("game-lose"); }

    // ---- 通用 UI ----
    /** 按钮点击 */
    public static void sfxClick()      { play("ui-click"); }
    /** 确认/确定 */
    public static void sfxConfirm()    { play("ui-confirm"); }
    /** 取消/返回 */
    public static void sfxCancel()     { play("ui-cancel"); }
    /** 选择/切换选项 */
    public static void sfxSelect()     { play("ui-select"); }

    // ---- 通用游戏流程 ----
    /** 游戏/对局开始 */
    public static void sfxGameStart()  { play("game-start"); }
    /** 胜利 */
    public static void sfxGameWin()    { play("game-win"); }
    /** 失败 */
    public static void sfxGameLose()   { play("game-lose"); }
    /** 平局 */
    public static void sfxDraw()       { play("game-draw"); }
    /** 回合/阶段开始 */
    public static void sfxTurnStart()  { play("turn-start"); }

    // ---- 小鸟飞翔 ----
    /** 拍翅/移动 */
    public static void sfxBirdFlap()   { play("bird-flap"); }
    /** 撞击 */
    public static void sfxBirdHit()    { play("bird-hit"); }
    /** 吃金币/奖励 */
    public static void sfxCoin()       { play("coin-pickup"); }
    /** 躲过障碍得分 */
    public static void sfxScore()      { play("score-point"); }

    // ---- 烂头背枪 ----
    /** 翻牌 */
    public static void sfxCardFlip()   { play("card-flip"); }
    /** 移动牌 */
    public static void sfxCardMove()   { play("card-move"); }
    /** 吃子/击杀 */
    public static void sfxCardEat()    { play("card-eat"); }
    /** 合成 */
    public static void sfxCardMerge()  { play("card-merge"); }
    /** 选中牌 */
    public static void sfxCardSelect() { play("card-select"); }
    /** 发牌/开局 */
    public static void sfxCardDeal()   { play("card-deal"); }
    /** 对战攻击 */
    public static void sfxBattle()     { play("battle-attack"); }

    // ---- 五子棋 ----
    /** 落子 */
    public static void sfxChessPlace() { play("chess-place"); }

    // ---- 俄罗斯方块 ----
    /** 左右移动 */
    public static void sfxTetrisMove()   { play("tetris-move"); }
    /** 旋转 */
    public static void sfxTetrisRotate() { play("tetris-rotate"); }
    /** 加速下落/软降 */
    public static void sfxTetrisDrop()   { play("tetris-drop"); }
    /** 方块锁定 */
    public static void sfxTetrisLock()   { play("tetris-lock"); }
    /** 消行 */
    public static void sfxTetrisClear()  { play("tetris-clear"); }

    // ---- 你画我猜 ----
    /** 选颜色 */
    public static void sfxDrawColor()    { play("draw-color"); }
    /** 橡皮 */
    public static void sfxDrawErase()    { play("draw-erase"); }
    /** 清空画板 */
    public static void sfxDrawClear()    { play("draw-clear"); }
    /** 猜对 */
    public static void sfxGuessCorrect() { play("guess-correct"); }
    /** 猜错/时间到 */
    public static void sfxGuessWrong()   { play("guess-wrong"); }
    /** 倒计时滴答 */
    public static void sfxTickTock()     { play("tick-tock"); }

    // ---- 聊天 / 联网 ----
    /** 发送消息 */
    public static void sfxChatSend()     { play("chat-send"); }
    /** 收到消息 */
    public static void sfxChatReceive()  { play("chat-receive"); }
    /** 连接成功 */
    public static void sfxNetConnect()   { play("net-connect"); }
    /** 断开连接 */
    public static void sfxNetDisconnect(){ play("net-disconnect"); }
    /** 通知/出题提示 */
    public static void sfxNotify()       { play("notify"); }

    // ==================== 设置读写 ====================

    public static boolean isMusicOn()      { return musicOn; }
    public static int     getMusicVolume() { return musicVolume; }
    public static boolean isSfxOn()        { return sfxOn; }
    public static int     getSfxVolume()   { return sfxVolume; }

    public static String  getNickname()    { return nickname; }
    public static void    setNickname(String n) {
        String t = n == null ? "" : n.replace("\r", "").replace("\n", "").replace("|", "").trim();
        if (t.isEmpty()) t = "玩家";
        if (t.length() > 12) t = t.substring(0, 12);
        nickname = t;
        saveSettings();
    }

    public static int     getGender()      { return gender; }
    public static void    setGender(int g) {
        gender = Math.max(0, Math.min(4, g));
        saveSettings();
    }

    public static int     getAvatar()      { return avatar; }
    public static void    setAvatar(int a) {
        avatar = Math.max(0, Math.min(5, a));
        saveSettings();
    }

    /** 游戏速度倍率（50-200，100=正常1.0倍） */
    public static int  getGameSpeed()          { return gameSpeed; }
    public static void setGameSpeed(int s)     { gameSpeed = Math.max(50, Math.min(200, s)); saveSettings(); }
    public static double getSpeedMul()         { return gameSpeed / 100.0; }

    public static boolean isFullscreen()           { return fullscreen; }
    public static void    setFullscreen(boolean f) { fullscreen = f; saveSettings(); }

    /** 头像文件名列表 */
    public static final String[] AVATAR_FILES = {
        "image/touxaing.png", "image/touxaing1.png", "image/touxaing2.png",
        "image/touxaing3.png", "image/touxiang4.png", "image/touxiang5.png"
    };

    public static void setMusicOn(boolean on) {
        musicOn = on;
        saveSettings();
        if (on) startBgm(); else stopBgm();
    }

    public static void setMusicVolume(int v) {
        musicVolume = clamp(v);
        saveSettings();
        if (loaded) {
            float mDb = musicOn ? volToDb(musicVolume) : -80f;
            for (Sound s : bgms) if (s != null) s.setDb(mDb);
        }
    }

    public static void setSfxOn(boolean on) {
        sfxOn = on;
        saveSettings();
        applyAllVolumes();
    }

    public static void setSfxVolume(int v) {
        sfxVolume = clamp(v);
        saveSettings();
        applyAllVolumes();
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    // ==================== 持久化 ====================

    private static void loadSettings() {
        try (BufferedReader r = new BufferedReader(new FileReader(SETTINGS_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("=", 2);
                if (p.length < 2) continue;
                String k = p[0].trim(), v = p[1].trim();
                if (k.equals("musicOn"))          musicOn     = Boolean.parseBoolean(v);
                else if (k.equals("musicVolume")) musicVolume = clamp(parseInt(v, 60));
                else if (k.equals("sfxOn"))       sfxOn       = Boolean.parseBoolean(v);
                else if (k.equals("sfxVolume"))   sfxVolume   = clamp(parseInt(v, 80));
                else if (k.equals("nickname"))    nickname    = v.isEmpty() ? "玩家" : v;
                else if (k.equals("gender"))      gender      = Math.max(0, Math.min(4, parseInt(v, 0)));
                else if (k.equals("avatar"))      avatar      = Math.max(0, Math.min(5, parseInt(v, 0)));
            }
        } catch (Exception e) { /* 首次运行用默认值 */ }
    }

    private static void saveSettings() {
        try (PrintWriter w = new PrintWriter(new FileWriter(SETTINGS_FILE))) {
            w.println("musicOn=" + musicOn);
            w.println("musicVolume=" + musicVolume);
            w.println("sfxOn=" + sfxOn);
            w.println("sfxVolume=" + sfxVolume);
            w.println("nickname=" + nickname);
            w.println("gender=" + gender);
            w.println("avatar=" + avatar);
        } catch (Exception e) { }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}