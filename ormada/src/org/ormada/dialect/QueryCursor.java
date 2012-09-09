package org.ormada.dialect;

import java.sql.SQLException;

public interface QueryCursor {

	void close();

	boolean isEmpty();

	boolean moveToFirst();

	boolean isAfterLast();

	boolean moveToNext();

	int getColumnCount() throws SQLException;

	String getColumnName(int col) throws SQLException;

	long getLong(int col) throws SQLException;

	int getInt(int col) throws SQLException;

	short getShort(int col) throws SQLException;

	float getFloat(int col) throws SQLException;

	double getDouble(int col) throws SQLException;

	byte[] getBlob(int col) throws SQLException;

	String getString(int col) throws SQLException;


}
