package labs.dx.playback.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import labs.dx.core.domain.repository.NarrationController
import labs.dx.playback.controller.NarrationPlaybackController

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindNarrationController(
        impl: NarrationPlaybackController
    ): NarrationController
}
