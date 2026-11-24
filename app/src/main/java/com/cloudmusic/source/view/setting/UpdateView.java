package com.cloudmusic.source.view.setting;

import android.content.Context;
import android.util.AttributeSet;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;


/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/13
 *     desc   : 去升级
 *     version: 1.0
 * </pre>
 */

public class UpdateView extends BaseDialogItem {
    public UpdateView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public UpdateView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UpdateView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.update_title;
        key = SettingHelper.update_key;
        setData(true, SettingHelper.getInstance().getSetting(key));

        setOnClickListener(view -> {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked());
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }
}
