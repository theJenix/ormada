package org.ormada.dialect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple default implementation of the ValueSet interface for standard SQL databases. 
 * 
 * @author thejenix
 *
 */
public class DefaultValueSet implements ValueSet {

	private Map<String, Object> valueMap = new HashMap<String, Object>();
	
	public boolean containsField(String field) {
		return valueMap.containsKey(field);
	}
	
	public Collection<String> getFields() {
		return valueMap.keySet();
	}

	public Object getAsObject(String field) {
		return valueMap.get(field);
	}

	@Override
	public void put(String key, byte[] value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Integer value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Short value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Long value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Float value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Double value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Boolean value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, Byte value) {
		this.valueMap.put(key, value);
	}

	@Override
	public void put(String key, String value) {
		this.valueMap.put(key, value);		
	}
}
