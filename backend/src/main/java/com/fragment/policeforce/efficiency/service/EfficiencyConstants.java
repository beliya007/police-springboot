package com.fragment.policeforce.efficiency.service;

/**
 * 效能计算相关的常量。
 */
public final class EfficiencyConstants {

    /**
     * 因素维度：年龄
     */
    public static final String FACTOR_TYPE_AGE = "AGE";

    /**
     * 因素维度：性别
     */
    public static final String FACTOR_TYPE_GENDER = "GENDER";

    /**
     * 单个维度内所有取值的权重和必须等于该值，否则预检拒绝。
     */
    public static final int FACTOR_WEIGHT_TOTAL = 100;

    /**
     * 体检结论：合格
     */
    public static final String CONCLUSION_PASS = "PASS";

    /**
     * 体检结论：不合格（不计入有效警力）
     */
    public static final String CONCLUSION_FAIL = "FAIL";

    /**
     * 体检结论：有伤病（体能类模块效能分下调）
     */
    public static final String CONCLUSION_INJURED = "INJURED";

    /**
     * 基础分。所有个体效能分都是 baseScore × 权重因子的乘积。
     */
    public static final double BASE_SCORE = 100.0d;

    /**
     * 触发来源：效能因素权重调整
     */
    public static final String TRIGGER_FACTOR_WEIGHT = "FACTOR_WEIGHT";

    /**
     * 触发来源：勤务模块比重调整
     */
    public static final String TRIGGER_MODULE_RATIO = "MODULE_RATIO";

    /**
     * 触发来源：配置人数调整
     */
    public static final String TRIGGER_HEADCOUNT = "HEADCOUNT";

    /**
     * 触发来源：体检结论录入
     */
    public static final String TRIGGER_EXAM_RESULT = "EXAM_RESULT";

    /**
     * 触发来源：手工触发
     */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /**
     * 范围：全量
     */
    public static final String SCOPE_ALL = "ALL";

    /**
     * 范围：某区域
     */
    public static final String SCOPE_DISTRICT = "DISTRICT";

    /**
     * 范围：某警局
     */
    public static final String SCOPE_STATION = "STATION";

    /**
     * 范围：某模块
     */
    public static final String SCOPE_MODULE = "MODULE";

    private EfficiencyConstants() {
    }
}
