package com.poyi.watchintervals;

import android.content.Context;
import android.view.MotionEvent;

/** Small activity-level swipe detector that coexists with vertical scrolling. */
final class SwipeTracker {
    interface Listener { void onSwipeRight(); void onSwipeLeft(); }

    private final float threshold;
    private final Listener listener;
    private float downX, downY;
    private boolean tracking;

    SwipeTracker(Context context, Listener listener) {
        threshold = Ui.dp(context, 36);
        this.listener = listener;
    }

    void observe(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); tracking = true;
        } else if ((event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP) && tracking) {
            float dx = event.getX() - downX, dy = event.getY() - downY;
            if (Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                tracking = false;
                if (dx > 0) listener.onSwipeRight(); else listener.onSwipeLeft();
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) tracking = false;
    }
}
