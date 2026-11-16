import com.doudizhu.engine.GameEngine;
import com.doudizhu.model.Card;
import com.doudizhu.model.CardPlay;
import com.doudizhu.model.Player;
import com.doudizhu.net.DdzCodec;

/**
 * 斗地主主机端适配器：桥接 NetPlay（统一文本协议）与 GameEngine。
 * - 实现 NetPlay.NetListener：接收客户端 OP:DDZ_ 操作 → 驱动引擎
 * - 实现 GameEngine.GameListener：引擎事件 → ST:DDZ_ 文本广播给客户端
 *
 * 座位映射：主机=0，客户端idx0=座位1，客户端idx1=座位2
 */
public class DdzHostAdapter implements NetPlay.NetListener, GameEngine.GameListener {

    private final NetPlay net;
    private final GameEngine engine;

    public DdzHostAdapter(NetPlay net, GameEngine engine) {
        this.net = net;
        this.engine = engine;
        engine.addListener(this);
        net.setListener(this);
    }

    // ==================== NetPlay.NetListener（接收客户端操作） ====================

    @Override
    public void onOpReceived(String op) {
        if (!op.startsWith("DDZ_")) return;
        String[] p = op.split(":", -1);
        String cmd = p[0];
        int seat = -1;
        if (p.length > 1) {
            try { seat = Integer.parseInt(p[1].trim()); } catch (Exception e) { }
        }
        // 校验操作来源是否为当前回合玩家
        switch (cmd) {
            case "DDZ_NEW" -> engine.startNewGame();
            case "DDZ_BID" -> {
                boolean call = p.length > 2 && Boolean.parseBoolean(p[2].trim());
                if (seat == engine.getCurrentPlayer()) engine.humanBid(call);
            }
            case "DDZ_DBL" -> {
                boolean dbl = p.length > 2 && Boolean.parseBoolean(p[2].trim());
                if (seat == engine.getCurrentPlayer()) engine.humanDouble(dbl);
            }
            case "DDZ_PLAY" -> {
                if (seat == engine.getCurrentPlayer() && p.length > 2) {
                    CardPlay play = DdzCodec.strToPlay(p[2]);
                    if (play != null) engine.submitPlay(play);
                }
            }
            case "DDZ_PASS" -> {
                if (seat == engine.getCurrentPlayer()) engine.submitPlay(null);
            }
        }
    }

    @Override public void onConnected() {}
    @Override public void onStateReceived(String state) {}
    @Override public void onChatReceived(String msg) {}
    @Override public void onDisconnected() {}
    @Override public void onSpectatorChanged(int count) {}

    // ==================== GameEngine.GameListener（广播引擎事件） ====================

    @Override
    public void onStateChanged(GameEngine.State state) {
        net.sendState("DDZ_STATE:" + state.name());
    }

    @Override
    public void onHandUpdated(int playerId) {
        if (playerId < 0) {
            // 全员更新（发牌后）
            for (Player pl : engine.getPlayers()) {
                net.sendState("DDZ_COUNT:" + pl.getId() + ":" + pl.getCardCount());
                sendHandTo(pl);
            }
            return;
        }
        Player pl = engine.getPlayer(playerId);
        net.sendState("DDZ_COUNT:" + playerId + ":" + pl.getCardCount());
        sendHandTo(pl);
    }

    /** 私发手牌给对应客户端（座位1→客户端idx0，座位2→客户端idx1）；主机自己直接读引擎 */
    private void sendHandTo(Player p) {
        if (p.getId() >= 1) {
            net.sendToClient(p.getId() - 1,
                    "ST:DDZ_HAND:" + p.getId() + ":" + DdzCodec.cardsToStr(p.getHand()));
        }
    }

    @Override
    public void onBidTurn(int playerId) {
        net.sendState("DDZ_BIDTURN:" + playerId);
    }

    @Override
    public void onBidResult(int playerId, boolean called) {
        net.sendState("DDZ_BIDRES:" + playerId + ":" + called);
    }

    @Override
    public void onLandlordDecided(int landlordId) {
        net.sendState("DDZ_LANDLORD:" + landlordId);
        net.sendState("DDZ_BOTTOM:" + DdzCodec.cardsToStr(engine.getBottomCards()));
    }

    @Override
    public void onDoubleTurn(int playerId) {
        net.sendState("DDZ_DBLTURN:" + playerId);
    }

    @Override
    public void onDoubleResult(int playerId, boolean doubled) {
        net.sendState("DDZ_DBLRES:" + playerId + ":" + doubled);
        net.sendState("DDZ_MULT:" + engine.getMultiplier() + ":" + engine.getBaseScore());
    }

    @Override
    public void onPlayTurn(int playerId) {
        net.sendState("DDZ_PLAYTURN:" + playerId);
    }

    @Override
    public void onPlay(int playerId, CardPlay play) {
        net.sendState("DDZ_PLAY:" + playerId + ":" + DdzCodec.playToStr(play));
        if (play.getType().isBombLike()) {
            net.sendState("DDZ_MULT:" + engine.getMultiplier() + ":" + engine.getBaseScore());
        }
    }

    @Override
    public void onPass(int playerId) {
        net.sendState("DDZ_PASS:" + playerId);
    }

    @Override
    public void onGameOver(int winnerId, boolean landlordWon, int multiplier,
                           int baseScore, boolean spring) {
        net.sendState("DDZ_OVER:" + winnerId + ":" + landlordWon + ":" + multiplier
                + ":" + baseScore + ":" + spring);
        // 游戏结束公开所有人手牌
        for (Player p : engine.getPlayers()) {
            net.sendState("DDZ_HAND:" + p.getId() + ":" + DdzCodec.cardsToStr(p.getHand()));
        }
    }
}