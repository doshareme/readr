package labs.dx.playback.controller;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.tts.engine.TtsEngine;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class NarrationPlaybackController_Factory implements Factory<NarrationPlaybackController> {
  private final Provider<TtsEngine> ttsEngineProvider;

  public NarrationPlaybackController_Factory(Provider<TtsEngine> ttsEngineProvider) {
    this.ttsEngineProvider = ttsEngineProvider;
  }

  @Override
  public NarrationPlaybackController get() {
    return newInstance(ttsEngineProvider.get());
  }

  public static NarrationPlaybackController_Factory create(Provider<TtsEngine> ttsEngineProvider) {
    return new NarrationPlaybackController_Factory(ttsEngineProvider);
  }

  public static NarrationPlaybackController newInstance(TtsEngine ttsEngine) {
    return new NarrationPlaybackController(ttsEngine);
  }
}
