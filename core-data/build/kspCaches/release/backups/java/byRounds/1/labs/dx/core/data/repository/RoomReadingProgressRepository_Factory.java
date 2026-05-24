package labs.dx.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import labs.dx.core.data.db.ReadingProgressDao;

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
public final class RoomReadingProgressRepository_Factory implements Factory<RoomReadingProgressRepository> {
  private final Provider<ReadingProgressDao> daoProvider;

  public RoomReadingProgressRepository_Factory(Provider<ReadingProgressDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomReadingProgressRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomReadingProgressRepository_Factory create(
      Provider<ReadingProgressDao> daoProvider) {
    return new RoomReadingProgressRepository_Factory(daoProvider);
  }

  public static RoomReadingProgressRepository newInstance(ReadingProgressDao dao) {
    return new RoomReadingProgressRepository(dao);
  }
}
