package org.andrormeda.dialect;

import java.util.HashMap;
import java.util.Map;

import org.ormada.dialect.QueryCursor;

import android.database.Cursor;

public class SQLiteCursor implements QueryCursor {

	private Cursor cursor;
    private int columnCount;
    private Map<Integer, String> nameCache;

	public SQLiteCursor(Cursor cursor) {
		this.cursor      = cursor;
		this.columnCount = this.cursor != null ? this.cursor.getColumnCount() : 0;
		this.nameCache   = new HashMap<Integer, String>();
	}

	@Override
	public void close() {
		this.cursor.close();
	}

	@Override
	public boolean isEmpty() {
		return this.cursor != null ? this.cursor.getCount() == 0 : true;
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
		return this.columnCount;
	}

	@Override
	public String getColumnName(int col) {
	    if (!this.nameCache.containsKey(col)) {
	        String name = this.cursor != null ? this.cursor.getColumnName(col) : null;
	        this.nameCache.put(col, name);
	    }
	    
	    return this.nameCache.get(col);
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
