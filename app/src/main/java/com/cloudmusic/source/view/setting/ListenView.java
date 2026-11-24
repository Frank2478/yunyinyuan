package com.cloudmusic.source.view.setting;

import android.content.Context;
import android.util.AttributeSet;
import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;



public class ListenView extends BaseDialogItem {
    public ListenView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ListenView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ListenView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.listen_title;
        sub = SettingHelper.listen_sub;
        key = SettingHelper.listen_key;
        setData(true, SettingHelper.getInstance().getSetting(key));

        setOnClickListener(view -> {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked());
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }
}
