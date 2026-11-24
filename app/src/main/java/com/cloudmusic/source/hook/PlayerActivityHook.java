package com.cloudmusic.source.hook;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ViewFlipper;

import com.cloudmusic.source.helper.SettingHelper;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/11/16
 *     desc   : 播放页hook
 *     version: 1.0
 * </pre>
 */

public class PlayerActivityHook {
    public PlayerActivityHook(Context context, final int versionCode) {
        //隐藏播放页顶部「免」标签（标题区域右侧自绘的 FrameLayout）
        XC_MethodHook freeTagHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final android.app.Activity act = (android.app.Activity) param.thisObject;
                act.getWindow().getDecorView().postDelayed(() -> hideFreeTag(act), 800);
            }
        };
        //打印提示容器类结构，便于定位「免」标签的来源
        try {
            Class<?> hintCls = XposedHelpers.findClass(
                    "com.netease.cloudmusic.module.hint.view.CustomHintContainer", context.getClassLoader());
            for (java.lang.reflect.Method m : hintCls.getDeclaredMethods()) {
            }
            for (java.lang.reflect.Field f : hintCls.getDeclaredFields()) {
            }
        } catch (Throwable t) {
        }

        try {
            XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.activity.PlayerActivity", context.getClassLoader()),
                    "onResume", freeTagHook);
        } catch (Throwable t) {
        }

        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.activity.PlayerActivity", context.getClassLoader()),
                "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        final android.app.Activity act = (android.app.Activity) param.thisObject;
                        act.getWindow().getDecorView().postDelayed(() -> hideFreeTag(act), 800);
                        //运行时判断开关，打开后无需重启
                        final boolean black = SettingHelper.getInstance().isEnable(SettingHelper.beauty_black_hide_key);
                        final boolean ksong = SettingHelper.getInstance().isEnable(SettingHelper.beauty_ksong_hide_key);
                        ViewFlipper playerDiscViewFlipper = null;
                        for (Field field : param.thisObject.getClass().getDeclaredFields()) {
                            if (black && field.getType().getName().contains("PlayerDiscViewFlipper")) {
                                field.setAccessible(true);
                                playerDiscViewFlipper = (ViewFlipper) field.get(param.thisObject);
                            }
                            if (ksong && field.getType().getName().contains("ImageView")) {
                                field.setAccessible(true);
                                ImageView imageView = (ImageView) field.get(param.thisObject);
                                if (imageView.getContentDescription() != null) {
                                    if (imageView.getContentDescription().toString().contains("音街")
                                            || imageView.getContentDescription().toString().contains("铃声")) {
                                        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                                        layoutParams.width = 0;
                                        layoutParams.height = 0;
                                        imageView.setLayoutParams(layoutParams);
                                        if (imageView.getParent() != null) {
                                            View parent = (View) imageView.getParent();
                                            layoutParams = parent.getLayoutParams();
                                            layoutParams.width = 0;
                                            layoutParams.height = 0;
                                            parent.setLayoutParams(layoutParams);
                                        }
                                    }
                                }
                            }
                        }
                        if (playerDiscViewFlipper == null)
                            return;
                        for (int i = 0; i < playerDiscViewFlipper.getChildCount(); i++) {
                            View coverView = null, imageView = null;
                            RelativeLayout rotationRelativeLayout = (RelativeLayout) playerDiscViewFlipper.getChildAt(i);
                            for (int j = 0; j < rotationRelativeLayout.getChildCount(); j++) {
                                if (rotationRelativeLayout.getChildAt(j).getClass().getName().contains("ImageView")
                                        && rotationRelativeLayout.getChildAt(j).getClass().getName().contains("android"))
                                    coverView = rotationRelativeLayout.getChildAt(j);
                                else {
                                    imageView = rotationRelativeLayout.getChildAt(j);
                                }
                            }
                            if (coverView != null && imageView != null) {
                                final View coverViewF = coverView;
                                final View imageViewF = imageView;
                                coverView.post(() -> {
                                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) imageViewF.getLayoutParams();
                                    layoutParams.height = coverViewF.getHeight();
                                    layoutParams.width = coverViewF.getWidth();
                                    imageViewF.setLayoutParams(layoutParams);
                                    coverViewF.setVisibility(View.INVISIBLE);
                                });
                            }
                        }
                    }
                });

        if (versionCode >= 123) {
                XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.ui.RotationRelativeLayout$AnimationHolder", context.getClassLoader()), "prepareAnimation", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        if (!SettingHelper.getInstance().isEnable(SettingHelper.beauty_rotation_key)) {
                            return;
                        }
                        param.setResult(null);
                    }
                });
            } else {
                XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.ui.RotationRelativeLayout$a", context.getClassLoader()), "b", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        if (!SettingHelper.getInstance().isEnable(SettingHelper.beauty_rotation_key)) {
                            return;
                        }
                        param.setResult(null);
                    }
                });
            }
    }

    /** 隐藏「免」标签：toolbar 菜单区里的提示容器（CustomHintContainer） */
    private static void hideFreeTag(android.app.Activity act) {
        try {
            int toolbarId = act.getResources().getIdentifier("toolbar", "id", act.getPackageName());
            if (toolbarId == 0) {
                return;
            }
            View toolbar = act.getWindow().getDecorView().findViewById(toolbarId);
            if (!(toolbar instanceof ViewGroup)) {
                return;
            }
            ViewGroup tg = (ViewGroup) toolbar;
            for (int i = 0; i < tg.getChildCount(); i++) {
                View child = tg.getChildAt(i);
                if (child.getClass().getName().contains("ActionMenuView")) {
                    hideHintContainers((ViewGroup) child);
                }
            }
        } catch (Throwable t) {
        }
    }

    /** 递归隐藏 CustomHintContainer 中的「免」小标签（宽度窄），保留分享等图标 */
    private static void hideHintContainers(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getClass().getName().contains("CustomHintContainer")) {
                //「免」标签约 65px，分享图标容器约 156px，按宽度区分避免误伤
                if (c.getWidth() > 0 && c.getWidth() < 100 && c.getVisibility() != View.GONE) {
                    c.setVisibility(View.GONE);
                }
            } else if (c instanceof ViewGroup) {
                hideHintContainers((ViewGroup) c);
            }
        }
    }
}
