package com.cloudmusic.source.hook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 「我的」页 VIP 徽章是自绘位图。
 * 第一步：追踪 MyMusicPrivilegeImageView / PrivilegeImageViewContainer 的方法调用，
 * 找出真正渲染徽章的入口。
 */
public class SvipBadgeHook {

    private static final String TAG = "YUNYINYUAN";
    private static final String BADGE_TAG = "yunyinyuan_svip_badge";
    private static final Set<String> CALLED = new HashSet<>();

    public SvipBadgeHook(Context context) {
        ClassLoader cl = context.getClassLoader();
        hookAll("com.netease.cloudmusic.module.mymusic.ui.MyMusicPrivilegeImageView", cl);
        hookAll("com.netease.cloudmusic.module.mymusic.ui.PrivilegeImageViewContainer", cl);
    }

    private void hookAll(String className, ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, cl);
            final String simple = className.substring(className.lastIndexOf('.') + 1);
            for (final Method m : cls.getDeclaredMethods()) {
                if (Modifier.isAbstract(m.getModifiers())) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = simple + "." + m.getName() + "/" + m.getParameterTypes().length;
                            synchronized (CALLED) {
                                if (!CALLED.add(key)) {
                                    return;
                                }
                            }
                            android.util.Log.i(TAG, "SvipBadge trace: " + key);
                            //容器渲染入口：注入 SVIP 标签
                            if (simple.contains("Container") && "i".equals(m.getName())
                                    && param.args != null && param.args.length == 3) {
                                inject((View) param.thisObject);
                            }
                        }
                    });
                } catch (Throwable ignore) {
                }
            }
            android.util.Log.i(TAG, "SvipBadge: hooked " + simple);
        } catch (Throwable t) {
            android.util.Log.i(TAG, "SvipBadge: fail " + className + " " + t);
        }
    }

    private static void inject(View container) {
        try {
            if (!(container instanceof ViewGroup)) {
                return;
            }
            ViewGroup g = (ViewGroup) container;
            if (g.findViewWithTag(BADGE_TAG) != null) {
                return;
            }
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.getClass().getName().contains("PrivilegeImageView")) {
                    c.setVisibility(View.GONE);
                }
            }
            TextView tv = new TextView(g.getContext());
            tv.setTag(BADGE_TAG);
            tv.setText("SVIP·柒");
            tv.setTextSize(9);
            tv.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFD4A257);
            bg.setCornerRadius(40);
            tv.setBackground(bg);
            tv.setPadding(14, 2, 14, 2);
            tv.setGravity(android.view.Gravity.CENTER);
            g.addView(tv);
            android.util.Log.i(TAG, "SvipBadge: injected");
        } catch (Throwable ignore) {
        }
    }
}
