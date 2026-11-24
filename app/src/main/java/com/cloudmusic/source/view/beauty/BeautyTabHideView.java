package com.cloudmusic.source.view.beauty;

import android.content.Context;
import android.util.AttributeSet;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/10/21
 *     desc   : tab隐藏
 *     version: 1.0
 * </pre>
 */

public class BeautyTabHideView extends BaseDialogItem {
    public BeautyTabHideView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BeautyTabHideView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BeautyTabHideView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.beauty_tab_hide_title + "（底部仅保留首页/我的）";
        sub = SettingHelper.beauty_tab_hide_sub;
        key = SettingHelper.beauty_tab_hide_key;
        setData(true, SettingHelper.getInstance().getSetting(key));
        setEnabled(true);

        setOnClickListener(view -> {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked());
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }
}
