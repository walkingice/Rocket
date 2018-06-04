/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadUtils {
    private static final ExecutorService backgroundExecutorService = Executors.newSingleThreadExecutor(getIoPrioritisedFactory());
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Thread uiThread = Looper.getMainLooper().getThread();

    public static void postToBackgroundThread(final Runnable runnable) {
        backgroundExecutorService.submit(runnable);
    }

    public static void postToMainThread(final Runnable runnable) {
        handler.post(runnable);
    }

    public static void postToMainThreadDelayed(final Runnable runnable, long delayMillis) {
        handler.postDelayed(runnable, delayMillis);
    }

    public static void assertOnUiThread() {
        final Thread currentThread = Thread.currentThread();
        final long currentThreadId = currentThread.getId();
        final long expectedThreadId = uiThread.getId();

        if (currentThreadId == expectedThreadId) {
            return;
        }

        throw new IllegalThreadStateException("Expected UI thread, but running on " + currentThread.getName());
    }

    private static ThreadFactory getIoPrioritisedFactory() {
        return new CustomThreadFactory("pool-io-background", Thread.NORM_PRIORITY - 1);
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final String threadName;
        private final int threadPriority;
        private final AtomicInteger mNumber = new AtomicInteger();

        public CustomThreadFactory(String threadName, int threadPriority) {
            super();
            this.threadName = threadName;
            this.threadPriority = threadPriority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, threadName + "-" + mNumber.getAndIncrement());
            thread.setPriority(threadPriority);
            return thread;
        }
    }
}
