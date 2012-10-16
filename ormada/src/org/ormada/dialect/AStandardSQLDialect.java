package org.ormada.dialect;

import java.io.CharArrayReader;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Date;

import org.ormada.ORMDataSource;
import org.ormada.annotations.Text;

/**
 * A generic class for SQL dialects. This class assumes that most or all
 * standard desktop/server SQL servers can use the same datatypes and same value
 * set logic.
 * 
 * @author Jesse Rosalia
 * 
 */
public abstract class AStandardSQLDialect implements Dialect<DefaultValueSet> {

    private static final int MAX_VARCHAR_LENGTH = 2048;

    private Connection connection;

    protected Connection getConnection() {
        return connection;
    }

    protected void setConnection(Connection connection) {
        this.connection = connection;
    }

    /*
     * Data definition/representation methods
     */

    public String getColumnType(Class<?> typeClass) {
        String type = null;
        if (int.class.isAssignableFrom(typeClass)
                || Integer.class.isAssignableFrom(typeClass)) {
            type = "integer" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (short.class.isAssignableFrom(typeClass)
                || Short.class.isAssignableFrom(typeClass)) {
            type = "smallint" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (long.class.isAssignableFrom(typeClass)
                || Long.class.isAssignableFrom(typeClass)) {
            type = "bigint" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (float.class.isAssignableFrom(typeClass)
                || Float.class.isAssignableFrom(typeClass)) {
            type = "real" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (double.class.isAssignableFrom(typeClass)
                || Double.class.isAssignableFrom(typeClass)) {
            type = "double" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (boolean.class.isAssignableFrom(typeClass)
                || Boolean.class.isAssignableFrom(typeClass)) {
            type = "boolean" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (byte.class.isAssignableFrom(typeClass)
                || Byte.class.isAssignableFrom(typeClass)) {
            type = "tinyint" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (char.class.isAssignableFrom(typeClass)
                || Character.class.isAssignableFrom(typeClass)) {
            type = "char(2)" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (String.class.isAssignableFrom(typeClass)) {
            type = "varchar(255)";
        } else if (Date.class.isAssignableFrom(typeClass)) {
            // NOTE: not null since we use a sentinal value to indicate null
            type = "long not null";
        } else if (Serializable.class.isAssignableFrom(typeClass)) {
            type = "bytea";
        } else if (Text.class.isAssignableFrom(typeClass)) {
            type = "clob";
        }
        return type;
    }

    public String getPrimaryKeyColumnType() {
        return "bigint generated always as identity primary key";
    }

    @Override
    public ValueSet prepareValueSet() {
        return new DefaultValueSet();
    }

    public void setIntoPreparedStatement(PreparedStatement ps,
            Collection<String> fields, DefaultValueSet values)
            throws SQLException {
        int inx = 1;
        for (String field : fields) {
            if (!values.containsField(field)) {
                throw new RuntimeException(
                        "Field does not exist in value set: '" + field + "'");
            }
            Object o = values.getAsObject(field);
            Class<?> typeClass = o.getClass();
            // call the type specific method in the PreparedStatement to set
            // this parameter
            if (Integer.class.isAssignableFrom(typeClass)) {
                ps.setInt(inx, (Integer) o);
            } else if (Short.class.isAssignableFrom(typeClass)) {
                ps.setShort(inx, (Short) o);
            } else if (Long.class.isAssignableFrom(typeClass)) {
                ps.setLong(inx, (Long) o);
            } else if (Float.class.isAssignableFrom(typeClass)) {
                ps.setFloat(inx, (Float) o);
            } else if (Double.class.isAssignableFrom(typeClass)) {
                ps.setDouble(inx, (Double) o);
            } else if (Boolean.class.isAssignableFrom(typeClass)) {
                ps.setBoolean(inx, (Boolean) o);
            } else if (Byte.class.isAssignableFrom(typeClass)) {
                ps.setByte(inx, (Byte) o);
            } else if (Character.class.isAssignableFrom(typeClass)) {
                // NOTE: this seems really inefficient..maybe there's a better
                // way to store characters
                ps.setCharacterStream(inx, new CharArrayReader(
                        new char[] { (Character) o }));
            } else if (String.class.isAssignableFrom(typeClass)) {
                ps.setString(inx, (String) o);
            } else if (byte[].class.isAssignableFrom(typeClass)) {
                ps.setBytes(inx, (byte[]) o);
            } else {
                throw new RuntimeException("Unknown field type type: "
                        + typeClass.getCanonicalName() + " for field: " + field);
            }
            // move onto the next parameter
            inx++;
        }
    }

    /*
     * Dialect methods
     */

    @Override
    public void close() throws SQLException {
        this.connection.close();
    }

    public abstract void open(ORMDataSource orm) throws SQLException;

    @Override
    public void execSQL(String stmt) throws SQLException {
        Statement s = this.connection.createStatement();
        try {
            s.execute(stmt);
        } finally {
            s.close();
        }
    }

    @Override
    public void delete(String table, String whereClause, String[] whereParams)
            throws SQLException {
        String stmt = "delete from " + table;
        if (whereClause != null) {
            stmt += " where " + whereClause;
        }
        PreparedStatement ps = this.connection.prepareStatement(stmt);
        try {
            if (whereClause != null && whereParams != null) {
                for (int ii = 1; ii <= whereParams.length; ii++) {
                    ps.setString(ii, whereParams[ii]);
                }
            }
            ps.execute();
        } finally {
            ps.close();
        }
    }

    @Override
    public long insert(String table, DefaultValueSet values)
            throws SQLException {

        // build the field and values part of the insert to execute below
        Collection<String> fields = values.getFields();
        StringBuilder fieldsBuilder = new StringBuilder();
        StringBuilder valuesBuilder = new StringBuilder();
        boolean firstTime = true;
        for (String field : fields) {
            if (!firstTime) {
                fieldsBuilder.append(",");
                valuesBuilder.append(",");
            }
            firstTime = false;
            fieldsBuilder.append(field);
            valuesBuilder.append("?");
        }

        // create the statement and execute the insert. this code assumes that
        // one row will be inserted
        // and that we will get back the newly inserted id
        String stmt = "insert into " + table + "(" + fieldsBuilder
                + ") VALUES(" + valuesBuilder + ");";
        PreparedStatement ps = this.connection.prepareStatement(stmt, Statement.RETURN_GENERATED_KEYS);
        ResultSet rs = null;
        long newId = -1;
        try {
            this.setIntoPreparedStatement(ps, fields, values);
            int count = ps.executeUpdate();//, Statement.RETURN_GENERATED_KEYS);
            if (count != 1) {
                throw new RuntimeException(
                        "Error inserting values.  Expected 1 inserted row, encountered "
                                + count + " inserted rows");
            }
            rs = ps.getGeneratedKeys();
            //FIXME: this may need to change when we add a database with a fancier result set
            if (!rs.next()) {
                throw new RuntimeException(
                        "Error retrieving inserted key.  The generated keys resultset is empty");
            }
            newId = rs.getLong(1);
        } finally {
            if (rs != null) {
                rs.close();
            }
            ps.close();
        }
        return newId;
    }

    @Override
    public void update(String table, DefaultValueSet values,
            String whereClause, String[] whereParams) throws SQLException {

        // build the field/values part of the update to execute below
        Collection<String> fields = values.getFields();
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;
        for (String field : fields) {
            if (!firstTime) {
                builder.append(",");
            }
            firstTime = false;
            builder.append(field).append("=").append("?");
        }
        // create the statement and execute the update. this code assumes that
        // one row will be inserted
        // and that we will get back the newly inserted id
        String stmt = "update " + table + " " + builder + " where "
                + whereClause;
        PreparedStatement ps = this.connection.prepareStatement(stmt);
        try {
            this.setIntoPreparedStatement(ps, fields, values);
            // set the where parameters, starting at the inx right after the
            // last field parameter
            if (whereParams != null) {
                int inx = fields.size() + 1;
                for (String whereParam : whereParams) {
                    ps.setString(inx, whereParam);
                    inx++;
                }
            }
            int count = ps.executeUpdate();
            System.out.println(count + " row(s) updated");
        } finally {
            ps.close();
        }
    }

    @Override
    public QueryCursor query(String table, String[] fields,
            String selectionClause, String[] selectionArgs, String groupBy,
            String having, String orderBy) throws SQLException {
        return query(table, fields, selectionClause, selectionArgs, groupBy,
                having, orderBy, null);
    }

    @Override
	public QueryCursor query(String table, String[] fields,
			String selectionClause, String[] selectionArgs, String groupBy,
			String having, String orderBy, String limit) throws SQLException {
		StringBuilder builder = new StringBuilder("select ");
		boolean firstTime = true;
		for (String field : fields) {
			if (!firstTime) {
				builder.append(",");
			}
			firstTime = false;
			builder.append(field);
		}
		
		builder.append(" from ").append(table);
		if (selectionClause != null) {
		    builder.append(" where ").append(selectionClause);
		}
        if (groupBy != null) {
            builder.append(" group by ").append(groupBy);
            if (having != null) {
                builder.append(" having ").append(having);
            }
        }
        if (limit != null) {
            builder.append(" limit ").append(limit);
        }

        PreparedStatement ps = this.connection.prepareStatement(builder.toString());
        try {
            // set the selection args parameters, starting at inx 0
            if (selectionArgs != null) {
                int inx = 1;
                for (String selectionArg : selectionArgs) {
                    ps.setString(inx, selectionArg);
                    inx++;
                }
            }
            boolean success = ps.execute();
            if (!success) {
                throw new RuntimeException("Error executing query: " + builder.toString());
            }
            return new ForwardOnlyResultSetCursor(ps.getResultSet());
        } finally {
            ps.close();
        }

	}
}
