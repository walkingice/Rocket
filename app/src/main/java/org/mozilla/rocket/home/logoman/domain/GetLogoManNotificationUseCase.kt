package org.mozilla.rocket.home.logoman.domain

import androidx.lifecycle.LiveData
import org.mozilla.rocket.extension.map
import org.mozilla.rocket.home.logoman.data.LogoManNotificationRepo
import org.mozilla.rocket.home.logoman.data.Notification

class GetLogoManNotificationUseCase(
    private val logoManNotificationRepo: LogoManNotificationRepo
) {

    operator fun invoke(): LiveData<Notification?> = logoManNotificationRepo.getNotification().map { it?.toLogoManNotification() }

    sealed class Notification(
        val id: String,
        val title: String,
        val subtitle: String?,
        val imageUrl: String?,
        val action: LogoManAction?,
        val type: String?
    ) {
        class RemoteNotification(
            id: String,
            title: String,
            subtitle: String?,
            imageUrl: String?,
            action: LogoManAction.UriAction?,
            type: String?
        ) : Notification(id, title, subtitle, imageUrl, action, type)
    }

    sealed class LogoManAction {
        data class UriAction(val action: String) : LogoManAction()

        fun getLink(): String? = when (this) {
            is UriAction -> action
        }
    }
}

private fun Notification.toLogoManNotification() = GetLogoManNotificationUseCase.Notification.RemoteNotification(
            messageId,
            title,
            subtitle,
            imageUrl,
            action?.let { GetLogoManNotificationUseCase.LogoManAction.UriAction(it) },
            type
        )