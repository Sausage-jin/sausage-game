package com.doudizhu.engine;

import com.doudizhu.model.*;

import javax.swing.SwingUtilities;
import java.util.*;
import java.util.List;

/**
 * 游戏主引擎：管理发牌、叫地主、出牌回合、胜负判定与计分。
 * 通过 GameListener 回调驱动 UI 更新；AI 行动使用 Timer 延迟以模拟思考。
 */
public class GameEngine implements IGameController {

    public enum State { IDLE, DEALING, BIDDING, DOUBLING, GUESSING, PLAYING, GAME_OVER }

    public interface GameListener {
        void onStateChanged(State state);
        void onHandUpdated(int playerId);
        void onBidTurn(int playerId);
        void onBidResult(int playerId, boolean called);
        void onLandlordDecided(int landlordId);
        void onDoubleTurn(int playerId);
        void onDoubleResult(int playerId, boolean doubled);
        void onPlayTurn(int playerId);
        void onPlay(int playerId, CardPlay play);
        void onPass(int playerId);
        void onGameOver(int winnerId, boolean landlordWon, int multiplier,
                        int baseScore, boolean spring);
        // ===== 四人模式扩展（默认空实现，三人模式无需覆盖） =====
        /** 轮到某玩家猜牌（亮牌分组） */
        default void onGuessTurn(int playerId) {}
        /** 某玩家亮出的猜牌 */
        default void onGuessResult(int playerId, Card card) {}
        /** 分组结果：teams[playerId] = 队伍号（0=大牌组, 1=小牌组） */
        default void onTeamsDecided(int[] teams) {}
        /** 四人模式游戏结束（winnerTeam=获胜队伍, bigTeamWon=大牌组是否获胜） */
        default void onGameOverTeam(int winnerTeam, boolean bigTeamWon, int multiplier) {}
    }

    private final List<Player> players = new ArrayList<>();
    private final List<Card> bottomCards = new ArrayList<>();
    private final List<GameListener> listeners = new ArrayList<>();

    private State state = State.IDLE;
    private int landlordId = -1;
    private int currentPlayer = 0;
    private CardPlay lastPlay = null;
    private int lastPlayer = -1;
    private int passCount = 0;
    private int multiplier = 1;       // 倍数（加倍/炸弹/春天累计）
    private int baseScore = 1;        // 底分
    private int bidStartPlayer = 0;
    private int bidIndex = 0;         // 已叫人数
    private boolean someoneCalled = false;
    // 加倍阶段
    private int doubleIndex = 0;      // 已加倍决策人数
    private final boolean[] doubled = new boolean[3];   // 各玩家是否加倍
    // 春天检测：各玩家出牌次数
    private final int[] playCounts = new int[3];
    private Timer aiTimer = new Timer("ai-timer", true);
    private boolean onlineMode = false;   // 联机模式：所有玩家等待远程操作，不触发AI
    /** 联机模式下的人机座位集合：这些座位即使 onlineMode 也由本地 AI 代打 */
    private final java.util.Set<Integer> botSeats = new java.util.HashSet<>();

    public GameEngine() {
        players.add(new Player(0, com.doudizhu.Main.userNickname, true));
        players.add(new Player(1, "电脑1", false));   // 下家（右侧）
        players.add(new Player(2, "电脑2", false));   // 上家（左侧）
    }

    public void addListener(GameListener l) { listeners.add(l); }
    public State getState() { return state; }
    /** 联机模式：座位1/2为远程真人，引擎等待其操作而非AI代打 */
    public void setOnlineMode(boolean b) { this.onlineMode = b; }

    /** 联机模式下设人机座位（这些座位由本地 AI 代打）；传空集合清除所有人机 */
    public void setBotSeats(java.util.Collection<Integer> seats) {
        botSeats.clear();
        if (seats != null) botSeats.addAll(seats);
    }

    /** 判断某座位在联机模式下是否为人机代打 */
    private boolean isOnlineBot(int playerId) {
        return onlineMode && botSeats.contains(playerId);
    }
    public List<Player> getPlayers() { return players; }
    public Player getPlayer(int id) { return players.get(id); }
    public List<Card> getBottomCards() { return bottomCards; }
    public int getLandlordId() { return landlordId; }
    public int getCurrentPlayer() { return currentPlayer; }
    public CardPlay getLastPlay() { return lastPlay; }
    public int getLastPlayer() { return lastPlayer; }
    public int getMultiplier() { return multiplier; }
    public int getBaseScore() { return baseScore; }
    public boolean[] getDoubled() { return doubled; }

    private void fireStateChanged() {
        SwingUtilities.invokeLater(() -> {
            for (GameListener l : listeners) l.onStateChanged(state);
        });
    }

    // ==================== 开局 ====================

    public void startNewGame() {
        aiTimer.purge();
        // 重置
        for (Player p : players) { p.getHand().clear(); p.setRole(Player.Role.FARMER); }
        bottomCards.clear();
        lastPlay = null; lastPlayer = -1; passCount = 0;
        multiplier = 1; baseScore = 1; landlordId = -1;
        someoneCalled = false; bidIndex = 0;
        doubleIndex = 0;
        for (int i = 0; i < 3; i++) { doubled[i] = false; playCounts[i] = 0; }

        state = State.DEALING;
        fireStateChanged();

        // 洗牌发牌
        Deck deck = new Deck();
        deck.shuffle();
        for (int i = 0; i < 51; i++) players.get(i % 3).addCard(deck.deal());
        for (int i = 0; i < 3; i++) bottomCards.add(deck.deal());
        for (Player p : players) p.sortHand();

        // 随机起始叫地主
        bidStartPlayer = new Random().nextInt(3);
        currentPlayer = bidStartPlayer;

        state = State.BIDDING;
        SwingUtilities.invokeLater(() -> {
            for (GameListener l : listeners) l.onHandUpdated(-1);
            for (GameListener l : listeners) l.onBidTurn(currentPlayer);
        });
        nextBid();
    }

    // ==================== 叫地主 ====================

    private void nextBid() {
        if (bidIndex >= 3) {
            if (!someoneCalled) { startNewGame(); return; } // 无人叫，重新发牌
            return; // 已有人叫，等流程完成
        }
        Player p = players.get(currentPlayer);
        if (!isOnlineBot(p.getId()) && (p.isHuman() || onlineMode)) {
            // 等待玩家点击（联机人机座位由本地AI代打）
        } else {
            aiTimer.schedule(new TimerTask() {
                @Override public void run() {
                    boolean call = AIEngine.shouldCallLandlord(p.getHand());
                    SwingUtilities.invokeLater(() -> humanBid(call));
                }
            }, 900);
        }
    }

    /** 玩家/AI 叫地主结果（true=叫，false=不叫） */
    public void humanBid(boolean call) {
        if (state != State.BIDDING) return;
        Player p = players.get(currentPlayer);
        for (GameListener l : listeners) l.onBidResult(p.getId(), call);
        bidIndex++;
        if (call) {
            someoneCalled = true;
            setLandlord(p.getId());
            return;
        }
        // 下一个叫
        currentPlayer = (currentPlayer + 1) % 3;
        for (GameListener l : listeners) l.onBidTurn(currentPlayer);
        nextBid();
    }

    private void setLandlord(int id) {
        landlordId = id;
        Player landlord = players.get(id);
        landlord.setRole(Player.Role.LANDLORD);
        // 地主拿底牌
        for (Card c : bottomCards) landlord.addCard(c);
        landlord.sortHand();
        for (GameListener l : listeners) l.onLandlordDecided(id);
        for (GameListener l : listeners) l.onHandUpdated(id);

        // 进入加倍阶段：从地主开始依次询问
        currentPlayer = id;
        doubleIndex = 0;
        state = State.DOUBLING;
        fireStateChanged();
        for (GameListener l : listeners) l.onDoubleTurn(currentPlayer);
        nextDouble();
    }

    // ==================== 加倍 ====================

    private void nextDouble() {
        if (state != State.DOUBLING) return;
        if (doubleIndex >= 3) {
            startPlaying();
            return;
        }
        Player p = players.get(currentPlayer);
        if (!isOnlineBot(p.getId()) && (p.isHuman() || onlineMode)) {
            // 等待玩家点击加倍/不加倍（联机人机座位由本地AI代打）
        } else {
            aiTimer.schedule(new TimerTask() {
                @Override public void run() {
                    boolean dbl = AIEngine.shouldDouble(p.getHand(),
                            p.getId() == landlordId);
                    SwingUtilities.invokeLater(() -> humanDouble(dbl));
                }
            }, 800 + new Random().nextInt(500));
        }
    }

    /** 玩家/AI 加倍决策（true=加倍，false=不加倍） */
    public void humanDouble(boolean dbl) {
        if (state != State.DOUBLING) return;
        Player p = players.get(currentPlayer);
        if (dbl) {
            doubled[p.getId()] = true;
            multiplier *= 2;
        }
        for (GameListener l : listeners) l.onDoubleResult(p.getId(), dbl);
        doubleIndex++;
        if (doubleIndex >= 3) {
            startPlaying();
            return;
        }
        currentPlayer = (currentPlayer + 1) % 3;
        for (GameListener l : listeners) l.onDoubleTurn(currentPlayer);
        nextDouble();
    }

    /** 加倍结束，进入出牌阶段 */
    private void startPlaying() {
        lastPlay = null; lastPlayer = -1; passCount = 0;
        currentPlayer = landlordId;
        state = State.PLAYING;
        fireStateChanged();
        for (GameListener l : listeners) l.onPlayTurn(currentPlayer);
        nextPlay();
    }

    // ==================== 出牌 ====================

    private void nextPlay() {
        if (state != State.PLAYING) return;
        Player p = players.get(currentPlayer);
        if (!isOnlineBot(p.getId()) && (p.isHuman() || onlineMode)) {
            // 等待玩家操作（联机人机座位由本地AI代打）
        } else {
            aiTimer.schedule(new TimerTask() {
                @Override public void run() {
                    CardPlay play = AIEngine.decide(p.getHand(), lastPlay, lastPlayer,
                            p.getId(), landlordId, players);
                    SwingUtilities.invokeLater(() -> submitPlay(play));
                }
            }, 1000 + new Random().nextInt(600));
        }
    }

    /** 提交出牌决策（null=过牌），自动校验合法性 */
    public void submitPlay(CardPlay play) {
        if (state != State.PLAYING) return;
        Player p = players.get(currentPlayer);

        if (play == null) {
            // 过牌
            passCount++;
            for (GameListener l : listeners) l.onPass(p.getId());
            advanceTurn();
            return;
        }

        // 合法性校验
        if (play.getType() == CardType.INVALID) return;
        if (!containsAll(p.getHand(), play.getCards())) return;
        if (lastPlay != null && lastPlayer != p.getId() && !play.canBeat(lastPlay)) return;

        // 炸弹/王炸翻倍
        if (play.getType() == CardType.BOMB) multiplier *= 2;
        if (play.getType() == CardType.ROCKET) multiplier *= 2;

        p.removeCards(play.getCards());
        lastPlay = play;
        lastPlayer = p.getId();
        passCount = 0;
        playCounts[p.getId()]++;

        for (GameListener l : listeners) l.onPlay(p.getId(), play);
        for (GameListener l : listeners) l.onHandUpdated(p.getId());

        // 胜利判定
        if (p.getCardCount() == 0) {
            gameOver(p.getId());
            return;
        }
        advanceTurn();
    }

    private void advanceTurn() {
        // 两人都过 → 重新首出
        if (passCount >= 2) {
            currentPlayer = lastPlayer;
            lastPlay = null;
            passCount = 0;
        } else {
            currentPlayer = (currentPlayer + 1) % 3;
        }
        for (GameListener l : listeners) l.onPlayTurn(currentPlayer);
        nextPlay();
    }

    private void gameOver(int winnerId) {
        state = State.GAME_OVER;
        boolean landlordWon = (winnerId == landlordId);

        // 春天检测
        boolean spring = false;
        if (landlordWon) {
            // 地主赢：两个农民都没出过牌 → 春天
            boolean farmersNeverPlayed = true;
            for (Player p : players) {
                if (p.getId() != landlordId && playCounts[p.getId()] > 0) {
                    farmersNeverPlayed = false; break;
                }
            }
            spring = farmersNeverPlayed;
        } else {
            // 农民赢：地主只出过一次牌（首手） → 反春天
            spring = (playCounts[landlordId] <= 1);
        }
        if (spring) multiplier *= 2;

        // 计分：底分 × 倍数
        int total = baseScore * multiplier;
        if (landlordWon) {
            players.get(landlordId).addScore(total * 2);
            for (Player p : players) if (p.getId() != landlordId) p.addScore(-total);
        } else {
            players.get(landlordId).addScore(-total * 2);
            for (Player p : players) if (p.getId() != landlordId) p.addScore(total);
        }
        for (GameListener l : listeners) l.onGameOver(winnerId, landlordWon,
                multiplier, baseScore, spring);
        fireStateChanged();
    }

    // ==================== 提示功能 ====================

    /** 为人类玩家生成提示出牌（轮转提示不同的解法） */
    public CardPlay getHint(List<Card> selected) {
        Player me = players.get(0);
        if (state != State.PLAYING || currentPlayer != 0) return null;
        boolean leading = (lastPlay == null || lastPlayer == 0);
        if (leading) {
            // 首出提示：推荐 AI 的首出策略
            return AIEngine.decide(me.getHand(), null, -1, 0, landlordId, players);
        }
        List<CardPlay> beats = RuleEngine.findBeats(me.getHand(), lastPlay);
        if (beats.isEmpty()) return null;
        // 优先非炸弹的最小解
        beats.sort(Comparator.comparingInt(CardPlay::getKeyRank));
        for (CardPlay b : beats) if (!b.getType().isBombLike()) return b;
        return beats.get(0);
    }

    // ==================== 工具 ====================

    private boolean containsAll(List<Card> hand, List<Card> cards) {
        List<Card> copy = new ArrayList<>(hand);
        for (Card c : cards) if (!copy.remove(c)) return false;
        return true;
    }
}