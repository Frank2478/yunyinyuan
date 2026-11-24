package com.cloudmusic.source.view.beauty;

import android.content.Context;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.cloudmusic.source.helper.SettingHelper;
import com.cloudmusic.source.view.BaseDialogItem;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/13
 *     desc   : 标题
 *     version: 1.0
 * </pre>
 */

public class BeautyTitleView extends BaseDialogItem {
    public BeautyTitleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BeautyTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BeautyTitleView(Context context) {
        super(context);
    }

    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        TextPaint paint = titleView.getPaint();
        paint.setFakeBoldText(true);

        title = SettingHelper.beauty_title;
        setData(false, false);
    }
}
