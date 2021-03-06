package org.mozilla.rocket.home.topsites.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.StrictMode
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.airbnb.lottie.LottieAnimationView
import org.mozilla.focus.R
import org.mozilla.focus.utils.DimenUtils
import org.mozilla.icon.FavIconUtils
import org.mozilla.rocket.adapter.AdapterDelegate
import org.mozilla.rocket.adapter.DelegateAdapter
import org.mozilla.rocket.chrome.ChromeViewModel
import org.mozilla.rocket.nightmode.themed.ThemedTextView
import org.mozilla.strictmodeviolator.StrictModeViolation

class SiteAdapterDelegate(
    private val topSiteClickListener: TopSiteClickListener,
    private val chromeViewModel: ChromeViewModel
) : AdapterDelegate {
    override fun onCreateViewHolder(view: View): DelegateAdapter.ViewHolder =
        SiteViewHolder(view, topSiteClickListener, chromeViewModel)
}

class SiteViewHolder(
    override val containerView: View,
    private val topSiteClickListener: TopSiteClickListener,
    private val chromeViewModel: ChromeViewModel
) : DelegateAdapter.ViewHolder(containerView) {

    override fun bind(uiModel: DelegateAdapter.UiModel) {
        val site = uiModel as Site
        // R.layout.item_top_site
        val contentImg = containerView.findViewById<AppCompatImageView>(R.id.content_image)
        val contentText = containerView.findViewById<ThemedTextView>(R.id.text)
        val pinIndicator = containerView.findViewById<ViewGroup>(R.id.pin_indicator)
        val contentImgMask = containerView.findViewById<LottieAnimationView>(R.id.content_image_mask)
        when (site) {
            is Site.UrlSite -> {
                contentText.text = site.title

                // Tried AsyncTask and other simple offloading, the performance drops significantly.
                // FIXME: 9/21/18 by saving bitmap color, cause FaviconUtils.getDominantColor runs slow.
                // Favicon
                val favicon = StrictModeViolation.tempGrant(
                    { obj: StrictMode.ThreadPolicy.Builder -> obj.permitDiskReads() },
                    { getFavicon(itemView.context, site) }
                )
                contentImg.visibility = View.VISIBLE
                contentImg.imageTintList = null
                contentImg.setImageBitmap(favicon)

                // Pin
                PinViewWrapper(pinIndicator).run {
                    visibility = when (site) {
                        is Site.UrlSite.FixedSite -> View.GONE
                        is Site.UrlSite.RemovableSite -> if (site.isPinned) View.VISIBLE else View.GONE
                    }
                    setPinColor(getBackgroundColor(favicon))
                }

                itemView.setOnClickListener { topSiteClickListener.onTopSiteClicked(site, adapterPosition) }
                if (site is Site.UrlSite.FixedSite) {
                    itemView.setOnLongClickListener(null)
                } else {
                    itemView.setOnLongClickListener {
                        it.tag = TOP_SITE_LONG_CLICK_TARGET
                        topSiteClickListener.onTopSiteLongClicked(site, adapterPosition)
                    }
                }

                if (site.highlight) {
                    contentImgMask.visibility = View.VISIBLE
                    contentImgMask.playAnimation()
                } else {
                    contentImgMask.cancelAnimation()
                    contentImgMask.visibility = View.GONE
                }
            }
            is Site.EmptyHintSite -> {
                contentText.setText(R.string.add_top_site_placeholder)

                contentImg.setImageResource(R.drawable.action_add)
                ViewCompat.setBackgroundTintList(contentImg, ColorStateList.valueOf(Color.WHITE))
                val contextThemeWrapper = ContextThemeWrapper(contentImg.context, 0)
                contentImg.imageTintList = ContextCompat.getColorStateList(contextThemeWrapper, R.color.paletteDarkGreyE100)
                contentImg.visibility = View.VISIBLE

                pinIndicator.visibility = View.GONE

                itemView.setOnClickListener { topSiteClickListener.onTopSiteClicked(site, adapterPosition) }
                itemView.setOnLongClickListener(null)
            }
            is Site.DummySite -> {
                itemView.setOnLongClickListener {
                    it.tag = TOP_SITE_LONG_CLICK_TARGET
                    topSiteClickListener.onTopSiteLongClicked(site, adapterPosition)
                }
            }
        }
        if (site != Site.DummySite) {
            contentText.setDarkTheme(chromeViewModel.isDarkTheme.value == true)
        }
    }

    private fun getFavicon(context: Context, site: Site.UrlSite): Bitmap {
        val faviconUri = site.iconUri
        var favicon: Bitmap? = null
        if (faviconUri != null) {
            favicon = FavIconUtils.getBitmapFromUri(context, faviconUri)
        }

        return getBestFavicon(context.resources, site.url, favicon)
    }

    private fun getBestFavicon(res: Resources, url: String, favicon: Bitmap?): Bitmap {
        return when {
            favicon == null -> createFavicon(res, url, Color.WHITE)
            DimenUtils.iconTooBlurry(res, favicon.width) -> createFavicon(res, url, FavIconUtils.getDominantColor(favicon))
            else -> favicon
        }
    }

    private fun createFavicon(resources: Resources, url: String, backgroundColor: Int): Bitmap {
        return DimenUtils.getInitialBitmap(
            resources, FavIconUtils.getRepresentativeCharacter(url),
            backgroundColor
        )
    }

    private fun getBackgroundColor(bitmap: Bitmap): Int = calculateBackgroundColor(bitmap)

    private fun calculateBackgroundColor(favicon: Bitmap): Int {
        val dominantColor = FavIconUtils.getDominantColor(favicon)
        val alpha = dominantColor and -0x1000000
        // Add 25% white to dominant Color
        val red = addWhiteToColorCode(dominantColor and 0x00FF0000 shr 16, 0.25f) shl 16
        val green = addWhiteToColorCode(dominantColor and 0x0000FF00 shr 8, 0.25f) shl 8
        val blue = addWhiteToColorCode(dominantColor and 0x000000FF, 0.25f)
        return alpha + red + green + blue
    }

    private fun addWhiteToColorCode(colorCode: Int, percentage: Float): Int {
        var result = (colorCode + 0xFF * percentage / 2).toInt()
        if (result > 0xFF) {
            result = 0xFF
        }
        return result
    }

    companion object {
        const val TOP_SITE_LONG_CLICK_TARGET = "top_site_long_click_target"
    }
}

sealed class Site : DelegateAdapter.UiModel() {
    sealed class UrlSite(
        open val id: Long,
        open val title: String,
        open val url: String,
        open val iconUri: String?,
        open val viewCount: Long,
        open val lastViewTimestamp: Long,
        open var highlight: Boolean = false
    ) : Site() {
        data class FixedSite(
            override val id: Long,
            override val title: String,
            override val url: String,
            override val iconUri: String?,
            override val viewCount: Long,
            override val lastViewTimestamp: Long
        ) : UrlSite(id, title, url, iconUri, viewCount, lastViewTimestamp)

        data class RemovableSite(
            override val id: Long,
            override val title: String,
            override val url: String,
            override val iconUri: String?,
            override val viewCount: Long,
            override val lastViewTimestamp: Long,
            val isDefault: Boolean,
            val isPinned: Boolean
        ) : UrlSite(id, title, url, iconUri, viewCount, lastViewTimestamp)
    }

    object EmptyHintSite : Site()
    object DummySite : Site()
}

fun Site.UrlSite.toSiteModel(): org.mozilla.focus.history.model.Site =
    org.mozilla.focus.history.model.Site(
        id,
        title,
        url,
        viewCount,
        lastViewTimestamp,
        iconUri
    ).apply {
        isDefault = when (this@toSiteModel) {
            is Site.UrlSite.FixedSite -> true
            is Site.UrlSite.RemovableSite -> this@toSiteModel.isDefault
        }
    }

interface TopSiteClickListener {
    fun onTopSiteClicked(site: Site, position: Int)
    fun onTopSiteLongClicked(site: Site, position: Int): Boolean
}
