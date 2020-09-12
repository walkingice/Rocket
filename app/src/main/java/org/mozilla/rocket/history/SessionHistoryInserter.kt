package org.mozilla.rocket.history

import android.text.TextUtils
import org.mozilla.rocket.tabs.Session
import java.util.WeakHashMap

/**
 * TODO: This class records some intermediate data of each tab to avoid inserting duplicate
 * history, maybe it'd be better to make these data as per-tab data
 */
internal class SessionHistoryInserter {
    private val failingUrls = WeakHashMap<Session, String?>()

    // Some url may have two onPageFinished for the same url. filter them out to avoid
    // adding twice to the history.
    private val lastInsertedUrls = WeakHashMap<Session, String>()
    fun onTabStarted(tab: Session) {
        lastInsertedUrls.remove(tab)
    }

    fun onTabFinished(tab: Session, url: String) {
        insertBrowsingHistory(tab, url)
    }

    fun updateFailingUrl(tab: Session, url: String?, updateFromError: Boolean) {
        val failingUrl = failingUrls[tab]
        if (!updateFromError && url != failingUrl) {
            failingUrls.remove(tab)
        } else {
            failingUrls[tab] = url
        }
    }

    private fun insertBrowsingHistory(tab: Session, urlToBeInserted: String) {
        val lastInsertedUrl = getLastInsertedUrl(tab)
        if (TextUtils.isEmpty(urlToBeInserted)) {
            return
        }
        if (urlToBeInserted == getFailingUrl(tab)) {
            return
        }
        if (urlToBeInserted == lastInsertedUrl) {
            return
        }
        tab.engineSession?.tabView?.insertBrowsingHistory()
        lastInsertedUrls[tab] = urlToBeInserted
    }

    private fun getFailingUrl(tab: Session): String {
        val url = failingUrls[tab]
        return requireNotNull(if (TextUtils.isEmpty(url)) "" else url)
    }

    private fun getLastInsertedUrl(tab: Session): String {
        val url = lastInsertedUrls[tab]
        return requireNotNull(if (TextUtils.isEmpty(url)) "" else url)
    }
}
