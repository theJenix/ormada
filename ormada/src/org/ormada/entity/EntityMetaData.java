package org.ormada.entity;

import java.lang.reflect.Method;

import org.ormada.reflect.Reflector;

/**
 * This class provides an interface to the fields and values
 * that are accessible in an entity.
 * 
 * @author Jesse Rosalia
 *
 */
public class EntityMetaData {

    public  static final String ID_FIELD  = "id";
    private static final String ID_GETTER = "getId";

    private Reflector reflector;
    private Class<?> entityClass;

    public EntityMetaData(Reflector reflector, Class<?> entityClass) {
        this.reflector = reflector;
        this.entityClass = entityClass;
    }

    //FIXME: I don't like this static method...need to investigate where it's used
    // and see if theres a way to remove it.
    public static boolean isIdGetter(Method m) {
        return m.getName().equals(ID_GETTER);
    }

    /**
     * Get the getter for the id field for this entity class.
     * 
     * @param valueClass
     * @return
     * @throws NoSuchMethodException
     */
    public <T> Method getIdGetter()
            throws NoSuchMethodException {
        return this.reflector.getGetter(this.entityClass, ID_FIELD);
    }

    /**
     * Get the setter for the id field for this entity class.
     * 
     * @param valueClass
     * @return
     * @throws NoSuchMethodException
     */
    public <T> Method getIdSetter()
            throws NoSuchMethodException {
        return this.reflector.getSetter(this.entityClass, ID_FIELD);
    }
}
