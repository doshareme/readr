package labs.dx.tts.engine;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class RumikCloudTtsEngine_Factory implements Factory<RumikCloudTtsEngine> {
  @Override
  public RumikCloudTtsEngine get() {
    return newInstance();
  }

  public static RumikCloudTtsEngine_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RumikCloudTtsEngine newInstance() {
    return new RumikCloudTtsEngine();
  }

  private static final class InstanceHolder {
    private static final RumikCloudTtsEngine_Factory INSTANCE = new RumikCloudTtsEngine_Factory();
  }
}
