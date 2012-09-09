package org.ormada.dialect;

import java.sql.SQLException;

import org.ormada.ORMDataSource;

public interface Dialect<V extends ValueSet> {

	void close() throws SQLException;
	
//	void create(String table, )
	
//	void drop(String table);
	
	/**
	 * Get the column type definition for the type class passed in
	 * 
	 * Implementations must handle both primitives and capital letter types according to one specific rule:
	 *     Primitives are not nullable, all other types are.
	 * 
	 * The core framework expects and relies on this convention.
	 * 
	 * @param typeClass
	 * @return
	 */
	String getColumnType(Class<?> typeClass);

	/**
	 * Get the column type definition for the primary key.
	 * 
	 * Implementations must provide a definition for a long integer type, as specified in the parameter
	 *  (e.g. this must be equivalent to getColumnType(Long.class) with all required primary key modifiers).
	 * 
	 * @return
	 */
    String getPrimaryKeyColumnType();

	ValueSet prepareValueSet();
	
	/**
	 * 
	 * @param orm The ORMDataSource object that is managing the object relationships.  This is likely used during the open process to create and upgrade tables.
	 * @throws SQLException 
	 */
	void open(ORMDataSource orm) throws SQLException;

    boolean isOpen();

	void execSQL(String stmt) throws SQLException;

	void delete(String table, String whereClause, String[] whereParams) throws SQLException;

	long insert(String table, V values) throws SQLException;

	void update(String table, V values, String whereClause,	String[] whereParams) throws SQLException;

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy) throws SQLException;

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy, String limit) throws SQLException;

}
