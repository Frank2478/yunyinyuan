package com.cloudmusic.source.view.setting;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;

/**
 * 播放音源选择：自动 / gdstudio（无损） / qijieya（320K）
 * 某个音源失效时可在此手动切换，下一首生效。
 */
public class SourceSelectView extends BaseDialogItem {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_GD = "gdstudio";
    public static final String MODE_QJ = "qijieya";

    private static final String[] MODES = {MODE_AUTO, MODE_GD, MODE_QJ};
    private static final String[] LABELS = {
            "自动（gdstudio 优先，失败用 qijieya）",
            "gdstudio（无损 FLAC）",
            "qijieya（320K MP3）"
    };

    public SourceSelectView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SourceSelectView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SourceSelectView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.source_title;
        sub = subText(SettingHelper.getInstance().getSource());
        setData(false, false);

        setOnClickListener(view -> new AlertDialog.Builder(context)
                .setTitle(SettingHelper.source_title)
                .setSingleChoiceItems(LABELS, indexOf(SettingHelper.getInstance().getSource()),
                        (dialog, which) -> {
                            dialog.dismiss();
                            SettingHelper.getInstance().setSource(MODES[which]);
                            sub = subText(MODES[which]);
                            setData(false, false);
                            sendBroadcast(SettingHelper.refresh_setting);
                        })
                .setNegativeButton("取消", null)
                .show());
    }

    private static int indexOf(String mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(mode)) {
                return i;
            }
        }
        return 0;
    }

    private static String subText(String mode) {
        return "当前：" + LABELS[indexOf(mode)];
    }
}
