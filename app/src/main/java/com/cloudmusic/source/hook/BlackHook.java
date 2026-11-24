package com.cloudmusic.source.hook;

import android.content.Context;

import com.google.gson.Gson;
import com.cloudmusic.source.helper.ExtraHelper;
import com.cloudmusic.source.model.UserPrivilegeBean;

import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * <pre>
 *     author : RainCat
 *     time   : 2019/10/26
 *     desc   : 黑胶，100黑胶，220音乐包
 *     version: 1.0
 * </pre>
 */

public class BlackHook {
    private static Object sRedPlus;
    private static long sRedPlusExpire;

    /**
     * 构造满级 RedPlus：剩余天数按 7 天算，UI 等级区间为 1..7，7 即满级
     */
    private static synchronized Object buildRedPlus(ClassLoader cl) {
        long now = System.currentTimeMillis();
        if (sRedPlus == null || sRedPlusExpire <= now) {
            try {
                Class<?> cls = XposedHelpers.findClass("com.netease.cloudmusic.meta.virtual.RedPlus", cl);
                Object rp = XposedHelpers.newInstance(cls);
                XposedHelpers.setLongField(rp, "expireTime", now + 7L * 86400000);
                XposedHelpers.setIntField(rp, "vipCode", 300);
                XposedHelpers.setIntField(rp, "vipLevel", 9);
                XposedHelpers.setBooleanField(rp, "isSign", true);
                XposedHelpers.setBooleanField(rp, "isSignIap", true);
                sRedPlus = rp;
                sRedPlusExpire = now + 7L * 86400000;
            } catch (Throwable ignore) {
            }
        }
        return sRedPlus;
    }

    private static boolean sLogoBridgeHooked;

    /** 按签名定位 bridge 里 "UserPrivilege -> d30.c" 的徽章类型方法，统一改成 SVIP */
    private static void hookLogoBridge(Class<?> bridgeCls, Class<?> upCls, Class<?> cCls, Object svip) {
        if (sLogoBridgeHooked) {
            return;
        }
        sLogoBridgeHooked = true;
        try {
            for (java.lang.reflect.Method m : bridgeCls.getMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && pts[0].getName().endsWith("UserPrivilege")
                        && m.getReturnType() == cCls) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            p.setResult(svip);
                        }
                    });
                }
            }
        } catch (Throwable t) {
        }
    }

    public BlackHook(Context context, int versionCode) {
        if (versionCode < 138) {
            XposedBridge.hookAllMethods(findClass("com.netease.cloudmusic.meta.Profile", context.getClassLoader()), "setUserPoint", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if ((long) XposedHelpers.callMethod(param.thisObject, "getUserId") == Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID))) {
                        XposedHelpers.callMethod(param.thisObject, "setVipType", 100);
                        XposedHelpers.callMethod(param.thisObject, "setVipProExpireTime", System.currentTimeMillis() + 31536000000L);
                        XposedHelpers.callMethod(param.thisObject, "setExpireTime", System.currentTimeMillis() + 31536000000L);
                    }
                }
            });

            //主题
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "i", XC_MethodReplacement.returnConstant(0));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "j", XC_MethodReplacement.returnConstant("免费"));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "o", XC_MethodReplacement.returnConstant(false));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "s", XC_MethodReplacement.returnConstant(false));
        } else {
            findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.getClassLoader()),
                    "fromJson", JSONObject.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            try {
                                JSONObject object = (JSONObject) param.args[0];
                                if (object == null || object.optInt("code") != 200 || object.isNull("data")) {
                                    return;
                                }
                                JSONObject data = object.getJSONObject("data");
                                if (data == null || data.isNull("userId")) {
                                    return;
                                }
                                long uid = data.optLong("userId");
                                long myUid = -1;
                                try {
                                    myUid = Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID));
                                } catch (Throwable ignore) {
                                }
                                //已知不是当前账号就不改；未记录 userId 时按当前账号处理
                                if (myUid > 0 && uid != myUid) {
                                    return;
                                }
                                long expire = System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000;

                                //associator: 100=黑胶VIP（UserPrivilege.isBlackVip 要求 vipCode==100）
                                JSONObject assoc = data.isNull("associator") ? new JSONObject() : data.getJSONObject("associator");
                                assoc.put("expireTime", expire);
                                assoc.put("vipCode", 100);
                                assoc.put("vipLevel", 9);
                                assoc.put("isSign", true);
                                assoc.put("isSignIap", true);
                                data.put("associator", assoc);

                                JSONObject musicPackage = data.isNull("musicPackage") ? new JSONObject() : data.getJSONObject("musicPackage");
                                musicPackage.put("expireTime", expire);
                                musicPackage.put("vipCode", 220);
                                musicPackage.put("vipLevel", 9);
                                musicPackage.put("isSign", true);
                                musicPackage.put("isSignIap", true);
                                data.put("musicPackage", musicPackage);

                                //redplus: 300=RedPlus(SVIP)，isRedPlus 要求 redPlus.getVipCode()==300
                                JSONObject redplus = data.isNull("redplus") ? new JSONObject() : data.getJSONObject("redplus");
                                //等级按剩余天数折算(1..7)，7 天即满级
                                redplus.put("expireTime", System.currentTimeMillis() + 7L * 86400000);
                                redplus.put("vipCode", 300);
                                redplus.put("vipLevel", 9);
                                redplus.put("isSign", true);
                                redplus.put("isSignIap", true);
                                data.put("redplus", redplus);

                                JSONObject albumVip = data.isNull("albumVip") ? new JSONObject() : data.getJSONObject("albumVip");
                                albumVip.put("expireTime", expire);
                                albumVip.put("vipCode", 400);
                                data.put("albumVip", albumVip);

                                data.put("redVipAnnualCount", 1);
                                data.put("redVipLevel", 9);
                            } catch (Throwable t) {
                            }
                        }
                    });

            //主题
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "getPoints", XC_MethodReplacement.returnConstant(0));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "getPrice", XC_MethodReplacement.returnConstant("免费"));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "isVip", XC_MethodReplacement.returnConstant(false));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "isDigitalAlbum", XC_MethodReplacement.returnConstant(false));
        }

        //账号会员判定：9.5.81 的判定和取值分散在 UserPrivilege，直接改返回值
        try {
            Class<?> upCls = findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.getClassLoader());
            //100 年，等效永久
            long expire = System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000;
            XposedBridge.hookAllMethods(upCls, "isBlackVip", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isWhateverVip", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isNormalMusicPackage", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isWhateverMusicPackage", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isAlbumVip", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isAnnualVip", XC_MethodReplacement.returnConstant(true));
            //SVIP 判定
            XposedBridge.hookAllMethods(upCls, "isSignSVip", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isSignBlackVip", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isSignMusicPackage", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "isRedPlus", XC_MethodReplacement.returnConstant(true));
            XposedBridge.hookAllMethods(upCls, "getBlackVipType", XC_MethodReplacement.returnConstant(100));
            XposedBridge.hookAllMethods(upCls, "getBlackVipExpireTime", XC_MethodReplacement.returnConstant(expire));
            //300 = RedPlus(SVIP)
            XposedBridge.hookAllMethods(upCls, "getRedPlusVipCode", XC_MethodReplacement.returnConstant(300));
            //getRedPlus(): 会员中心/侧边栏按 RedPlus.expireTime 折算剩余天数(1..7)显示等级，7=满级
            try {
                final ClassLoader cl = context.getClassLoader();
                XposedBridge.hookAllMethods(upCls, "getRedPlus", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object rp = buildRedPlus(cl);
                        if (rp != null) {
                            param.setResult(rp);
                        }
                    }
                });
            } catch (Throwable t) {
            }
            XposedBridge.hookAllMethods(upCls, "getMusicPackageType", XC_MethodReplacement.returnConstant(220));
            XposedBridge.hookAllMethods(upCls, "getMusicPackageExpireTime", XC_MethodReplacement.returnConstant(expire));
            XposedBridge.hookAllMethods(upCls, "getAlbumVipCode", XC_MethodReplacement.returnConstant(400));
            XposedBridge.hookAllMethods(upCls, "getAlbumVipExpireTime", XC_MethodReplacement.returnConstant(expire));
            XposedBridge.hookAllMethods(upCls, "getRedVipLevel", XC_MethodReplacement.returnConstant(9));
            XposedBridge.hookAllMethods(upCls, "getRedVipAnnualCount", XC_MethodReplacement.returnConstant(1));
        } catch (Throwable t) {
        }

        //去掉播放页"正在试听"提示
        try {
            Class<?> spCls = findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader());
            XposedBridge.hookAllMethods(spCls, "getFreeTrialPrivilege", XC_MethodReplacement.returnConstant(null));
            //下载权限
            XposedBridge.hookAllMethods(spCls, "getBanDownload", XC_MethodReplacement.returnConstant(0));
            XposedBridge.hookAllMethods(spCls, "getDownloadMaxbr", XC_MethodReplacement.returnConstant(999000));
            XposedBridge.hookAllMethods(spCls, "getDownloadMaxBrLevel", XC_MethodReplacement.returnConstant("lossless"));
        } catch (Throwable t) {
        }

        //Profile.userPrivilege 为空时"我的"页徽章会走"开通"分支，补一个满级对象
        try {
            Class<?> profileCls = findClass("com.netease.cloudmusic.meta.Profile", context.getClassLoader());
            final ClassLoader pcl = context.getClassLoader();
            XposedBridge.hookAllMethods(profileCls, "getUserPrivilege", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() != null) {
                        return;
                    }
                    try {
                        long uid = (long) XposedHelpers.callMethod(param.thisObject, "getUserId");
                        if (uid <= 0) {
                            return;
                        }
                        Class<?> upCls2 = XposedHelpers.findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", pcl);
                        Object up = XposedHelpers.newInstance(upCls2);
                        long expire = System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000;
                        XposedHelpers.setLongField(up, "userId", uid);
                        XposedHelpers.setIntField(up, "blackVipType", 100);
                        XposedHelpers.setLongField(up, "blackVipExpireTime", expire);
                        XposedHelpers.setIntField(up, "musicPackageType", 220);
                        XposedHelpers.setLongField(up, "musicPackageExpireTime", expire);
                        XposedHelpers.setIntField(up, "redVipLevel", 9);
                        XposedHelpers.setIntField(up, "redVipAnnualCount", 1);
                        //paymentState 位掩码：黑胶/音乐包/SVIP 的签约位全部置 1
                        XposedHelpers.setIntField(up, "paymentState", 0x1FF);
                        Object rp = buildRedPlus(pcl);
                        if (rp != null) {
                            XposedHelpers.setObjectField(up, "redPlus", rp);
                        }
                        param.setResult(up);
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable t) {
        }

        //侧边栏会员卡片文案来自 Profile.myVipInfo，直接隐藏（GONE=3）
        try {
            Class<?> profileCls = findClass("com.netease.cloudmusic.meta.Profile", context.getClassLoader());
            XposedBridge.hookAllMethods(profileCls, "getMyVipInfo", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object info = param.getResult();
                    if (info != null) {
                        try {
                            XposedHelpers.callMethod(info, "setVipHintStatus", Integer.valueOf(3));
                        } catch (Throwable ignore) {
                        }
                    }
                }
            });
            //数据写入时也清掉开通/价格文案
            XposedBridge.hookAllMethods(profileCls, "setMyVipInfo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                        try {
                            Object info = param.args[0];
                            XposedHelpers.callMethod(info, "setVipHintStatus", Integer.valueOf(3));
                            XposedHelpers.callMethod(info, "setButtonText", "");
                        } catch (Throwable t) {
                        }
                    }
                }
            });
        } catch (Throwable t) {
        }

        //「我的」页 VIP 徽章类型由 bridge 决定，拿到 bridge 实例后按签名 hook
        try {
            Class<?> cCls = findClass("d30.c", context.getClassLoader());
            //枚举常量被混淆，按声明顺序取：VIP=0, SVIP=1
            Object[] constants = cCls.getEnumConstants();
            final Object svip = constants != null && constants.length > 1 ? constants[1] : null;
            final Class<?> upCls = findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.getClassLoader());
            if (svip == null) {
            } else {
                Class<?> companionCls = XposedHelpers.findClassIfExists("d30.d$a", context.getClassLoader());
                if (companionCls == null) {
                    companionCls = XposedHelpers.findClassIfExists("d30.d$Companion", context.getClassLoader());
                }
                if (companionCls != null) {
                    XposedBridge.hookAllMethods(companionCls, "a", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object bridge = param.getResult();
                            if (bridge == null) {
                                return;
                            }
                            hookLogoBridge(bridge.getClass(), upCls, cCls, svip);
                        }
                    });
                }
            }
        } catch (Throwable t) {
        }

        //注意：不要 hook SongUrlInfo 的 isTrial/getFreeTrialInfo，
        //PlayUrlHook 依赖它们判断"是否为试听片段"，hook 掉会导致试听地址不被替换

        //云盘上传：黑胶权益，canUpload 恒 true
        try {
            Class<?> uploadCls = findClass("com.netease.cloudmusic.module.transfer.upload.music.UploadMusicObject", context.getClassLoader());
            XposedBridge.hookAllMethods(uploadCls, "canUpload", XC_MethodReplacement.returnConstant(true));
        } catch (Throwable t) {
        }

        //音质切换
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "isVipFee", XC_MethodReplacement.returnConstant(false));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getPlayMaxLevel", XC_MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getDownMaxLevel", XC_MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getFee", XC_MethodReplacement.returnConstant(0));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getPayed", XC_MethodReplacement.returnConstant(0));
        XposedBridge.hookAllMethods(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "isFee", XC_MethodReplacement.returnConstant(false));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "canShare", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "getFreeLevel", XC_MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getFlag", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        //云盘歌曲&运算0x8不等于0
                        param.setResult(((int) param.getResult() & 0x8) == 0 ? 0 : param.getResult());
                    }
                });
    }
}
