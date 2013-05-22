package org.andrormeda.dialect;

import org.ormada.dialect.ValueSet;

import android.content.ContentValues;

/**
 * An implementation of ValueSet that wraps a ContentValues object,
 * for storing values to be saved to the DB.
 * 
 * @author Jesse Rosalia
 *
 */
public class SQLiteValueSet implements ValueSet {

	private ContentValues contentValues;

	public SQLiteValueSet() {
		this.contentValues = new ContentValues();
	}
	
	public ContentValues getContentValues() {
		return contentValues;
	}

	@Override
	public void put(String key, byte[] value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Integer value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Short value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Long value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Float value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Double value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Boolean value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, Byte value) {
		this.contentValues.put(key, value);
	}

	@Override
	public void put(String key, String value) {
		this.contentValues.put(key, value);
	}
}
