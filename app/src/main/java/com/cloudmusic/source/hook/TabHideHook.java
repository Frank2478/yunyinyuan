package com.cloudmusic.source.hook;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 精简底部导航：9.5.81 的底部栏是 MainActivity 里的 LinearLayout(bottomNav)，
 * 只保留第一项「首页」和最后一项「我的」。
 * 该功能默认生效（面板开关暂不参与判断）。
 */
public class TabHideHook {

    private static final String TAG = "YUNYINYUAN";

    public TabHideHook(Context context) {
        if (!com.cloudmusic.source.helper.SettingHelper.getInstance()
                .isEnable(com.cloudmusic.source.helper.SettingHelper.beauty_tab_hide_key)) {
            return;
        }
        try {
            Class<?> mainCls = XposedHelpers.findClass(
                    "com.netease.cloudmusic.activity.MainActivity", context.getClassLoader());
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        final Activity act = (Activity) param.thisObject;
                        act.getWindow().getDecorView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                hideMiddleTabs(act);
                            }
                        }, 600);
                    } catch (Throwable ignore) {
                    }
                }
            };
            XposedBridge.hookAllMethods(mainCls, "onResume", hook);
            XposedBridge.hookAllMethods(mainCls, "onStart", hook);
            XposedBridge.hookAllMethods(mainCls, "onCreate", hook);
            android.util.Log.i(TAG, "TabHide: hooked MainActivity lifecycle");
        } catch (Throwable t) {
            android.util.Log.i(TAG, "TabHide: failed " + t);
        }
    }

    private static void hideMiddleTabs(Activity act) {
        try {
            int id = act.getResources().getIdentifier("bottomNav", "id", act.getPackageName());
            if (id == 0) {
                android.util.Log.i(TAG, "TabHide: bottomNav id not found");
                return;
            }
            View nav = act.findViewById(id);
            if (!(nav instanceof ViewGroup)) {
                android.util.Log.i(TAG, "TabHide: bottomNav not ViewGroup");
                return;
            }
            ViewGroup g = (ViewGroup) nav;
            int count = g.getChildCount();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                View child = g.getChildAt(i);
                sb.append(tabText(child)).append('|');
                if (i > 0 && i < count - 1) {
                    child.setVisibility(View.GONE);
                }
            }
            android.util.Log.i(TAG, "TabHide: count=" + count + " tabs=" + sb);
        } catch (Throwable t) {
            android.util.Log.i(TAG, "TabHide: hide failed " + t);
        }
    }

    private static String tabText(View v) {
        try {
            java.util.ArrayList<String> texts = new java.util.ArrayList<>();
            collect(v, texts);
            return texts.toString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void collect(View v, java.util.List<String> out) {
        if (v == null) {
            return;
        }
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs != null && cs.length() > 0) {
                out.add(cs.toString());
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out);
            }
        }
    }
}
