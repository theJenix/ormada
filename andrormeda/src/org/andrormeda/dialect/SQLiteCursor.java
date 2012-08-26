package org.andrormeda.dialect;

import org.ormada.dialect.QueryCursor;

import android.database.Cursor;

public class SQLiteCursor implements QueryCursor {

	private Cursor cursor;

	public SQLiteCursor(Cursor cursor) {
		this.cursor = cursor;
	}

	@Override
	public void close() {
		this.cursor.close();
	}

	@Override
	public int getCount() {
		return this.cursor != null ? this.cursor.getCount() : 0;
	}

	@Override
	public boolean moveToFirst() {
		return this.cursor != null ? this.cursor.moveToFirst() : false;
	}

	@Override
	public boolean isAfterLast() {
		return this.cursor != null ? this.cursor.isAfterLast() : true;
	}

	@Override
	public boolean moveToNext() {
		return this.cursor != null ? this.cursor.moveToNext() : false;
	}

	@Override
	public int getColumnCount() {
		return this.cursor != null ? this.cursor.getColumnCount() : 0;
	}

	@Override
	public String getColumnName(int col) {
		return this.cursor != null ? this.cursor.getColumnName(col) : null;
	}

	@Override
	public long getLong(int col) {
		return this.cursor.getLong(col);
	}

	@Override
	public int getInt(int col) {
		return this.cursor.getInt(col);
	}

	@Override
	public short getShort(int col) {
		return this.cursor.getShort(col);
	}

	@Override
	public float getFloat(int col) {
		return this.cursor.getFloat(col);
	}

	@Override
	public double getDouble(int col) {
		return this.cursor.getDouble(col);
	}

	@Override
	public byte[] getBlob(int col) {
		return this.cursor.getBlob(col);
	}

	@Override
	public String getString(int col) {
		return this.cursor.getString(col);
	}
}
