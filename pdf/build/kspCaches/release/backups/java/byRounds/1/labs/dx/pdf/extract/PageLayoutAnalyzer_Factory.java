package labs.dx.pdf.extract;

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
public final class PageLayoutAnalyzer_Factory implements Factory<PageLayoutAnalyzer> {
  @Override
  public PageLayoutAnalyzer get() {
    return newInstance();
  }

  public static PageLayoutAnalyzer_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PageLayoutAnalyzer newInstance() {
    return new PageLayoutAnalyzer();
  }

  private static final class InstanceHolder {
    private static final PageLayoutAnalyzer_Factory INSTANCE = new PageLayoutAnalyzer_Factory();
  }
}
