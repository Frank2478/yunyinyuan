package com.cloudmusic.source;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;

import com.cloudmusic.source.helper.ClassHelper;
import com.cloudmusic.source.helper.ExtraHelper;
import com.cloudmusic.source.helper.FileHelper;
import com.cloudmusic.source.helper.NotificationHelper;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.hook.AdAndUpdateHook;
import com.cloudmusic.source.hook.AutoSignInHook;
import com.cloudmusic.source.hook.BlackHook;
import com.cloudmusic.source.hook.CdnHook;
import com.cloudmusic.source.hook.CommentHotClickHook;
import com.cloudmusic.source.hook.DownloadMD5Hook;
import com.cloudmusic.source.hook.EAPIHook;
import com.cloudmusic.source.hook.GrayHook;
import com.cloudmusic.source.hook.HideBannerHook;
import com.cloudmusic.source.hook.HideBubbleHook;
import com.cloudmusic.source.hook.HideSidebarHook;
import com.cloudmusic.source.hook.HideTabHook;
import com.cloudmusic.source.hook.InternalDialogHook;
import com.cloudmusic.source.hook.MagiskFixHook;
import com.cloudmusic.source.hook.PlayerActivityHook;
import com.cloudmusic.source.hook.ProxyHook;
import com.cloudmusic.source.hook.SettingHook;
import com.cloudmusic.source.hook.UserProfileHook;
import com.cloudmusic.source.utils.Tools;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hooklite {
    private final static String PACKAGE_NAME = "com.netease.cloudmusic.lite";
    //进程初始化状态
    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;
    //主线程反编译dex完成后通知可以对play进程进行hook了
    private final String msg_hook_play_process = "hookPlayProcess";
    //play进程初始化完成通知主线程
    private final String msg_play_process_init_finish = "playProcessInitFinish";
    //发通知
    public static final String msg_send_notification = "sendNotification";

    public Hooklite(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader),
                "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        int versionCode = 140;
                        //初始化仓库
                        ExtraHelper.init(context);
                        //初始化设置
                        SettingHelper.init(context);

                        final String processName = Tools.getCurrentProcessName(context);
                        if (processName.equals(PACKAGE_NAME)) {
                            //设置
                            new SettingHook(context, versionCode);
                            //总开关
                            if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key))
                                return;
                            //音源代理
                            // disabled-9.5.81: new ProxyHook(context, false);
                            //黑胶
                            if (SettingHelper.getInstance().isEnable(SettingHelper.black_key)) {
                                // disabled-9.5.81: new BlackHook(context, versionCode);
                                deleteAdAndTinker();
                            }
                            //不变灰
                            new GrayHook(context);
                            //自动签到
                            // disabled-9.5.81: new AutoSignInHook(context, versionCode);
                            //去广告与去升级
                            new AdAndUpdateHook(context, versionCode);
                            //修复magisk冲突导致的无法读写外置sd卡
                            // disabled-9.5.81: new MagiskFixHook(context);
                            //去掉内测与听歌识曲弹窗
                            // disabled-9.5.81: new InternalDialogHook(context, versionCode);
//                            new TestHook(context);
                            ClassHelper.getCacheClassList(context, versionCode, () -> {
                                //获取账号信息
                                // disabled-9.5.81: new UserProfileHook(context);
                                //网络访问
                                new EAPIHook(context);
                                //下载MD5校验
                                // disabled-9.5.81: new DownloadMD5Hook(context);
                                //精简tab
                                // disabled-9.5.81: new HideTabHook(context, versionCode);
                                //精简侧边栏
                                new HideSidebarHook(context, versionCode);
                                //移除Banner
                                new HideBannerHook(context, versionCode);
                                //隐藏小红点
                                new HideBubbleHook(context);
                                //打开评论后优先显示最热评论
                                new CommentHotClickHook(context);
                                //黑胶停转，隐藏K歌按钮
                                new PlayerActivityHook(context, versionCode);
                                // disabled-9.5.81: new CdnHook(context, versionCode);

                                mainProcessInit = true;
                                if (mainProcessInit && playProcessInit)
                                    context.sendBroadcast(new Intent(msg_hook_play_process));
                            });
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_play_process_init_finish);
                            intentFilter.addAction(msg_send_notification);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_play_process_init_finish.equals(intent.getAction())) {
                                        playProcessInit = true;
                                        if (mainProcessInit && playProcessInit)
                                            context.sendBroadcast(new Intent(msg_hook_play_process));
                                    } else if (msg_send_notification.equals(intent.getAction())) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            NotificationHelper.getInstance(context).sendUnLockNotification(context, intent.getIntExtra("code", 0x10),
                                                    intent.getStringExtra("title"), intent.getStringExtra("title"), intent.getStringExtra("message"));
                                        XposedBridge.log(intent.getStringExtra("title") + "：" + intent.getStringExtra("message"));
                                    }
                                }
                            }, intentFilter);
                        } else if (processName.equals(PACKAGE_NAME + ":play") && SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                            //音源代理
                            // disabled-9.5.81: new ProxyHook(context, true);
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_hook_play_process);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_hook_play_process.equals(intent.getAction())) {
                                        ClassHelper.getCacheClassList(context, versionCode, () -> {
                                            new EAPIHook(context);
                                            // disabled-9.5.81: new CdnHook(context, versionCode);
                                        });
                                    }
                                }
                            }, intentFilter);
                            context.sendBroadcast(new Intent(msg_play_process_init_finish));
                        }
                    }
                });
    }

    private void deleteAdAndTinker() throws IOException {
        //广告缓存路径
        String CACHE_PATH3 = Environment.getExternalStorageDirectory() + "/netease/cloudmusic/lite/Ad";
        String CACHE_PATH4 = Environment.getExternalStorageDirectory() + "/Android/data/com.netease.cloudmusic.lite/cache/Ad";
        String TINKER_PATH = "data/data/" + PACKAGE_NAME + "/tinker";

        FileHelper.deleteDirectory(CACHE_PATH3);
        FileHelper.deleteDirectory(CACHE_PATH4);

        File tinkerFile = new File(TINKER_PATH);
        if (tinkerFile.exists() && tinkerFile.isDirectory())
            FileHelper.deleteDirectory(TINKER_PATH);
        if (!tinkerFile.exists())
            tinkerFile.createNewFile();

        String command = "chmod 000 " + tinkerFile.getAbsolutePath();
        Runtime runtime = Runtime.getRuntime();
        runtime.exec(command);
    }
}