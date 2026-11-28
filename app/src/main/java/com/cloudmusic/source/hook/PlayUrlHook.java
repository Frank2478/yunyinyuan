package com.cloudmusic.source.hook;

import com.cloudmusic.source.helper.SourceHelper;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 9.5.81 的播放地址入口：
 * 1. HttpDataSource / ComplexDataSource 的 getPlayUrlInfo（播放主路径）
 * 2. 两者的 onlyGetPlayUrlInfo（预加载等场景）
 * 原地址为空或只是试听片段时，按请求码率换成自有音源。
 */
public class PlayUrlHook {

    private static final String[] TARGETS = {
            "com.netease.cloudmusic.module.player.datasource.HttpDataSource",
            "com.netease.cloudmusic.module.player.nmcache.ComplexDataSource"
    };

    public PlayUrlHook(final ClassLoader cl) {
        for (String name : TARGETS) {
            hookClass(cl, name);
        }
    }

    private void hookClass(ClassLoader cl, String className) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(className, cl);
            if (cls == null) {
                return;
            }
            for (Method m : cls.getDeclaredMethods()) {
                String mn = m.getName();
                Class<?>[] pt = m.getParameterTypes();
                if ("onlyGetPlayUrlInfo".equals(mn)) {
                    // (BizMusicMeta, bitrate, ...)
                    hook(m, 0, 1);
                } else if ("getPlayUrlInfo".equals(mn) && pt.length >= 3
                        && pt[0] == long.class && pt[1] == long.class) {
                    // (cloudSongUserId, musicId, bitrate, ...)
                    hook(m, 1, 2);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void hook(Method m, final int idIndex, final int brIndex) {
        try {
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    handle(param, idIndex, brIndex);
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private static void handle(XC_MethodHook.MethodHookParam param, int idIndex, int brIndex) {
        try {
            long id = -1;
            if (idIndex == 0) {
                Object meta = (param.args != null && param.args.length > 0) ? param.args[0] : null;
                if (meta != null) {
                    Object idObj = XposedHelpers.callMethod(meta, "getId");
                    if (idObj instanceof Number) {
                        id = ((Number) idObj).longValue();
                    }
                }
            } else {
                Object idObj = (param.args != null && param.args.length > idIndex) ? param.args[idIndex] : null;
                if (idObj instanceof Number) {
                    id = ((Number) idObj).longValue();
                }
            }

            int reqBr = 0;
            if (param.args != null && param.args.length > brIndex) {
                Object b = param.args[brIndex];
                if (b instanceof Number) {
                    reqBr = ((Number) b).intValue();
                }
            }

            Object info = param.getResult();
            String origin = null;
            if (info != null) {
                try {
                    Object u = XposedHelpers.callMethod(info, "getUrl");
                    if (u instanceof String) {
                        origin = (String) u;
                    }
                } catch (Throwable ignore) {
                }
            }
            boolean trial = false;
            if (info != null) {
                //优先用 isTrial()：flag 含 1024 且 freeTrialInfo 非空
                try {
                    Object t = XposedHelpers.callMethod(info, "isTrial");
                    trial = Boolean.TRUE.equals(t);
                } catch (Throwable ignore) {
                }
                //兜底：直接看 freeTrialInfo 字段
                if (!trial) {
                    try {
                        trial = XposedHelpers.callMethod(info, "getFreeTrialInfo") != null;
                    } catch (Throwable ignore) {
                    }
                }
            }

            if (id <= 0 || info == null) {
                return;
            }
            //原地址可用且不是试听片段时，保持网易云原地址
            if (!trial && origin != null && origin.length() > 0) {
                return;
            }
            SourceHelper.Result r = SourceHelper.getUrl(id, reqBr);
            if (r == null) {
                return;
            }
            XposedHelpers.callMethod(info, "setUrl", r.url);
            XposedHelpers.callMethod(info, "setCode", Integer.valueOf(200));
            XposedHelpers.callMethod(info, "setFee", Integer.valueOf(0));
            try {
                XposedHelpers.callMethod(info, "setBr", Integer.valueOf(r.br > 0 ? r.br : reqBr));
            } catch (Throwable ignore) {
            }
            try {
                if (r.size > 0) {
                    XposedHelpers.callMethod(info, "setSize", Long.valueOf(r.size));
                }
            } catch (Throwable ignore) {
            }
            try {
                XposedHelpers.callMethod(info, "setLevel", levelFor(r.br, reqBr));
            } catch (Throwable ignore) {
            }
            clearFreeTrialInfo(info);
        } catch (Throwable ignore) {
        }
    }

    private static String levelFor(int realBr, int reqBr) {
        int br = realBr > 0 ? realBr : reqBr;
        if (br >= 740000) {
            return "lossless";
        }
        if (br >= 320000) {
            return "exhigh";
        }
        if (br >= 192000) {
            return "higher";
        }
        return "standard";
    }

    /** 清掉试听标记，否则播放器仍按试听片段长度（30~60s）切歌 */
    private static void clearFreeTrialInfo(Object info) {
        try {
            for (Method m : info.getClass().getMethods()) {
                if (m.getParameterTypes().length != 1) {
                    continue;
                }
                String n = m.getName();
                if ("setFreeTrialInfo".equals(n) || "setFreeTrialPrivilege".equals(n)
                        || "setFreeTimeTrialPrivilege".equals(n)) {
                    try {
                        m.invoke(info, new Object[]{null});
                    } catch (Throwable ignore) {
                    }
                }
            }
            try {
                Object flagObj = XposedHelpers.callMethod(info, "getFlag");
                if (flagObj instanceof Integer) {
                    XposedHelpers.callMethod(info, "setFlag", ((Integer) flagObj) & ~1024);
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
    }
}
