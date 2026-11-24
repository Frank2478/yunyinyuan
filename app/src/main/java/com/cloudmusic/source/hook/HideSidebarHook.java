package com.cloudmusic.source.hook;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.model.SidebarEnum;

import java.lang.reflect.Method;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 侧边栏精简（9.5.81 结构）：
 * 侧边栏条目由 music.biz.sidebar.account.*ViewHolder 的 render(AccountItem, ...) 绑定，
 * 这里按二级面板勾选结果把对应条目隐藏。
 */
public class HideSidebarHook {
    private static final String PKG = "com.netease.cloudmusic.music.biz.sidebar.account.";
    private static final String[] HOLDERS = {
            "AccountAlarmViewHolder",
            "AccountBaseNormalViewHolder",
            "AccountCloudShellViewHolder",
            "AccountCreatorCenterViewHolder",
            "AccountGroupTitleViewHolder",
            "AccountLogoutViewHolder",
            "AccountMessageViewHolder",
            "AccountNormalViewHolder",
            "AccountPrivacyViewHolder",
            "AccountSBHintViewHolder",
            "AccountSimpleDescViewHolder",
            "AccountSwitchViewHolder",
            "AccountUpgradeNewFrameworkViewHolder",
            "AccountVipStatusViewHolder",
            "StopTimerViewHolder"
    };

    private HashMap<String, Boolean> sidebarSettingMap = new HashMap<>();
    private int renderCount = 0;
    private int rvCount = 0;

    public HideSidebarHook(final Context context, int versionCode) {
        try {
            Class<?> enumCls = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.music.biz.sidebar.ui.MainDrawer$DrawerItemEnum", context.getClassLoader());
            if (enumCls != null && enumCls.isEnum()) {
                SidebarEnum.setSidebarEnum(enumCls.getEnumConstants());
                sidebarSettingMap = SettingHelper.getInstance().getSidebarSetting(SidebarEnum.getSidebarEnum());
            }
        } catch (Throwable t) {
        }
        if (sidebarSettingMap == null) {
            sidebarSettingMap = new HashMap<>();
        }

        int hooked = 0;
        for (String shortName : HOLDERS) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(PKG + shortName, context.getClassLoader());
                if (cls == null) {
                    continue;
                }
                for (Method m : cls.getDeclaredMethods()) {
                    if (!"render".equals(m.getName())) {
                        continue;
                    }
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length < 1 || !pt[0].getName().endsWith("AccountItem")) {
                        continue;
                    }
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            applyVisibility(param);
                        }
                    });
                    hooked++;
                }
            } catch (Throwable ignore) {
            }
        }

        //诊断：打印所有 RecyclerView 的 adapter 类名，定位侧边栏真实渲染类
        try {
            Class<?> rv = XposedHelpers.findClassIfExists("androidx.recyclerview.widget.RecyclerView", context.getClassLoader());
            if (rv != null) {
                XposedBridge.hookAllMethods(rv, "setAdapter", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object adapter = param.args.length > 0 ? param.args[0] : null;
                            if (adapter != null && rvCount < 30) {
                                rvCount++;
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                });
            }
        } catch (Throwable ignore) {
        }
    }

    private void applyVisibility(XC_MethodHook.MethodHookParam param) {
        try {
            Object item = param.args[0];
            if (item == null) {
                return;
            }
            Object enumObj = XposedHelpers.callMethod(item, "getEnumType");
            if (enumObj == null) {
                return;
            }
            String enumName = enumObj.toString();
            boolean remove = false;
            if ("GROUP".equals(enumName)) {
                int group = (int) XposedHelpers.callMethod(item, "getGroup");
                Boolean g = sidebarSettingMap.get("GROUP" + group);
                remove = g != null && g;
            } else {
                Boolean v = sidebarSettingMap.get(enumName);
                remove = v != null && v;
            }
            if (renderCount < 15) {
                renderCount++;
            }
            View itemView = (View) XposedHelpers.getObjectField(param.thisObject, "itemView");
            if (itemView == null) {
                return;
            }
            //RecyclerView 会复用 itemView，必须双向恢复
            if (remove) {
                itemView.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    itemView.setLayoutParams(lp);
                }
            } else {
                itemView.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                if (lp != null && lp.height == 0) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    itemView.setLayoutParams(lp);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
