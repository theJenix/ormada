package org.ormada.entity;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.ormada.ORMDataSource;
import org.ormada.dialect.QueryCursor;
import org.ormada.reflect.Reflector;

/**
 * This class is responsible for building entities from cursor results,
 * and maintaining a list of already build entities in a cache.
 * 
 * @author Jesse Rosalia
 *
 */
public class EntityBuilder {
    
    private EntityCache entityCache = new EntityCache();

    private Reflector reflector;

    private ORMDataSource orm;
    
    public EntityBuilder(ORMDataSource orm, Reflector reflector) {
        //FIXME: this relationship ONLY EXISTS for isEntity.  As we split apart a proper entity management class hierarcy
        // this should go away.
        this.orm = orm;
        this.reflector = reflector;
    }

    /**
     * Check to see if the builder contains the object
     * identified by the class and id.  If so, we probably
     * don't need to build it again.
     * 
     * @param clazz
     * @param id
     * @return
     */
    public boolean contains(Class<?> clazz, long id) {
        return entityCache.contains(clazz, id);
    }

    /**
     * Get the previously built object identified by
     * the class and id.
     * 
     * @param clazz
     * @param id
     * @return
     */
    public <T> T get(Class<T> clazz, long id) {
        return entityCache.get(clazz, id);
    }

    /**
     * Build an object from each entry in the cursor, and add those objects
     * to the list.
     * 
     * @param clazz
     * @param c
     * @param list
     * @return
     * @throws SQLException
     * @throws NoSuchMethodException
     * @throws Exception
     */
    public <T> List<T> cursorToObjects(QueryCursor c, boolean autoClose, Class<T> clazz)
            throws SQLException, NoSuchMethodException, Exception {
        List<T> list = new LinkedList<T>();
        try {
            List<Method> methods = getAllSetters(clazz, c);
            while (!c.isAfterLast()) {
                T o = doCursorToObject(c, false, clazz, methods, entityCache);
                list.add(o);
                c.moveToNext();
            }
        } finally {
            if (autoClose) {
                //done with the cursor, close it normally
                c.close();
            }
        }
        return list;
    }
    
    /**
     * Build an object from the current row in the cursor.
     * 
     * @param c
     * @param autoClose
     * @param clazz
     * @return
     * @throws Exception
     */
    public <T> T cursorToObject(QueryCursor c, boolean autoClose, Class<T> clazz) throws Exception {
        return doCursorToObject(c, autoClose, clazz, getAllSetters(clazz, c), entityCache);
    }

    /**
     * Build one object from one row in the cursor.
     * 
     * This is separated out as a private method, to allow us to keep a tight external interface.
     * 
     * @param c
     * @param autoClose
     * @param clazz
     * @param methods
     * @param entityCache
     * @return
     * @throws Exception
     */
    private <T> T doCursorToObject(QueryCursor c, boolean autoClose, Class<T> clazz, List<Method> methods, EntityCache entityCache) throws Exception {
        try {   
            T instance = clazz.newInstance();
            for (int ii = 0; ii < c.getColumnCount(); ii++) {
                this.setValueFromCursor(instance, methods.get(ii), c, ii);
            }
            Entity entity = new Entity(reflector, instance);
            entityCache.add(clazz, entity.getId(), instance);
            return instance;
        } finally {
            if (autoClose) {
                c.close();
            }
        }
    }

    /**
     * Get all of the setters exposed by the supplied class that line up
     * with fields in the cursor.
     * 
     * @param clazz
     * @param c
     * @return
     * @throws SecurityException
     * @throws SQLException
     * @throws NoSuchMethodException
     */
    private List<Method> getAllSetters(Class<?> clazz, QueryCursor c) throws SecurityException, SQLException, NoSuchMethodException {
        List<Method> methods = new ArrayList<Method>();
        for (int ii = 0; ii < c.getColumnCount(); ii++) {
            String name = c.getColumnName(ii);
            methods.add(this.reflector.getSetter(clazz, name));
        }        
        return methods;
    }

    /**
     * Get a specific column value out of the cursor and set the corresponding value in the object.
     *
     *  Note: this method and getSQLiteType must be kept in sync, since that method defines
     *  how this method will read.
     *
     * @param o
     * @param c
     * @param col
     * @throws Exception
     */
    private void setValueFromCursor(Object o, Method m, QueryCursor c, int col) throws Exception {
        //use the setter parameter to determine the data type to get from the cursor
        Class<?> typeClass = m.getParameterTypes()[0];
        if (int.class.isAssignableFrom(typeClass) || Integer.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getInt(col));
        } else if (short.class.isAssignableFrom(typeClass) || Short.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getShort(col));
        } else if (long.class.isAssignableFrom(typeClass) || Long.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getLong(col));
        } else if (float.class.isAssignableFrom(typeClass) || Float.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getFloat(col));
        } else if (double.class.isAssignableFrom(typeClass) || Double.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getDouble(col));
        } else if (boolean.class.isAssignableFrom(typeClass) || Boolean.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getInt(col) == 1);
        } else if (byte.class.isAssignableFrom(typeClass) || Byte.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getBlob(col)[0]);
        } else if (char.class.isAssignableFrom(typeClass) || Character.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getString(col).charAt(0));
        } else if (Enum.class.isAssignableFrom(typeClass)) {
            String name = c.getString(col);
            m.invoke(o, Enum.valueOf((Class<? extends Enum>)typeClass, name));
        } else if (String.class.isAssignableFrom(typeClass)) {
            m.invoke(o, c.getString(col));
        } else if (Date.class.isAssignableFrom(typeClass)) {
            //NOTE: since dates cannot be < 0, and null long columns are a pain in the butt,
            // use -1 to denote null
            long val = c.getLong(col);
            if (val >= 0) {
                m.invoke(o, new Date(val));
            } else {
                m.invoke(o, new Object[]{null});
            }
        } else if (orm.isEntity(typeClass)) {
            //skip these for now...they're processed separately
/*
            //NOTE: since entity ids cannot be < 0, and null long columns are a pain in the butt,
            // use -1 to denote null
            long val = c.getLong(col);
            if (val >= 0) {
                //BIG NOTE: this is a database call, to get the entity
                m.invoke(o, get(typeClass, val));
            } else {
                m.invoke(o, new Object[]{null});
            } */
        } else if (Serializable.class.isAssignableFrom(typeClass)) {
            //unserialize the object
            byte[] bytes = c.getBlob(col);
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
            // Deserialize the object
            Object ro = typeClass.cast(in.readObject());
            in.close();
            m.invoke(o, ro);
        } else {
            throw new RuntimeException("Unsupported type: " + typeClass.getCanonicalName());
        }
    }
}
