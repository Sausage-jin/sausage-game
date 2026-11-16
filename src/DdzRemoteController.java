import com.doudizhu.engine.GameEngine;
import com.doudizhu.engine.IGameController;
import com.doudizhu.model.Card;
import com.doudizhu.model.CardPlay;
import com.doudizhu.model.Player;
import com.doudizhu.net.DdzCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 斗地主远程控制器（客户端）：通过 NetPlay 文本协议与主机通信。
 * - 解析 ST:DDZ_ 消息，维护本地状态镜像，回调 GameListener 驱动 UI
 * - 玩家操作转 OP:DDZ_ 发送给主机
 *
 * 座位视角：服务器座位 0=房主, 1=客户端A, 2=客户端B
 * 本地视角：自己=0，下家=1，上家=2（与 GamePanel 渲染一致）
 */
public class DdzRemoteController implements IGameController, NetPlay.NetListener {

    private final NetPlay net;
    private final int mySeat;   // 服务器分配的座位（1或2）

    // 本地状态镜像
    private GameEngine.State state = GameEngine.State.IDLE;
    private final List<Player> players = new ArrayList<>();
    private final List<Card> bottomCards = new ArrayList<>();
    private int landlordId = -1;
    private int currentPlayer = -1;
    private CardPlay lastPlay = null;
    private int lastPlayer = -1;
    private int multiplier = 1;
    private int baseScore = 1;
    private final boolean[] doubled = new boolean[3];

    private final List<GameEngine.GameListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param net    已连接的 NetPlay
     * @param player HomeUI 分配的玩家号（2=客户端A→座位1，3=客户端B→座位2）
     */
    public DdzRemoteController(NetPlay net, int player) {
        this.net = net;
        this.mySeat = player - 1;
        players.add(new Player(0, com.doudizhu.Main.userNickname, true));
        players.add(new Player(1, "下家", false));
        players.add(new Player(2, "上家", false));
        net.setListener(this);
    }

    // ==================== NetPlay.NetListener（解析主机状态） ====================

    @Override
    public void onStateReceived(String body) {
        if (!body.startsWith("DDZ_")) return;
        String[] p = body.split(":", -1);
        switch (p[0]) {
            case "DDZ_STATE" -> {
                state = GameEngine.State.valueOf(p[1]);
                if (state == GameEngine.State.DEALING) {
                    // 新局开始：重置本地镜像
                    multiplier = 1; baseScore = 1;
                    landlordId = -1; lastPlay = null; lastPlayer = -1;
                    for (int i = 0; i < 3; i++) doubled[i] = false;
                    for (Player pl : players) {
                        pl.getHand().clear();
                        pl.setDisplayCount(-1);
                        pl.setRole(Player.Role.FARMER);
                    }
                    bottomCards.clear();
                }
                for (GameEngine.GameListener l : listeners) l.onStateChanged(state);
            }
            case "DDZ_COUNT" -> {
                int sid = Integer.parseInt(p[1].trim());
                int cnt = Integer.parseInt(p[2].trim());
                players.get(toLocalId(sid)).setDisplayCount(cnt);
            }
            case "DDZ_HAND" -> {
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
            case "DDZ_BIDTURN" -> {
                currentPlayer = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onBidTurn(toLocalId(currentPlayer));
            }
            case "DDZ_BIDRES" -> {
                int sid = Integer.parseInt(p[1].trim());
                boolean called = Boolean.parseBoolean(p[2].trim());
                for (GameEngine.GameListener l : listeners) l.onBidResult(toLocalId(sid), called);
            }
            case "DDZ_LANDLORD" -> {
                landlordId = Integer.parseInt(p[1].trim());
                for (int i = 0; i < 3; i++) {
                    players.get(i).setRole(toServerId(i) == landlordId
                            ? Player.Role.LANDLORD : Player.Role.FARMER);
                }
                for (GameEngine.GameListener l : listeners) l.onLandlordDecided(toLocalId(landlordId));
            }
            case "DDZ_BOTTOM" -> {
                bottomCards.clear();
                bottomCards.addAll(DdzCodec.strToCards(p[1]));
            }
            case "DDZ_DBLTURN" -> {
                currentPlayer = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onDoubleTurn(toLocalId(currentPlayer));
            }
            case "DDZ_DBLRES" -> {
                int sid = Integer.parseInt(p[1].trim());
                boolean dbl = Boolean.parseBoolean(p[2].trim());
                doubled[toLocalId(sid)] = dbl;
                for (GameEngine.GameListener l : listeners) l.onDoubleResult(toLocalId(sid), dbl);
            }
            case "DDZ_MULT" -> {
                multiplier = Integer.parseInt(p[1].trim());
                baseScore = Integer.parseInt(p[2].trim());
            }
            case "DDZ_PLAYTURN" -> {
                currentPlayer = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onPlayTurn(toLocalId(currentPlayer));
            }
            case "DDZ_PLAY" -> {
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
            case "DDZ_PASS" -> {
                int sid = Integer.parseInt(p[1].trim());
                for (GameEngine.GameListener l : listeners) l.onPass(toLocalId(sid));
            }
            case "DDZ_OVER" -> {
                int winnerId = Integer.parseInt(p[1].trim());
                boolean landlordWon = Boolean.parseBoolean(p[2].trim());
                multiplier = Integer.parseInt(p[3].trim());
                baseScore = Integer.parseInt(p[4].trim());
                boolean spring = Boolean.parseBoolean(p[5].trim());
                int total = baseScore * multiplier;
                for (int i = 0; i < 3; i++) {
                    boolean isLL = players.get(i).getRole() == Player.Role.LANDLORD;
                    boolean winSide = (isLL == landlordWon);
                    players.get(i).addScore(winSide ? (isLL ? total * 2 : total)
                            : (isLL ? -total * 2 : -total));
                }
                for (GameEngine.GameListener l : listeners)
                    l.onGameOver(toLocalId(winnerId), landlordWon, multiplier, baseScore, spring);
            }
        }
    }

    @Override public void onConnected() {}
    @Override public void onOpReceived(String op) {}
    @Override public void onChatReceived(String msg) {}
    @Override public void onDisconnected() {}
    @Override public void onSpectatorChanged(int count) {}

    // ==================== 座位视角转换 ====================

    /** 服务器座位 → 本地座位（自己=0，下家=1，上家=2） */
    private int toLocalId(int serverId) {
        return (serverId - mySeat + 3) % 3;
    }

    /** 本地座位 → 服务器座位 */
    private int toServerId(int localId) {
        return (localId + mySeat) % 3;
    }

    // ==================== IGameController ====================

    @Override public GameEngine.State getState() { return state; }
    @Override public List<Player> getPlayers() { return players; }
    @Override public Player getPlayer(int id) { return players.get(id); }
    @Override public List<Card> getBottomCards() { return bottomCards; }
    @Override public int getLandlordId() { return landlordId < 0 ? -1 : toLocalId(landlordId); }
    @Override public int getCurrentPlayer() { return currentPlayer < 0 ? -1 : toLocalId(currentPlayer); }
    @Override public CardPlay getLastPlay() { return lastPlay; }
    @Override public int getLastPlayer() { return lastPlayer < 0 ? -1 : toLocalId(lastPlayer); }
    @Override public int getMultiplier() { return multiplier; }
    @Override public int getBaseScore() { return baseScore; }
    @Override public boolean[] getDoubled() { return doubled; }

    @Override
    public void addListener(GameEngine.GameListener l) { listeners.add(l); }

    @Override
    public void startNewGame() {
        net.sendOp("DDZ_NEW:" + mySeat);
    }

    @Override
    public void humanBid(boolean call) {
        net.sendOp("DDZ_BID:" + mySeat + ":" + call);
    }

    @Override
    public void humanDouble(boolean dbl) {
        net.sendOp("DDZ_DBL:" + mySeat + ":" + dbl);
    }

    @Override
    public void submitPlay(CardPlay play) {
        if (play == null) {
            net.sendOp("DDZ_PASS:" + mySeat);
        } else {
            net.sendOp("DDZ_PLAY:" + mySeat + ":" + DdzCodec.playToStr(play));
        }
    }

    @Override
    public CardPlay getHint(List<Card> selected) {
        return null;   // 联机模式不提供提示
    }
}