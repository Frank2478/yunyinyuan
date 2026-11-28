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
        } catch (Throwable ignore) {
        }
    }

    private static void hideMiddleTabs(Activity act) {
        try {
            int id = act.getResources().getIdentifier("bottomNav", "id", act.getPackageName());
            if (id == 0) {
                return;
            }
            View nav = act.findViewById(id);
            if (!(nav instanceof ViewGroup)) {
                return;
            }
            ViewGroup g = (ViewGroup) nav;
            int count = g.getChildCount();
            for (int i = 0; i < count; i++) {
                if (i > 0 && i < count - 1) {
                    g.getChildAt(i).setVisibility(View.GONE);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
