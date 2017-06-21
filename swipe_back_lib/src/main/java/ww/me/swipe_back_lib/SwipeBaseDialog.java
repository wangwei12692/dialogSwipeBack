package ww.me.swipe_back_lib;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;


/**
 * Created by ww on 6/19.
 */

public class SwipeBaseDialog extends Dialog implements SwipeBackHelper.SlideBackManager {

    private static String TAG = "SwipeBaseDialog";
    private SwipeBackHelper mSwipeBackHelper;

    public SwipeBaseDialog(@NonNull Context context) {
        this(context, R.style.full_screen);
    }

    public SwipeBaseDialog(@NonNull Context context, @StyleRes int themeResId) {
        super(context, R.style.full_screen);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getWindow().getDecorView().setBackgroundColor(getWindowBackgroundColor());
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = getContext().getResources().getDisplayMetrics().widthPixels;
        int height = getHostActivity().getWindow().getDecorView().getMeasuredHeight();
        lp.gravity = Gravity.BOTTOM; //重要 错位问题
        lp.height = height - getStatusBarHeight();
        getWindow().setAttributes(lp);

    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (mSwipeBackHelper == null) {
            mSwipeBackHelper = new SwipeBackHelper(this, getWindow());
        }
        return mSwipeBackHelper.processTouchEvent(ev) || super.dispatchTouchEvent(ev);
    }

    private int getWindowBackgroundColor() {
        TypedArray array = null;
        try {
            array = getContext().getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowBackground});
            return array.getColor(0, ContextCompat.getColor(getContext(), android.R.color.transparent));
        } finally {
            if (array != null) {
                array.recycle();
            }
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (mSwipeBackHelper != null) {
            mSwipeBackHelper.finishSwipeImmediately();
            mSwipeBackHelper = null;
        }

    }

    @Override
    public void onSwipeBackAnimFinished() {
        dismiss();
    }

    @Override
    public Activity getHostActivity() {
        return getOwnerActivity() == null ? (Activity) getContext() : getOwnerActivity();
    }

    @Override
    public boolean supportSlideBack() {
        return true;
    }

    @Override
    public boolean canBeSlideBack() {
        return true;
    }

    public int getStatusBarHeight() {
        int size = Resources.getSystem().getDimensionPixelSize(
                Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android"));
        return size;
    }
}
