package com.xiaopangxianren.classes;

public class Parameter {
    public static double EPS = 1e-9;
    public static final int LONG_SIZE = 64, MAX_PATTERN_NUM = 2_000_000;

    public static boolean EarlyTerminationEnable = true;  // 启用早停策略
    public static boolean isUseSR_Cut = true,     // 启用SR剪枝
            isUsePatternEnum = true,              // 启用模式枚举
            isDiving = true,                      // 启用潜水策略
            isUseGrouping = true;                 // 启用分组策略

    // 不用修改这个参数，程序会根据实例类型自动设置
    public static boolean isTimeWindowConstraint = false;
}
