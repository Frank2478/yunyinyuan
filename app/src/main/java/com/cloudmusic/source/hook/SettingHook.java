package com.cloudmusic.source.hook;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cloudmusic.source.helper.ExtraHelper;
import com.cloudmusic.source.helper.HookStat;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.utils.Tools;
import com.cloudmusic.source.view.BaseDialogInputItem;
import com.cloudmusic.source.view.BaseDialogItem;
import com.cloudmusic.source.view.beauty.BeautyBannerHideView;
import com.cloudmusic.source.view.beauty.BeautyBlackHideView;
import com.cloudmusic.source.view.beauty.BeautyBubbleHideView;
import com.cloudmusic.source.view.beauty.BeautyCommentHotView;
import com.cloudmusic.source.view.beauty.BeautyKSongHideView;
import com.cloudmusic.source.view.beauty.BeautyNightModeView;
import com.cloudmusic.source.view.beauty.BeautyRotationView;
import com.cloudmusic.source.view.beauty.BeautyTabHideView;
import com.cloudmusic.source.view.beauty.BeautyTitleView;
import com.cloudmusic.source.view.proxy.ProxyCoverView;
import com.cloudmusic.source.view.proxy.ProxyFlacView;
import com.cloudmusic.source.view.proxy.ProxyGrayView;
import com.cloudmusic.source.view.proxy.ProxyHttpView;
import com.cloudmusic.source.view.proxy.ProxyMasterView;
import com.cloudmusic.source.view.proxy.ProxyOriginalView;
import com.cloudmusic.source.view.proxy.ProxyPortView;
import com.cloudmusic.source.view.proxy.ProxyPriorityView;
import com.cloudmusic.source.view.proxy.ProxyServerView;
import com.cloudmusic.source.view.proxy.ProxyTitleView;
import com.cloudmusic.source.view.setting.BeautyView;
import com.cloudmusic.source.view.setting.BlackView;
import com.cloudmusic.source.view.setting.DexView;
import com.cloudmusic.source.view.setting.FixCommentView;
import com.cloudmusic.source.view.setting.MasterView;
import com.cloudmusic.source.view.setting.ProxyView;
import com.cloudmusic.source.view.setting.SignSongDailyView;
import com.cloudmusic.source.view.setting.SignSongSelfView;
import com.cloudmusic.source.view.setting.SignView;
import com.cloudmusic.source.view.setting.TitleView;
import com.cloudmusic.source.view.setting.UpdateView;
import com.cloudmusic.source.view.setting.ListenView;
import com.cloudmusic.source.view.setting.WarnView;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

/**
 * <pre>
 *     author : RainCat
 *     time   : 2019/10/26
 *     desc   : 设置
 *     version: 1.0
 * </pre>
 */
public class SettingHook {
    public static SettingHook instance;
    private String SettingActivity;
    private String switchViewName = "";
    private boolean receiverRegistered = false;
    private Context receiverContext;
    private TextView titleView, subView;
    private LinearLayout dialogRoot, dialogProxyRoot, dialogBeautyRoot;

    private BroadcastReceiver broadcastReceiver;

    public SettingHook(Context context,int versionCode) {
        instance = this;
        //一切的前提，没这个页面连设置都进不去
        if(versionCode>=8007000)
        {
            SettingActivity="com.netease.cloudmusic.music.biz.setting.activity.SettingActivity";
        }else
        {
            SettingActivity="com.netease.cloudmusic.activity.SettingActivity";
        }
        Class<?> settingActivityClass = findClassIfExists(SettingActivity, context.getClassLoader());
        Field[] allFields = settingActivityClass.getDeclaredFields();
        for (Field field : allFields) {
            if (field.getType().getName().contains("Switch")) {
                switchViewName = field.getName();
                break;
            }
        }

        findAndHookMethod(settingActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Context c = (Context) param.thisObject;
                //注册广播
                registerBroadcastReceiver(c);
                //初始化控件
                initView(c);
            }
        });

        //9.5.81 设置页是 RN 页面，SettingActivity 不会打开，直接注册广播用于面板开关刷新
        try {
            registerBroadcastReceiver(context);
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("SettingHook registerReceiver failed: " + t);
        }
    }

    private void initView(final Context context) {
        try {
            initViewInner(context);
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("SettingHook initView failed: " + t);
            fallbackView(context);
        }
    }

    /** 9.x 布局变化时，直接把入口加到设置页顶部 */
    private void fallbackView(final Context context) {
        try {
            android.view.View decor = ((android.app.Activity) context).getWindow().getDecorView();
            android.view.View content = decor.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) {
                de.robv.android.xposed.XposedBridge.log("SettingHook fallback: no content view");
                return;
            }
            TextView tv = new TextView(context);
            tv.setText("云音源");
            tv.setTextSize(17);
            tv.setTextColor(0xFF3B7BFF);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setBackgroundColor(0xF21C1C1E);
            tv.setPadding(80, 60, 80, 60);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSettingDialog(context);
                }
            });
            ((ViewGroup) content).addView(tv);
            tv.bringToFront();
            de.robv.android.xposed.XposedBridge.log("SettingHook fallback: added");
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("SettingHook fallback failed: " + t);
        }
    }

    private void initViewInner(final Context context) {
        TextView originalText = null;
        //获取开关控件
        View switchCompat = (View) XposedHelpers.getObjectField(context, switchViewName);
        //获取开关控件爸爸
        ViewGroup parent = (ViewGroup) switchCompat.getParent();
        //获取开关控件爷爷
        ViewGroup grandparent = (ViewGroup) parent.getParent();

        LinearLayout linearLayout = new LinearLayout(context);
        ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
        linearLayout.setLayoutParams(layoutParams);
        linearLayout.setBackground(parent.getBackground());
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        grandparent.addView(linearLayout, 0);

        titleView = new TextView(context);
        linearLayout.addView(titleView);
        subView = new TextView(context);
        linearLayout.addView(subView);
        refresh();
        start:
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof TextView) {
                originalText = (TextView) parent.getChildAt(i);
                break;
            } else if (parent.getChildAt(i) instanceof ViewGroup) {
                for (int j = 0; j < ((ViewGroup) parent.getChildAt(i)).getChildCount(); j++) {
                    if (((ViewGroup) parent.getChildAt(i)).getChildAt(j) instanceof TextView) {
                        originalText = (TextView) ((ViewGroup) parent.getChildAt(i)).getChildAt(j);
                        break start;
                    }
                }
            }
        }

        if (originalText != null) {
            titleView.setTextColor(originalText.getTextColors());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalText.getTextSize());
            titleView.setPadding(originalText.getPaddingLeft() == 0 ? Tools.dp2px(context, 10) : originalText.getPaddingLeft(), 0, 0, 0);
            subView.setTextColor(originalText.getTextColors());
            subView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (int) (originalText.getTextSize() / 3.0 * 2.0));
        }

        linearLayout.setOnClickListener(view -> showSettingDialog(context));
    }

    @SuppressLint("SetTextI18n")
    private void refresh() {
        //RN 入口下没有原生标题控件，直接跳过，避免 NPE 导致崩溃重启
        if (titleView == null || subView == null) {
            return;
        }
        titleView.setText("云音源");
        String userId = ExtraHelper.getExtraDate(ExtraHelper.USER_ID);
        if (userId == null || userId.equals("-1")) {
            subView.setText("（USERID获取失败）");
        } else if (!Boolean.TRUE.equals(SettingHelper.getInstance().getSetting(SettingHelper.master_key)))
            subView.setText("（已关闭）");
        else if ("1".equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS)))
            subView.setText("（UnblockNeteaseMusic正在运行）");
        else
            subView.setText("（UnblockNeteaseMusic停止运行）");
    }

    private void registerBroadcastReceiver(final Context context) {
        if (receiverRegistered) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SettingHelper.refresh_setting);
        intentFilter.addAction(SettingHelper.proxy_setting);
        intentFilter.addAction(SettingHelper.beauty_setting);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getAction().equals(SettingHelper.refresh_setting)) {
                    if (dialogRoot == null) {
                        return;
                    }
                    for (int i = 0; i < dialogRoot.getChildCount(); i++) {
                        if (dialogRoot.getChildAt(i) instanceof BaseDialogItem)
                            ((BaseDialogItem) dialogRoot.getChildAt(i)).refresh();
                    }
                    if (dialogProxyRoot != null)
                        for (int i = 0; i < dialogProxyRoot.getChildCount(); i++) {
                            if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogProxyRoot.getChildAt(i)).refresh();
                            else if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogInputItem)
                                ((BaseDialogInputItem) dialogProxyRoot.getChildAt(i)).refresh();
                        }
                    if (dialogBeautyRoot != null)
                        for (int i = 0; i < dialogBeautyRoot.getChildCount(); i++) {
                            if (dialogBeautyRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogBeautyRoot.getChildAt(i)).refresh();
                        }
                } else if (intent.getAction().equals(SettingHelper.proxy_setting)) {
                    showProxyDialog(dialogContext(context));
                } else if (intent.getAction().equals(SettingHelper.beauty_setting)) {
                    showBeautyDialog(dialogContext(context));
                }
            }
        };
        context.registerReceiver(broadcastReceiver, intentFilter);
        receiverRegistered = true;
        receiverContext = context;
    }

    /** 弹窗必须用 Activity context，Application context 会抛 BadTokenException */
    private Context dialogContext(Context fallback) {
        if (dialogRoot != null) {
            return dialogRoot.getContext();
        }
        return fallback;
    }

    public void showSettingDialog(final Context context) {
        //兜底：Application 阶段注册失败时，这里用 Activity context 再注册一次
        try {
            registerBroadcastReceiver(context);
        } catch (Throwable ignore) {
        }
        dialogRoot = new BaseDialogItem(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(dialogRoot);

        MasterView masterView = new MasterView(context);
        DexView dexView = new DexView(context);
        dexView.setBaseOnView(masterView);
        WarnView warnView = new WarnView(context);
        warnView.setBaseOnView(masterView);
        BlackView blackView = new BlackView(context);
        blackView.setBaseOnView(masterView);
        ListenView listenView = new ListenView(context);
        listenView.setBaseOnView(masterView);
        FixCommentView fixCommentView = new FixCommentView(context);
        fixCommentView.setBaseOnView(masterView);
        UpdateView updateView = new UpdateView(context);
        updateView.setBaseOnView(masterView);
        SignView signView = new SignView(context);
        signView.setBaseOnView(masterView);
        SignSongDailyView signSongDailyView = new SignSongDailyView(context);
        signSongDailyView.setBaseOnView(masterView);
        SignSongSelfView signSongSelfView = new SignSongSelfView(context);
        signSongSelfView.setBaseOnView(masterView);
        ProxyView proxyView = new ProxyView(context);
        proxyView.setBaseOnView(masterView);
        BeautyView beautyView = new BeautyView(context);
        beautyView.setBaseOnView(masterView);

        dialogRoot.addView(new TitleView(context));
        //Hook 成功率
        TextView hookStatView = new TextView(context);
        hookStatView.setText(HookStat.text());
        hookStatView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hookStatView.setTextColor(0xFF888888);
        int hp = Tools.dp2px(context, 20);
        hookStatView.setPadding(hp, 0, hp, Tools.dp2px(context, 8));
        dialogRoot.addView(hookStatView);
        dialogRoot.addView(masterView);
        dialogRoot.addView(dexView);
        dialogRoot.addView(warnView);
        dialogRoot.addView(blackView);
        dialogRoot.addView(listenView);
        dialogRoot.addView(fixCommentView);
        dialogRoot.addView(updateView);
        dialogRoot.addView(signView);
        dialogRoot.addView(signSongDailyView);
        dialogRoot.addView(signSongSelfView);
        dialogRoot.addView(proxyView);
        dialogRoot.addView(beautyView);
        new AlertDialog.Builder(context)
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("确定", (dialogInterface, i) -> refresh())
                .setNegativeButton("重启网易云", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showProxyDialog(final Context context) {
        dialogProxyRoot = new BaseDialogItem(context);
        dialogProxyRoot.setOrientation(LinearLayout.VERTICAL);
        ProxyMasterView proxyMasterView = new ProxyMasterView(context);
        ProxyCoverView proxyCoverView = new ProxyCoverView(context);
        proxyCoverView.setBaseOnView(proxyMasterView);
        ProxyServerView ProxyServerView = new ProxyServerView(context);
        ProxyServerView.setBaseOnView(proxyMasterView);
        ProxyPriorityView proxyPriorityView = new ProxyPriorityView(context);
        proxyPriorityView.setBaseOnView(proxyMasterView);
        ProxyFlacView proxyFlacView = new ProxyFlacView(context);
        proxyFlacView.setBaseOnView(proxyMasterView);
        ProxyGrayView proxyGrayView = new ProxyGrayView(context);
        proxyGrayView.setBaseOnView(proxyMasterView);
        ProxyHttpView proxyHttpView = new ProxyHttpView(context);
        proxyHttpView.setBaseOnView(proxyMasterView);
        ProxyPortView proxyPortView = new ProxyPortView(context);
        proxyPortView.setBaseOnView(proxyMasterView);
        ProxyOriginalView proxyOriginalView = new ProxyOriginalView(context);
        proxyOriginalView.setBaseOnView(proxyMasterView);

        dialogProxyRoot.addView(new ProxyTitleView(context));
        dialogProxyRoot.addView(proxyMasterView);
        dialogProxyRoot.addView(proxyCoverView);
        dialogProxyRoot.addView(ProxyServerView);
        dialogProxyRoot.addView(proxyPriorityView);
        dialogProxyRoot.addView(proxyFlacView);
        dialogProxyRoot.addView(proxyGrayView);
        dialogProxyRoot.addView(proxyHttpView);
        dialogProxyRoot.addView(proxyPortView);
        dialogProxyRoot.addView(proxyOriginalView);
        new AlertDialog.Builder(context)
                .setView(dialogProxyRoot)
                .setCancelable(true)
                .setPositiveButton("仅保存", (dialogInterface, i) -> refresh())
                .setNegativeButton("保存并重启", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showBeautyDialog(final Context context) {
        dialogBeautyRoot = new BaseDialogItem(context);
        dialogBeautyRoot.setOrientation(LinearLayout.VERTICAL);
        dialogBeautyRoot.addView(new BeautyTitleView(context));
        dialogBeautyRoot.addView(new BeautyNightModeView(context));
        dialogBeautyRoot.addView(new BeautyTabHideView(context));
        dialogBeautyRoot.addView(new BeautyBannerHideView(context));
        dialogBeautyRoot.addView(new BeautyBubbleHideView(context));
        dialogBeautyRoot.addView(new BeautyKSongHideView(context));
        dialogBeautyRoot.addView(new BeautyBlackHideView(context));
        dialogBeautyRoot.addView(new BeautyRotationView(context));
        dialogBeautyRoot.addView(new BeautyCommentHotView(context));
        new AlertDialog.Builder(context)
                .setView(dialogBeautyRoot)
                .setCancelable(true)
                .setPositiveButton("仅保存", (dialogInterface, i) -> refresh())
                .setNegativeButton("保存并重启", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void restartApplication(Context context) {
        ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoListist = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessInfoListist) {
            if (runningAppProcessInfo.processName.contains(":play")) {
                android.os.Process.killProcess(runningAppProcessInfo.pid);
                break;
            }
        }
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
