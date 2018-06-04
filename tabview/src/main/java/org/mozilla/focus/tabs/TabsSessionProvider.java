/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.tabs;

import android.app.Activity;
import android.support.annotation.Nullable;

public final class TabsSessionProvider {
    public interface SessionHost {
        TabsSession getTabsSession();
    }

    private TabsSessionProvider() {
    }

    public static TabsSession getOrThrow(Activity activity) throws IllegalArgumentException {
        if (activity instanceof SessionHost) {
            return ((SessionHost) activity).getTabsSession();
        }
        throw new IllegalArgumentException("activity must implement TabsSessionProvider.SessionHost");
    }

    @Nullable
    public static TabsSession getOrNull(Activity activity) {
        try {
            return getOrThrow(activity);
        } catch (Exception e) {
            return null;
        }
    }
}
