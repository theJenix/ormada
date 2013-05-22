package org.ormada.entity;

import org.ormada.reflect.Reflector;

/**
 * A wrapper around an Entity class or object, which exposes entity related information
 * such as ID.
 * 
 * @author Jesse Rosalia
 *
 */
public class Entity {

    //NOTE: this assumes that the default value for a "long" in Java is 0
    private static final int UNSAVED_ID = 0;
    
    private EntityMetaData metaData;
    private Object entity;

    public Entity(Reflector reflector, Object entity) {
        this.metaData = new EntityMetaData(reflector, entity.getClass());
        this.entity    = entity;
    }

    public long getId() {
        try {
            return (Long) metaData.getIdGetter().invoke(this.entity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setId(long id) {
        try {
            metaData.getIdSetter().invoke(this.entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isSaved() {
        return getId() != UNSAVED_ID;
    }

    public static boolean isSaved(long id) {
        return id != UNSAVED_ID;
    }
}
