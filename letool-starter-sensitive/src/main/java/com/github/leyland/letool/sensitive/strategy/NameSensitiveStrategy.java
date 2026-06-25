package com.github.leyland.letool.sensitive.strategy;

import com.github.leyland.letool.sensitive.core.MaskContext;
import com.github.leyland.letool.sensitive.core.SensitiveStrategy;

import java.util.Set;

/**
 * 中文姓名脱敏 —— 单姓保留姓、复姓保留前两字，名字部分用 {@code *} 遮盖.
 *
 * <pre>
 *   "张三"   → "张*"
 *   "张三丰"  → "张*"
 *   "欧阳修"  → "欧阳*"
 *   "司马光"  → "司马*"
 * </pre>
 *
 * <p>复姓集合由 {@link #COMPOUND_SURNAMES} 定义，收录常见 40+ 个中文复姓.
 * 可通过 {@link MaskContext#getMaskChar()} 覆盖默认遮盖字符.</p>
 */
public class NameSensitiveStrategy implements SensitiveStrategy<MaskContext> {

    /**
     * 常见中文复姓集合 —— 包含 40+ 个复姓（欧阳、上官、司马、诸葛…）.
     * 用于判断姓名前两字是否为复姓，复姓保留两字（如 "欧阳*"），单姓保留一字（如 "张*"）.
     */
    private static final Set<String> COMPOUND_SURNAMES = Set.of(
            "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫",
            "万俟", "闻人", "夏侯", "诸葛", "尉迟", "公羊", "赫连", "澹台",
            "皇甫", "宗政", "濮阳", "公冶", "太叔", "申屠", "公孙", "慕容",
            "仲孙", "钟离", "长孙", "宇文", "司徒", "鲜于", "司空", "司寇",
            "子车", "亓官", "巫马", "公西", "壤驷", "乐正", "令狐", "段干",
            "百里", "呼延", "东郭", "梁丘", "左丘", "闾丘", "谷梁", "拓跋"
    );

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.isEmpty()) return value;

        // context 为 null 时使用默认遮盖字符 '*'
        char ch = context != null ? context.getMaskChar() : '*';

        // 长度 >= 2 时检查前两字是否为复姓
        if (value.length() >= 2) {
            String firstTwo = value.substring(0, 2);
            if (COMPOUND_SURNAMES.contains(firstTwo)) {
                return firstTwo + ch;          // 欧阳修 → "欧阳*"
            }
        }
        // 单姓：保留第一个字 + 遮盖
        return value.charAt(0) + String.valueOf(ch);  // 张三 → "张*"
    }
}
