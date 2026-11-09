package com.fragment.policeforce.exam.model;

/**
 * 体检预约相关状态常量。
 */
public final class ExamStatusConstants {

    /**
     * 体检预约单状态：已预约
     */
    public static final String BOOKED = "BOOKED";

    /**
     * 体检预约单状态：已取消
     */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 体检预约单状态：已签到
     */
    public static final String CHECKED_IN = "CHECKED_IN";

    /**
     * 体检预约单状态：已完成
     */
    public static final String COMPLETED = "COMPLETED";

    /**
     * 请求账本状态：处理中
     */
    public static final String PROCESSING = "PROCESSING";

    /**
     * 请求账本状态：已确认
     */
    public static final String CONFIRMED = "CONFIRMED";

    /**
     * 请求账本状态：已拒绝
     */
    public static final String REJECTED = "REJECTED";

    /**
     * 请求账本状态：已过期
     */
    public static final String EXPIRED = "EXPIRED";

    /**
     * 候补状态：排队中
     */
    public static final String WAITING = "WAITING";

    /**
     * 候补状态：已递补
     */
    public static final String PROMOTED = "PROMOTED";

    /**
     * 候补状态：已过期
     */
    public static final String EXPIRED_WAITING = "EXPIRED";

    /**
     * 拒绝码：名额已满
     */
    public static final String REJECT_OUT_OF_STOCK = "OUT_OF_STOCK";

    /**
     * 拒绝码：该警员已有预约（一人一档）
     */
    public static final String REJECT_DUPLICATE_OFFICER = "DUPLICATE_OFFICER";

    /**
     * 拒绝码：批次未放号
     */
    public static final String REJECT_NOT_RELEASED = "NOT_RELEASED";

    /**
     * 拒绝码：批次已结束
     */
    public static final String REJECT_BATCH_CLOSED = "BATCH_CLOSED";

    /**
     * 拒绝码：时段不可预约
     */
    public static final String REJECT_SLOT_NOT_BOOKABLE = "SLOT_NOT_BOOKABLE";

    /**
     * 拒绝码：已进入候补队列
     */
    public static final String REJECT_WAITING = "WAITING";

    private ExamStatusConstants() {
    }
}
