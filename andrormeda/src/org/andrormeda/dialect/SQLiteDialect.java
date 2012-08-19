package org.andrormeda.dialect;

import org.andrormeda.ORMDataSource;

import android.content.ContentValues;
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
public class SQLiteDialect extends SQLiteOpenHelper implements Dialect {

	private SQLiteDatabase database;
	private ORMDataSource orm;

	public SQLiteDialect(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
    }

	public void open(ORMDataSource orm) {
		this.getWritableDatabase();
		this.orm = orm;
	}

	@Override
	public void onCreate(SQLiteDatabase database) {
		this.database = database;
		//TODO: maybe pull table management out into a separate class
		this.orm.createAllTables();
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		this.database = database; 
        Log.w(ORMDataSource.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
		//TODO: maybe pull table management out into a separate class
		this.orm.upgradeAllTAbles(oldVersion, newVersion);
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
	public void execSQL(String stmt) {
		this.database.execSQL(stmt);
	}

	@Override
	public void delete(String table, String whereClause, String[] whereArgs) {
		this.database.delete(table, whereClause, whereArgs);
	}

	@Override
	public long insert(String table, ContentValues values) {
		return this.database.insert(table, null, values);
	}

	@Override
	public void update(String table, ContentValues values, String whereClause,
			String[] whereArgs) {
		this.database.update(table, values, whereClause, whereArgs);
	}

	@Override
	public Cursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy) {
		return this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy);
	}

	@Override
	public Cursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy, String limit) {
		return this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy, limit);
	}
}
