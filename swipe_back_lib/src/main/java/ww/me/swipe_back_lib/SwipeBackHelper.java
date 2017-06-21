package ww.me.swipe_back_lib;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;


public class SwipeBackHelper extends Handler {

    private static final String TAG = "SwipeBackHelper";

    private static final String CURRENT_POINT_X = "currentPointX"; //点击事件

    private static final int MSG_ACTION_DOWN = 1; //点击事件
    private static final int MSG_ACTION_MOVE = 2; //滑动事件
    private static final int MSG_ACTION_UP = 3;  //点击结束
    private static final int MSG_SLIDE_CANCEL = 4; //开始滑动，不返回前一个页面
    private static final int MSG_SLIDE_CANCELED = 5;  //结束滑动，不返回前一个页面
    private static final int MSG_SLIDE_PROCEED = 6; //开始滑动，返回前一个页面
    private static final int MSG_SLIDE_FINISHED = 7;//结束滑动，返回前一个页面

    private static final int SHADOW_WIDTH = 50; //px 阴影宽度
    private static final int EDGE_SIZE = 20;  //dp 默认拦截手势区间
    private final SlideBackManager mSlideBackManager;
    private final FrameLayout mCurrentContentView;
    private final Window mWindow;
    private int mEdgeSize;  //px 拦截手势区间
    private boolean mIsSliding; //是否正在滑动
    private boolean mIsSlideAnimPlaying; //滑动动画展示过程中
    private float mDistanceX;  //px 当前滑动距离 （正数或0）
    private float mLastPointX;  //记录手势在屏幕上的X轴坐标
    private boolean mIsSupportSlideBack; //
    private int mTouchSlop;
    private boolean mIsInThresholdArea;
    private Activity mHostActivity;
    private ViewManager mViewManager;
    private AnimatorSet mAnimatorSet;

    /**
     * @param slideBackManager
     * @param dialogWindow
     */
    public SwipeBackHelper(SlideBackManager slideBackManager, Window dialogWindow) {
        if (slideBackManager == null || slideBackManager.getHostActivity() == null) {
            throw new RuntimeException("Neither SlideBackManager nor the method 'getHostActivity()' can be null!");
        }
        mSlideBackManager = slideBackManager;
        mHostActivity = mSlideBackManager.getHostActivity();
        mWindow = dialogWindow;
        Log.e(TAG, "SwipeBackHelper: " + mHostActivity);
        mIsSupportSlideBack = mSlideBackManager.supportSlideBack();
        mCurrentContentView = (FrameLayout) dialogWindow.findViewById(Window.ID_ANDROID_CONTENT);

        mViewManager = new ViewManager();
        mTouchSlop = ViewConfiguration.get(mHostActivity).getScaledTouchSlop();
        final float density = mHostActivity.getResources().getDisplayMetrics().density;
        mEdgeSize = (int) (EDGE_SIZE * density + 0.5f); //滑动拦截事件的区域
    }

    public boolean processTouchEvent(MotionEvent ev) {
        if (!mIsSupportSlideBack) { //不支持滑动返回，则手势事件交给View处理
            return false;
        }

        if (mIsSlideAnimPlaying) {  //正在滑动动画播放中，直接消费手势事件
            return true;
        }

        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_DOWN) {
            mLastPointX = ev.getRawX();
            mIsInThresholdArea = mLastPointX >= 0 && mLastPointX <= mEdgeSize;
        }

        if (!mIsInThresholdArea) {  //不满足滑动区域，不做处理
            return false;
        }

        final int actionIndex = ev.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                sendEmptyMessage(MSG_ACTION_DOWN);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (mIsSliding) {  //有第二个手势事件加入，而且正在滑动事件中，则直接消费事件
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                //一旦触发滑动机制，拦截所有其他手指的滑动事件
                if (actionIndex != 0) {
                    return mIsSliding;
                }

                final float curPointX = ev.getRawX();

                boolean isSliding = mIsSliding;
                if (!isSliding) {
                    if (Math.abs(curPointX - mLastPointX) < mTouchSlop) { //判断是否满足滑动
                        return false;
                    } else {
                        mIsSliding = true;
                    }
                }

                Bundle bundle = new Bundle();
                bundle.putFloat(CURRENT_POINT_X, curPointX);
                Message message = obtainMessage();
                message.what = MSG_ACTION_MOVE;
                message.setData(bundle);
                sendMessage(message);

                if (isSliding == mIsSliding) {
                    return true;
                } else {
                    MotionEvent cancelEvent = MotionEvent.obtain(ev); //首次判定为滑动需要修正事件：手动修改事件为 ACTION_CANCEL，并通知底层View
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                    mWindow.superDispatchTouchEvent(cancelEvent);
                    return true;
                }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_OUTSIDE:
                if (mDistanceX == 0) { //没有进行滑动
                    mIsSliding = false;
                    sendEmptyMessage(MSG_ACTION_UP);
                    return false;
                }

                if (mIsSliding && actionIndex == 0) { // 取消滑动 或 手势抬起 ，而且手势事件是第一手势，开始滑动动画
                    mIsSliding = false;
                    sendEmptyMessage(MSG_ACTION_UP);
                    return true;
                } else if (mIsSliding && actionIndex != 0) {
                    return true;
                }
                break;
            default:
                mIsSliding = false;
                break;
        }
        return false;
    }

    public void finishSwipeImmediately() {
        if (mIsSliding) {
            mViewManager.resetHostView();
        }

        if (mAnimatorSet != null) {
            mAnimatorSet.cancel();
        }
        removeCallbacksAndMessages(null);
        mHostActivity = null;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case MSG_ACTION_DOWN:
                // hide input method
                InputMethodManager inputMethod = (InputMethodManager) mHostActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                View view = mHostActivity.getCurrentFocus();
                if (view != null) {
                    inputMethod.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                if (!mViewManager.addViewFromHostActivity()) return;

                // add shadow view on the left of content view
                mViewManager.addShadowView();

                if (mCurrentContentView.getChildCount() >= 3) {
                    View curView = mViewManager.getDisplayView();
                    if (curView.getBackground() == null) {
                        int color = getWindowBackgroundColor();
                        curView.setBackgroundColor(color);
                    }
                }
                break;

            case MSG_ACTION_MOVE:
                final float curPointX = msg.getData().getFloat(CURRENT_POINT_X);
                onSliding(curPointX);
                break;

            case MSG_ACTION_UP:
                final int width = mHostActivity.getResources().getDisplayMetrics().widthPixels;
                if (mDistanceX == 0) {
                    if (mCurrentContentView.getChildCount() >= 3) {
                        mViewManager.removeShadowView();
                        mViewManager.resetHostView();
                    }
                } else if (mDistanceX > width / 3) {
                    sendEmptyMessage(MSG_SLIDE_PROCEED);
                } else {
                    sendEmptyMessage(MSG_SLIDE_CANCEL);
                }
                break;

            case MSG_SLIDE_CANCEL:
                startSlideAnim(true);
                break;

            case MSG_SLIDE_CANCELED:
                mDistanceX = 0;
                mIsSliding = false;
                mViewManager.removeShadowView();
                mViewManager.resetHostView();
                break;

            case MSG_SLIDE_PROCEED:
                startSlideAnim(false);
                break;

            case MSG_SLIDE_FINISHED:
                mViewManager.removeShadowView();
                mViewManager.resetHostView();

                if (mSlideBackManager != null)
                    mSlideBackManager.onSwipeBackAnimFinished();
                break;

            default:
                break;
        }
    }

    private int getWindowBackgroundColor() {
        TypedArray array = null;
        try {
            array = mHostActivity.getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowBackground});
            return array.getColor(0, Color.parseColor("#00000000"));//transparent
        } finally {
            if (array != null) {
                array.recycle();
            }
        }
    }

    /**
     * 手动处理滑动事件
     */
    private synchronized void onSliding(float curPointX) {
        final int width = mHostActivity.getResources().getDisplayMetrics().widthPixels;
        View previewActivityContentView = mViewManager.mHostContentView;
        View shadowView = mViewManager.mShadowView;
        View currentActivityContentView = mViewManager.getDisplayView();

        if (previewActivityContentView == null || currentActivityContentView == null || shadowView == null) {
            sendEmptyMessage(MSG_SLIDE_CANCELED);
            return;
        }

        final float distanceX = curPointX - mLastPointX;
        mLastPointX = curPointX;
        mDistanceX = mDistanceX + distanceX;
        if (mDistanceX < 0) {
            mDistanceX = 0;
        }

        previewActivityContentView.setX(-width / 3 + mDistanceX / 3);
        shadowView.setX(mDistanceX - SHADOW_WIDTH);
        currentActivityContentView.setX(mDistanceX);
    }

    /**
     * 开始自动滑动动画
     *
     * @param slideCanceled 是不是要返回（true则不关闭当前页面）
     */
    private void startSlideAnim(final boolean slideCanceled) {
        final View previewView = mViewManager.mHostContentView;
        final View shadowView = mViewManager.mShadowView;
        final View currentView = mViewManager.getDisplayView();

        if (previewView == null || currentView == null) {
            return;
        }

        int width = mHostActivity.getResources().getDisplayMetrics().widthPixels;
        Interpolator interpolator = new DecelerateInterpolator(2f);

        // preview view's animation
        ObjectAnimator previewViewAnim = new ObjectAnimator();
        previewViewAnim.setInterpolator(interpolator);
        previewViewAnim.setProperty(View.TRANSLATION_X);
        float preViewStart = mDistanceX / 3 - width / 3;
        float preViewStop = slideCanceled ? -width / 3 : 0;
        previewViewAnim.setFloatValues(preViewStart, preViewStop);
        previewViewAnim.setTarget(previewView);

        // shadow view's animation
        ObjectAnimator shadowViewAnim = new ObjectAnimator();
        shadowViewAnim.setInterpolator(interpolator);
        shadowViewAnim.setProperty(View.TRANSLATION_X);
        float shadowViewStart = mDistanceX - SHADOW_WIDTH;
        float shadowViewEnd = slideCanceled ? SHADOW_WIDTH : width + SHADOW_WIDTH;
        shadowViewAnim.setFloatValues(shadowViewStart, shadowViewEnd);
        shadowViewAnim.setTarget(shadowView);

        // current view's animation
        ObjectAnimator currentViewAnim = new ObjectAnimator();
        currentViewAnim.setInterpolator(interpolator);
        currentViewAnim.setProperty(View.TRANSLATION_X);
        float curViewStart = mDistanceX;
        float curViewStop = slideCanceled ? 0 : width;
        currentViewAnim.setFloatValues(curViewStart, curViewStop);
        currentViewAnim.setTarget(currentView);

        // play animation together
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.setDuration(slideCanceled ? 150 : 300);
        mAnimatorSet.playTogether(previewViewAnim, shadowViewAnim, currentViewAnim);
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                if (slideCanceled) {
                    mIsSlideAnimPlaying = false;
                    previewView.setX(0);
                    shadowView.setX(-SHADOW_WIDTH);
                    currentView.setX(0);
                    sendEmptyMessage(MSG_SLIDE_CANCELED);
                } else {
                    sendEmptyMessage(MSG_SLIDE_FINISHED);
                }
            }
        });
        mAnimatorSet.start();
        mIsSlideAnimPlaying = true;
    }


    public interface SlideBackManager {

        void onSwipeBackAnimFinished();

        Activity getHostActivity();

        /**
         * 是否支持滑动返回
         *
         * @return
         */
        boolean supportSlideBack();

        /**
         * 能否滑动返回至当前Activity
         *
         * @return
         */
        boolean canBeSlideBack();

    }

    private class ViewManager {
        private View mHostContentView;
        private View mShadowView;
        private ViewGroup.LayoutParams mLp;

        /**
         * Remove view from previous Activity and add into current Activity
         *
         * @return Is view added successfully
         */
        private boolean addViewFromHostActivity() {
            if (mCurrentContentView.getChildCount() == 0) {
                mHostContentView = null;
                return false;
            }

            ViewGroup hostActivityContainer = (ViewGroup) mHostActivity.findViewById(Window.ID_ANDROID_CONTENT);
            if (hostActivityContainer == null || hostActivityContainer.getChildCount() == 0) {
                mHostContentView = null;
                return false;
            }

            mHostContentView = hostActivityContainer.getChildAt(0);
            hostActivityContainer.removeView(mHostContentView);

            mLp = mHostContentView.getLayoutParams();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mCurrentContentView.addView(mHostContentView, 0, params);
            return true;
        }

        /**
         * Remove the mHostContentView at current mCurrentContentView and put it into HostActivity.
         */
        private void resetHostView() {
            if (mHostContentView == null) return;
            mCurrentContentView.removeView(mHostContentView);
            ((ViewGroup) mHostActivity.findViewById(Window.ID_ANDROID_CONTENT)).addView(mHostContentView, mLp);
            mHostContentView = null;
        }

        /**
         * add shadow view on the left of content view
         */
        private synchronized void addShadowView() {
            if (mShadowView == null) {
                mShadowView = new ShadowView(mHostActivity);
                mShadowView.setX(-SHADOW_WIDTH);
            }
            final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    SHADOW_WIDTH, FrameLayout.LayoutParams.MATCH_PARENT);

            if (this.mShadowView.getParent() == null) {
                mCurrentContentView.addView(this.mShadowView, 0, layoutParams);
            } else {
                this.removeShadowView();
                this.addShadowView();
            }
        }

        private synchronized void removeShadowView() {
            if (mShadowView == null) return;
            mCurrentContentView.removeView(mShadowView);
            mShadowView = null;
        }


        private View getDisplayView() {
            int index = 0;
            if (mViewManager.mHostContentView != null) {
                index = index + 1;
            }

            if (mViewManager.mShadowView != null) {
                index = index + 1;
            }
            return mCurrentContentView.getChildAt(index);
        }
    }
}