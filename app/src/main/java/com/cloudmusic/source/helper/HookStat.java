package com.cloudmusic.source.helper;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook 注册成功率统计，显示在设置面板顶部。
 */
public class HookStat {
    public static int total = 0;
    public static int success = 0;
    public static final List<String> failed = new ArrayList<>();

    public static synchronized void record(boolean ok, String name) {
        total++;
        if (ok) {
            success++;
        } else {
            failed.add(name);
        }
    }

    public static synchronized String text() {
        if (total == 0) {
            return "Hook: 尚未执行";
        }
        int percent = success * 100 / total;
        StringBuilder sb = new StringBuilder();
        sb.append("Hook 成功: ").append(success).append("/").append(total).append(" (").append(percent).append("%)");
        if (!failed.isEmpty()) {
            sb.append("\n失败: ");
            for (int i = 0; i < failed.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(failed.get(i));
            }
        }
        return sb.toString();
    }
}
