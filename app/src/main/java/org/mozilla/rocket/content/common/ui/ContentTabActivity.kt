package org.mozilla.rocket.content.common.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import dagger.Lazy
import org.mozilla.focus.R
import org.mozilla.focus.activity.BaseActivity
import org.mozilla.focus.databinding.ActivityContentTabBinding
import org.mozilla.focus.utils.Constants
import org.mozilla.focus.utils.IntentUtils
import org.mozilla.focus.widget.BackKeyHandleable
import org.mozilla.focus.widget.ResizableKeyboardLayout.OnKeyboardVisibilityChangedListener
import org.mozilla.permissionhandler.PermissionHandler
import org.mozilla.rocket.chrome.BottomBarItemAdapter
import org.mozilla.rocket.chrome.ChromeViewModel
import org.mozilla.rocket.content.appComponent
import org.mozilla.rocket.content.common.data.ContentTabTelemetryData
import org.mozilla.rocket.content.getViewModel
import org.mozilla.rocket.content.view.BottomBar
import org.mozilla.rocket.download.data.DownloadsRepository
import org.mozilla.rocket.extension.nonNullObserve
import org.mozilla.rocket.extension.switchFrom
import org.mozilla.rocket.privately.PrivateTabViewProvider
import org.mozilla.rocket.tabs.SessionManager
import org.mozilla.rocket.tabs.TabViewProvider
import org.mozilla.rocket.tabs.TabsSessionProvider
import java.net.URISyntaxException
import javax.inject.Inject

class ContentTabActivity : BaseActivity(), TabsSessionProvider.SessionHost, ContentTabViewContract {

    @Inject
    lateinit var chromeViewModelCreator: Lazy<ChromeViewModel>

    @Inject
    lateinit var bottomBarViewModelCreator: Lazy<ContentTabBottomBarViewModel>

    @Inject
    lateinit var telemetryViewModelCreator: Lazy<ContentTabTelemetryViewModel>

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var chromeViewModel: ChromeViewModel
    private lateinit var telemetryViewModel: ContentTabTelemetryViewModel
    private lateinit var tabViewProvider: TabViewProvider
    private lateinit var sessionManager: SessionManager
    private lateinit var contentTabHelper: ContentTabHelper
    private lateinit var contentTabObserver: ContentTabHelper.Observer
    private lateinit var uiMessageReceiver: BroadcastReceiver
    private lateinit var bottomBarItemAdapter: BottomBarItemAdapter

    private var binding: ActivityContentTabBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        appComponent().inject(this)
        super.onCreate(savedInstanceState)

        val binding = ActivityContentTabBinding.inflate(layoutInflater)
            .also { setContentView(it.root) }
        this.binding = binding

        chromeViewModel = getViewModel(chromeViewModelCreator)
        telemetryViewModel = getViewModel(telemetryViewModelCreator)
        tabViewProvider = PrivateTabViewProvider(this)
        sessionManager = SessionManager(tabViewProvider)

        binding.appbar.setOnApplyWindowInsetsListener { v, insets ->
            (v.layoutParams as ConstraintLayout.LayoutParams).topMargin =
                insets.systemWindowInsetTop
            insets
        }

        makeStatusBarTransparent()

        setupBottomBar(binding.bottomBar)

        initBroadcastReceivers()

        contentTabHelper = ContentTabHelper(this)
        contentTabHelper.initPermissionHandler()

        contentTabObserver = contentTabHelper.getObserver()
        sessionManager.register(contentTabObserver)

        observeChromeAction()
        chromeViewModel.showUrlInput.value = chromeViewModel.currentUrl.value

        telemetryViewModel.initialize(intent?.extras?.getParcelable(EXTRA_TELEMETRY_DATA))

        if (savedInstanceState == null) {
            val url = intent?.extras?.getString(EXTRA_URL) ?: ""
            val enableTurboMode = intent?.extras?.getBoolean(EXTRA_ENABLE_TURBO_MODE) ?: true
            val contentTabFragment = ContentTabFragment.newInstance(url, enableTurboMode)
            supportFragmentManager.beginTransaction()
                .replace(R.id.browser_container, contentTabFragment)
                .commit()

            Looper.myQueue().addIdleHandler {
                contentTabFragment.setOnKeyboardVisibilityChangedListener(
                    OnKeyboardVisibilityChangedListener { visible ->
                        if (visible) {
                            contentTabFragment.setOnKeyboardVisibilityChangedListener(null)
                            telemetryViewModel.onKeyboardShown()
                        }
                    }
                )
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager.resume()
        val uiActionFilter = IntentFilter()
        uiActionFilter.addCategory(Constants.CATEGORY_FILE_OPERATION)
        uiActionFilter.addAction(Constants.ACTION_NOTIFY_RELOCATE_FINISH)
        LocalBroadcastManager.getInstance(this).registerReceiver(uiMessageReceiver, uiActionFilter)
        telemetryViewModel.onSessionStarted()
    }

    override fun onPause() {
        super.onPause()
        sessionManager.pause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiMessageReceiver)
        telemetryViewModel.onSessionEnded()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.unregister(contentTabObserver)
        sessionManager.destroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val binding = this.binding ?: return

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.toolbar.root.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
        } else {
            binding.toolbar.root.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionHandler.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.isStateSaved) {
            return
        }

        val fragment = supportFragmentManager.findFragmentById(R.id.browser_container)
        if (fragment != null && fragment is BackKeyHandleable) {
            val handled = fragment.onBackPressed()
            if (handled) {
                return
            }
        }

        super.onBackPressed()
    }

    override fun applyLocale() = Unit

    override fun getSessionManager() = sessionManager

    override fun getHostActivity() = this

    override fun getCurrentSession() = sessionManager.focusSession

    override fun getChromeViewModel() = chromeViewModel

    override fun getSiteIdentity(): ImageView? = binding?.toolbar?.siteIdentity

    override fun getDisplayUrlView(): TextView? = binding?.toolbar?.displayUrl

    override fun getProgressBar(): ProgressBar? = binding?.progress

    override fun getFullScreenGoneViews() = binding?.let { listOf(it.toolbar.root, it.bottomBar) }
        ?: emptyList()

    override fun getFullScreenInvisibleViews() = binding?.let { listOf(it.browserContainer) }
        ?: emptyList()

    override fun getFullScreenContainerView(): ViewGroup = binding!!.videoContainer

    private fun setupBottomBar(bottomBar: BottomBar) {
        bottomBar.setOnItemClickListener { type, position ->
            when (type) {
                BottomBarItemAdapter.TYPE_BACK -> chromeViewModel.goBack.call()
                BottomBarItemAdapter.TYPE_REFRESH -> {
                    chromeViewModel.refreshOrStop.call()
                    telemetryViewModel.onReloadButtonClicked()
                }
                BottomBarItemAdapter.TYPE_SHARE -> chromeViewModel.share.call()
                else -> throw IllegalArgumentException("Unhandled bottom bar item, type: $type")
            }
        }
        bottomBarItemAdapter =
            BottomBarItemAdapter(bottomBar, BottomBarItemAdapter.Theme.PrivateMode)
        val bottomBarViewModel = getViewModel(bottomBarViewModelCreator)
        bottomBarViewModel.items.nonNullObserve(this) {
            bottomBarItemAdapter.setItems(it)
        }

        chromeViewModel.isRefreshing.switchFrom(bottomBarViewModel.items)
            .observe(this) {
                bottomBarItemAdapter.setRefreshing(it == true)

                if (it == true) {
                    telemetryViewModel.onPageLoadingStarted()
                } else {
                    telemetryViewModel.onPageLoadingStopped()
                }
            }
        chromeViewModel.canGoForward.switchFrom(bottomBarViewModel.items)
            .observe(this) { bottomBarItemAdapter.setCanGoForward(it == true) }
    }

    private fun makeStatusBarTransparent() {
        var visibility = window.decorView.systemUiVisibility
        // do not overwrite existing value
        visibility =
            visibility or (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        window.decorView.systemUiVisibility = visibility
    }

    private fun initBroadcastReceivers() {
        uiMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Constants.ACTION_NOTIFY_RELOCATE_FINISH) {
                    chromeViewModel.onRelocateFinished(
                        intent.getLongExtra(
                            Constants.EXTRA_ROW_ID,
                            -1
                        )
                    )
                }
            }
        }
    }

    private fun observeChromeAction() {
        chromeViewModel.goBack.observe(this) {
            onBackPressed()
            telemetryViewModel.onBackButtonClicked()
        }

        chromeViewModel.share.observe(this) {
            chromeViewModel.currentUrl.value?.let { url ->
                onShareClicked(url)
            }
            telemetryViewModel.onShareButtonClicked()
        }

        chromeViewModel.currentUrl.observe(this) {
            telemetryViewModel.onUrlOpened()
        }

        chromeViewModel.downloadState.observe(this) { downloadState ->
            when (downloadState) {
                is DownloadsRepository.DownloadState.StorageUnavailable ->
                    Toast.makeText(
                        this,
                        R.string.message_storage_unavailable_cancel_download,
                        Toast.LENGTH_LONG
                    ).show()
                is DownloadsRepository.DownloadState.FileNotSupported ->
                    Toast.makeText(this, R.string.download_file_not_supported, Toast.LENGTH_LONG)
                        .show()
                is DownloadsRepository.DownloadState.Success ->
                    if (!downloadState.isStartFromContextMenu) {
                        Toast.makeText(this, R.string.download_started, Toast.LENGTH_LONG).show()
                    }
            }
        }

        chromeViewModel.showDownloadFinishedSnackBar.observe(this) { downloadInfo ->
            val binding = this.binding ?: return@observe
            val message = getString(R.string.download_completed, downloadInfo.fileName)
            Snackbar.make(binding.snackBarContainer, message, Snackbar.LENGTH_LONG).apply {
                // Set the open action only if we can.
                if (downloadInfo.existInDownloadManager()) {
                    setAction(R.string.open) {
                        try {
                            IntentUtils.intentOpenFile(
                                this@ContentTabActivity,
                                downloadInfo.fileUri,
                                downloadInfo.mimeType
                            )
                        } catch (e: URISyntaxException) {
                            e.printStackTrace()
                        }
                    }
                }
                anchorView = binding.bottomBar
                show()
            }
        }
    }

    private fun onShareClicked(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_dialog_title)))
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TELEMETRY_DATA = "telemetry_data"
        private const val EXTRA_ENABLE_TURBO_MODE = "enable_turbo_mode"

        fun getStartIntent(
            context: Context,
            url: String,
            telemetryData: ContentTabTelemetryData? = null,
            enableTurboMode: Boolean = true
        ) =
            Intent(context, ContentTabActivity::class.java).also { intent ->
                intent.putExtra(EXTRA_URL, url)
                intent.putExtra(EXTRA_ENABLE_TURBO_MODE, enableTurboMode)
                telemetryData?.let { intent.putExtra(EXTRA_TELEMETRY_DATA, it) }
            }
    }
}
