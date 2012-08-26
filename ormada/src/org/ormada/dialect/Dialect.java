package org.ormada.dialect;

import java.util.Map;

import org.ormada.ORMDataSource;

public interface Dialect<V extends ValueSet> {

	void close();
	
//	void create(String table, )
	
//	void drop(String table);
	/**
	 * 
	 * @param orm The ORMDataSource object that is managing the object relationships.  This is likely used during the open process to create and upgrade tables.
	 */
	void open(ORMDataSource orm);

	void execSQL(String stmt);

	void delete(String table, String whereClause,	String[] whereParams);

	long insert(String table, V values);

	void update(String table, V values, String whereClause,	String[] whereParams);

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy);

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy, String limit);

	ValueSet prepareValueSet();
}
