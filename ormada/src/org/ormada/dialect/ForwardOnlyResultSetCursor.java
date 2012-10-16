package org.ormada.dialect;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * A generic QueryCursor implementation for "forward only" JDBC ResultSet objects.
 * 
 * Forward only ResultSets only support a handful of the functions defined in the ResultSet
 * object.  Sucks to be us.  This class is written accordingly, to build an intelligent cursor
 * using only the next() function.  Note that because of this, the cursor will be "forward only", 
 * in that it cannot be rewound or replayed.  This should be ok for our ORM implementation.
 * 
 * NOTE: JDBC uses 1 based column indexing, and the ORM framework uses 0 based indexing.  This class adjusts for that.
 * @author Jesse Rosalia
 *
 */
public class ForwardOnlyResultSetCursor implements QueryCursor {

    private ResultSet         resultSet;
    private ResultSetMetaData rsMetaData;
    private boolean first;
    private boolean next;

    public ForwardOnlyResultSetCursor(ResultSet resultSet) throws SQLException {
        this.resultSet  = resultSet;
        this.rsMetaData = resultSet.getMetaData();
        
        this.first = this.resultSet.next();
        this.next  = this.first;
    }

    @Override
	public void close() {
        try {
            this.resultSet.close();
        } catch (SQLException e) {
            //we're closing...nothing to do
        }
	}

	@Override
	public boolean isEmpty() {
	    //FIXME: this isnt right, as this will return true after moving past the first element
        return !this.first;
	}

	@Override
	public boolean moveToFirst() {
		try {
            return this.first;
        } finally {
            this.first = false;
        }
	}

	@Override
	public boolean isAfterLast() {
        return !this.next;
	}

	@Override
	public boolean moveToNext() {
        try {
            this.next = this.resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            this.next = false;
        }
        return this.next;
	}

	@Override
	public int getColumnCount() throws SQLException {
	    return this.rsMetaData.getColumnCount();
	}

	@Override
	public String getColumnName(int col) throws SQLException {
	    return this.rsMetaData.getColumnLabel(col + 1);
	}

	@Override
	public long getLong(int col) throws SQLException {
		return this.resultSet.getLong(col + 1);
	}

	@Override
	public int getInt(int col) throws SQLException {
		return this.resultSet.getInt(col + 1);
	}

	@Override
	public short getShort(int col) throws SQLException {
		return this.resultSet.getShort(col + 1);
	}

	@Override
	public float getFloat(int col) throws SQLException {
		return this.resultSet.getFloat(col + 1);
	}

	@Override
	public double getDouble(int col) throws SQLException {
		return this.resultSet.getDouble(col + 1);
	}

	@Override
	public byte[] getBlob(int col) throws SQLException {
		return this.resultSet.getBytes(col + 1);
	}

	@Override
	public String getString(int col) throws SQLException {
		return this.resultSet.getString(col + 1);
	}
}
