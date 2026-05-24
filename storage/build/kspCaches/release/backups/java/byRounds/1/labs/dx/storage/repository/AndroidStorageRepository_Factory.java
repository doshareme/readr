package labs.dx.storage.repository;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AndroidStorageRepository_Factory implements Factory<AndroidStorageRepository> {
  private final Provider<Context> contextProvider;

  public AndroidStorageRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidStorageRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidStorageRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidStorageRepository_Factory(contextProvider);
  }

  public static AndroidStorageRepository newInstance(Context context) {
    return new AndroidStorageRepository(context);
  }
}
