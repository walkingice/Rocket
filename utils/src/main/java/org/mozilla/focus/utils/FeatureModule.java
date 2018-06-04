package org.mozilla.focus.utils;

import android.content.Context;
import android.support.annotation.NonNull;

public class FeatureModule {
    private static FeatureModule sInstance;

    private boolean supportPrivateBrowsing = true;

    public synchronized static FeatureModule getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new FeatureModule();
            sInstance.refresh(ctx);
        }

        return sInstance;
    }

    private FeatureModule() {
    }

    public void refresh(@NonNull final Context context) {
        supportPrivateBrowsing = true;
    }

    public boolean isSupportPrivateBrowsing() {
        return supportPrivateBrowsing;
    }
}
