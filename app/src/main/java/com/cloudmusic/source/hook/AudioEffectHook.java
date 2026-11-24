package com.cloudmusic.source.hook;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 鲸云音效权益：同 RC 做法，改写音效数据解析时的权限字段
 * - AudioEffectDetailData.setType(int) -> 8
 * - AudioEffectBrowseData$Item.setAeVipType(int) -> 0
 */
public class AudioEffectHook {

    private static final String PKG = "com.netease.cloudmusic.music.base.bridge.member.audioeffect.model.";

    public AudioEffectHook(Context context) {
        ClassLoader cl = context.getClassLoader();
        hookIntArg(cl, PKG + "AudioEffectDetailData", "setType", 8);
        hookIntArg(cl, PKG + "AudioEffectBrowseData$Item", "setAeVipType", 0);
    }

    private void hookIntArg(ClassLoader cl, String className, String method, final int value) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, cl);
            XposedHelpers.findAndHookMethod(cls, method, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = value;
                }
            });
        } catch (Throwable t) {
        }
    }
}
