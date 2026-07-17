package com.xiaopangxianren.main;

public enum InstanceBinType {
    Set1_Bin_3,
    Set1_Bin_5,
    Set2_Bin_Linear,        // 线性
    Set2_Bin_Concavity,     // 凹性
    Set2_Bin_Convexity,     // 凸性

    // 带时间窗约束的数据集
    Set3_Opt_With_Time_Window,   // 已知最优解的数据集(带时间窗约束)
    Set3_Hem_With_Time_Window    // 未知最优解的数据集(带时间窗约束)
}
