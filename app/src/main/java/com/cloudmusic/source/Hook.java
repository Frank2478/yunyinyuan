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
import com.cloudmusic.source.helper.HookStat;
import com.cloudmusic.source.helper.NotificationHelper;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.hook.AdAndUpdateHook;
import com.cloudmusic.source.hook.AdExtraHook;
import com.cloudmusic.source.hook.AutoSignInHook;
import com.cloudmusic.source.hook.BlackHook;
import com.cloudmusic.source.hook.CdnHook;
import com.cloudmusic.source.hook.CommentHotClickHook;
import com.cloudmusic.source.hook.DownloadMD5Hook;
import com.cloudmusic.source.hook.DownloadHook;
import com.cloudmusic.source.hook.EAPIHook;
import com.cloudmusic.source.hook.GrayHook;
import com.cloudmusic.source.hook.HideBannerHook;
import com.cloudmusic.source.hook.HideBubbleHook;
import com.cloudmusic.source.hook.HideVipHintHook;
import com.cloudmusic.source.hook.HideTabHook;
import com.cloudmusic.source.hook.InternalDialogHook;
import com.cloudmusic.source.hook.LoginFixHook;
import com.cloudmusic.source.hook.MagiskFixHook;
import com.cloudmusic.source.hook.NightModeHook;
import com.cloudmusic.source.hook.PlayerActivityHook;
import com.cloudmusic.source.hook.ProxyHook;
import com.cloudmusic.source.hook.SettingHook;
import com.cloudmusic.source.hook.UserProfileHook;
import com.cloudmusic.source.hook.ListentogetherHook;
import com.cloudmusic.source.utils.Tools;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/09/22
 *     desc   : hook入口
 *     version: 1.0
 * </pre>
 */

public class Hook {

    private interface HookTask {
        void run() throws Throwable;
    }

    private static void safe(String name, HookTask task) {
        try {
            task.run();
            HookStat.record(true, name);
        } catch (Throwable t) {
            HookStat.record(false, name);
        }
    }

    private final static String PACKAGE_NAME = "com.netease.cloudmusic";
    //进程初始化状态
    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;
    //主线程反编译dex完成后通知可以对play进程进行hook了
    private final String msg_hook_play_process = "hookPlayProcess";
    //play进程初始化完成通知主线程
    private final String msg_play_process_init_finish = "playProcessInitFinish";
    //发通知
    public static final String msg_send_notification = "sendNotification";

    public Hook(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader),
                "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        final int versionCode = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionCode;
                        //加载提示：确认模块已生效
                        try {
                            final Context appContext = context;
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    android.widget.Toast.makeText(appContext, "云音源 9.5.81 by Frank 已加载", android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Throwable ignore) {
                        }
                        //初始化仓库
                        ExtraHelper.init(context);
                        //初始化设置
                        SettingHelper.init(context);
                        //预热自有音源 API，避免首播时 DNS/TLS 冷启动过慢导致播放器切歌
                        try {
                            com.cloudmusic.source.helper.SourceHelper.warmUp();
                        } catch (Throwable ignore) {
                        }

                        final String processName = Tools.getCurrentProcessName(context);
                        if (processName.equals(PACKAGE_NAME)) {
                            //设置
                            safe("SettingHook", () -> new SettingHook(context, versionCode));
                            //9.5.81 设置页是 RN 页面，在 RN 设置页插入「云音源」入口
                            safe("RnSettingHook", () -> new com.cloudmusic.source.hook.RnSettingHook(context));
                            //9.5.81 播放地址 hook（我们的音源）
                            safe("PlayUrlHook", () -> new com.cloudmusic.source.hook.PlayUrlHook(context.getClassLoader()));
                            //账号信息（保存 userId / cookie）
                            safe("UserProfileHook", () -> new UserProfileHook(context));
                            //伪装会员状态（隐藏服务器下发的过期/试听文案）
                            safe("HideVipHintHook", () -> new HideVipHintHook(context));
                            //会员下载（下载地址接口 + 权限）
                            safe("DownloadHook", () -> new DownloadHook(context.getClassLoader()));
                            //总开关
                            if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key))
                                return;
                            //音源代理
                            // disabled-9.5.81: new ProxyHook(context, false);
                            //黑胶
                            if (SettingHelper.getInstance().isEnable(SettingHelper.black_key)) {
                                safe("BlackHook", () -> new BlackHook(context, versionCode));
                                //网络层响应改写：VIP/音效/播放模式数据
                                safe("ResponseHook", () -> new com.cloudmusic.source.hook.ResponseHook(context));
                                //鲸云音效权限放行
                                safe("AudioEffectHook", () -> new com.cloudmusic.source.hook.AudioEffectHook(context));
                                //「我的」页 SVIP 徽章（bridge 位图无法改文字，叠加标签）
                                safe("SvipBadgeHook", () -> new com.cloudmusic.source.hook.SvipBadgeHook(context));
                                deleteAdAndTinker();
                            }
                            //一起听
                            if (SettingHelper.getInstance().isEnable(SettingHelper.listen_key)) {
                                safe("ListenTogether", () -> {
                                    Class<?> roomCls = XposedHelpers.findClass(
                                            "com.netease.cloudmusic.module.listentogether.meta.RoomInfo",
                                            context.getClassLoader());
                                    XposedBridge.hookAllMethods(roomCls, "getUnlockedIdentity",
                                            de.robv.android.xposed.XC_MethodReplacement.returnConstant(true));
                                });
                            }
                            //不变灰
                            safe("GrayHook", () -> new GrayHook(context));
                            //自动签到
                            safe("AutoSignInHook", () -> new AutoSignInHook(context, versionCode));
                            //去广告与去升级
                            safe("AdAndUpdateHook", () -> new AdAndUpdateHook(context, versionCode));
                            //修复magisk冲突导致的无法读写外置sd卡
                            // disabled-9.5.81: new MagiskFixHook(context);
                            //去掉内测与听歌识曲弹窗
                            safe("InternalDialogHook", () -> new InternalDialogHook(context, versionCode));
                            //修复登录失败
                            // disabled-9.5.81: new LoginFixHook(context);
//                            new TestHook(context);
                            ClassHelper.getCacheClassList(context, versionCode, () -> {
                                //获取账号信息
                                // disabled-9.5.81: new UserProfileHook(context);
                                //网络访问：9.5.81 网络响应类已不存在，改由 PlayUrlHook/BlackHook 承担，不再启用 EAPIHook
                                //下载MD5校验
                                safe("DownloadMD5Hook", () -> new DownloadMD5Hook(context));
                                //夜间模式
                                safe("NightModeHook", () -> new NightModeHook(context, versionCode));
                                //精简tab
                                safe("TabHideHook", () -> new com.cloudmusic.source.hook.TabHideHook(context));
                                //移除Banner
                                safe("HideBannerHook", () -> new HideBannerHook(context, versionCode));
                                //隐藏小红点
                                safe("HideBubbleHook", () -> new HideBubbleHook(context));
                                //黑胶停转，隐藏K歌按钮
                                safe("PlayerActivityHook", () -> new PlayerActivityHook(context, versionCode));
                                //打开评论后优先显示最热评论
                                safe("CommentHotClickHook", () -> new CommentHotClickHook(context));
                                //绕过CDN责任链拦截器检测
                                // disabled-9.5.81: new CdnHook(context, versionCode);
                                //广告移除增强
                                safe("AdExtraHook", () -> new AdExtraHook());

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
                                    } else if (msg_send_notification.equals(intent.getAction())
                                            && SettingHelper.getInstance().isEnable(SettingHelper.warn_key)) {
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
                                            //9.5.81 无对应网络响应类，EAPIHook 由 PlayUrlHook/BlackHook 替代
                                            // disabled-9.5.81: new CdnHook(context, versionCode);
                                        });
                                    }
                                }
                            }, intentFilter);
                            context.sendBroadcast(new Intent(msg_play_process_init_finish));
                        }
                    }
                });

        //关闭tinker
        Class<?> tinkerClass = XposedHelpers.findClassIfExists("com.tencent.tinker.loader.app.TinkerApplication", lpparam.classLoader);
        if (tinkerClass != null)
            XposedBridge.hookAllConstructors(tinkerClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = 0;
                }
            });
    }

    /**
     * 删掉广告和热修复
     */
    private void deleteAdAndTinker() throws IOException {
        //广告缓存路径
        String CACHE_PATH = Environment.getExternalStorageDirectory() + "/netease/cloudmusic/Ad";
        String CACHE_PATH2 = Environment.getExternalStorageDirectory() + "/Android/data/com.netease.cloudmusic/cache/Ad";

        String TINKER_PATH = "data/data/" + PACKAGE_NAME + "/tinker";

        FileHelper.deleteDirectory(CACHE_PATH);
        FileHelper.deleteDirectory(CACHE_PATH2);


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
