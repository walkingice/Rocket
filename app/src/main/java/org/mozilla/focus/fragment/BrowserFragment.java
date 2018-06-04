/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.fragment;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.mozilla.focus.R;
import org.mozilla.focus.activity.ScreenNavigator;
import org.mozilla.focus.download.EnqueueDownloadTask;
import org.mozilla.focus.locale.LocaleAwareFragment;
import org.mozilla.focus.menu.WebContextMenu;
import org.mozilla.focus.permission.PermissionHandle;
import org.mozilla.focus.permission.PermissionHandler;
import org.mozilla.focus.screenshot.CaptureRunnable;
import org.mozilla.focus.tabs.SiteIdentity;
import org.mozilla.focus.tabs.Tab;
import org.mozilla.focus.tabs.TabCounter;
import org.mozilla.focus.tabs.TabView;
import org.mozilla.focus.tabs.TabsChromeListener;
import org.mozilla.focus.tabs.TabsSession;
import org.mozilla.focus.tabs.TabsSessionProvider;
import org.mozilla.focus.tabs.TabsViewListener;
import org.mozilla.focus.tabs.utils.TabUtil;
import org.mozilla.focus.telemetry.TelemetryWrapper;
import org.mozilla.focus.utils.AppConstants;
import org.mozilla.focus.utils.ColorUtils;
import org.mozilla.focus.utils.DrawableUtils;
import org.mozilla.focus.utils.FileChooseAction;
import org.mozilla.focus.utils.IntentUtils;
import org.mozilla.focus.utils.ThreadUtils;
import org.mozilla.focus.utils.UrlUtils;
import org.mozilla.focus.web.BrowsingSession;
import org.mozilla.focus.web.CustomTabConfig;
import org.mozilla.focus.web.Download;
import org.mozilla.focus.widget.AnimatedProgressBar;
import org.mozilla.focus.widget.BackKeyHandleable;
import org.mozilla.focus.widget.FragmentListener;
import org.mozilla.focus.widget.TabRestoreMonitor;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Fragment for displaying the browser UI.
 */
public class BrowserFragment extends LocaleAwareFragment implements View.OnClickListener,
        BackKeyHandleable {

    public static final String FRAGMENT_TAG = "browser";

    /** Custom data that is passed when calling {@link TabsSession#addTab(String, Bundle)} */
    public static final String EXTRA_NEW_TAB_SRC = "extra_bkg_tab_src";
    public static final int SRC_CONTEXT_MENU = 0;

    private static final Handler HANDLER = new Handler();

    private static final int ANIMATION_DURATION = 300;

    private static final int SITE_GLOBE = 0;
    private static final int SITE_LOCK = 1;

    private final static int NONE = -1;
    private int systemVisibility = NONE;

    private DownloadCallback downloadCallback = new DownloadCallback();

    private static final int BUNDLE_MAX_SIZE = 300 * 1000; // 300K

    private ViewGroup webViewSlot;
    private TabsSession tabsSession;

    private View backgroundView;
    private TransitionDrawable backgroundTransition;
    private TabCounter tabCounter;
    private TextView urlView;
    private AnimatedProgressBar progressView;
    private ImageView siteIdentity;
    private Dialog webContextMenu;

    //GeoLocationPermission
    private String geolocationOrigin;
    private GeolocationPermissions.Callback geolocationCallback;
    private AlertDialog geoDialog;

    /**
     * Container for custom video views shown in fullscreen mode.
     */
    private ViewGroup videoContainer;

    /**
     * Container containing the browser chrome and web content.
     */
    private View browserContainer;

    private TabView.FullscreenCallback fullscreenCallback;

    private boolean isLoading = false;

    // Set an initial WeakReference so we never have to handle loadStateListenerWeakReference being null
    // (i.e. so we can always just .get()).
    private WeakReference<LoadStateListener> loadStateListenerWeakReference = new WeakReference<>(null);

    // pending action for file-choosing
    private FileChooseAction fileChooseAction;

    private PermissionHandler permissionHandler;
    private static final int ACTION_DOWNLOAD = 0;
    private static final int ACTION_PICK_FILE = 1;
    private static final int ACTION_GEO_LOCATION = 2;
    private static final int ACTION_CAPTURE = 3;

    private boolean hasPendingScreenCaptureTask = false;

    final TabsContentListener tabsContentListener = new TabsContentListener();

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            permissionHandler.onRestoreInstanceState(savedInstanceState);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        permissionHandler = new PermissionHandler(new PermissionHandle() {
            @Override
            public void doActionDirect(String permission, int actionId, Parcelable params) {
                switch (actionId) {
                    case ACTION_DOWNLOAD:
                        if (getContext() == null) {
                            Log.w(FRAGMENT_TAG, "No context to use, abort callback onDownloadStart");
                            return;
                        }

                        Download download = (Download) params;

                        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            // We do have the permission to write to the external storage. Proceed with the download.
                            queueDownload(download);
                        }
                        break;
                    case ACTION_PICK_FILE:
                        fileChooseAction.startChooserActivity();
                        break;
                    case ACTION_GEO_LOCATION:
                        showGeolocationPermissionPrompt();
                        break;
                    case ACTION_CAPTURE:
                        showLoadingAndCapture();
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown actionId");
                }
            }


            private void actionDownloadGranted(Parcelable parcelable) {
                Download download = (Download) parcelable;
                queueDownload(download);
            }

            private void actionPickFileGranted() {
                if (fileChooseAction != null) {
                    fileChooseAction.startChooserActivity();
                }
            }

            private void actionCaptureGranted() {
                hasPendingScreenCaptureTask = true;
            }

            private void doActionGrantedOrSetting(String permission, int actionId, Parcelable params) {
                switch (actionId) {
                    case ACTION_DOWNLOAD:
                        actionDownloadGranted(params);
                        break;
                    case ACTION_PICK_FILE:
                        actionPickFileGranted();
                        break;
                    case ACTION_GEO_LOCATION:
                        showGeolocationPermissionPrompt();
                        break;
                    case ACTION_CAPTURE:
                        actionCaptureGranted();
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown actionId");
                }
            }

            @Override
            public void doActionGranted(String permission, int actionId, Parcelable params) {
                doActionGrantedOrSetting(permission, actionId, params);
            }

            @Override
            public void doActionSetting(String permission, int actionId, Parcelable params) {
                doActionGrantedOrSetting(permission, actionId, params);
            }

            @Override
            public void doActionNoPermission(String permission, int actionId, Parcelable params) {
                switch (actionId) {
                    case ACTION_DOWNLOAD:
                        // Do nothing
                        break;
                    case ACTION_PICK_FILE:
                        if (fileChooseAction != null) {
                            fileChooseAction.cancel();
                            fileChooseAction = null;
                        }
                        break;
                    case ACTION_GEO_LOCATION:
                        if (geolocationCallback != null) {
                            rejectGeoRequest();
                        }
                        break;
                    case ACTION_CAPTURE:
                        // Do nothing
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown actionId");
                }
            }

            @Override
            public int getDoNotAskAgainDialogString(int actionId) {
                if (actionId == ACTION_DOWNLOAD || actionId == ACTION_PICK_FILE || actionId == ACTION_CAPTURE) {
                    return R.string.permission_dialog_msg_storage;
                } else if (actionId == ACTION_GEO_LOCATION) {
                    return R.string.permission_dialog_msg_location;
                } else {
                    throw new IllegalArgumentException("Unknown Action");
                }
            }

            @Override
            public Snackbar makeAskAgainSnackBar(int actionId) {
                return PermissionHandler.makeAskAgainSnackBar(BrowserFragment.this, getActivity().findViewById(R.id.container), getAskAgainSnackBarString(actionId));
            }

            private int getAskAgainSnackBarString(int actionId) {
                if (actionId == ACTION_DOWNLOAD || actionId == ACTION_PICK_FILE || actionId == ACTION_CAPTURE) {
                    return R.string.permission_toast_storage;
                } else if (actionId == ACTION_GEO_LOCATION) {
                    return R.string.permission_toast_location;
                } else {
                    throw new IllegalArgumentException("Unknown Action");
                }
            }

            @Override
            public void requestPermissions(int actionId) {
                switch (actionId) {
                    case ACTION_DOWNLOAD:
                    case ACTION_CAPTURE:
                        BrowserFragment.this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, actionId);
                        break;
                    case ACTION_PICK_FILE:
                        BrowserFragment.this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, actionId);
                        break;
                    case ACTION_GEO_LOCATION:
                        BrowserFragment.this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, actionId);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown Action");
                }
            }
        });
    }

    @Override
    public void onPause() {
        tabsSession.pause();
        super.onPause();
    }

    @Override
    public void applyLocale() {
        // We create and destroy a new WebView here to force the internal state of WebView to know
        // about the new language. See issue #666.
        final WebView unneeded = new WebView(getContext());
        unneeded.destroy();
    }

    @Override
    public void onResume() {
        tabsSession.resume();
        super.onResume();
        if (hasPendingScreenCaptureTask) {
            showLoadingAndCapture();
            hasPendingScreenCaptureTask = false;
        }
    }

    private void updateURL(final String url) {
        if (UrlUtils.isInternalErrorURL(url)) {
            return;
        }

        urlView.setText(UrlUtils.stripUserInfo(url));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_browser, container, false);

        videoContainer = (ViewGroup) view.findViewById(R.id.video_container);
        browserContainer = view.findViewById(R.id.browser_container);

        urlView = (TextView) view.findViewById(R.id.display_url);

        backgroundView = view.findViewById(R.id.background);
        backgroundTransition = (TransitionDrawable) backgroundView.getBackground();

        tabCounter = view.findViewById(R.id.btn_tab_tray);
        final View newTabBtn = view.findViewById(R.id.btn_open_new_tab);
        final View searchBtn = view.findViewById(R.id.btn_search);
        final View captureBtn = view.findViewById(R.id.btn_capture);
        final View menuBtn = view.findViewById(R.id.btn_menu);
        if (tabCounter != null) {
            tabCounter.setOnClickListener(this);
        }
        if (newTabBtn != null) {
            newTabBtn.setOnClickListener(this);
        }
        if (searchBtn != null) {
            searchBtn.setOnClickListener(this);
        }
        if (captureBtn != null) {
            captureBtn.setOnClickListener(this);
        }
        if (menuBtn != null) {
            menuBtn.setOnClickListener(this);
        }

        siteIdentity = (ImageView) view.findViewById(R.id.site_identity);

        progressView = (AnimatedProgressBar) view.findViewById(R.id.progress);

        if (BrowsingSession.getInstance().isCustomTab()) {
            initialiseCustomTabUi(view);
        } else {
            initialiseNormalBrowserUi();
        }

        webViewSlot = (ViewGroup) view.findViewById(R.id.webview_slot);

        tabsSession = TabsSessionProvider.getOrThrow(getActivity());

        tabsSession.addTabsViewListener(this.tabsContentListener);
        tabsSession.addTabsChromeListener(this.tabsContentListener);
        tabsSession.setDownloadCallback(downloadCallback);

        if (tabCounter != null && isTabRestoredComplete()) {
            tabCounter.setCount(tabsSession.getTabsCount());
        }

        return view;
    }

    @Override
    public void onViewCreated(@Nullable View container, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(container, savedInstanceState);

        // restore WebView state
        if (savedInstanceState != null) {
            // Fragment was destroyed
            // FIXME: Obviously, only restore current tab is not enough
            final Tab focusTab = tabsSession.getFocusTab();
            if (focusTab != null) {
                TabView tabView = focusTab.getTabView();
                if (tabView != null) {
                    tabView.restoreViewState(savedInstanceState);
                } else {
                    // Focus to tab again to force initialization.
                    tabsSession.switchToTab(focusTab.getId());
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        permissionHandler.onActivityResult(getActivity(), requestCode, resultCode, data);
        if (requestCode == FileChooseAction.REQUEST_CODE_CHOOSE_FILE) {
            final boolean done = (fileChooseAction == null) || fileChooseAction.onFileChose(resultCode, data);
            if (done) {
                fileChooseAction = null;
            }
        }
    }

    public void onCaptureClicked() {
        permissionHandler.tryAction(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, ACTION_CAPTURE, null);
    }

    public void goBackground() {
        final Tab current = tabsSession.getFocusTab();
        if (current != null) {
            final TabView tabView = current.getTabView();
            if (tabView == null) {
                return;
            }
            //current.detach();
            //webViewSlot.removeView(tabView.getView());
        }
    }

    public void goForeground() {
        final Tab current = tabsSession.getFocusTab();
        if (webViewSlot.getChildCount() == 0 && current != null) {
            final TabView tabView = current.getTabView();
            if (tabView == null) {
                return;
            }
            final View inView = tabView.getView();
            if (inView.getParent() == null) {
                webViewSlot.addView(inView);
            }
        }
    }

    private void initialiseNormalBrowserUi() {
        urlView.setOnClickListener(this);
    }

    private void initialiseCustomTabUi(final @NonNull View view) {
        final CustomTabConfig customTabConfig = BrowsingSession.getInstance().getCustomTabConfig();

        final int textColor;

        final View toolbar = view.findViewById(R.id.urlbar);
        if (customTabConfig.toolbarColor != null) {
            toolbar.setBackgroundColor(customTabConfig.toolbarColor);

            textColor = ColorUtils.getReadableTextColor(customTabConfig.toolbarColor);
            urlView.setTextColor(textColor);
        } else {
            textColor = Color.WHITE;
        }

        final ImageView closeButton = (ImageView) view.findViewById(R.id.customtab_close);

        closeButton.setVisibility(View.VISIBLE);
        closeButton.setOnClickListener(this);

        if (customTabConfig.closeButtonIcon != null) {
            closeButton.setImageBitmap(customTabConfig.closeButtonIcon);
        } else {
            // Always set the icon in case it's been overridden by a previous CT invocation
            final Drawable closeIcon = DrawableUtils.loadAndTintDrawable(getContext(), R.drawable.ic_close, textColor);

            closeButton.setImageDrawable(closeIcon);
        }

        if (customTabConfig.disableUrlbarHiding) {
            AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();
            params.setScrollFlags(0);
        }

        if (customTabConfig.actionButtonConfig != null) {
            final ImageButton actionButton = (ImageButton) view.findViewById(R.id.customtab_actionbutton);
            actionButton.setVisibility(View.VISIBLE);

            actionButton.setImageBitmap(customTabConfig.actionButtonConfig.icon);
            actionButton.setContentDescription(customTabConfig.actionButtonConfig.description);

            final PendingIntent pendingIntent = customTabConfig.actionButtonConfig.pendingIntent;

            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        final Intent intent = new Intent();
                        intent.setData(Uri.parse(getUrl()));

                        pendingIntent.send(getContext(), 0, intent);
                    } catch (PendingIntent.CanceledException e) {
                        // There's really nothing we can do here...
                    }
                }
            });
        }

        // We need to tint some icons.. We already tinted the close button above. Let's tint our other icons too.
        final Drawable tintedIcon = DrawableUtils.loadAndTintDrawable(getContext(), R.drawable.ic_lock, textColor);
        siteIdentity.setImageDrawable(tintedIcon);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        permissionHandler.onSaveInstanceState(outState);
        if (tabsSession.getFocusTab() != null) {
            final TabView tabView = tabsSession.getFocusTab().getTabView();
            if (tabView != null) {
                tabView.saveViewState(outState);
            }
        }

        // Workaround for #1107 TransactionTooLargeException
        // since Android N, system throws a exception rather than just a warning(then drop bundle)
        // To set a threshold for dropping WebView state manually
        // refer: https://issuetracker.google.com/issues/37103380
        final String key = "WEBVIEW_CHROMIUM_STATE";
        if (outState.containsKey(key)) {
            final int size = outState.getByteArray(key).length;
            if (size > BUNDLE_MAX_SIZE) {
                outState.remove(key);
            }
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.FRAGMENT_STARTED, FRAGMENT_TAG);
    }

    @Override
    public void onStop() {
        if (systemVisibility != NONE) {
            final Tab tab = tabsSession.getFocusTab();
            if (tab != null) {
                final TabView tabView = tab.getTabView();
                if (tabView != null) {
                    tabView.performExitFullScreen();
                }
            }
        }
        dismissGeoDialog();
        super.onStop();
        FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.FRAGMENT_STOPPED, FRAGMENT_TAG);
    }

    @Override
    public void onDestroyView() {
        tabsSession.removeTabsViewListener(this.tabsContentListener);
        tabsSession.removeTabsChromeListener(this.tabsContentListener);
        super.onDestroyView();
    }

    public void setContentBlockingEnabled(boolean enabled) {
        // TODO: Better if we can move this logic to some setting-like classes, and provider interface
        // for configuring blocking function of each tab.
        for (final Tab tab : tabsSession.getTabs()) {
            tab.setContentBlockingEnabled(enabled);
        }
    }

    public void setImageBlockingEnabled(boolean enabled) {
        // TODO: Better if we can move this logic to some setting-like classes, and provider interface
        // for configuring blocking function of each tab.
        for (Tab tab : tabsSession.getTabs()) {
            tab.setImageBlockingEnabled(enabled);
        }
    }

    public interface LoadStateListener {
        void isLoadingChanged(boolean isLoading);
    }

    /**
     * Set a (singular) LoadStateListener. Only one listener is supported at any given time. Setting
     * a new listener means any previously set listeners will be dropped. This is only intended
     * to be used by NavigationItemViewHolder. If you want to use this method for any other
     * parts of the codebase, please extend it to handle a list of listeners. (We would also need
     * to automatically clean up expired listeners from that list, probably when adding to that list.)
     *
     * @param listener The listener to notify of load state changes. Only a weak reference will be kept,
     *                 no more calls will be sent once the listener is garbage collected.
     */
    public void setIsLoadingListener(final LoadStateListener listener) {
        loadStateListenerWeakReference = new WeakReference<>(listener);
    }

    private void showLoadingAndCapture() {
        if (!isResumed()) {
            return;
        }
        hasPendingScreenCaptureTask = false;
        final ScreenCaptureDialogFragment capturingFragment = ScreenCaptureDialogFragment.newInstance();
        capturingFragment.show(getChildFragmentManager(), "capturingFragment");

        final int WAIT_INTERVAL = 150;
        // Post delay to wait for Dialog to show
        HANDLER.postDelayed(new CaptureRunnable(getContext(), this, capturingFragment, getActivity().findViewById(R.id.container)), WAIT_INTERVAL);
    }

    private void updateIsLoading(final boolean isLoading) {
        this.isLoading = isLoading;
        final BrowserFragment.LoadStateListener currentListener = loadStateListenerWeakReference.get();
        if (currentListener != null) {
            currentListener.isLoadingChanged(isLoading);
        }
    }

    /**
     * Hide system bars. They can be revealed temporarily with system gestures, such as swiping from
     * the top of the screen. These transient system bars will overlay app’s content, may have some
     * degree of transparency, and will automatically hide after a short timeout.
     */
    private int switchToImmersiveMode() {
        final Activity activity = getActivity();
        Window window = activity.getWindow();
        final int original = window.getDecorView().getSystemUiVisibility();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        return original;
    }

    /**
     * Show the system bars again.
     */
    private void exitImmersiveMode(int visibility) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(visibility);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionHandler.onRequestPermissionsResult(getContext(), requestCode, permissions, grantResults);
    }

    /**
     * Use Android's Download Manager to queue this download.
     */
    private void queueDownload(Download download) {
        Activity activity = getActivity();
        if (activity == null || download == null) {
            return;
        }

        new EnqueueDownloadTask(getActivity(), download, getUrl()).execute();
    }

    /*
     * show webview geolocation permission prompt
     */
    private void showGeolocationPermissionPrompt() {
        if (!isPopupWindowAllowed()) {
            return;
        }

        if (geolocationCallback == null) {
            return;
        }
        if (geoDialog != null && geoDialog.isShowing()) {
            return;
        }
        geoDialog = buildGeoPromptDialog();
        geoDialog.show();
    }

    public void dismissGeoDialog() {
        if (geoDialog != null) {
            geoDialog.dismiss();
            geoDialog = null;
        }
    }

    private AlertDialog buildGeoPromptDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(getString(R.string.geolocation_dialog_message, geolocationOrigin))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.geolocation_dialog_allow), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        acceptGeoRequest();
                    }
                })
                .setNegativeButton(getString(R.string.geolocation_dialog_block), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        rejectGeoRequest();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        rejectGeoRequest();
                    }
                });
        return builder.create();
    }

    private void acceptGeoRequest() {
        if (geolocationCallback == null) {
            return;
        }
        geolocationCallback.invoke(geolocationOrigin, true, false);
        geolocationOrigin = "";
        geolocationCallback = null;
    }

    private void rejectGeoRequest() {
        if (geolocationCallback == null) {
            return;
        }
        geolocationCallback.invoke(geolocationOrigin, false, false);
        geolocationOrigin = "";
        geolocationCallback = null;
    }

    private boolean isStartedFromExternalApp() {
        final Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        // No SafeIntent needed here because intent.getAction() is safe (SafeIntent simply calls intent.getAction()
        // without any wrapping):
        final Intent intent = activity.getIntent();
        boolean isFromInternal = intent != null && intent.getBooleanExtra(IntentUtils.EXTRA_IS_INTERNAL_REQUEST, false);
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && !isFromInternal;
    }

    public boolean onBackPressed() {
        if (canGoBack()) {
            // Go back in web history
            goBack();
        } else {
            final Tab focus = tabsSession.getFocusTab();
            if (focus == null) {
                return false;
            } else if (focus.isFromExternal() || focus.hasParentTab()) {
                tabsSession.closeTab(focus.getId());
            } else {
                ScreenNavigator.get(getContext()).popToHomeScreen(true);
            }
        }

        return true;
    }

    /**
     * @param url target url
     * @param openNewTab whether to load url in a new tab or not
     * @param isFromExternal if this url is started from external VIEW intent
     * @param onViewReadyCallback callback to notify that web view is ready for showing.
     */
    public void loadUrl(@NonNull final String url, boolean openNewTab,
                        boolean isFromExternal, final Runnable onViewReadyCallback) {
        updateURL(url);
        if (UrlUtils.isUrl(url)) {
            if (openNewTab) {
                tabsSession.addTab(url, TabUtil.argument(null, isFromExternal, true));

                // In case we call TabsSession#addTab(), which is an async operation calls back in the next
                // message loop. By posting this runnable we can call back in the same message loop with
                // TabsContentListener#onFocusChanged(), which is when the view is ready and being attached.
                ThreadUtils.postToMainThread(onViewReadyCallback);
            } else {
                Tab currentTab = tabsSession.getFocusTab();
                if (currentTab != null && currentTab.getTabView() != null) {
                    currentTab.getTabView().loadUrl(url);
                    onViewReadyCallback.run();
                } else {
                    tabsSession.addTab(url, TabUtil.argument(null, isFromExternal, true));
                    ThreadUtils.postToMainThread(onViewReadyCallback);
                }
            }
        } else if (AppConstants.isDevBuild()) {
            // throw exception to highlight this issue, except release build.
            throw new RuntimeException("trying to open a invalid url: " + url);
        }
    }

    public void loadTab(final String tabId) {
        if (!TextUtils.isEmpty(tabId)) {
            tabsSession.switchToTab(tabId);
        }
    }

    public void openPreference() {
        FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.OPEN_PREFERENCE, null);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.display_url:
                FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.SHOW_URL_INPUT, getUrl());
                TelemetryWrapper.clickUrlbar();
                break;
            case R.id.btn_search:
                FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.SHOW_URL_INPUT, getUrl());
                TelemetryWrapper.clickToolbarSearch();
                break;
            case R.id.btn_open_new_tab:
                ScreenNavigator.get(getContext()).addHomeScreen(true);
                TelemetryWrapper.clickAddTabToolbar();
                break;
            case R.id.btn_tab_tray:
                FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.SHOW_TAB_TRAY, FRAGMENT_TAG);
                TelemetryWrapper.showTabTrayToolbar();
                break;
            case R.id.btn_menu:
                FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.SHOW_MENU, null);
                TelemetryWrapper.showMenuToolbar();
                break;
            case R.id.btn_capture:
                onCaptureClicked();
                TelemetryWrapper.clickToolbarCapture();
                break;
            case R.id.customtab_close:
                BrowsingSession.getInstance().clearCustomTabConfig();
                getActivity().finishAndRemoveTask();
                break;
            default:
                throw new IllegalArgumentException("Unhandled menu item in BrowserFragment");
        }
    }

    @NonNull
    public String getUrl() {
        // getUrl() is used for things like sharing the current URL. We could try to use the webview,
        // but sometimes it's null, and sometimes it returns a null URL. Sometimes it returns a data:
        // URL for error pages. The URL we show in the toolbar is (A) always correct and (B) what the
        // user is probably expecting to share, so lets use that here:
        return urlView.getText().toString();
    }

    public boolean canGoForward() {
        return tabsSession.getFocusTab() != null && tabsSession.getFocusTab().getTabView() != null && tabsSession.getFocusTab().getTabView().canGoForward();
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean canGoBack() {
        return tabsSession.getFocusTab() != null && tabsSession.getFocusTab().getTabView() != null && tabsSession.getFocusTab().getTabView().canGoBack();
    }

    public void goBack() {
        final Tab currentTab = tabsSession.getFocusTab();
        if (currentTab != null) {
            final TabView current = currentTab.getTabView();
            if (current == null) {
                return;
            }
//            WebBackForwardList webBackForwardList = ((WebView) current).copyBackForwardList();
//            WebHistoryItem item = webBackForwardList.getItemAtIndex(webBackForwardList.getCurrentIndex() - 1);
//            updateURL(item.getUrl());
            current.goBack();
        }
    }

    public void goForward() {
        final Tab currentTab = tabsSession.getFocusTab();
        if (currentTab != null) {
            final TabView current = currentTab.getTabView();
            if (current == null) {
                return;
            }
//            WebBackForwardList webBackForwardList = ((WebView) current).copyBackForwardList();
//            WebHistoryItem item = webBackForwardList.getItemAtIndex(webBackForwardList.getCurrentIndex() + 1);
//            updateURL(item.getUrl());
            current.goForward();
        }
    }

    public void reload() {
        final Tab currentTab = tabsSession.getFocusTab();
        if (currentTab != null) {
            final TabView current = currentTab.getTabView();
            if (current == null) {
                return;
            }
            current.reload();
        }
    }

    public void stop() {
        final Tab currentTab = tabsSession.getFocusTab();
        if (currentTab != null) {
            final TabView current = currentTab.getTabView();
            if (current == null) {
                return;
            }
            current.stopLoading();
        }
    }

    public interface ScreenshotCallback {
        void onCaptureComplete(String title, String url, Bitmap bitmap);
    }

    public boolean capturePage(@NonNull ScreenshotCallback callback) {
        final Tab currentTab = tabsSession.getFocusTab();
        // Failed to get WebView
        if (currentTab == null) {
            return false;
        }
        final TabView current = currentTab.getTabView();
        if (current == null || !(current instanceof WebView)) {
            return false;
        }
        WebView webView = (WebView) current;
        Bitmap content = getPageBitmap(webView);
        // Failed to capture
        if (content == null) {
            return false;
        }
        callback.onCaptureComplete(current.getTitle(), current.getUrl(), content);
        return true;
    }

    public void dismissWebContextMenu() {
        if (webContextMenu != null) {
            webContextMenu.dismiss();
            webContextMenu = null;
        }
    }

    private Bitmap getPageBitmap(WebView webView) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        try {
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), (int) (webView.getContentHeight() * displaymetrics.density), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
            // OOM may occur, even if OOMError is not thrown, operations during Bitmap creation may
            // throw other Exceptions such as NPE when the bitmap is very large.
        } catch (Exception | OutOfMemoryError ex) {
            return null;
        }

    }

    private boolean isPopupWindowAllowed() {
        return ScreenNavigator.get(getContext()).isBrowserInForeground();
    }

    private boolean isTabRestoredComplete() {
        if (!(getActivity() instanceof TabRestoreMonitor)) {
            if (AppConstants.isDevBuild()) {
                throw new RuntimeException("Base activity needs to implement TabRestoreMonitor");
            } else {
                return true; // No clue for the tab restore status. Just return true to bypass smile face tab counter
            }
        }
        return ((TabRestoreMonitor) getActivity()).isTabRestoredComplete();
    }

    class TabsContentListener implements TabsViewListener, TabsChromeListener {
        private HistoryInserter historyInserter = new HistoryInserter();

        // Some url may report progress from 0 again for the same url. filter them out to avoid
        // progress bar regression when scrolling.
        private String loadedUrl = null;

        private ValueAnimator tabTransitionAnimator;

        @Override
        public void onFocusChanged(@Nullable final Tab tab, @Factor int factor) {
            if (tab == null) {
                if (factor == FACTOR_NO_FOCUS && !isStartedFromExternalApp()) {
                    ScreenNavigator.get(getContext()).popToHomeScreen(true);
                } else {
                    getActivity().finish();
                }
            } else {
                transitToTab(tab);
                refreshChrome(tab);
            }
        }

        @Override
        public void onTabAdded(@NonNull final Tab tab, @Nullable final Bundle arguments) {
            if (arguments == null) {
                return;
            }

            int src = arguments.getInt(EXTRA_NEW_TAB_SRC, -1);
            switch (src) {
                case SRC_CONTEXT_MENU:
                    onTabAddedByContextMenu(tab, arguments);
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onTabStarted(@NonNull Tab tab) {
            historyInserter.onTabStarted(tab);

            if (!isForegroundTab(tab)) {
                return;
            }

            loadedUrl = null;

            updateIsLoading(true);

            updateURL(tab.getUrl());
            siteIdentity.setImageLevel(SITE_GLOBE);

            backgroundTransition.resetTransition();
        }

        private void updateUrlFromWebView(@NonNull Tab source) {
            if (tabsSession.getFocusTab() != null) {
                final String viewURL = tabsSession.getFocusTab().getUrl();
                onURLChanged(source, viewURL);
            }
        }

        @Override
        public void onTabFinished(@NonNull Tab tab, boolean isSecure) {
            if (isForegroundTab(tab)) {
                // The URL which is supplied in onTabFinished() could be fake (see #301), but webview's
                // URL is always correct _except_ for error pages
                updateUrlFromWebView(tab);

                updateIsLoading(false);

                FragmentListener.notifyParent(BrowserFragment.this, FragmentListener.TYPE.UPDATE_MENU, null);

                backgroundTransition.startTransition(ANIMATION_DURATION);

                siteIdentity.setImageLevel(isSecure ? SITE_LOCK : SITE_GLOBE);
            }
            historyInserter.onTabFinished(tab);
        }

        @Override
        public void onTabCountChanged(int count) {
            if (isTabRestoredComplete()) {
                tabCounter.setCountWithAnimation(count);
            }
        }

        @Override
        public void onURLChanged(@NonNull Tab tab, final String url) {
            if (!isForegroundTab(tab)) {
                return;
            }
            updateURL(url);
        }

        @Override
        public void onProgressChanged(@NonNull Tab tab, int progress) {
            if (!isForegroundTab(tab)) {
                return;
            }

            if (tabsSession.getFocusTab() != null) {
                final String currentUrl = tabsSession.getFocusTab().getUrl();
                final boolean progressIsForLoadedUrl = TextUtils.equals(currentUrl, loadedUrl);
                // Some new url may give 100 directly and then start from 0 again. don't treat
                // as loaded for these urls;
                final boolean urlBarLoadingToFinished =
                        progressView.getMax() != progressView.getProgress() && progress == progressView.getMax();
                if (urlBarLoadingToFinished) {
                    loadedUrl = currentUrl;
                }
                if (progressIsForLoadedUrl) {
                    return;
                }
            }
            progressView.setProgress(progress);
        }

        @Override
        public boolean handleExternalUrl(final String url) {
            if (getContext() == null) {
                Log.w(FRAGMENT_TAG, "No context to use, abort callback handleExternalUrl");
                return false;
            }

            return IntentUtils.handleExternalUri(getContext(), url);
        }

        @Override
        public void updateFailingUrl(@NonNull Tab tab, String url, boolean updateFromError) {
            historyInserter.updateFailingUrl(tab, url, updateFromError);
        }

        @Override
        public void onLongPress(@NonNull Tab tab, final TabView.HitTarget hitTarget) {
            if (getActivity() == null) {
                Log.w(FRAGMENT_TAG, "No context to use, abort callback onLongPress");
                return;
            }

            webContextMenu = WebContextMenu.show(getActivity(), downloadCallback, hitTarget);
        }

        @Override
        public void onEnterFullScreen(@NonNull Tab tab,
                                      @NonNull final TabView.FullscreenCallback callback,
                                      @Nullable View fullscreenContentView) {
            if (!isForegroundTab(tab)) {
                callback.fullScreenExited();
                return;
            }

            fullscreenCallback = callback;

            if (tab.getTabView() != null && fullscreenContentView != null) {
                // Hide browser UI and web content
                browserContainer.setVisibility(View.INVISIBLE);

                // Add view to video container and make it visible
                final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                videoContainer.addView(fullscreenContentView, params);
                videoContainer.setVisibility(View.VISIBLE);

                // Switch to immersive mode: Hide system bars other UI controls
                systemVisibility = switchToImmersiveMode();
            }
        }

        @Override
        public void onExitFullScreen(@NonNull Tab tab) {
            // Remove custom video views and hide container
            videoContainer.removeAllViews();
            videoContainer.setVisibility(View.GONE);

            // Show browser UI and web content again
            browserContainer.setVisibility(View.VISIBLE);

            if (systemVisibility != NONE) {
                exitImmersiveMode(systemVisibility);
            }

            // Notify renderer that we left fullscreen mode.
            if (fullscreenCallback != null) {
                fullscreenCallback.fullScreenExited();
                fullscreenCallback = null;
            }

            // WebView gets focus, but unable to open the keyboard after exit Fullscreen for Android 7.0+
            // We guess some component in WebView might lock focus
            // So when user touches the input text box on Webview, it will not trigger to open the keyboard
            // It may be a WebView bug.
            // The workaround is clearing WebView focus
            // The WebView will be normal when it gets focus again.
            // If android change behavior after, can remove this.
            if (tab.getTabView() instanceof WebView) {
                ((WebView) tab.getTabView()).clearFocus();
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(@NonNull Tab tab,
                                                       final String origin,
                                                       final GeolocationPermissions.Callback callback) {
            if (!isForegroundTab(tab) || !isPopupWindowAllowed()) {
                return;
            }

            geolocationOrigin = origin;
            geolocationCallback = callback;
            permissionHandler.tryAction(BrowserFragment.this, Manifest.permission.ACCESS_FINE_LOCATION, ACTION_GEO_LOCATION, null);
        }

        @Override
        public boolean onShowFileChooser(@NonNull Tab tab,
                                         TabView tabView,
                                         ValueCallback<Uri[]> filePathCallback,
                                         WebChromeClient.FileChooserParams fileChooserParams) {
            if (!isForegroundTab(tab)) {
                return false;
            }

            TelemetryWrapper.browseFilePermissionEvent();
            try {
                BrowserFragment.this.fileChooseAction = new FileChooseAction(BrowserFragment.this, filePathCallback, fileChooserParams);
                permissionHandler.tryAction(BrowserFragment.this, Manifest.permission.READ_EXTERNAL_STORAGE, BrowserFragment.ACTION_PICK_FILE, null);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public void onReceivedTitle(@NonNull Tab tab, String title) {
            if (!isForegroundTab(tab)) {
                return;
            }
            if (!BrowserFragment.this.getUrl().equals(tab.getUrl())) {
                updateURL(tab.getUrl());
            }
        }

        @Override
        public void onReceivedIcon(@NonNull Tab tab, Bitmap icon) {
        }

        private void transitToTab(@NonNull Tab targetTab) {
            final TabView tabView = targetTab.getTabView();
            if (tabView == null) {
                throw new RuntimeException("Tabview should be created at this moment and never be null");
            }
            targetTab.getTabView().onAttach(webViewSlot);
        }

        private void refreshChrome(Tab tab) {
            geolocationOrigin = "";
            geolocationCallback = null;

            dismissGeoDialog();

            updateURL(tab.getUrl());
            progressView.setProgress(0);

            int identity = (tab.getSecurityState() == SiteIdentity.SECURE) ? SITE_LOCK : SITE_GLOBE;
            siteIdentity.setImageLevel(identity);
        }

        @SuppressWarnings("SameParameterValue")
        private void startTransitionAnimation(@Nullable final View outView, @NonNull final View inView,
                                              @Nullable final Runnable finishCallback) {
            stopTabTransition();

            inView.setAlpha(0f);
            if (outView != null) {
                outView.setAlpha(1f);
            }

            int duration = inView.getResources().getInteger(R.integer.tab_transition_time);
            tabTransitionAnimator = ValueAnimator.ofFloat(0, 1).setDuration(duration);
            tabTransitionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float alpha = (float) animation.getAnimatedValue();
                    if (outView != null) {
                        outView.setAlpha(1 - alpha);
                    }
                    inView.setAlpha(alpha);
                }
            });
            tabTransitionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (finishCallback != null) {
                        finishCallback.run();
                    }
                    inView.setAlpha(1f);
                    if (outView != null) {
                        outView.setAlpha(1f);
                    }
                }
            });
            tabTransitionAnimator.start();
        }

        @Nullable
        private View findExistingTabView(ViewGroup parent) {
            int viewCount = parent.getChildCount();
            for (int childIdx = 0; childIdx < viewCount; ++childIdx) {
                View childView = parent.getChildAt(childIdx);
                if (childView instanceof TabView) {
                    return ((TabView) childView).getView();
                }
            }
            return null;
        }

        private void stopTabTransition() {
            if (tabTransitionAnimator != null && tabTransitionAnimator.isRunning()) {
                tabTransitionAnimator.end();
            }
        }

        private boolean isForegroundTab(Tab tab) {
            return tabsSession.getFocusTab() == tab;
        }

        private void onTabAddedByContextMenu(@NonNull final Tab tab, @NonNull Bundle arguments) {
            if (!TabUtil.toFocus(arguments)) {
                Snackbar.make(webViewSlot, R.string.new_background_tab_hint, Snackbar.LENGTH_LONG)
                        .setAction(R.string.new_background_tab_switch, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                tabsSession.switchToTab(tab.getId());
                            }
                        }).show();
            }
        }
    }

    class DownloadCallback implements org.mozilla.focus.web.DownloadCallback {

        @Override
        public void onDownloadStart(@NonNull Download download) {
            permissionHandler.tryAction(BrowserFragment.this, Manifest.permission.WRITE_EXTERNAL_STORAGE, ACTION_DOWNLOAD, download);
        }
    }

    /**
     * TODO: This class records some intermediate data of each tab to avoid inserting duplicate
     * history, maybe it'd be better to make these data as per-tab data
     */
    private final class HistoryInserter {
        private WeakHashMap<Tab, String> failingUrls = new WeakHashMap<>();

        // Some url may have two onPageFinished for the same url. filter them out to avoid
        // adding twice to the history.
        private WeakHashMap<Tab, String> lastInsertedUrls = new WeakHashMap<>();

        void onTabStarted(@NonNull Tab tab) {
            lastInsertedUrls.remove(tab);
        }

        void onTabFinished(@NonNull Tab tab) {
            insertBrowsingHistory(tab);
        }

        void updateFailingUrl(@NonNull Tab tab, String url, boolean updateFromError) {
            String failingUrl = failingUrls.get(tab);
            if (!updateFromError && !url.equals(failingUrl)) {
                failingUrls.remove(tab);
            } else {
                failingUrls.put(tab, url);
            }
        }

        private void insertBrowsingHistory(Tab tab) {
            String urlToBeInserted = getUrl();
            @NonNull String lastInsertedUrl = getLastInsertedUrl(tab);

            if (TextUtils.isEmpty(urlToBeInserted)) {
                return;
            }

            if (urlToBeInserted.equals(getFailingUrl(tab))) {
                return;
            }

            if (urlToBeInserted.equals(lastInsertedUrl)) {
                return;
            }

            TabView tabView = tab.getTabView();
            if (tabView != null) {
                tabView.insertBrowsingHistory();
            }
            lastInsertedUrls.put(tab, urlToBeInserted);
        }

        private String getFailingUrl(Tab tab) {
            String url = failingUrls.get(tab);
            return TextUtils.isEmpty(url) ? "" : url;
        }

        private String getLastInsertedUrl(Tab tab) {
            String url = lastInsertedUrls.get(tab);
            return TextUtils.isEmpty(url) ? "" : url;
        }
    }
}
