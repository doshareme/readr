package labs.dx.core.data.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.core.data.db.ReadrDatabase;

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
public final class DataProvidesModule_ProvideDatabaseFactory implements Factory<ReadrDatabase> {
  private final Provider<Context> contextProvider;

  public DataProvidesModule_ProvideDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ReadrDatabase get() {
    return provideDatabase(contextProvider.get());
  }

  public static DataProvidesModule_ProvideDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DataProvidesModule_ProvideDatabaseFactory(contextProvider);
  }

  public static ReadrDatabase provideDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DataProvidesModule.INSTANCE.provideDatabase(context));
  }
}
