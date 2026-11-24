package com.cloudmusic.source.hook;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 网络响应改写（对齐 RC 思路）：
 * hook com.netease.cloudmusic.core.jsbridge.rpc.handler.impl.o$c.r(String,int,Map,HttpUrl,wg.b,String)
 * args[0]=响应体字符串, args[3]=okhttp3.HttpUrl
 * 在响应进入业务层之前改掉 VIP / 音效 / 播放模式等数据，UI 自然显示满级会员
 */
public class ResponseHook {

    private static final String TARGET = "com.netease.cloudmusic.core.jsbridge.rpc.handler.impl.o$c";

    public ResponseHook(Context context) {
        try {
            Class<?> cls = XposedHelpers.findClass(TARGET, context.getClassLoader());
            XposedBridge.hookAllMethods(cls, "r", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handle(param);
                }
            });
        } catch (Throwable t) {
        }
    }

    private static void handle(XC_MethodHook.MethodHookParam param) {
        try {
            if (param.args.length < 4 || !(param.args[0] instanceof String) || param.args[3] == null) {
                return;
            }
            String body = (String) param.args[0];
            String url = param.args[3].toString();
            if (url == null || !(url.contains("/eapi/") || url.contains("/xeapi/"))) {
                return;
            }
            String out = body;
            if (url.contains("music-vip-membership/client/vip/info")
                    || url.contains("music-vip-membership/front/vip/info")) {
                out = patchVipInfo(body);
            } else if (url.contains("vipauth/app")) {
                if (body.contains("无权限使用该权益") || body.contains("\"code\":1002")) {
                    out = "{\"code\":200,\"data\":true,\"message\":\"\"}";
                }
            } else if (url.contains("sound/mobile") || url.contains("sound/queryAllSound")) {
                out = body
                        .replaceAll("\"type\":\\d+", "\"type\":1")
                        .replaceAll("\"canUse\":false", "\"canUse\":true")
                        .replaceAll("\"canNotUseReasonCode\":\\d+", "\"canNotUseReasonCode\":0")
                        .replaceAll("\"canUseType\":\\d+", "\"canUseType\":1");
            } else if (url.contains("playermode/check")) {
                out = patchPlayerMode(body);
            } else if (url.contains("delivery/batch-deliver?positionIds=")) {
                out = clearDelivery(body);
            }
            if (out != body) {
                param.args[0] = out;
            }
        } catch (Throwable t) {
        }
    }

    private static String patchVipInfo(String body) {
        try {
            JSONObject jso = new JSONObject(body);
            JSONObject data = jso.optJSONObject("data");
            if (data == null) {
                data = new JSONObject();
                jso.put("data", data);
            }
            long expire = System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000;

            JSONObject assoc = data.optJSONObject("associator");
            if (assoc == null) {
                assoc = new JSONObject();
                data.put("associator", assoc);
            }
            assoc.put("expireTime", expire);
            assoc.put("vipCode", 100);
            assoc.put("vipLevel", 9);
            assoc.put("isSign", true);
            assoc.put("isSignIap", true);

            JSONObject musicPackage = data.optJSONObject("musicPackage");
            if (musicPackage == null) {
                musicPackage = new JSONObject();
                data.put("musicPackage", musicPackage);
            }
            musicPackage.put("expireTime", expire);
            musicPackage.put("vipCode", 220);
            musicPackage.put("vipLevel", 9);
            musicPackage.put("isSign", true);
            musicPackage.put("isSignIap", true);

            JSONObject redplus = data.optJSONObject("redplus");
            if (redplus == null) {
                redplus = new JSONObject();
                data.put("redplus", redplus);
            }
            //等级按剩余天数折算(1..7)，7 天即满级
            redplus.put("expireTime", System.currentTimeMillis() + 7L * 86400000);
            redplus.put("vipCode", 300);
            redplus.put("vipLevel", 9);
            redplus.put("isSign", true);
            redplus.put("isSignIap", true);

            data.put("redVipAnnualCount", 1);
            data.put("redVipLevel", 9);
            return jso.toString();
        } catch (Throwable t) {
            return body;
        }
    }

    private static String patchPlayerMode(String body) {
        try {
            JSONObject jso = new JSONObject(body);
            JSONArray arr = jso.optJSONArray("data");
            if (arr == null) {
                return body;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null) {
                    item.put("available", true);
                    item.put("unavailableReason", JSONObject.NULL);
                    item.put("actionUrl", JSONObject.NULL);
                }
            }
            jso.put("data", arr);
            return jso.toString();
        } catch (Throwable t) {
            return body;
        }
    }

    private static String clearDelivery(String body) {
        try {
            JSONObject jso = new JSONObject(body);
            jso.put("data", new JSONObject());
            return jso.toString();
        } catch (Throwable t) {
            return body;
        }
    }
}
