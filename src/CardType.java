/**
 * 卡牌类型枚举
 * 中立资源（owner=0）：大树、巨石、猫咪、旺财、白/黑洋枪、白/黑捕虫网
 * 白牌=双方均可移动；黑牌=双方均不可移动（但可参与合成）
 */
public enum CardType {
    LANTOU("烂头"),
    BAIQIANG("白洋枪"),
    SHARENHU("杀人虎"),
    XIAOHAI("小孩"),
    GONGJI("公鸡"),
    MIFENG("蜜蜂"),
    LANTOUBEIQIANG("烂头背枪"),
    HEIQIANG("黑洋枪"),
    DASHU("大树"),
    JUSHI("巨石"),
    MAOMI("猫咪"),
    WANGCAI("旺财"),
    BAIBUCHONG("白捕虫网"),
    HEIBUCHONG("黑捕虫网"),
    BUCHONGXIAOHAI("捕虫男孩");

    private final String name;

    CardType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** 是否中立牌（无归属，owner=0） */
    public boolean isNeutral() {
        return this == DASHU || this == JUSHI || this == MAOMI || this == WANGCAI
            || this == BAIQIANG || this == HEIQIANG
            || this == BAIBUCHONG || this == HEIBUCHONG;
    }

    /** 是否可主动移动（黑牌与大树/巨石不可动） */
    public boolean isMovable() {
        return this != DASHU && this != JUSHI
            && this != HEIQIANG && this != HEIBUCHONG;
    }

    /** 是否洋枪（白或黑，可参与烂头合成） */
    public boolean isQiang() {
        return this == BAIQIANG || this == HEIQIANG;
    }

    /** 是否捕虫网（白或黑，可参与小孩合成） */
    public boolean isBuchong() {
        return this == BAIBUCHONG || this == HEIBUCHONG;
    }
}