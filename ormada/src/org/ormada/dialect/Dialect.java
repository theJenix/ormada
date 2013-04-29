package org.ormada.dialect;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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

	/**
	 * Save multiple entities to the database.  This performs a save operation (insert or update)
	 * across multiple objects across multiple tables in an efficient way.
	 * 
	 * @param valueMap A map of table names to list of ValueSet objects to save
	 * @return A map of table to list of ids for the objects saved to that table.  This entry will have an id
	 * regardless of whether the row was inserted or updated (for correspondence with the valueMap)
	 */
    Map<String, List<Long>> bulkSave(Map<String, List<V>> valueMap);

    /**
     * Count the entries in the specified table that conform to the specified where clause.
     * 
     * @param table
     * @param whereClause
     * @param whereParams
     * @return
     * @throws SQLException
     */
    long count(String table, String whereClause, String[] whereParams) throws SQLException;

    /**
	 * Raw insert into the database.  This will insert the values set in the ValueSet
	 * into a new row in the specified table.
	 * 
	 * @param table
	 * @param values
	 * @return
	 * @throws SQLException
	 */
	long insert(String table, V values) throws SQLException;

    /**
     * Save an entity to the database.  This method uses the id set inside the ValueSet
     * to determine whether to insert a new row or update an existing row for the
     * given entity
     * 
     * @param table
     * @param values
     * @return
     */
    long save(String table, V values) throws SQLException;

    /**
     * Raw update of a row or rows in the database.  This will update the values set in the ValueSet
     * in rows that correspond with the where clause/params.
     * 
     * @param table
     * @param values
     * @return
     * @throws SQLException
     */
	void update(String table, V values, String whereClause,	String[] whereParams) throws SQLException;

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy) throws SQLException;

	QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having, String orderBy, String limit) throws SQLException;

//    Map<String, List<Long>> bulkInsert(Map<String, List<V>> values);

}
