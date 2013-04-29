package org.andrormeda.dialect;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ormada.ORMDataSource;
import org.ormada.annotations.Text;
import org.ormada.dialect.Dialect;
import org.ormada.dialect.QueryCursor;
import org.ormada.dialect.ValueSet;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * An ORM Dialect implemented for the SQLite database found on the Android devices.
 * 
 * This dialect is implemented as a SQLiteOpenHelper, and wraps the database that's opened
 * by the helper.  The class then implements the Dialect interface by delegating either to
 * the helper or the opened database.
 * 
 * @author Jesse Rosalia
 *
 */
public class SQLiteDialect extends SQLiteOpenHelper implements Dialect<SQLiteValueSet> {

	private SQLiteDatabase database = null;
	private ORMDataSource orm;
    private int dbVersion;

	public SQLiteDialect(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        this.dbVersion = dbVersion;
    }

	public void open(ORMDataSource orm) {
		this.orm = orm;
		this.getWritableDatabase();
	}

	@Override
	public boolean isOpen() {
	    return this.database != null;
	}
	
	@Override
	public void onCreate(SQLiteDatabase database) {
		this.database = database;
		//TODO: maybe pull table management out into a separate class
		this.orm.createAllTables(this.dbVersion);
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		this.database = database; 
        Log.w(ORMDataSource.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
		//TODO: maybe pull table management out into a separate class
		this.orm.upgradeAllTables(oldVersion, newVersion);
	}

	@Override
	public void onOpen(SQLiteDatabase database) {
		this.database = database;
	}
	
	@Override
	public void close() {
		this.database.close();
	}

	@Override
	public ValueSet prepareValueSet() {
		return new SQLiteValueSet();
	}

    public String getColumnType(Class<?> typeClass) {
        String type = null;
        if (int.class.isAssignableFrom(typeClass) || Integer.class.isAssignableFrom(typeClass)) {
            type = "integer" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (short.class.isAssignableFrom(typeClass) || Short.class.isAssignableFrom(typeClass)) {
            type = "integer" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (long.class.isAssignableFrom(typeClass) || Long.class.isAssignableFrom(typeClass)) {
            type = "integer" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (float.class.isAssignableFrom(typeClass) || Float.class.isAssignableFrom(typeClass)) {
            type = "float"   + (typeClass.isPrimitive() ? " not null" : "");
        } else if (double.class.isAssignableFrom(typeClass) || Double.class.isAssignableFrom(typeClass)) {
            type = "double"  + (typeClass.isPrimitive() ? " not null" : "");
        } else if (boolean.class.isAssignableFrom(typeClass) || Boolean.class.isAssignableFrom(typeClass)) {
            type = "boolean" + (typeClass.isPrimitive() ? " not null" : "");
        } else if (byte.class.isAssignableFrom(typeClass) || Byte.class.isAssignableFrom(typeClass)) {
            type = "byte"    + (typeClass.isPrimitive() ? " not null" : "");
        } else if (char.class.isAssignableFrom(typeClass) || Character.class.isAssignableFrom(typeClass)) {
            type = "char"    + (typeClass.isPrimitive() ? " not null" : "");
        } else if (String.class.isAssignableFrom(typeClass) || Text.class.isAssignableFrom(typeClass)) {
            type = "text";
        } else if (Date.class.isAssignableFrom(typeClass)) {
            //NOTE: not null since we use a sentinal value to indicate null
            type = "long not null";
        } else if (Serializable.class.isAssignableFrom(typeClass)) {
            type = "blob";
        }
        return type;
    }

    public String getPrimaryKeyColumnType() {
        return "integer not null primary key autoincrement";
    }

    @Override
	public void execSQL(String stmt) {
		this.database.execSQL(stmt);
	}

	@Override
	public void delete(String table, String whereClause, String[] whereArgs) {
		this.database.delete(table, whereClause, whereArgs);
	}

	@Override
	public Map<String, List<Long>> bulkSave(Map<String, List<SQLiteValueSet>> valueMap) {
	    this.database.beginTransaction();
	    try {
	        Map<String, List<Long>> idMap = new HashMap<String, List<Long>>();
	        for (Map.Entry<String, List<SQLiteValueSet>> e : valueMap.entrySet()) {
	            List<Long> idList = new ArrayList<Long>();
	            idMap.put(e.getKey(), idList);
	            for (SQLiteValueSet values : e.getValue()) {
	                long newId = this.save(e.getKey(), values);
	                idList.add(newId);
	            }
	        }
	        this.database.setTransactionSuccessful();
	        return idMap;
	    } finally {
	        this.database.endTransaction();
	    }
	}

	@Override
	public long count(String table, String whereClause, String[] whereParams)
	        throws SQLException {
        System.out.println("select from " + table);
        Cursor c = this.database.query(table, new String[] {"count(*)"}, whereClause, whereParams, null, null, null);
        c.moveToFirst();
        return c.getLong(0);
	}
	@Override
	public long insert(String table, SQLiteValueSet values) {
		return this.database.insert(table, null, values.getContentValues());
	}

   @Override
    public long save(String table, SQLiteValueSet values) {
        long id = values.getContentValues().getAsLong(ORMDataSource.ID_FIELD);
        if (id != ORMDataSource.UNSAVED_ID) {
            this.update(table, values, ORMDataSource.ID_FIELD + " = " + id, null);            
        } else {
            values.getContentValues().remove(ORMDataSource.ID_FIELD);
            id = this.insert(table, values);
        }
        return id;
    }

	@Override
	public void update(String table, SQLiteValueSet values, String whereClause,
			String[] whereArgs) {
		int numRows = this.database.update(table, values.getContentValues(), whereClause, whereArgs);
		System.out.println(numRows + " updated in " + table + " table");
	}

	@Override
	public QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy) {
        System.out.println("select from " + table);
		return new SQLiteCursor(this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy));
	}

	@Override
	public QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy, String limit) {
	    System.out.println("select from " + table);
		return new SQLiteCursor(this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy, limit));
	}
}
