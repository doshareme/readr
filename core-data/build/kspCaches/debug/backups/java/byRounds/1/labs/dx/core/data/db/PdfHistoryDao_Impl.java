package labs.dx.core.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PdfHistoryDao_Impl implements PdfHistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PdfHistoryEntity> __insertionAdapterOfPdfHistoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByDocumentId;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePinned;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCover;

  public PdfHistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPdfHistoryEntity = new EntityInsertionAdapter<PdfHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `pdf_history` (`documentId`,`uriString`,`displayName`,`mimeType`,`sizeBytes`,`lastOpenedAtEpochMillis`,`isPinned`,`pinnedAtEpochMillis`,`coverImagePath`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PdfHistoryEntity entity) {
        statement.bindString(1, entity.getDocumentId());
        statement.bindString(2, entity.getUriString());
        statement.bindString(3, entity.getDisplayName());
        if (entity.getMimeType() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getMimeType());
        }
        if (entity.getSizeBytes() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getSizeBytes());
        }
        statement.bindLong(6, entity.getLastOpenedAtEpochMillis());
        final int _tmp = entity.isPinned() ? 1 : 0;
        statement.bindLong(7, _tmp);
        if (entity.getPinnedAtEpochMillis() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getPinnedAtEpochMillis());
        }
        if (entity.getCoverImagePath() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getCoverImagePath());
        }
      }
    };
    this.__preparedStmtOfDeleteByDocumentId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pdf_history WHERE documentId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdatePinned = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE pdf_history\n"
                + "        SET isPinned = ?,\n"
                + "            pinnedAtEpochMillis = ?\n"
                + "        WHERE documentId = ?\n"
                + "        ";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCover = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE pdf_history\n"
                + "        SET coverImagePath = ?\n"
                + "        WHERE documentId = ?\n"
                + "        ";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final PdfHistoryEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPdfHistoryEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByDocumentId(final String documentId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByDocumentId.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, documentId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByDocumentId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePinned(final String documentId, final boolean pinned,
      final Long pinnedAtEpochMillis, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePinned.acquire();
        int _argIndex = 1;
        final int _tmp = pinned ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        if (pinnedAtEpochMillis == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, pinnedAtEpochMillis);
        }
        _argIndex = 3;
        _stmt.bindString(_argIndex, documentId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdatePinned.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCover(final String documentId, final String coverImagePath,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCover.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, coverImagePath);
        _argIndex = 2;
        _stmt.bindString(_argIndex, documentId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateCover.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PdfHistoryEntity>> observeAll() {
    final String _sql = "\n"
            + "        SELECT * FROM pdf_history\n"
            + "        ORDER BY \n"
            + "            isPinned DESC,\n"
            + "            CASE WHEN isPinned = 1 THEN COALESCE(pinnedAtEpochMillis, 0) ELSE lastOpenedAtEpochMillis END DESC,\n"
            + "            lastOpenedAtEpochMillis DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"pdf_history"}, new Callable<List<PdfHistoryEntity>>() {
      @Override
      @NonNull
      public List<PdfHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfUriString = CursorUtil.getColumnIndexOrThrow(_cursor, "uriString");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfLastOpenedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "lastOpenedAtEpochMillis");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfPinnedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "pinnedAtEpochMillis");
          final int _cursorIndexOfCoverImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverImagePath");
          final List<PdfHistoryEntity> _result = new ArrayList<PdfHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PdfHistoryEntity _item;
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final String _tmpUriString;
            _tmpUriString = _cursor.getString(_cursorIndexOfUriString);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final Long _tmpSizeBytes;
            if (_cursor.isNull(_cursorIndexOfSizeBytes)) {
              _tmpSizeBytes = null;
            } else {
              _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            }
            final long _tmpLastOpenedAtEpochMillis;
            _tmpLastOpenedAtEpochMillis = _cursor.getLong(_cursorIndexOfLastOpenedAtEpochMillis);
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final Long _tmpPinnedAtEpochMillis;
            if (_cursor.isNull(_cursorIndexOfPinnedAtEpochMillis)) {
              _tmpPinnedAtEpochMillis = null;
            } else {
              _tmpPinnedAtEpochMillis = _cursor.getLong(_cursorIndexOfPinnedAtEpochMillis);
            }
            final String _tmpCoverImagePath;
            if (_cursor.isNull(_cursorIndexOfCoverImagePath)) {
              _tmpCoverImagePath = null;
            } else {
              _tmpCoverImagePath = _cursor.getString(_cursorIndexOfCoverImagePath);
            }
            _item = new PdfHistoryEntity(_tmpDocumentId,_tmpUriString,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpLastOpenedAtEpochMillis,_tmpIsPinned,_tmpPinnedAtEpochMillis,_tmpCoverImagePath);
            _result.add(_item);
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

  @Override
  public Object getByDocumentId(final String documentId,
      final Continuation<? super PdfHistoryEntity> $completion) {
    final String _sql = "SELECT * FROM pdf_history WHERE documentId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PdfHistoryEntity>() {
      @Override
      @Nullable
      public PdfHistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfUriString = CursorUtil.getColumnIndexOrThrow(_cursor, "uriString");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfLastOpenedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "lastOpenedAtEpochMillis");
          final int _cursorIndexOfIsPinned = CursorUtil.getColumnIndexOrThrow(_cursor, "isPinned");
          final int _cursorIndexOfPinnedAtEpochMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "pinnedAtEpochMillis");
          final int _cursorIndexOfCoverImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverImagePath");
          final PdfHistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final String _tmpUriString;
            _tmpUriString = _cursor.getString(_cursorIndexOfUriString);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final Long _tmpSizeBytes;
            if (_cursor.isNull(_cursorIndexOfSizeBytes)) {
              _tmpSizeBytes = null;
            } else {
              _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            }
            final long _tmpLastOpenedAtEpochMillis;
            _tmpLastOpenedAtEpochMillis = _cursor.getLong(_cursorIndexOfLastOpenedAtEpochMillis);
            final boolean _tmpIsPinned;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsPinned);
            _tmpIsPinned = _tmp != 0;
            final Long _tmpPinnedAtEpochMillis;
            if (_cursor.isNull(_cursorIndexOfPinnedAtEpochMillis)) {
              _tmpPinnedAtEpochMillis = null;
            } else {
              _tmpPinnedAtEpochMillis = _cursor.getLong(_cursorIndexOfPinnedAtEpochMillis);
            }
            final String _tmpCoverImagePath;
            if (_cursor.isNull(_cursorIndexOfCoverImagePath)) {
              _tmpCoverImagePath = null;
            } else {
              _tmpCoverImagePath = _cursor.getString(_cursorIndexOfCoverImagePath);
            }
            _result = new PdfHistoryEntity(_tmpDocumentId,_tmpUriString,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpLastOpenedAtEpochMillis,_tmpIsPinned,_tmpPinnedAtEpochMillis,_tmpCoverImagePath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
