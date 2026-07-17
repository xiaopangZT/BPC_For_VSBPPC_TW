package com.xiaopangxianren.utils;

public class TimeUtil {
    // 定义时间限制，单位为毫秒（例如：600000毫秒 = 10分钟）
    public  static long TimeLimit = 600000;
    public static long startTime;

    public static long getCurTime() {
        return System.currentTimeMillis() - startTime;
    }

    public static boolean isTimeLimit() {
        return System.currentTimeMillis() - startTime >= TimeLimit;
    }

    public static long getRemainingTime() {
        return Math.max(0, TimeLimit - System.currentTimeMillis() + startTime);
    }
}
