import com.doudizhu.engine.FourPlayerEngine;
import com.doudizhu.engine.GameEngine;
import com.doudizhu.model.Card;
import com.doudizhu.model.CardPlay;
import com.doudizhu.model.Player;
import com.doudizhu.net.DdzCodec;

/**
 * 四人斗地主主机端适配器：桥接 NetPlay（统一文本协议）与 FourPlayerEngine。
 * - 实现 NetPlay.NetListener：接收客户端 OP:DDZ4_ 操作 → 驱动引擎
 * - 实现 GameEngine.GameListener：引擎事件 → ST:DDZ4_ 文本广播给客户端
 *
 * 座位映射：主机=0，有效客户端按顺序=座位1/2/3
 */
public class Ddz4HostAdapter implements NetPlay.NetListener, GameEngine.GameListener {

    private final NetPlay net;
    private final FourPlayerEngine engine;

    public Ddz4HostAdapter(NetPlay net, FourPlayerEngine engine) {
        this.net = net;
        this.engine = engine;
        engine.addListener(this);
        net.setListener(this);
    }

    // ==================== NetPlay.NetListener（接收客户端操作） ====================

    @Override
    public void onOpReceived(String op) {
        if (!op.startsWith("DDZ4_")) return;
        String[] p = op.split(":", -1);
        String cmd = p[0];
        int seat = -1;
        if (p.length > 1) {
            try { seat = Integer.parseInt(p[1].trim()); } catch (Exception e) { }
        }
        switch (cmd) {
            case "DDZ4_NEW" -> engine.startNewGame();
            case "DDZ4_GUESS" -> {
                if (seat == engine.getCurrentPlayer() && p.length > 2) {
                    Card card = DdzCodec.strToCard(p[2].trim());
                    if (card != null) engine.submitGuess(card);
                }
            }
            case "DDZ4_PLAY" -> {
                if (seat == engine.getCurrentPlayer() && p.length > 2) {
                    CardPlay play = DdzCodec.strToPlay(p[2]);
                    if (play != null) engine.submitPlay(play);
                }
            }
            case "DDZ4_PASS" -> {
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
        net.sendState("DDZ4_STATE:" + state.name());
    }

    @Override
    public void onHandUpdated(int playerId) {
        if (playerId < 0) {
            // 全员更新（发牌后）
            for (Player pl : engine.getPlayers()) {
                net.sendState("DDZ4_COUNT:" + pl.getId() + ":" + pl.getCardCount());
                sendHandTo(pl);
            }
            return;
        }
        Player pl = engine.getPlayer(playerId);
        net.sendState("DDZ4_COUNT:" + playerId + ":" + pl.getCardCount());
        sendHandTo(pl);
    }

    /** 私发手牌给对应客户端（座位1/2/3→第1/2/3个有效客户端）；主机自己直接读引擎 */
    private void sendHandTo(Player p) {
        if (p.getId() >= 1) {
            int ci = net.getClientIndexForSeat(p.getId());
            if (ci >= 0) {
                net.sendToClient(ci,
                        "ST:DDZ4_HAND:" + p.getId() + ":" + DdzCodec.cardsToStr(p.getHand()));
            }
        }
    }

    @Override
    public void onGuessTurn(int playerId) {
        net.sendState("DDZ4_GUESSTURN:" + playerId);
    }

    @Override
    public void onGuessResult(int playerId, Card card) {
        net.sendState("DDZ4_GUESSRES:" + playerId + ":" + DdzCodec.cardToStr(card));
    }

    @Override
    public void onTeamsDecided(int[] teams) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < teams.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(teams[i]);
        }
        net.sendState("DDZ4_TEAMS:" + sb.toString());
    }

    @Override
    public void onPlayTurn(int playerId) {
        net.sendState("DDZ4_PLAYTURN:" + playerId);
    }

    @Override
    public void onPlay(int playerId, CardPlay play) {
        net.sendState("DDZ4_PLAY:" + playerId + ":" + DdzCodec.playToStr(play));
    }

    @Override
    public void onPass(int playerId) {
        net.sendState("DDZ4_PASS:" + playerId);
    }

    @Override
    public void onGameOverTeam(int winnerTeam, boolean bigTeamWon, int multiplier) {
        net.sendState("DDZ4_OVER:" + winnerTeam + ":" + bigTeamWon + ":" + multiplier);
        // 游戏结束公开所有人手牌
        for (Player p : engine.getPlayers()) {
            net.sendState("DDZ4_HAND:" + p.getId() + ":" + DdzCodec.cardsToStr(p.getHand()));
        }
    }

    // 三人模式事件（四人模式不触发，空实现）
    @Override public void onBidTurn(int playerId) {}
    @Override public void onBidResult(int playerId, boolean called) {}
    @Override public void onLandlordDecided(int landlordId) {}
    @Override public void onDoubleTurn(int playerId) {}
    @Override public void onDoubleResult(int playerId, boolean doubled) {}

    @Override
    public void onGameOver(int winnerId, boolean landlordWon, int multiplier,
                           int baseScore, boolean spring) {
        // 四人模式通过 onGameOverTeam 广播，此回调不处理
    }
}