package labs.dx.core.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.core.data.db.PdfHistoryDao;
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
public final class DataProvidesModule_ProvidePdfHistoryDaoFactory implements Factory<PdfHistoryDao> {
  private final Provider<ReadrDatabase> databaseProvider;

  public DataProvidesModule_ProvidePdfHistoryDaoFactory(Provider<ReadrDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public PdfHistoryDao get() {
    return providePdfHistoryDao(databaseProvider.get());
  }

  public static DataProvidesModule_ProvidePdfHistoryDaoFactory create(
      Provider<ReadrDatabase> databaseProvider) {
    return new DataProvidesModule_ProvidePdfHistoryDaoFactory(databaseProvider);
  }

  public static PdfHistoryDao providePdfHistoryDao(ReadrDatabase database) {
    return Preconditions.checkNotNullFromProvides(DataProvidesModule.INSTANCE.providePdfHistoryDao(database));
  }
}
