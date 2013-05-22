package org.ormada.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to maintain a map of all results pulled within a single
 * get operation. This is both an optimization, to ensure we don't fetch the
 * same entity more than once, and a way of handling circular references (so we
 * don't continuously fetch the same objects over and over again).
 * 
 * @author Jesse Rosalia
 * 
 */
public class EntityCache {

    private Map<Class<?>, Map<Long, Object>> entityMap = new HashMap<Class<?>, Map<Long, Object>>();

    /**
     * Add an entity to the cache, identified by the class and id passed in.
     * 
     * @param clazz
     * @param id
     * @param object
     */
    public <T> void add(Class<T> clazz, long id, T object) {
        Map<Long, Object> entityForClassMap = entityMap.get(clazz);
        if (entityForClassMap == null) {
            entityForClassMap = new HashMap<Long, Object>();
            entityMap.put(clazz, entityForClassMap);
        }
        if (entityForClassMap.containsKey(id)
                && !entityForClassMap.get(id).equals(object)) {
            throw new RuntimeException(
                    "Attempting to add a duplicate object that does not equal" +
                    " the original object.  This should never happen.");
        }
        entityForClassMap.put(id, object);
    }

    /**
     * Test to see if the cache contains the object identified by cache and id.
     * 
     * @param clazz
     * @param id
     * @return
     */
    public boolean contains(Class<?> clazz, long id) {
        return entityMap.containsKey(clazz)
                && entityMap.get(clazz).containsKey(id);
    }

    /**
     * Get the object previously built, identified by the cache and id.
     * 
     * @param clazz
     * @param id
     * @return
     */
    public <T> T get(Class<T> clazz, long id) {
        if (contains(clazz, id)) {
            return (T) entityMap.get(clazz).get(id);
        } else {
            return null;
        }
    }
}
