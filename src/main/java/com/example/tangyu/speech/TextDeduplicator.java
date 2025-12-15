package com.example.tangyu.speech;

import java.util.regex.Pattern;

/**
 * 文本去重处理器，参照Python逻辑实现。
 * 去除连续重复的字符和短语，清理多余标点。
 */
public class TextDeduplicator {

    /**
     * 去除重复字词
     * 例如：'你好你好，你是谁你是谁？' -> '你好，你是谁？'
     *
     * @param text 原始文本
     * @return 去重后的文本
     */
    public static String deduplicate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // 1. 去除连续重复的字符（如 "你你你" -> "你"）
        result = Pattern.compile("(.)\\1{2,}").matcher(result).replaceAll("$1");

        // 2. 去除连续重复的短语（2-6字）
        for (int length = 6; length >= 2; length--) {
            String pattern = "(.{" + length + "})\\1+";
            result = Pattern.compile(pattern).matcher(result).replaceAll("$1");
        }

        // 3. 清理多余空格
        result = result.replaceAll("\\s+", " ").trim();

        // 4. 修复标点问题（多个连续标点）
        result = Pattern.compile("[，,]{2,}").matcher(result).replaceAll("，");
        result = Pattern.compile("[。.]{2,}").matcher(result).replaceAll("。");
        result = Pattern.compile("[？?]{2,}").matcher(result).replaceAll("？");
        result = Pattern.compile("[！!]{2,}").matcher(result).replaceAll("！");

        return result.trim();
    }
}

