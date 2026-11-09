package com.fragment.policeforce.common.constants;

/**
 * 体检时段类型。
 */
public final class ExamSlotTypeConstants {

    /**
     * 普通时段：直接走 MySQL 条件扣减即可。
     */
    public static final String NORMAL = "NORMAL";

    /**
     * 热门时段：放号瞬间高并发，必须走 Redis 前置裁决。
     */
    public static final String HOT = "HOT";

    private ExamSlotTypeConstants() {
    }
}
