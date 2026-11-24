package com.cloudmusic.source.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 9.5.81 的设置页是 React Native 页面（MainProcessRNActivity），
 * 原生 SettingActivity 不再承载设置页，这里在 RN 设置页底部插入「云音源」入口。
 */
public class RnSettingHook {

    private static final String RN_ACTIVITY = "com.netease.cloudmusic.music.biz.rn.activity.MainProcessRNActivity";
    private static final String ENTRY_TAG = "cloudmusic_source_entry";

    public RnSettingHook(final Context context) {
        Class<?> cls = XposedHelpers.findClassIfExists(RN_ACTIVITY, context.getClassLoader());
        if (cls == null) {
            XposedBridge.log("RnSettingHook: class not found");
            return;
        }
        XposedHelpers.findAndHookMethod(cls, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                String url = null;
                String pageName = null;
                try {
                    Intent intent = activity.getIntent();
                    if (intent != null) {
                        url = intent.getStringExtra("extra_source_url");
                    }
                } catch (Throwable ignore) {
                }
                try {
                    Object name = XposedHelpers.callMethod(activity, "providePageName");
                    if (name instanceof String) {
                        pageName = (String) name;
                    }
                } catch (Throwable ignore) {
                }
                XposedBridge.log("RnSettingHook: url=" + url + " pageName=" + pageName);
                if (!isSettingPage(url, pageName)) {
                    return;
                }
                activity.getWindow().getDecorView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        addEntry(activity);
                    }
                }, 600);
            }
        });
    }

    private static boolean isSettingPage(String url, String pageName) {
        String u = url == null ? "" : url.toLowerCase();
        String p = pageName == null ? "" : pageName.toLowerCase();
        return u.contains("setting") || p.contains("setting");
    }

    private static void addEntry(final Activity activity) {
        try {
            View content = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) {
                XposedBridge.log("RnSettingHook: no content view");
                return;
            }
            ViewGroup root = (ViewGroup) content;
            if (root.findViewWithTag(ENTRY_TAG) != null) {
                return;
            }

            LinearLayout box = new LinearLayout(activity);
            box.setTag(ENTRY_TAG);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setBackgroundColor(0xF21C1C1E);
            int padH = dp(activity, 20);
            int padV = dp(activity, 12);
            box.setPadding(padH, padV, padH, padV);
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (SettingHook.instance != null) {
                            SettingHook.instance.showSettingDialog(activity);
                        } else {
                            XposedBridge.log("RnSettingHook: SettingHook instance is null");
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("RnSettingHook: open panel failed: " + t);
                    }
                }
            });

            TextView title = new TextView(activity);
            title.setText("云音源");
            title.setTextColor(0xFF3B7BFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

            TextView sub = new TextView(activity);
            sub.setText("点击打开设置面板");
            sub.setTextColor(0xFFAAAAAA);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

            box.addView(title);
            box.addView(sub);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin = dp(activity, 24);
            lp.leftMargin = dp(activity, 16);
            lp.rightMargin = dp(activity, 16);
            root.addView(box, lp);
            box.bringToFront();
            XposedBridge.log("RnSettingHook: entry added");
        } catch (Throwable t) {
            XposedBridge.log("RnSettingHook: addEntry failed: " + t);
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
