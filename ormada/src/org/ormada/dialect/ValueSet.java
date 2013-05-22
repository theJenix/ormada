package org.ormada.dialect;

/**
 * This interface defines an API for database specific
 * values structures.  These structures are built
 * for each entity that will be saved, and passed
 * to the Dialect for persistence.
 * 
 * @author Jesse Rosalia
 *
 */
public interface ValueSet {

	void put(String key, byte[] value);

	void put(String key, Integer value);

	void put(String key, Short value);

	void put(String key, Long value);

	void put(String key, Float value);

	void put(String key, Double value);

	void put(String key, Boolean value);

	void put(String key, Byte value);

	void put(String key, String value);

}
