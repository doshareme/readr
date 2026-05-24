package labs.dx.tts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import labs.dx.tts.engine.AndroidTtsEngine
import labs.dx.tts.engine.TtsEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(
        impl: AndroidTtsEngine
    ): TtsEngine
}
