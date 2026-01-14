package com.bin.sandbox.utils;

/**
 * 抛出异常工具类。
 */
public final class ThrowUtils {

    private ThrowUtils() {
    }

    public static void throwIf(boolean condition, String message) {
        if (condition) {
            throw new RuntimeException(message);
        }
    }
}
