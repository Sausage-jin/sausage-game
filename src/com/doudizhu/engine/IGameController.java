package com.doudizhu.engine;

import com.doudizhu.model.*;

import java.util.List;

/**
 * 游戏控制器接口：本地引擎和远程引擎均实现此接口，
 * 使 GamePanel 可以无差别地与本地 AI 对战或远程联机。
 */
public interface IGameController {

    GameEngine.State getState();

    List<Player> getPlayers();

    Player getPlayer(int id);

    List<Card> getBottomCards();

    int getLandlordId();

    int getCurrentPlayer();

    CardPlay getLastPlay();

    int getLastPlayer();

    int getMultiplier();

    int getBaseScore();

    boolean[] getDoubled();

    void addListener(GameEngine.GameListener l);

    /** 开始新游戏（本地模式直接开局，远程模式发送请求） */
    void startNewGame();

    /** 叫地主决策 */
    void humanBid(boolean call);

    /** 加倍决策 */
    void humanDouble(boolean dbl);

    /** 提交出牌（null=过牌） */
    void submitPlay(CardPlay play);

    /** 获取提示 */
    CardPlay getHint(List<Card> selected);
}