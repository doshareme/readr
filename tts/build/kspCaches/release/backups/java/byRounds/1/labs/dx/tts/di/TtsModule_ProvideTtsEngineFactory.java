package labs.dx.tts.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.tts.engine.HybridTtsEngine;
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
public final class TtsModule_ProvideTtsEngineFactory implements Factory<TtsEngine> {
  private final Provider<HybridTtsEngine> implProvider;

  public TtsModule_ProvideTtsEngineFactory(Provider<HybridTtsEngine> implProvider) {
    this.implProvider = implProvider;
  }

  @Override
  public TtsEngine get() {
    return provideTtsEngine(implProvider.get());
  }

  public static TtsModule_ProvideTtsEngineFactory create(Provider<HybridTtsEngine> implProvider) {
    return new TtsModule_ProvideTtsEngineFactory(implProvider);
  }

  public static TtsEngine provideTtsEngine(HybridTtsEngine impl) {
    return Preconditions.checkNotNullFromProvides(TtsModule.INSTANCE.provideTtsEngine(impl));
  }
}
