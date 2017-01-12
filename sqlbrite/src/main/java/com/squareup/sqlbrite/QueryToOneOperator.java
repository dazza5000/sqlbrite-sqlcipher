package com.squareup.sqlbrite;

import android.database.Cursor;
import rx.Observable;
import rx.Subscriber;
import rx.exceptions.Exceptions;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Func1;

final class QueryToOneOperator<T> implements Observable.Operator<T, SqlBrite.Query> {
  final Func1<Cursor, T> mapper;
  boolean emitDefault;
  T defaultValue;

  QueryToOneOperator(Func1<Cursor, T> mapper, boolean emitDefault, T defaultValue) {
    this.mapper = mapper;
    this.emitDefault = emitDefault;
    this.defaultValue = defaultValue;
  }

  @Override public Subscriber<? super SqlBrite.Query> call(final Subscriber<? super T> subscriber) {
    return new Subscriber<SqlBrite.Query>(subscriber) {
      @Override public void onNext(SqlBrite.Query query) {
        try {
          boolean emit = false;
          T item = null;
          Cursor cursor = query.run();
          if (cursor != null) {
            try {
              if (cursor.moveToNext()) {
                item = mapper.call(cursor);
                emit = true;
                if (cursor.moveToNext()) {
                  throw new IllegalStateException("Cursor returned more than 1 row");
                }
              }
            } finally {
              cursor.close();
            }
          }
          if (!subscriber.isUnsubscribed()) {
            if (emit) {
              subscriber.onNext(item);
            } else if (emitDefault) {
              subscriber.onNext(defaultValue);
            } else {
              request(1L); // Account upstream for the lack of downstream emission.
            }
          }
        } catch (Throwable e) {
          Exceptions.throwIfFatal(e);
          onError(OnErrorThrowable.addValueAsLastCause(e, query.toString()));
        }
      }

      @Override public void onCompleted() {
        subscriber.onCompleted();
      }

      @Override public void onError(Throwable e) {
        subscriber.onError(e);
      }
    };
  }
}
