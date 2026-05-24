package labs.dx.tts.engine;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class HybridTtsEngine_Factory implements Factory<HybridTtsEngine> {
  private final Provider<AndroidTtsEngine> localEngineProvider;

  private final Provider<RumikCloudTtsEngine> cloudEngineProvider;

  public HybridTtsEngine_Factory(Provider<AndroidTtsEngine> localEngineProvider,
      Provider<RumikCloudTtsEngine> cloudEngineProvider) {
    this.localEngineProvider = localEngineProvider;
    this.cloudEngineProvider = cloudEngineProvider;
  }

  @Override
  public HybridTtsEngine get() {
    return newInstance(localEngineProvider.get(), cloudEngineProvider.get());
  }

  public static HybridTtsEngine_Factory create(Provider<AndroidTtsEngine> localEngineProvider,
      Provider<RumikCloudTtsEngine> cloudEngineProvider) {
    return new HybridTtsEngine_Factory(localEngineProvider, cloudEngineProvider);
  }

  public static HybridTtsEngine newInstance(AndroidTtsEngine localEngine,
      RumikCloudTtsEngine cloudEngine) {
    return new HybridTtsEngine(localEngine, cloudEngine);
  }
}
