package labs.dx.core.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.core.data.db.ReadingProgressDao;
import labs.dx.core.data.db.ReadrDatabase;

@ScopeMetadata
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
public final class DataProvidesModule_ProvideReadingProgressDaoFactory implements Factory<ReadingProgressDao> {
  private final Provider<ReadrDatabase> databaseProvider;

  public DataProvidesModule_ProvideReadingProgressDaoFactory(
      Provider<ReadrDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ReadingProgressDao get() {
    return provideReadingProgressDao(databaseProvider.get());
  }

  public static DataProvidesModule_ProvideReadingProgressDaoFactory create(
      Provider<ReadrDatabase> databaseProvider) {
    return new DataProvidesModule_ProvideReadingProgressDaoFactory(databaseProvider);
  }

  public static ReadingProgressDao provideReadingProgressDao(ReadrDatabase database) {
    return Preconditions.checkNotNullFromProvides(DataProvidesModule.INSTANCE.provideReadingProgressDao(database));
  }
}
