package labs.dx.pdf.session;

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
public final class AndroidPdfRepository_Factory implements Factory<AndroidPdfRepository> {
  private final Provider<Context> contextProvider;

  public AndroidPdfRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidPdfRepository get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidPdfRepository_Factory create(Provider<Context> contextProvider) {
    return new AndroidPdfRepository_Factory(contextProvider);
  }

  public static AndroidPdfRepository newInstance(Context context) {
    return new AndroidPdfRepository(context);
  }
}
