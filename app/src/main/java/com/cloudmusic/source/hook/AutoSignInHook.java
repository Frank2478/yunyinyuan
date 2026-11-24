package com.cloudmusic.source.hook;

import android.content.Context;

import com.cloudmusic.source.helper.ExtraHelper;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.helper.SignSongHelper;
import com.cloudmusic.source.utils.Tools;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2020/09/26
 *     desc   : 自动签到hook（9.5.81 适配）
 *     version: 1.1
 * </pre>
 */

public class AutoSignInHook {

    public AutoSignInHook(Context context, int versionCode) {
        //MainActivity 每次启动检查当天是否已签到
        findAndHookMethod("com.netease.cloudmusic.activity.MainActivity", context.getClassLoader(),
                "onStart", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Context ctx = (Context) param.thisObject;
                        new Thread(() -> checkAndSign(ctx)).start();
                    }
                });

        //Profile 显示"已签到"
        Class<?> profileCls = findClassIfExists("com.netease.cloudmusic.meta.Profile", context.getClassLoader());
        if (profileCls != null) {
            findAndHookMethod(profileCls, "isMobileSign", XC_MethodReplacement.returnConstant(true));
        }
    }

    private void checkAndSign(Context context) {
        try {
            if (!SettingHelper.getInstance().isEnable(SettingHelper.sign_key)) {
                return;
            }
            String userId = ExtraHelper.getExtraDate(ExtraHelper.USER_ID);
            String cookie = ExtraHelper.getExtraDate(ExtraHelper.COOKIE);
            if ("-1".equals(userId) || "-1".equals(cookie)) {
                return;
            }
            long lastSignInTime = Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.SIGN_TIME + userId));
            long todayStart = Tools.getTodayStartTime();
            if (lastSignInTime >= todayStart) {
                return;
            }
            if (!sign(context, cookie)) {
                return;
            }
            ExtraHelper.setExtraDate(ExtraHelper.SIGN_TIME + userId, System.currentTimeMillis());
            //歌曲打卡（默认关闭，需用户手动开启）
            if (SettingHelper.getInstance().isEnable(SettingHelper.sign_song_key)) {
                long lastSongTime = Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.SIGN_SONG_TIME + userId));
                if (lastSongTime < Tools.getTodayStartTime()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            SignSongHelper.showSignStatusDialog(context, SettingHelper.sign_song_title, null);
                        } catch (Throwable t) {
                        }
                    });
                    ExtraHelper.setExtraDate(ExtraHelper.SIGN_SONG_TIME + userId, System.currentTimeMillis());
                }
            }
        } catch (Throwable t) {
        }
    }

    /**
     * 签到：type=1 为 PC 端，type=0 为移动端
     */
    private boolean sign(Context context, String cookie) {
        postDailyTask("1", cookie);
        String mobile = postDailyTask("0", cookie);
        if (mobile == null) {
            return false;
        }
        boolean ok = mobile.contains("\"code\":200") || mobile.contains("\"code\":-2");
        if (ok) {
            Tools.showToastOnLooper(context, "自动签到成功");
        }
        return ok;
    }

    /** 直接 POST https://music.163.com/api/point/dailyTask */
    private static String postDailyTask(String type, String cookie) {
        java.net.HttpURLConnection conn = null;
        java.io.InputStream is = null;
        try {
            java.net.URL url = new java.net.URL("https://music.163.com/api/point/dailyTask");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", cookie);
            java.io.DataOutputStream out = new java.io.DataOutputStream(conn.getOutputStream());
            out.writeBytes("type=" + type);
            out.flush();
            out.close();
            int code = conn.getResponseCode();
            is = code == java.net.HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            String body = sb.toString();
            return body;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Throwable ignore) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
