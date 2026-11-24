package com.cloudmusic.source.view.proxy;

import android.content.Context;
import android.util.AttributeSet;

import com.cloudmusic.source.helper.ScriptHelper;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.utils.Tools;
import com.cloudmusic.source.view.BaseDialogItem;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/09/10
 *     desc   : 脚本释放
 *     version: 1.0
 * </pre>
 */

public class ProxyCoverView extends BaseDialogItem {
    public ProxyCoverView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ProxyCoverView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProxyCoverView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_cover_title;
        sub = SettingHelper.proxy_cover_sub;
        key = SettingHelper.proxy_cover_key;
        setData(false, false);

        setOnClickListener(view -> {
            ScriptHelper.initScript(context, true);
            if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)
                    && !SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)) {
                Tools.showToastOnLooper(context, "操作成功，脚本即将重新启动");
            } else {
                Tools.showToastOnLooper(context, "操作成功");
            }
            ScriptHelper.startScript();
        });
    }
}
