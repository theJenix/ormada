package org.ormada.dialect;

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
