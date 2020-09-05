package org.mozilla.rocket.content.di

import dagger.Module
import dagger.Provides
import org.mozilla.rocket.content.common.ui.ContentTabBottomBarViewModel
import org.mozilla.rocket.content.common.ui.ContentTabTelemetryViewModel
import org.mozilla.rocket.content.common.ui.TabSwipeTelemetryViewModel

@Module
object ContentModule {

    @JvmStatic
    @Provides
    fun provideContentTabBottomBarViewModel(): ContentTabBottomBarViewModel = ContentTabBottomBarViewModel()

    @JvmStatic
    @Provides
    fun provideContentTabTelemetryViewModel(): ContentTabTelemetryViewModel = ContentTabTelemetryViewModel()

    @JvmStatic
    @Provides
    fun provideTabSwipeTelemetryViewModel(): TabSwipeTelemetryViewModel = TabSwipeTelemetryViewModel()
}
