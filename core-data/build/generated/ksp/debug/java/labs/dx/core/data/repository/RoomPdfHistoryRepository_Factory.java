package labs.dx.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.core.data.db.PdfHistoryDao;

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
public final class RoomPdfHistoryRepository_Factory implements Factory<RoomPdfHistoryRepository> {
  private final Provider<PdfHistoryDao> daoProvider;

  public RoomPdfHistoryRepository_Factory(Provider<PdfHistoryDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomPdfHistoryRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomPdfHistoryRepository_Factory create(Provider<PdfHistoryDao> daoProvider) {
    return new RoomPdfHistoryRepository_Factory(daoProvider);
  }

  public static RoomPdfHistoryRepository newInstance(PdfHistoryDao dao) {
    return new RoomPdfHistoryRepository(dao);
  }
}
