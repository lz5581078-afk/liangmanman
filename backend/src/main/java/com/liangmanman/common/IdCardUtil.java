package com.liangmanman.common;

/** 18 位身份证号校验（GB 11643-1999 校验码） */
public final class IdCardUtil {

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private IdCardUtil() {}

    public static boolean valid(String id) {
        if (id == null) return false;
        String s = id.trim().toUpperCase();
        if (!s.matches("^\\d{17}[\\dX]$")) return false;
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (s.charAt(i) - '0') * WEIGHTS[i];
        }
        return CHECK[sum % 11] == s.charAt(17);
    }
}
