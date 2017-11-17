/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import org.mozilla.focus.utils.OnSwipeListener;
import org.mozilla.focus.utils.SwipeMotionDetector;


public class SwipeMotionLayout extends RelativeLayout {

    private SwipeMotionDetector swipeMotionDetector;

    public SwipeMotionLayout(Context context) {
        this(context, null);
    }

    public SwipeMotionLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMotionLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SwipeMotionLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (swipeMotionDetector != null) {
            swipeMotionDetector.onTouch(this, ev);
        }

        return super.onInterceptTouchEvent(ev);
    }

    public void setOnSwipeListener(OnSwipeListener onSwipeListener) {
        if (onSwipeListener != null) {
            swipeMotionDetector = new SwipeMotionDetector(getContext(), onSwipeListener);
            setOnTouchListener(swipeMotionDetector);
        } else {
            swipeMotionDetector = null;
            setOnTouchListener(null);
        }
    }
}
