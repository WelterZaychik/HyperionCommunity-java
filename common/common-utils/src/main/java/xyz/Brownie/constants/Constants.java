package xyz.Brownie.constants;

public final class Constants {

    /**
     * 单方关注状态
     * - A关注了B，但B未关注A
     */
    public static final int FOLLOWS_STATUS_ZERO = 0;

    /**
     * 互相关注状态
     * - A和B相互关注
     */
    public static final int FOLLOWS_STATUS_ONE = 1;

    /**
     *
     * - A拉黑了B，B无法关注A或给A发送消息
     */
    public static final int FOLLOWS_STATUS_TWO = 2;
}
