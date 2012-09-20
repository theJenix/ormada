package org.ormada.reflect;

import java.lang.reflect.Method;

/**
 * This interface defines an API for a reflector class.  The reflector class
 * is responsible for reflecting about the specified objects.
 * 
 * This is defined as an interface, because it may be implemented differently
 * on multiple platforms (e.g. Android is slow as balls).
 * 
 * @author Jesse Rosalia
 *
 */
public interface Reflector {

    public Class<?> getFieldType(Class<?> clazz, String field)
            throws SecurityException, NoSuchMethodException;

    public Method getGetter(Class<?> clazz, String field)
            throws SecurityException, NoSuchMethodException;

    public Method getAdder(Class<?> clazz, String field,
            Class<?> fieldType) throws SecurityException, NoSuchMethodException;

    public Method getSetter(Class<?> clazz, String field)
            throws SecurityException, NoSuchMethodException;

}