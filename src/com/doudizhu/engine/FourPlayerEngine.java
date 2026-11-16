package com.doudizhu.engine;

import com.doudizhu.model.*;

import javax.swing.SwingUtilities;
import java.util.*;
import java.util.List;

/**
 * 四人斗地主引擎（2v2 组队模式）。
 * 规则：
 *  - 80 张牌（每种点数 6 张 + 大小王），每人 20 张，无底牌
 *  - 猜牌分组：每人亮一张牌，亮牌最大的 2 人组成大牌组（team 0），
 *    最小的 2 人组成小牌组（team 1）
 *  - 大牌组中亮牌最大者先出牌
 *  - 任一玩家出完牌 → 该玩家所属阵营获胜，游戏结束
 *  - 六张炸弹 > 四张炸弹 > 普通牌型；炸弹/六炸/王炸使倍数 ×2
 */
public class FourPlayerEngine implements IGameController {

    private final List<Player> players = new ArrayList<>();
    private final List<GameEngine.GameListener> listeners = new ArrayList<>();

    private GameEngine.State state = GameEngine.State.IDLE;
    private int currentPlayer = 0;
    private CardPlay lastPlay = null;
    private int lastPlayer = -1;
    private int passCount = 0;
    private int multiplier = 1;

    // 猜牌阶段
    private int guessIndex = 0;
    private final Card[] guessCards = new Card[4];
    private final boolean[] guessDone = new boolean[4];

    // 分组
    private final int[] teams = new int[4];   // teams[playerId] = 队伍号
    private int startPlayer = 0;

    // 出牌统计
    private final int[] playCounts = new int[4];
    private Timer aiTimer = new Timer("ai4-timer", true);
    private boolean onlineMode = false;   // 联机模式：远程座位等待操作，不触发AI
    /** 联机模式下的人机座位集合：这些座位即使 onlineMode 也由本地 AI 代打 */
    private final java.util.Set<Integer> botSeats = new java.util.HashSet<>();

    public FourPlayerEngine() {
        players.add(new Player(0, com.doudizhu.Main.userNickname, true));
        players.add(new Player(1, "电脑1", false));    // 右（东）
        players.add(new Player(2, "电脑2", false));    // 上（北）
        players.add(new Player(3, "电脑3", false));    // 左（西）
    }

    /** 联机模式：座位1/2/3为远程真人，引擎等待其操作而非AI代打 */
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

    /** 设置玩家昵称（联机时主机根据客户端信息设置） */
    public void setPlayerName(int id, String name) {
        if (id >= 0 && id < 4 && name != null && !name.isEmpty()) {
            players.get(id).setName(name);
        }
    }

    // ==================== IGameController ====================

    @Override public GameEngine.State getState() { return state; }
    @Override public List<Player> getPlayers() { return players; }
    @Override public Player getPlayer(int id) { return players.get(id); }
    @Override public List<Card> getBottomCards() { return Collections.emptyList(); }
    @Override public int getLandlordId() { return -1; }
    @Override public int getCurrentPlayer() { return currentPlayer; }
    @Override public CardPlay getLastPlay() { return lastPlay; }
    @Override public int getLastPlayer() { return lastPlayer; }
    @Override public int getMultiplier() { return multiplier; }
    @Override public int getBaseScore() { return 1; }
    @Override public boolean[] getDoubled() { return new boolean[4]; }

    @Override
    public void addListener(GameEngine.GameListener l) { listeners.add(l); }

    /** 四人模式专属：获取分组 */
    public int[] getTeams() { return teams; }

    /** 四人模式专属：获取猜牌 */
    public Card[] getGuessCards() { return guessCards; }

    /** 四人模式专属：某玩家是否已亮牌 */
    public boolean isGuessDone(int playerId) { return guessDone[playerId]; }

    // 三人模式接口（四人模式不使用）
    @Override public void humanBid(boolean call) { }
    @Override public void humanDouble(boolean dbl) { }

    // ==================== 开局 ====================

    @Override
    public void startNewGame() {
        aiTimer.purge();
        for (Player p : players) {
            p.getHand().clear();
            p.setRole(Player.Role.FARMER);
            p.setTeam(-1);
        }
        lastPlay = null; lastPlayer = -1; passCount = 0;
        multiplier = 1; guessIndex = 0; startPlayer = 0;
        for (int i = 0; i < 4; i++) {
            guessCards[i] = null; guessDone[i] = false;
            teams[i] = -1; playCounts[i] = 0;
        }

        state = GameEngine.State.DEALING;
        fireStateChanged();

        // 洗牌发牌：80 张，每人 20 张
        Deck deck = new Deck(true);
        deck.shuffle();
        for (int i = 0; i < 80; i++) players.get(i % 4).addCard(deck.deal());
        for (Player p : players) p.sortHand();

        // 进入猜牌阶段，从玩家 0 开始
        currentPlayer = 0;
        state = GameEngine.State.GUESSING;
        SwingUtilities.invokeLater(() -> {
            for (GameEngine.GameListener l : listeners) l.onHandUpdated(-1);
            fireStateChanged();
            for (GameEngine.GameListener l : listeners) l.onGuessTurn(currentPlayer);
        });
        nextGuess();
    }

    // ==================== 猜牌分组 ====================

    private void nextGuess() {
        if (state != GameEngine.State.GUESSING) return;
        if (guessIndex >= 4) {
            decideTeams();
            return;
        }
        Player p = players.get(currentPlayer);
        if (!isOnlineBot(p.getId()) && (p.isHuman() || onlineMode)) {
            // 等待玩家操作（联机人机座位由本地AI代打）
        } else {
            aiTimer.schedule(new TimerTask() {
                @Override public void run() {
                    Card c = AIEngine.chooseGuessCard(p.getHand());
                    SwingUtilities.invokeLater(() -> submitGuess(c));
                }
            }, 800 + new Random().nextInt(500));
        }
    }

    /** 人类玩家提交猜牌（亮出的牌） */
    public void submitGuess(Card card) {
        if (state != GameEngine.State.GUESSING) return;
        if (guessDone[currentPlayer]) return;
        Player p = players.get(currentPlayer);
        if (card == null || !p.getHand().contains(card)) return;

        guessCards[currentPlayer] = card;
        guessDone[currentPlayer] = true;
        for (GameEngine.GameListener l : listeners) l.onGuessResult(currentPlayer, card);
        guessIndex++;

        if (guessIndex >= 4) {
            decideTeams();
            return;
        }
        currentPlayer = (currentPlayer + 1) % 4;
        for (GameEngine.GameListener l : listeners) l.onGuessTurn(currentPlayer);
        nextGuess();
    }

    /** 根据亮牌大小分组：最大 2 人大牌组(0)，最小 2 人小牌组(1) */
    private void decideTeams() {
        // 按（rank, suit）排序亮牌
        Integer[] order = {0, 1, 2, 3};
        Arrays.sort(order, (a, b) -> {
            Card ca = guessCards[a], cb = guessCards[b];
            int c = Integer.compare(ca.getRank(), cb.getRank());
            if (c != 0) return c;
            return ca.getSuit().ordinal() - cb.getSuit().ordinal();
        });
        // order[2], order[3] 是最大的两张 → 大牌组
        teams[order[2]] = 0;
        teams[order[3]] = 0;
        teams[order[0]] = 1;
        teams[order[1]] = 1;
        for (Player p : players) p.setTeam(teams[p.getId()]);

        // 亮牌最大者先出
        startPlayer = order[3];

        for (GameEngine.GameListener l : listeners) l.onTeamsDecided(teams.clone());

        // 进入出牌
        lastPlay = null; lastPlayer = -1; passCount = 0;
        currentPlayer = startPlayer;
        state = GameEngine.State.PLAYING;
        fireStateChanged();
        for (GameEngine.GameListener l : listeners) l.onPlayTurn(currentPlayer);
        nextPlay();
    }

    // ==================== 出牌 ====================

    private void nextPlay() {
        if (state != GameEngine.State.PLAYING) return;
        Player p = players.get(currentPlayer);
        if (!isOnlineBot(p.getId()) && (p.isHuman() || onlineMode)) {
            // 等待玩家操作（联机人机座位由本地AI代打）
        } else {
            aiTimer.schedule(new TimerTask() {
                @Override public void run() {
                    CardPlay play = AIEngine.decide4p(p.getHand(), lastPlay, lastPlayer,
                            p.getId(), teams, players);
                    SwingUtilities.invokeLater(() -> submitPlay(play));
                }
            }, 1000 + new Random().nextInt(600));
        }
    }

    @Override
    public void submitPlay(CardPlay play) {
        if (state != GameEngine.State.PLAYING) return;
        Player p = players.get(currentPlayer);

        if (play == null) {
            passCount++;
            for (GameEngine.GameListener l : listeners) l.onPass(p.getId());
            advanceTurn();
            return;
        }

        // 合法性校验
        if (play.getType() == CardType.INVALID) return;
        if (!containsAll(p.getHand(), play.getCards())) return;
        if (lastPlay != null && lastPlayer != p.getId() && !play.canBeat(lastPlay)) return;

        // 炸弹类翻倍
        if (play.getType() == CardType.BOMB) multiplier *= 2;
        if (play.getType() == CardType.SIX_BOMB) multiplier *= 2;
        if (play.getType() == CardType.ROCKET) multiplier *= 2;

        p.removeCards(play.getCards());
        lastPlay = play;
        lastPlayer = p.getId();
        passCount = 0;
        playCounts[p.getId()]++;

        for (GameEngine.GameListener l : listeners) l.onPlay(p.getId(), play);
        for (GameEngine.GameListener l : listeners) l.onHandUpdated(p.getId());

        // 胜利判定：任一玩家出完牌，其阵营立即获胜
        if (p.getCardCount() == 0) {
            gameOver(teams[p.getId()]);
            return;
        }
        advanceTurn();
    }

    private void advanceTurn() {
        // 三人都过 → 重新首出
        if (passCount >= 3) {
            currentPlayer = lastPlayer;
            lastPlay = null;
            passCount = 0;
        } else {
            currentPlayer = (currentPlayer + 1) % 4;
            // 跳过已出完牌的玩家
            int safety = 0;
            while (players.get(currentPlayer).getCardCount() == 0 && safety < 4) {
                currentPlayer = (currentPlayer + 1) % 4;
                safety++;
            }
        }
        for (GameEngine.GameListener l : listeners) l.onPlayTurn(currentPlayer);
        nextPlay();
    }

    private void gameOver(int winnerTeam) {
        state = GameEngine.State.GAME_OVER;
        boolean bigTeamWon = (winnerTeam == 0);
        // 小牌组以弱胜强额外奖励：大牌组胜 ×2，小牌组胜 ×3
        int unit = bigTeamWon ? 2 : 3;
        int total = unit * multiplier;
        for (Player p : players) {
            if (teams[p.getId()] == winnerTeam) p.addScore(total);
            else p.addScore(-total);
        }
        for (GameEngine.GameListener l : listeners)
            l.onGameOverTeam(winnerTeam, bigTeamWon, multiplier);
        // 同时触发通用 onGameOver 供 UI 兼容（winnerId 用先出完者）
        int firstOut = -1;
        for (Player p : players)
            if (teams[p.getId()] == winnerTeam && p.getCardCount() == 0) { firstOut = p.getId(); break; }
        for (GameEngine.GameListener l : listeners)
            l.onGameOver(firstOut, bigTeamWon, multiplier, unit, false);
        fireStateChanged();
    }

    // ==================== 提示 ====================

    @Override
    public CardPlay getHint(List<Card> selected) {
        Player me = players.get(0);
        if (state != GameEngine.State.PLAYING || currentPlayer != 0) return null;
        boolean leading = (lastPlay == null || lastPlayer == 0);
        if (leading) {
            return AIEngine.decide4p(me.getHand(), null, -1, 0, teams, players);
        }
        List<CardPlay> beats = RuleEngine.findBeats(me.getHand(), lastPlay);
        if (beats.isEmpty()) return null;
        beats.sort(Comparator.comparingInt(CardPlay::getKeyRank));
        for (CardPlay b : beats) if (!b.getType().isBombLike()) return b;
        return beats.get(0);
    }

    // ==================== 工具 ====================

    private void fireStateChanged() {
        SwingUtilities.invokeLater(() -> {
            for (GameEngine.GameListener l : listeners) l.onStateChanged(state);
        });
    }

    private boolean containsAll(List<Card> hand, List<Card> cards) {
        List<Card> copy = new ArrayList<>(hand);
        for (Card c : cards) if (!copy.remove(c)) return false;
        return true;
    }
}