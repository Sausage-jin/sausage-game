package com.doudizhu.model;

/**
 * 斗地主牌型枚举。
 */
public enum CardType {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_ONE("三带一"),
    TRIPLE_TWO("三带二"),
    STRAIGHT("顺子"),
    DOUBLE_STRAIGHT("连对"),
    PLANE("飞机"),
    PLANE_ONE("飞机带单"),
    PLANE_TWO("飞机带对"),
    FOUR_TWO("四带二"),
    FOUR_TWO_PAIR("四带两对"),
    BOMB("炸弹"),
    SIX_BOMB("六张炸弹"),
    ROCKET("王炸"),
    INVALID("非法牌型");

    public final String label;
    CardType(String label) { this.label = label; }

    /** 是否是炸弹类（可压制普通牌型） */
    public boolean isBombLike() { return this == BOMB || this == SIX_BOMB || this == ROCKET; }
}