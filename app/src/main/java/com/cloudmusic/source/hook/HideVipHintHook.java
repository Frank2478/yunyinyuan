package com.cloudmusic.source.hook;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 伪装会员状态（UI 层）：
 * 侧边栏会员卡片文案来自 SidebarAccount 接口（服务器下发），数据层 hook 改不到，
 * 这里统一把过期/续费类文案替换为永久会员，并拦截购买页。
 */
public class HideVipHintHook {

    private static final String VIP_TITLE = "SVIP·柒";

    /** 「正在试听中」胶囊（id=hintText）及其父容器，被布局反复设回 VISIBLE，这里强制定为 GONE */
    private static volatile View sHintView;
    private static volatile View sHintParent;

    public HideVipHintHook(Context context) {
        hookText();
        hookHintVisibility();
        blockVipPage(context);
    }

    private void hookHintVisibility() {
        try {
            XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object v = param.thisObject;
                    if (v == sHintView || v == sHintParent) {
                        param.args[0] = View.GONE;
                    }
                }
            });
        } catch (Throwable t) {
        }
    }

    private void hookText() {
        try {
            XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        TextView tv = (TextView) param.thisObject;
                        CharSequence cs = tv.getText();
                        if (cs == null) {
                            return;
                        }
                        String text = cs.toString().trim();
                        int len = text.length();
                        if (len == 0 || len > 40) {
                            return;
                        }

                        //0. 播放页「免」标签：单独一个"免"字，隐藏其容器
                        if (len == 1 && "免".equals(text)) {
                            View p = (View) tv.getParent();
                            if (p != null) {
                                p.setVisibility(View.GONE);
                            }
                            return;
                        }

                        //0. 底部导航只留「首页」「我的」：隐藏「关注」
                        if ("关注".equals(text) && isBottomTab(tv)) {
                            android.view.View p = (android.view.View) tv.getParent();
                            if (p != null) {
                                p.setVisibility(android.view.View.GONE);
                            }
                            return;
                        }

                        //1. 过期/失效类标题 -> 黑胶会员
                        if (text.contains("过期") || text.contains("失效")) {
                            if (len <= 20 && !text.contains("开通") && !text.contains("立即")) {
                                tv.setText(VIP_TITLE);
                            }
                            return;
                        }

                        //2. 副标题（续费/成长值/特权/有效期）-> 整行隐藏，呈现满级无到期时间
                        if ((text.contains("续费") || text.contains("成长值") || text.contains("特权")
                                || text.contains("永久有效") || text.contains("有效期至"))
                                && !text.contains("开通") && !text.contains("立即") && !text.contains("¥")
                                && !text.contains("￥")) {
                            hide(tv, text);
                            return;
                        }

                        //3. 「我的」页 VIP 价格标签 -> 满级 SVIP
                        if (text.startsWith("VIP") && (text.contains("¥") || text.contains("￥")) && len <= 12) {
                            tv.setText(VIP_TITLE);
                            return;
                        }

                        //4. 单独的价格小标签（如 ¥4.）-> 隐藏
                        if ((text.startsWith("¥") || text.startsWith("￥")) && len <= 6 && !text.contains("开通")) {
                            hide(tv, text);
                            return;
                        }

                        //5. 试听提示：记录实例由 setVisibility hook 持续压制，避免被布局改回 VISIBLE
                        if (text.contains("正在试听")) {
                            sHintView = tv;
                            View paren = (View) tv.getParent();
                            if (paren != null) {
                                sHintParent = paren;
                                paren.setVisibility(View.GONE);
                            }
                            if (tv.getVisibility() != View.GONE) {
                                tv.setVisibility(View.GONE);
                            }
                            return;
                        }

                        //5b. 其它购买/营销按钮 -> 隐藏（有背景的按钮整体隐藏，避免残留空背景）
                        if (text.contains("开通VIP") || text.contains("开通SVIP") || text.contains("开通黑胶")
                                || text.contains("立即开通") || text.contains("续费")
                                || text.contains("特惠") || text.contains("立享优惠") || text.contains("仅需")
                                || text.contains("畅听全曲") || text.contains("SVIP仅")
                                || text.contains("畅听整月") || text.contains("VIP¥") || text.contains("一键开启")
                                || text.contains("续时长")) {
                            hideButton(tv, text);
                            return;
                        }

                        //6. 带价格的开通/续费标签（如 "¥4.8开通"）-> 隐藏
                        if ((text.contains("¥") || text.contains("￥"))
                                && (text.contains("开通") || text.contains("续费")) && len <= 14) {
                            hideButton(tv, text);
                        }
                    } catch (Throwable ignore) {
                    }
                }

                private void hide(TextView tv, String text) {
                    //「正在试听中」胶囊被布局反复显示，记录实例后由 setVisibility hook 持续压制
                    if (text.contains("试听")) {
                        sHintView = tv;
                        View p = (View) tv.getParent();
                        if (p != null) {
                            sHintParent = p;
                            p.setVisibility(View.GONE);
                        }
                    }
                    if (tv.getVisibility() == View.GONE) {
                        return;
                    }
                    tv.setVisibility(View.GONE);
                }
            });
        } catch (Throwable t) {
        }
    }

    /** 判断是否为底部导航文本（id=desc 或位于屏幕底部区域） */
    private static boolean isBottomTab(TextView tv) {
        try {
            int id = tv.getId();
            if (id != View.NO_ID) {
                String name = tv.getResources().getResourceEntryName(id);
                if ("desc".equals(name)) {
                    return true;
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            int[] loc = new int[2];
            tv.getLocationOnScreen(loc);
            int screenH = tv.getResources().getDisplayMetrics().heightPixels;
            return loc[1] > screenH * 0.88f;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 隐藏按钮整体：按钮背景 drawable 在父容器上，只隐藏文字会留下空背景 */
    private static void hideButton(TextView tv, String text) {
        View target = tv;
        try {
            android.view.ViewParent p = tv.getParent();
            int depth = 0;
            while (p instanceof ViewGroup && depth < 3) {
                ViewGroup pv = (ViewGroup) p;
                if (pv.getBackground() != null && pv.getChildCount() <= 3) {
                    target = pv;
                    break;
                }
                p = pv.getParent();
                depth++;
            }
        } catch (Throwable ignore) {
        }
        if (target.getVisibility() != View.GONE) {
            target.setVisibility(View.GONE);
        }
    }

    private void blockVipPage(Context context) {
        try {
            Class<?> vipAct = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.activity.VipActivity", context.getClassLoader());
            if (vipAct == null) {
            } else {
                XposedBridge.hookAllMethods(vipAct, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            ((android.app.Activity) param.thisObject).finish();
                        } catch (Throwable ignore) {
                        }
                    }
                });
            }
            //RN 收银台/开通页：URL 命中支付相关路由时关闭
            Class<?> rnAct = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.music.biz.rn.activity.CloudMusicRNActivity", context.getClassLoader());
            if (rnAct != null) {
                XposedBridge.hookAllMethods(rnAct, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            android.content.Intent intent = ((android.app.Activity) param.thisObject).getIntent();
                            if (intent == null) {
                                return;
                            }
                            String url = intent.getStringExtra("extra_source_url");
                            if (url == null) {
                                url = intent.getStringExtra("EXTRA_ROOT_CTRP");
                            }
                            if (url == null) {
                                return;
                            }
                            String lower = url.toLowerCase();
                            if (lower.contains("cashier") || lower.contains("vipbuy") || lower.contains("vip-buy")
                                    || lower.contains("vip_pay") || lower.contains("vip-pay")
                                    || lower.contains("pay/vip") || lower.contains("vip/pay")) {
                                ((android.app.Activity) param.thisObject).finish();
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                });
            }
            //独立的 RN 收银台 Activity
            Class<?> cashierAct = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.music.biz.rn.activity.CashierRNActivity", context.getClassLoader());
            if (cashierAct != null) {
                XposedBridge.hookAllMethods(cashierAct, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            ((android.app.Activity) param.thisObject).finish();
                        } catch (Throwable ignore) {
                        }
                    }
                });
            }
        } catch (Throwable t) {
        }
    }
}
