package org.mozilla.focus.utils;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.OnSuccessListener;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FeatureModule {
    private final static String TAG = "Rocket";
    private final static String DYNAMIC_FEATURE_GECKO_POWER = "geckopower";

    private static String appPkgName = "org.mozilla.rocket";

    private static FeatureModule sInstance;
    private boolean supportPrivateBrowsing = true;

    public synchronized static FeatureModule getInstance() {
        if (sInstance == null) {
            sInstance = new FeatureModule();
        }

        return sInstance;
    }

    private FeatureModule() {
    }

    public void refresh(@NonNull final Context context) {
        final SplitInstallManager mgr = SplitInstallManagerFactory.create(context);
        final Set<String> set = mgr.getInstalledModules();

        appPkgName = context.getPackageName();

        Log.d(TAG, "Feature-module - Installed modules size: " + set.size());
        for (final String s : set) {
            Log.d(TAG, "Feature-module - Installed module: " + s);
        }

        boolean newValue = set.contains(DYNAMIC_FEATURE_GECKO_POWER);
        boolean dirty = supportPrivateBrowsing != newValue;
        supportPrivateBrowsing = newValue;

        if (dirty) {
            provider = null;
        }
    }

    public boolean isSupportPrivateBrowsing() {
        return supportPrivateBrowsing;
    }

    public static Intent intentForPrivateBrowsing(final String url) {
        // IT IS TRICK
        // if you extract apks file and see AndroidManifest.xml of base-apk, you will see
        // PrivateActivity is under packge "org.mozilla.rocket"
        final String pkgName = appPkgName;
        Intent intent = new Intent();
        intent.putExtra("extra_url", url);
        intent.setClassName(pkgName, "org.mozilla.rocket.privatebrowsing.PrivateActivity");
        return intent;
    }

    public void install(final Context context, final StatusListener callback) {
        final SplitInstallManager mgr = SplitInstallManagerFactory.create(context);
        if (isSupportPrivateBrowsing()) {
            callback.onDone();
            return;
        }
        final SplitInstallRequest req = SplitInstallRequest
                .newBuilder()
                .addModule(DYNAMIC_FEATURE_GECKO_POWER)
                .build();
        final List<String> modules = new ArrayList<>();
        modules.add(DYNAMIC_FEATURE_GECKO_POWER);
        mgr.startInstall(req)
                .addOnSuccessListener(new OnSuccessListener<Integer>() {
                    @Override
                    public void onSuccess(Integer integer) {
                        refresh(context);
                        callback.onDone();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        refresh(context);
                        callback.onDone();
                    }
                });
    }

    public void uninstall(final Context context, final StatusListener callback) {
        if (!isSupportPrivateBrowsing()) {
            callback.onDone();
            return;
        }

        final SplitInstallManager mgr = SplitInstallManagerFactory.create(context);
        final List<String> modules = new ArrayList<>();
        modules.add(DYNAMIC_FEATURE_GECKO_POWER);
        mgr.deferredUninstall(modules)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        refresh(context);
                        callback.onDone();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        refresh(context);
                        callback.onDone();
                    }
                });
    }

    private static TheViewProvider provider = null;

    public TheViewProvider getViewProvider(Context ctx) {
        if (provider == null) {
            if (isSupportPrivateBrowsing()) {
                try {
                    Class c = Class.forName("org.mozilla.gecko.tabs.FeatureViewProvider");
                    Constructor<?> cs = c.getConstructor(Context.class);
                    provider = (TheViewProvider) cs.newInstance(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    provider = new FallbackViewProvider();
                }
            } else {
                provider = new BaseViewProvider();
            }
        }

        return provider;
    }

    public interface StatusListener {
        void onDone();
    }

    public class FallbackViewProvider implements TheViewProvider {
        @Override
        public Foo createFoo() {
            return new FallbackFoo();
        }

        class FallbackFoo extends Foo {
            FallbackFoo() {
                super.name = "FallbackFoo";
            }
        }
    }
}
