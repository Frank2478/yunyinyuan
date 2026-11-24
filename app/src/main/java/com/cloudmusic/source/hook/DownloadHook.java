package com.cloudmusic.source.hook;

import com.cloudmusic.source.helper.SourceHelper;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 会员下载：
 * 1. NeteaseMusicApiImpl.k2 —— song/enhance/download/url/v1，返回 Object[]
 *    [0]=code [1]=url [2]=br [3]=size [4]=md5 [5]=type [6]=fee [7]=payed [8]=flag ...
 * 2. NeteaseMusicApiImpl.v(long) / W7(...) —— 返回下载权限 SongPrivilege
 */
public class DownloadHook {
    private static final String API_IMPL = "com.netease.cloudmusic.api.impl.NeteaseMusicApiImpl";

    public DownloadHook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(API_IMPL, cl);
            if (cls == null) {
                log("class not found");
                return;
            }
            //k2 由 F0 内部调用，只 hook F0 即可避免双层替换互相覆盖
            try {
                XposedHelpers.findAndHookMethod(cls, "F0", long.class, int.class, long.class, String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                log("F0 raw=" + describe(param.getResult()));
                                handleDownload(param, 0, 1);
                                log("F0 after=" + describe(param.getResult()));
                            }
                        });
                log("F0 hooked");
            } catch (Throwable t) {
                log("F0 failed " + t);
            }
            try {
                //k2 内部 code != 200 时会调 ba(code) 抛异常，屏蔽掉让我们能接管返回值
                XposedHelpers.findAndHookMethod(cls, "ba", int.class,
                        de.robv.android.xposed.XC_MethodReplacement.returnConstant(null));
                log("ba hooked");
            } catch (Throwable t) {
                log("ba failed " + t);
            }
            try {
                XposedHelpers.findAndHookMethod(cls, "v", long.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        fixPrivilege(param.getResult());
                    }
                });
                log("v hooked");
            } catch (Throwable t) {
                log("v failed " + t);
            }
            try {
                XposedHelpers.findAndHookMethod(cls, "W7", List.class, int.class, List.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (result instanceof android.util.LongSparseArray) {
                            android.util.LongSparseArray<?> sa = (android.util.LongSparseArray<?>) result;
                            for (int i = 0; i < sa.size(); i++) {
                                fixPrivilege(sa.valueAt(i));
                            }
                        }
                    }
                });
                log("W7 hooked");
            } catch (Throwable t) {
                log("W7 failed " + t);
            }
        } catch (Throwable t) {
            log("init failed " + t);
        }

        //下载器内部：x() 取地址、f() 执行下载，抓真实失败原因
        try {
            Class<?> rCls = XposedHelpers.findClass(
                    "com.netease.cloudmusic.module.transfer.download.r", cl);
            XposedBridge.hookAllMethods(rCls, "x", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable th = param.getThrowable();
                    if (th != null) {
                        log("r.x() throw: " + th);
                        return;
                    }
                    Object res = param.getResult();
                    if (res instanceof Object[]) {
                        Object[] a = (Object[]) res;
                        log("r.x() ok url=" + a[0] + " size=" + a[4] + " md5=" + a[5]);
                    }
                }
            });
            XposedBridge.hookAllMethods(rCls, "f", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable th = param.getThrowable();
                    if (th != null) {
                        log("r.f() throw: " + th);
                    } else {
                        log("r.f() done ok");
                    }
                }
            });
            log("downloader hooked");
        } catch (Throwable t) {
            log("downloader hook failed " + t);
        }
    }

    private static String describe(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Object[]) {
            Object[] a = (Object[]) o;
            Object code = a.length > 0 ? a[0] : null;
            Object url = a.length > 1 ? a[1] : null;
            return "arr[" + a.length + "] code=" + code + " url=" + (url instanceof String ? ((String) url).length() + "c" : url);
        }
        return o.getClass().getSimpleName();
    }

    private static void handleDownload(XC_MethodHook.MethodHookParam param, int idIndex, int brIndex) {
        try {
            long songId = ((Number) param.args[idIndex]).longValue();
            int level = ((Number) param.args[brIndex]).intValue();
            Object result = param.getResult();
            int code = -1;
            String url = null;
            if (result instanceof Object[]) {
                Object[] arr = (Object[]) result;
                if (arr.length > 0 && arr[0] instanceof Integer) {
                    code = ((Integer) arr[0]).intValue();
                }
                if (arr.length > 1 && arr[1] instanceof String) {
                    url = (String) arr[1];
                }
            }
            log("k2 id=" + songId + " level=" + level + " code=" + code
                    + " url=" + (url == null ? "null" : url.length() + "chars"));
            //打印调用来源，定位重复调用
            try {
                StackTraceElement[] st = Thread.currentThread().getStackTrace();
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < Math.min(st.length, 9); i++) {
                    sb.append(st[i].getClassName()).append('.')
                            .append(st[i].getMethodName()).append(':')
                            .append(st[i].getLineNumber()).append(" <- ");
                }
                log("caller: " + sb);
            } catch (Throwable ignore) {
            }
            //正常返回且地址可用：只把 fee 清零
            if (code == 200 && url != null && url.length() > 0) {
                if (result instanceof Object[] && ((Object[]) result).length > 6) {
                    ((Object[]) result)[6] = 0;
                }
                return;
            }
            //服务器拒绝（-105 等）或地址为空：换自有音源
            SourceHelper.Result r = SourceHelper.getUrl(songId, level);
            log("download replace id=" + songId + " -> " + (r == null ? "null" : r.url));
            if (r == null) {
                return;
            }
            Object[] arr = new Object[17];
            arr[0] = 200;
            arr[1] = r.url;
            arr[2] = r.br > 0 ? r.br : level;
            //x() 里会把 [3] 强转 Long 作为文件大小、[4] 作为 md5，类型必须严格一致
            arr[3] = Long.valueOf(r.size);
            arr[4] = extractMd5(r.url);
            arr[5] = "flac";
            arr[6] = 0;
            arr[7] = 0;
            arr[8] = 0;
            arr[9] = 0.0d;
            arr[10] = r.br >= 740000 ? "lossless" : "exhigh";
            arr[11] = 0.0d;
            arr[12] = null;
            arr[13] = 0.0d;
            arr[14] = 0.0d;
            arr[15] = "";
            arr[16] = null;
            param.setResult(arr);
        } catch (Throwable t) {
            log("handleDownload failed " + t);
        }
    }

    /** 网易 CDN 文件名就是文件 md5，下载器会用它做完整性校验 */
    private static String extractMd5(String url) {
        try {
            String p = url;
            int q = p.indexOf('?');
            if (q > 0) {
                p = p.substring(0, q);
            }
            int slash = p.lastIndexOf('/');
            String name = slash >= 0 ? p.substring(slash + 1) : p;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            if (name.length() == 32 && name.matches("[0-9a-fA-F]{32}")) {
                return name;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static void fixPrivilege(Object sp) {
        if (sp == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(sp, "setDownloadMaxbr", Integer.valueOf(999000));
            XposedHelpers.callMethod(sp, "setDownloadMaxBrLevel", "lossless");
            XposedHelpers.callMethod(sp, "setBanDownload", Integer.valueOf(0));
            XposedHelpers.callMethod(sp, "setFee", Integer.valueOf(0));
            XposedHelpers.callMethod(sp, "setPayed", Integer.valueOf(0));
            XposedHelpers.callMethod(sp, "setFlag", Integer.valueOf(0));
        } catch (Throwable ignore) {
        }
    }

    private static void log(String s) {
    }
}
