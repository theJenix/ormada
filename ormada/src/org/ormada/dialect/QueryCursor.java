package org.ormada.dialect;

import java.sql.SQLException;

/**
 * This interface defines an API for a query cursor.  A cursor
 * is returned from the Dialect's query methods, and used to iterate
 * over the results and populate the fetched entities.
 * 
 * @author Jesse Rosalia
 *
 */
public interface QueryCursor {

    /**
     * Close the cursor
     * 
     */
	void close();

	/**
	 * Test if the cursor is empty
	 * 
	 * @return
	 */
	boolean isEmpty();
	
	/**
	 * Move to the head of the cursor
	 * 
	 * @return True if there are more elements in the cursor, false if not.
	 */
	boolean moveToFirst();

	/**
	 * Test if the cursor is pointing past the last element in the cursor.
	 * 
	 */
	boolean isAfterLast();

	/**
	 * Move to the next element in the cursor
	 * 
	 * @return True if the cursor is pointing to the next valid element, false if the cursor
	 * is pointing past the last element.
	 */
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
