package labs.dx.tts.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import labs.dx.tts.engine.HybridTtsEngine
import labs.dx.tts.engine.TtsEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {
    @Provides
    @Singleton
    fun provideTtsEngine(
        impl: HybridTtsEngine
    ): TtsEngine = impl
}
