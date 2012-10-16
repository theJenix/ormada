package org.ormada.dialect;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * A generic QueryCursor implementation for JDBC ResultSet objects.
 * 
 * NOTE: several of the cursor position methods will return to indicate no data on exception.  While
 * this may lose a bit of information, it is because in these cases, the caller is just looking for
 * an answer about whether there is data/more data.
 * 
 * @author Jesse Rosalia
 *
 */
public class FullResultSetCursor implements QueryCursor {

    private ResultSet         resultSet;
    private ResultSetMetaData rsMetaData;

    public FullResultSetCursor(ResultSet resultSet) throws SQLException {
        this.resultSet  = resultSet;
        this.rsMetaData = resultSet.getMetaData();
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
	    return !moveToFirst();
	}

	@Override
	public boolean moveToFirst() {
        try {
            return this.resultSet.first();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
	}

	@Override
	public boolean isAfterLast() {
        try {
            return this.resultSet.isAfterLast();
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
	}

	@Override
	public boolean moveToNext() {
        try {
            return this.resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
	}

	@Override
	public int getColumnCount() throws SQLException {
	    return this.rsMetaData.getColumnCount();
	}

	@Override
	public String getColumnName(int col) throws SQLException {
	    return this.rsMetaData.getColumnLabel(col);
	}

	@Override
	public long getLong(int col) throws SQLException {
		return this.resultSet.getLong(col);
	}

	@Override
	public int getInt(int col) throws SQLException {
		return this.resultSet.getInt(col);
	}

	@Override
	public short getShort(int col) throws SQLException {
		return this.resultSet.getShort(col);
	}

	@Override
	public float getFloat(int col) throws SQLException {
		return this.resultSet.getFloat(col);
	}

	@Override
	public double getDouble(int col) throws SQLException {
		return this.resultSet.getDouble(col);
	}

	@Override
	public byte[] getBlob(int col) throws SQLException {
		return this.resultSet.getBytes(col);
	}

	@Override
	public String getString(int col) throws SQLException {
		return this.resultSet.getString(col);
	}
}
