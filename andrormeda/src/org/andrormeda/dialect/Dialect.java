package org.andrormeda.dialect;

import org.andrormeda.ORMDataSource;

import android.content.ContentValues;
import android.database.Cursor;

public interface Dialect {

	void close();
	
	/**
	 * 
	 * @param orm The ORMDataSource object that is managing the object relationships.  This is likely used during the open process to create and upgrade tables.
	 */
	void open(ORMDataSource orm);

//	void create(String table, )
	void execSQL(String stmt);

	void delete(String table, String whereClause,	String[] whereParams);

	long insert(String table, ContentValues values);

	void update(String table, ContentValues values, String whereClause,	String[] whereParams);

	Cursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy);

	Cursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy, String limit);
}
