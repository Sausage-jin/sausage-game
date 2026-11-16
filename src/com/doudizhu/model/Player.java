package com.doudizhu.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家类。
 */
public class Player {

    public enum Role { FARMER, LANDLORD }

    private final int id;           // 0=玩家自己, 1=右侧AI, 2=左侧AI
    private String name;
    private final boolean human;
    private Role role = Role.FARMER;
    private int team = -1;          // 四人模式队伍（0=大牌组, 1=小牌组, -1=三人模式）
    private final List<Card> hand = new ArrayList<>();
    private int score = 0;
    private int displayCount = -1;  // 远程模式下的牌数（-1=使用 hand.size()）

    public Player(int id, String name, boolean human) {
        this.id = id;
        this.name = name;
        this.human = human;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isHuman() { return human; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isLandlord() { return role == Role.LANDLORD; }
    public int getTeam() { return team; }
    public void setTeam(int team) { this.team = team; }
    public int getScore() { return score; }
    public void addScore(int delta) { this.score += delta; }

    public List<Card> getHand() { return hand; }

    public void addCard(Card c) { hand.add(c); }

    public void sortHand() { Collections.sort(hand); }

    /** 从手牌中移除指定的牌 */
    public void removeCards(List<Card> cards) {
        for (Card c : cards) hand.remove(c);
    }

    public int getCardCount() { return displayCount >= 0 ? displayCount : hand.size(); }
    public void setDisplayCount(int n) { this.displayCount = n; }

    /** 手牌中某 rank 的数量 */
    public int countRank(int rank) {
        int n = 0;
        for (Card c : hand) if (c.getRank() == rank) n++;
        return n;
    }
}