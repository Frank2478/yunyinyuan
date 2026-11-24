package com.cloudmusic.source.view.beauty;

import android.content.Context;
import android.util.AttributeSet;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/10/23
 *     desc   : 移除Banner
 *     version: 1.0
 * </pre>
 */

public class BeautyBannerHideView extends BaseDialogItem {
    public BeautyBannerHideView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BeautyBannerHideView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BeautyBannerHideView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.beauty_banner_hide_title;
        key = SettingHelper.beauty_banner_hide_key;
        setData(true, SettingHelper.getInstance().getSetting(key));
        setEnabled(true);

        setOnClickListener(view -> {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked());
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }

}
