package com.cloudmusic.source.hook;

import android.content.Context;
import android.os.Bundle;

import com.cloudmusic.source.helper.ClassHelper;
import com.cloudmusic.source.helper.ExtraHelper;
import com.cloudmusic.source.helper.UserHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/17
 *     desc   : 获取账号信息
 *     version: 1.0
 * </pre>
 */

public class UserProfileHook {
    public UserProfileHook(Context context) {
        //获取用户id
        Class<?> userProfileClass = findClassIfExists("com.netease.cloudmusic.meta.Profile", context.getClassLoader());
        if (userProfileClass != null) {
            findAndHookMethod(userProfileClass, "setNickname", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    String nickName = (String) param.args[0];
                    if (nickName.equals("未登录") || nickName.length() == 0)
                        return;
                    if ((boolean) XposedHelpers.callMethod(param.thisObject, "isMe") && ExtraHelper.getExtraDate(ExtraHelper.USER_ID).equals("-1"))
                        ExtraHelper.setExtraDate(ExtraHelper.USER_ID, XposedHelpers.callMethod(param.thisObject, "getUserId"));
                }
            });
        }

        Class<?> mainActivityClass = findClassIfExists("com.netease.cloudmusic.activity.MainActivity", context.getClassLoader());
        if (mainActivityClass != null) {
            findAndHookMethod(mainActivityClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    new Thread(() -> {
                        try {
                            if (ExtraHelper.getExtraDate(ExtraHelper.COOKIE).equals("-1")) {
                                String cookie = ClassHelper.Cookie.getCookie(context);
                                if (cookie != null) {
                                    ExtraHelper.setExtraDate(ExtraHelper.COOKIE, cookie);
                                }
                            }
                            if (ExtraHelper.getExtraDate(ExtraHelper.USER_ID).equals("-1")
                                    && !ExtraHelper.getExtraDate(ExtraHelper.COOKIE).equals("-1")) {
                                UserHelper.getUserInfo();
                            }
                        } catch (Throwable t) {
                        }
                    }).start();
                }
            });
        }

        //9.5.81 的 cookie 存储类已变更，直接 hook okhttp 请求头捕获 Cookie
        try {
            Class<?> builderCls = findClassIfExists("okhttp3.Request$Builder", context.getClassLoader());
            if (builderCls != null) {
                XC_MethodHook cookieHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object nameObj = param.args[0];
                            if (!(nameObj instanceof String) || !"cookie".equalsIgnoreCase((String) nameObj)) {
                                return;
                            }
                            Object valObj = param.args[1];
                            if (!(valObj instanceof String)) {
                                return;
                            }
                            String v = (String) valObj;
                            if (v.contains("MUSIC_U=") && "-1".equals(ExtraHelper.getExtraDate(ExtraHelper.COOKIE))) {
                                ExtraHelper.setExtraDate(ExtraHelper.COOKIE, v);
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                };
                XposedBridge.hookAllMethods(builderCls, "addHeader", cookieHook);
                XposedBridge.hookAllMethods(builderCls, "header", cookieHook);
            } else {
            }
        } catch (Throwable t) {
        }

        //登录页被创建的时候说明用户数据需要被刷新
        Class<?> loginActivityClass = findClassIfExists("com.netease.cloudmusic.activity.LoginActivity", context.getClassLoader());
        if (loginActivityClass != null) {
            findAndHookMethod(loginActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    ExtraHelper.cleanUserData();
                }
            });
        }

        //获取我喜欢的歌单id
        Class<?> playListClass = findClassIfExists("com.netease.cloudmusic.meta.PlayList", context.getClassLoader());
        if (playListClass != null) {
            findAndHookMethod(playListClass, "setSpecialType", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if ((int) param.args[0] == 5 && ExtraHelper.getExtraDate(ExtraHelper.LOVE_PLAY_LIST).equals("-1"))
                        ExtraHelper.setExtraDate(ExtraHelper.LOVE_PLAY_LIST, XposedHelpers.callMethod(param.thisObject, "getId"));
                }
            });
        }
    }
}
