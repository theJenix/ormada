package org.ormada.dialect;

public interface QueryCursor {

	void close();

	int getCount();

	boolean moveToFirst();

	boolean isAfterLast();

	boolean moveToNext();

	int getColumnCount();

	String getColumnName(int col);

	long getLong(int col);

	int getInt(int col);

	short getShort(int col);

	float getFloat(int col);

	double getDouble(int col);

	byte[] getBlob(int col);

	String getString(int col);

}
