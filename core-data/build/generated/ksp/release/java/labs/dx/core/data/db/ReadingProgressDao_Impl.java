package labs.dx.core.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ReadingProgressDao_Impl implements ReadingProgressDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ReadingProgressEntity> __insertionAdapterOfReadingProgressEntity;

  public ReadingProgressDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfReadingProgressEntity = new EntityInsertionAdapter<ReadingProgressEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `reading_progress` (`documentId`,`globalWordIndex`,`pageIndex`,`sentenceIndex`,`paragraphIndex`,`updatedAtEpochMillis`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ReadingProgressEntity entity) {
        statement.bindString(1, entity.getDocumentId());
        statement.bindLong(2, entity.getGlobalWordIndex());
        statement.bindLong(3, entity.getPageIndex());
        statement.bindLong(4, entity.getSentenceIndex());
        statement.bindLong(5, entity.getParagraphIndex());
        statement.bindLong(6, entity.getUpdatedAtEpochMillis());
      }
    };
  }

  @Override
  public Object upsert(final ReadingProgressEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfReadingProgressEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<ReadingProgressEntity> observeByDocumentId(final String documentId) {
    final String _sql = "SELECT * FROM reading_progress WHERE documentId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"reading_progress"}, new Callable<ReadingProgressEntity>() {
      @Override
      @Nullable
      public ReadingProgressEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfGlobalWordIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "globalWordIndex");
          final int _cursorIndexOfPageIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "pageIndex");
          final int _cursorIndexOfSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "sentenceIndex");
          final int _cursorIndexOfParagraphIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "paragraphIndex");
          final int _cursorIndexOfUpdatedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtEpochMillis");
          final ReadingProgressEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final int _tmpGlobalWordIndex;
            _tmpGlobalWordIndex = _cursor.getInt(_cursorIndexOfGlobalWordIndex);
            final int _tmpPageIndex;
            _tmpPageIndex = _cursor.getInt(_cursorIndexOfPageIndex);
            final int _tmpSentenceIndex;
            _tmpSentenceIndex = _cursor.getInt(_cursorIndexOfSentenceIndex);
            final int _tmpParagraphIndex;
            _tmpParagraphIndex = _cursor.getInt(_cursorIndexOfParagraphIndex);
            final long _tmpUpdatedAtEpochMillis;
            _tmpUpdatedAtEpochMillis = _cursor.getLong(_cursorIndexOfUpdatedAtEpochMillis);
            _result = new ReadingProgressEntity(_tmpDocumentId,_tmpGlobalWordIndex,_tmpPageIndex,_tmpSentenceIndex,_tmpParagraphIndex,_tmpUpdatedAtEpochMillis);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
