package com.liangmanman.common;

/** 打码工具：平台只保存打码后的个人信息 */
public final class Mask {

    private Mask() {}

    /** 王大山 → 王** */
    public static String name(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }

    /** 6228480012345678 → **** 5678 */
    public static String card(String card) {
        if (card == null || card.length() < 4) return "****";
        return "**** " + card.substring(card.length() - 4);
    }

    /** 13812345678 → 138****5678 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 昵称打码：张三丰 → 张*，用于凑单成员、评价列表 */
    public static String nickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) return "农友";
        return nickname.charAt(0) + "*";
    }
}
