/**
 * 卡牌：包含类型、归属玩家、是否翻开
 */
public class Card {
    private final CardType type;
    private final int owner;   // 0=中立, 1=红方(玩家1), 2=蓝方(玩家2)
    private boolean faceUp;    // 是否明牌

    public Card(CardType type, int owner) {
        this.type = type;
        this.owner = owner;
        this.faceUp = false;
    }

    public CardType getType() {
        return type;
    }

    public int getOwner() {
        return owner;
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public void setFaceUp(boolean faceUp) {
        this.faceUp = faceUp;
    }
}