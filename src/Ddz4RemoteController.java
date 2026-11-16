import com.doudizhu.engine.GameEngine;
import com.doudizhu.engine.IGameController;
import com.doudizhu.model.Card;
import com.doudizhu.model.CardPlay;
import com.doudizhu.model.Player;
import com.doudizhu.net.DdzCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 四人斗地主远程控制器（客户端）：通过 NetPlay 文本协议与主机通信。
 * - 解析 ST:DDZ4_ 消息，维护本地状态镜像，回调 GameListener 驱动 UI
 * - 玩家操作转 OP:DDZ4_ 发送给主机
 *
 * 座位视角：服务器座位 0=房主, 1=客户端A, 2=客户端B, 3=客户端C
 * 本地视角：自己=0，右(东)=1，上(北)=2，左(西)=3（与 GamePanel 渲染一致）
 */
public class Ddz4RemoteController implements IGameController, NetPlay.NetListener {

    private final NetPlay net;
    private final int mySeat;   // 服务器分配的座位（1/2/3）

    // 本地状态镜像
    private GameEngine.State state = GameEngine.State.IDLE;
    private final List<Player> players = new ArrayList<>();
    private int currentPlayer = -1;
    private CardPlay lastPlay = null;
    private int lastPlayer = -1;
    private int multiplier = 1;

    // 四人模式专属
    private final Card[] guessCards = new Card[4];    // 本地视角亮牌
    private final int[] serverTeams = new int[4];     // 服务器视角分组
    private final int[] localTeams = new int[4];      // 本地视角分组

    private final List<GameEngine.GameListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param net    已连接的 NetPlay
     * @param player HomeUI 分配的玩家号（2→座位1，3→座位2，4→座位3）
     */
    public Ddz4RemoteController(NetPlay net, int player) {
        this.net = net;
        this.mySeat = player - 1;
        players.add(new Player(0, com.doudizhu.Main.userNickname, true));
        players.add(new Player(1, "玩家2", false));   // 右（东）
        players.add(new Player(2, "玩家3", false));   // 上（北）
        players.add(new Player(3, "玩家4", false));   // 左（西）
        for (int i = 0; i < 4; i++) { serverTeams[i] = -1; localTeams[i] = -1; }
        net.setListener(this);
    }

    // ==================== NetPlay.NetListener（解析主机状态） ====================

    @Override
    public void onStateReceived(String body) {
        if (!body.startsWith("DDZ4_")) return;
        String[] p = body.split(":", -1);
        switch (p[0]) {
            case "DDZ4_STATE" -> {
                state = GameEngine.State.valueOf(p[1]);
                if (state == GameEngine.State.DEALING) {
                    // 新局开始：重置本地镜像
                    multiplier = 1;
                    currentPlayer = -1; lastPlay = null; lastPlayer = -1;
                    for (int i = 0; i < 4; i++) {
                        guessCards[i] = null;
                        serverTeams[i] = -1; localTeams[i] = -1;
                    }
                    for (Player pl : players) {
                        pl.getHand().clear();
                        pl.setDisplayCount(-1);
                        pl.setRole(Player.Role.FARMER);
                        pl.setTeam(-1);
                    }
                }
                for (GameEngine.GameListener l : listeners) l.onStateChanged(state);
            }
            case "DDZ4_COUNT" -> {
                int sid = Integer.parseInt(p[1].trim());
                int cnt = Integer.parseInt(p[2].trim());
                players.get(toLocalId(sid)).setDisplayCount(cnt);
            }
            case "DDZ4_HAND" -> {
                int sid = Integer.parseInt(p[1].trim());
                List<Card> hand = DdzCodec.strToCards(p[2]);
                int lid = toLocalId(sid);
                if (sid == mySeat || state == GameEngine.State.GAME_OVER) {
                    Player pl = players.get(lid);
                    pl.getHand().clear();
                    pl.getHand().addAll(hand);
                    pl.sortHand();
                    pl.setDisplayCount(-1);
                    for (GameEngine.GameListener l : listeners) l.onHandUpdated(lid);
                }
            }
            case "DDZ4_GUESSTURN" -> {
                currentPlayer = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onGuessTurn(toLocalId(currentPlayer));
            }
            case "DDZ4_GUESSRES" -> {
                int sid = Integer.parseInt(p[1].trim());
                Card card = DdzCodec.strToCard(p[2].trim());
                int lid = toLocalId(sid);
                guessCards[lid] = card;
                for (GameEngine.GameListener l : listeners) l.onGuessResult(lid, card);
            }
            case "DDZ4_TEAMS" -> {
                String[] t = p[1].split(",");
                for (int s = 0; s < 4; s++) {
                    serverTeams[s] = Integer.parseInt(t[s].trim());
                }
                // 转换为本地视角
                for (int l = 0; l < 4; l++) {
                    localTeams[l] = serverTeams[toServerId(l)];
                    players.get(l).setTeam(localTeams[l]);
                }
                int[] clone = localTeams.clone();
                for (GameEngine.GameListener li : listeners) li.onTeamsDecided(clone);
            }
            case "DDZ4_PLAYTURN" -> {
                currentPlayer = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onPlayTurn(toLocalId(currentPlayer));
            }
            case "DDZ4_PLAY" -> {
                int sid = Integer.parseInt(p[1].trim());
                CardPlay play = DdzCodec.strToPlay(p[2]);
                lastPlay = play;
                lastPlayer = sid;
                int lid = toLocalId(sid);
                if (lid == 0) {
                    players.get(0).removeCards(play.getCards());
                } else {
                    Player pl = players.get(lid);
                    pl.setDisplayCount(pl.getCardCount() - play.getCards().size());
                }
                for (GameEngine.GameListener l : listeners) l.onPlay(lid, play);
            }
            case "DDZ4_PASS" -> {
                int sid = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onPass(toLocalId(sid));
            }
            case "DDZ4_OVER" -> {
                int winnerTeam = Integer.parseInt(p[1].trim());
                boolean bigTeamWon = Boolean.parseBoolean(p[2].trim());
                multiplier = Integer.parseInt(p[3].trim());
                // 计分（与 FourPlayerEngine.gameOver 一致：大牌组胜×2，小牌组胜×3）
                int unit = bigTeamWon ? 2 : 3;
                int total = unit * multiplier;
                for (int s = 0; s < 4; s++) {
                    int delta = (serverTeams[s] == winnerTeam) ? total : -total;
                    players.get(toLocalId(s)).addScore(delta);
                }
                for (GameEngine.GameListener l : listeners)
                    l.onGameOverTeam(winnerTeam, bigTeamWon, multiplier);
                // 通用 onGameOver（GamePanel 统一显示结算；winnerId 四人模式未使用）
                for (GameEngine.GameListener l : listeners)
                    l.onGameOver(-1, bigTeamWon, multiplier, unit, false);
            }
        }
    }

    @Override public void onConnected() {}
    @Override public void onOpReceived(String op) {}
    @Override public void onChatReceived(String msg) {}
    @Override public void onDisconnected() {}
    @Override public void onSpectatorChanged(int count) {}

    // ==================== 座位视角转换 ====================

    /** 服务器座位 → 本地座位（自己=0，右=1，上=2，左=3） */
    private int toLocalId(int serverId) {
        return ((serverId - mySeat) % 4 + 4) % 4;
    }

    /** 本地座位 → 服务器座位 */
    private int toServerId(int localId) {
        return (localId + mySeat) % 4;
    }

    // ==================== IGameController ====================

    @Override public GameEngine.State getState() { return state; }
    @Override public List<Player> getPlayers() { return players; }
    @Override public Player getPlayer(int id) { return players.get(id); }
    @Override public List<Card> getBottomCards() { return Collections.emptyList(); }
    @Override public int getLandlordId() { return -1; }
    @Override public int getCurrentPlayer() { return currentPlayer < 0 ? -1 : toLocalId(currentPlayer); }
    @Override public CardPlay getLastPlay() { return lastPlay; }
    @Override public int getLastPlayer() { return lastPlayer < 0 ? -1 : toLocalId(lastPlayer); }
    @Override public int getMultiplier() { return multiplier; }
    @Override public int getBaseScore() { return 1; }
    @Override public boolean[] getDoubled() { return new boolean[4]; }

    @Override
    public void addListener(GameEngine.GameListener l) { listeners.add(l); }

    @Override
    public void startNewGame() {
        net.sendOp("DDZ4_NEW:" + mySeat);
    }

    @Override public void humanBid(boolean call) { }    // 四人模式无叫地主
    @Override public void humanDouble(boolean dbl) { }  // 四人模式无加倍

    @Override
    public void submitGuess(Card card) {
        if (card == null) return;
        net.sendOp("DDZ4_GUESS:" + mySeat + ":" + DdzCodec.cardToStr(card));
    }

    @Override
    public void submitPlay(CardPlay play) {
        if (play == null) {
            net.sendOp("DDZ4_PASS:" + mySeat);
        } else {
            net.sendOp("DDZ4_PLAY:" + mySeat + ":" + DdzCodec.playToStr(play));
        }
    }

    @Override
    public CardPlay getHint(List<Card> selected) {
        return null;   // 联机模式不提供提示
    }
}