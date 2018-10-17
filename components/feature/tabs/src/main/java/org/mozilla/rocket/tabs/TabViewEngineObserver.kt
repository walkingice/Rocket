package org.mozilla.rocket.tabs

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import mozilla.components.support.base.observer.Consumable
import org.mozilla.rocket.tabs.TabView.HitTarget

class TabViewEngineObserver(
        val session: Session
) : TabViewEngineSession.Observer {

    var tabView: TabView? = null

    override fun onTitleChange(title: String) {
        session.title = title
        session.notifyObservers { onTitleChanged(session, title) }
    }

    override fun onLoadingStateChange(loading: Boolean) {
        session.notifyObservers { onLoadingStateChanged(session, loading) }
    }

    override fun onSecurityChange(secure: Boolean, host: String?, issuer: String?) =
            session.notifyObservers { onSecurityChanged(session, secure) }

    override fun onLocationChange(url: String) {
        session.url = url
        session.notifyObservers { onUrlChanged(session, url) }
    }

    override fun updateFailingUrl(url: String?, updateFromError: Boolean) =
            session.notifyObservers { updateFailingUrl(url, updateFromError) }

    override fun handleExternalUrl(url: String?): Boolean {
        var consumers = session.wrapConsumers<String?> { url -> handleExternalUrl(url) }
        return Consumable.from(url).consumeBy(consumers)
    }

    override fun onCreateWindow(
            isDialog: Boolean,
            isUserGesture: Boolean,
            msg: Message?): Boolean {
        val consumers = session.wrapConsumers<Triple<Boolean, Boolean, Message?>> {
            onCreateWindow(it.first, it.second, it.third)
        }
        val args = Triple(isDialog, isUserGesture, msg)
        return Consumable.from(args).consumeBy(consumers)
    }

    override fun onCloseWindow(es: TabViewEngineSession) {
        session.notifyObservers { onCloseWindow(es.tabView) }
    }

    override fun onProgress(progress: Int) {
        session.notifyObservers { onProgress(session, progress) }
    }

    override fun onShowFileChooser(es: TabViewEngineSession, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
        if (tabView == null) {
            return false
        }

        val consumers = session.wrapConsumers<Triple<TabView, ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams>> {
            onShowFileChooser(it.first, it.second, it.third)
        }
        val args = Triple(tabView!!, filePathCallback!!, fileChooserParams!!)
        return Consumable.from(args).consumeBy(consumers)
    }

    override fun onReceivedIcon(icon: Bitmap?) {
        session.favicon = icon
        session.notifyObservers { onReceivedIcon(icon) }
    }

    override fun onLongPress(hitTarget: HitTarget) {
        session.notifyObservers { onLongPress(session, hitTarget) }
    }

    override fun onEnterFullScreen(callback: TabView.FullscreenCallback, view: View?) =
            session.notifyObservers { onEnterFullScreen(callback, view) }

    override fun onExitFullScreen() = session.notifyObservers { onExitFullScreen() }

    override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback?) =
            session.notifyObservers { onGeolocationPermissionsShowPrompt(origin, callback) }

}
