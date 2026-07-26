package com.poyi.watchintervals;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/** Pixel-following watch pager modeled after the system sports TossViewPager behavior. */
final class WatchPagerLayout extends ViewGroup {
    /** Fired when the user drags right past the first page and releases — the watch-wide
     *  "swipe right to leave" gesture. Only screens that register this can be exited that way;
     *  the workout pager deliberately does not, so a sweaty mis-swipe cannot end a session. */
    interface OnExitListener { void onSwipeExit(); }

    /** Drag past the edge moves the content at one third speed — enough to feel the boundary. */
    private static final float EDGE_DAMPING = 3f;
    /** Fraction of the page width the RAW finger travel past the edge must cover to exit. */
    private static final float EXIT_FRACTION = 0.22f;

    private final Scroller scroller;
    private final int touchSlop, minimumVelocity, maximumVelocity;
    private VelocityTracker velocity;
    private float downX, downY, lastX;
    /** Undamped finger-tracked scroll position. Damping is applied to the SHOWN value only;
     *  damping the accumulator itself compounded per event and froze the edge almost solid. */
    private float virtualScroll;
    private boolean horizontalDrag;
    private int currentItem;
    private OnExitListener exitListener;

    WatchPagerLayout(Context context) {
        super(context); scroller=new Scroller(context);
        ViewConfiguration config=ViewConfiguration.get(context);touchSlop=config.getScaledTouchSlop();minimumVelocity=config.getScaledMinimumFlingVelocity();maximumVelocity=config.getScaledMaximumFlingVelocity();
        setFocusable(true);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    void setOnExitListener(OnExitListener listener) { exitListener = listener; }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) { super.requestDisallowInterceptTouchEvent(false); }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=event.getX();downY=event.getY();virtualScroll=getScrollX();horizontalDrag=false;if(!scroller.isFinished())scroller.abortAnimation();obtainVelocity(event);return false;}
        if(action==MotionEvent.ACTION_MOVE){float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);if(dx>touchSlop&&dx>dy*1.15f){horizontalDrag=true;/* Preserve downX as lastX: the first intercepted MOVE must apply the full displacement. Resetting lastX here made short reverse drags snap back to the route page. */getParent().requestDisallowInterceptTouchEvent(true);return true;}if(dy>touchSlop&&dy>dx)return false;}
        return horizontalDrag;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        obtainVelocity(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN: downX=lastX=event.getX();downY=event.getY();virtualScroll=getScrollX();if(!scroller.isFinished())scroller.abortAnimation();return true;
            case MotionEvent.ACTION_MOVE: {
                float x=event.getX();float delta=lastX-x;lastX=x;
                int limit=Math.max(0,(getChildCount()-1)*getWidth());
                // Track the raw finger position; damp only what is drawn. Past either end the
                // page keeps following at reduced speed — the watch-native cue that there is no
                // further page. The damped right-overscroll on page 0 doubles as the exit gesture.
                virtualScroll+=delta;
                float shown;
                if(virtualScroll<0)shown=virtualScroll/EDGE_DAMPING;
                else if(virtualScroll>limit)shown=limit+(virtualScroll-limit)/EDGE_DAMPING;
                else shown=virtualScroll;
                scrollTo(Math.round(shown),0);return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000,maximumVelocity);float vx=velocity.getXVelocity();int page;
                if(virtualScroll<0&&getScrollX()<=0){
                    boolean exitCommitted=event.getActionMasked()!=MotionEvent.ACTION_CANCEL
                            &&(-virtualScroll>=getWidth()*EXIT_FRACTION||vx>=minimumVelocity);
                    recycleVelocity();horizontalDrag=false;virtualScroll=0;
                    if(exitCommitted&&exitListener!=null){exitListener.onSwipeExit();return true;}
                    setCurrentItem(0,true);return true;
                }
                float travel=event.getX()-downX;float commitDistance=Math.max(touchSlop*2f,getWidth()*0.16f);
                if(event.getActionMasked()!=MotionEvent.ACTION_CANCEL&&Math.abs(travel)>=commitDistance)page=travel<0?currentItem+1:currentItem-1;
                else if(Math.abs(vx)>=minimumVelocity*2)page=vx<0?currentItem+1:currentItem-1;else page=Math.round(getScrollX()/(float)Math.max(1,getWidth()));
                setCurrentItem(Math.max(0,Math.min(getChildCount()-1,page)),true);recycleVelocity();horizontalDrag=false;return true;
        }
        return true;
    }

    private void obtainVelocity(MotionEvent event){if(velocity==null)velocity=VelocityTracker.obtain();velocity.addMovement(event);}
    private void recycleVelocity(){if(velocity!=null){velocity.recycle();velocity=null;}}

    void setCurrentItem(int item,boolean smooth){currentItem=Math.max(0,Math.min(getChildCount()-1,item));int destination=currentItem*getWidth();if(!smooth||getWidth()==0){scrollTo(destination,0);invalidate();return;}int dx=destination-getScrollX();scroller.startScroll(getScrollX(),0,dx,0,Math.min(320,160+Math.abs(dx)/2));invalidate();}
    int getCurrentItem(){return currentItem;}

    @Override public void computeScroll(){if(scroller.computeScrollOffset()){scrollTo(scroller.getCurrX(),scroller.getCurrY());postInvalidateOnAnimation();}}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);int cw=MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),ch=MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY);for(int i=0;i<getChildCount();i++)getChildAt(i).measure(cw,ch);}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){int width=right-left;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.layout(i*width,0,(i+1)*width,bottom-top);}if(!horizontalDrag&&scroller.isFinished())scrollTo(currentItem*width,0);}
}
