package com.raiferoleplay.game.launcher.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

import com.raiferoleplay.game.launcher.MainActivity;

public final class ViewPagerWithoutSwipe extends ViewPager {
    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return false;
    }

    public ViewPagerWithoutSwipe(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }
}
