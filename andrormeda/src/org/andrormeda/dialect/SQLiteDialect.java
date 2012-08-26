package org.andrormeda.dialect;

import java.util.Map;

import org.ormada.ORMDataSource;
import org.ormada.dialect.Dialect;
import org.ormada.dialect.QueryCursor;
import org.ormada.dialect.ValueSet;

import android.content.ContentValues;
import android.content.Context;
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

	private SQLiteDatabase database;
	private ORMDataSource orm;

	public SQLiteDialect(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
    }

	public void open(ORMDataSource orm) {
		this.orm = orm;
		this.getWritableDatabase();
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
	public ValueSet prepareValueSet() {
		return new SQLiteValueSet();
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
	public long insert(String table, SQLiteValueSet values) {
		return this.database.insert(table, null, values.getContentValues());
	}

	@Override
	public void update(String table, SQLiteValueSet values, String whereClause,
			String[] whereArgs) {
		this.database.update(table, values.getContentValues(), whereClause, whereArgs);
	}

	@Override
	public QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy) {
		return new SQLiteCursor(this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy));
	}

	@Override
	public QueryCursor query(String table, String[] fields, String selectionClause,
			String[] selectionArgs, String groupBy, String having,
			String orderBy, String limit) {
		return new SQLiteCursor(this.database.query(table, fields, selectionClause, selectionArgs, groupBy, having, orderBy, limit));
	}
}
