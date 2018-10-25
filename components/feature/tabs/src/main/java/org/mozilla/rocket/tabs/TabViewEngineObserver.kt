package org.mozilla.rocket.tabs

import android.graphics.Bitmap
import android.view.View
import android.webkit.GeolocationPermissions
import mozilla.components.browser.session.Session.SecurityInfo
import org.mozilla.rocket.tabs.TabView.HitTarget

class TabViewEngineObserver(
        val session: Session
) : TabViewEngineSession.Observer {

    var tabView: TabView? = null

    override fun onTitleChange(title: String) {
        session.title = title
    }

    override fun onLoadingStateChange(loading: Boolean) {
        session.loading = loading
        // TODO: clear find result, just like AC EngineObserver did.
    }

    override fun onSecurityChange(secure: Boolean, host: String?, issuer: String?) {
        session.securityInfo = SecurityInfo(secure, host
                ?: "", issuer ?: "")
    }

    override fun onLocationChange(url: String) {
        session.url = url
    }

    override fun onProgress(progress: Int) {
        session.progress = progress
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
