package com.liangmanman.common;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.util.Locale;

/** 商品名转拼音：“复合肥” → “fuhefei fhf”，存进 search_text，农户打拼音或首字母也能搜到 */
public final class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtil() {}

    public static String searchText(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char c : text.toCharArray()) {
            String[] py = null;
            try {
                py = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            } catch (Exception ignored) {
                // 非汉字
            }
            if (py != null && py.length > 0) {
                full.append(py[0]);
                initials.append(py[0].charAt(0));
            } else if (Character.isLetterOrDigit(c)) {
                char lc = Character.toLowerCase(c);
                full.append(lc);
                initials.append(lc);
            }
        }
        return (full + " " + initials).toLowerCase(Locale.ROOT);
    }
}
