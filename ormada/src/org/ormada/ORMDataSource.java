package org.ormada;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ormada.annotations.OneToMany;
import org.ormada.annotations.Reference;
import org.ormada.annotations.Text;
import org.ormada.annotations.Transient;
import org.ormada.dialect.Dialect;
import org.ormada.dialect.QueryCursor;
import org.ormada.dialect.ValueSet;
import org.ormada.entity.Entity;
import org.ormada.entity.EntityBuilder;
import org.ormada.entity.EntityMetaData;
import org.ormada.exception.MixedCollectionException;
import org.ormada.exception.UnableToOpenException;
import org.ormada.exception.UnsavedReferenceException;
import org.ormada.model.ORMeta;
import org.ormada.reflect.DefaultReflector;
import org.ormada.reflect.Reflector;
import org.ormada.util.Profiler;

/**
 * Copyright (c) 2012> Jesse Rosalia
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *   and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *   The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *   
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWAR
 *
 * Part of the ORMada project.
 * 
 * @author Jesse Rosalia
 *
 */
public class ORMDataSource {

    // Database creation sql format
    private static final String DATABASE_CREATE_FMT = "create table %s (%s);";

    private static final int CURRENT_ORM_VERSION = 1;

    private List<Class<?>> entities;

    private Dialect database;

    private boolean useORMeta;
    
    private Reflector reflector;

    public ORMDataSource(Dialect dialect, Class<?> ... entities) {
    	this.database  = dialect;
        this.entities  = Arrays.asList(entities);
        this.reflector = new DefaultReflector();
        for (Class<?> entity : entities) {
            checkIsEntityClass(entity);
        }
    }

    public void open() throws UnableToOpenException {
        try {
            this.database.open(this);
        } catch (SQLException e) {
            throw new UnableToOpenException();
        }
    }

    public void close() throws SQLException {
    	this.database.close();
    }

    /**
     * Create all of the tables for the entities in the ORM Data Source.
     * 
     * @param dbVersion
     */
    public void createAllTables(int dbVersion) {
    	try {
	        //if we need to use the ORMeta table to keep track of version info, create the table here
	        // and insert the one record
	        if (this.useORMeta) {
	            createTablesForClass(database, ORMeta.class);
	            ORMeta meta = new ORMeta();
	            meta.setDbVersion(dbVersion);
	            meta.setOrmVersion(CURRENT_ORM_VERSION);
                saveOne(meta, true);
	        }

	        for (Class<?> entity : entities) {
                createTablesForClass(database, entity);
            }
    	} catch (SQLException se) {
    		throw new RuntimeException(se);
    	}
    }

    /**
     * Create all tables for this class.  The tables for the class will be:
     *  One table for the class data
     *  One table for each collection using the name format: className_collectionName (e.g. Conference_buildings)
     * 
     * @param database
     * @param clazz
     * @throws SQLException
     */
    private void createTablesForClass(Dialect database,
            Class<?> clazz) throws SQLException {
        List<String> createStmts = new LinkedList<String>();
        //add the main class table
        StringBuilder fieldListBuilder = new StringBuilder();
        for (Method m : clazz.getMethods()) {
            //process the getters for singular objects here
            //no collections...they get processed later
            if (isPersisted(m) && !isCollection(m)) {
                if (fieldListBuilder.length() > 0) {
                    fieldListBuilder.append(",");
                }
                fieldListBuilder.append(getFieldNameFromMethod(m)).append(" ");
                if (EntityMetaData.isIdGetter(m)) {
                    if (!(long.class.isAssignableFrom(m.getReturnType()) || Long.class.isAssignableFrom(m.getReturnType()))) {
                        throw new RuntimeException("Id field must be a long or Long type");
                    }
                    fieldListBuilder.append(" ").append(this.database.getPrimaryKeyColumnType());
                } else {
                    fieldListBuilder.append(getColumnType(m.getReturnType(), m.isAnnotationPresent(Text.class)));
                }
            }
        }

        //add to the list...we queue up the create statements and exec them all at the end
        // to avoid half creating the db and encountering an error
        createStmts.add(String.format(DATABASE_CREATE_FMT, getTableNameForClass(clazz), fieldListBuilder.toString()));
        //process collections here...collections will be stored in a join table
        // which will use this object's key and either a static value or
        // another objects key
        for (Method m : clazz.getMethods()) {
            if (isPersisted(m) && isCollection(m)) {
                OneToMany c = m.getAnnotation(OneToMany.class);
                if (c == null || c.value() == null) {
                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient: " + m.toString());
                }

                Class<?> colClass = c.value();
                String fieldName = getFieldNameFromMethod(m);
                //the join table name will be objname_fieldName
                String tableName = getTableNameForClass(clazz);
                String joinTableName = buildJoinTableName(tableName, fieldName);
                fieldListBuilder = new StringBuilder();
                //build the field list using the camel case representation of the object's class name
                // and the fieldName.  This is similar to the table name..
                //TODO: this breaks if the class name (table name) and the field name are the same, but that's unlikely
                // since the class name is likely to be singular and the collection name is likely to be plural
                fieldListBuilder.append(getJoinTableIDName(tableName))   .append(" ")
                                .append(getColumnType(clazz, false))     .append(",")
                                .append(getJoinTableValueName(fieldName)).append(" ")
                                .append(getColumnType(c.value(), m.isAnnotationPresent(Text.class)));
                createStmts.add(String.format(DATABASE_CREATE_FMT, joinTableName, fieldListBuilder.toString()));
            }
        }

        //execute all of the create statements
        for (String stmt : createStmts) {
            database.execSQL(stmt);
        }
    }

    private String getJoinTableIDName(String tableName) {
    	//use a camelcase of the table name
    	return toCamelCase(tableName);
    }
    
    private String getJoinTableValueName(String fieldName) {
    	return fieldName;
    }

    private String buildJoinTableName(String className, String fieldName) {
        return className + "_" + fieldName;
    }
    
    private String getColumnType(Class<?> typeClass, boolean isText) {
    	String type = null; 
    	//all entity references are stored as longs (for the foreign key)
    	if (isEntity(typeClass)) {
    		type = this.database.getColumnType(Long.class);
    	} else if (String.class.isAssignableFrom(typeClass) && isText) {
    	    type = this.database.getColumnType(Text.class);
    	} else {
    		type = this.database.getColumnType(typeClass);
    	}
        if (type == null) {
            throw new RuntimeException("Unsupported type: " + typeClass.getCanonicalName());
        }
        return type;
    }

    public boolean isEntity(Class<?> typeClass) {
        try {
            new EntityMetaData(reflector, typeClass).getIdGetter();
            //NOTE: ORMeta will not be in entities, but we want to treat it as an entity if we're using that class to store data.
            return (ORMeta.class.isAssignableFrom(typeClass) && this.useORMeta) || entities.contains(typeClass);
        } catch (Exception e) {}
        return false;
    }

    private String getFieldNameFromMethod(Method m) {
        String stripped = m.getName().startsWith("is") ? m.getName().substring(2) : m.getName().substring(3);
        String camel = toCamelCase(stripped);
        //TODO: look at appending _id onto entity fields, like with rails and other systems
//        if (isEntity(m.getReturnType())) {
//            camel += "_id";
//        }
        return camel;
    }

    /**
     * Test if a field is a collection by looking at it's getter method return value.
     * 
     * This returns true if:
     *  The method returns a Collection or any derived/implementing classes or interfaces.
     *  
     * @param m
     * @return
     */
    private boolean isCollection(Method m) {
        return java.util.Collection.class.isAssignableFrom(m.getReturnType());
    }
    
    /**
     * Test if a field is persisted by looking at it's getter method.
     * 
     * This returns true if:
     * 	The method passed in starts with "get" or "is", is not declared in Object, and is not marked as Transient
     * 
     * @param m
     * @return
     */
    private boolean isPersisted(Method m) {
        return (m.getName().startsWith("get") || m.getName().startsWith("is")) &&
        		m.getDeclaringClass() != Object.class   &&
        		!m.isAnnotationPresent(Transient.class);
    }

    /**
     * Test if a field is a reference by looking at it's getter method.
     * 
     * This returns true if:
     *  The method is declared to return an Entity and is marked as a Reference (using the reference annotation)
     * @param m
     * @return
     */
    private boolean isReference(Method m) {
        return m.isAnnotationPresent(Reference.class);
    }
    /**
     * Test if a field should be included in the insert/update ValueSet set
     *
     * NOTE: we test the method here...it's a little funky, but as a general rule
     * if a getter exists (and it's not the id, or in Object), it can be
     * inserted/updated.
     *
     * @param m
     * @return
     */
    private boolean isIncludedInValueSet(Method m, boolean includeId) {
        return isPersisted(m) && (includeId || !EntityMetaData.isIdGetter(m)) && !isCollection(m);
    }

    private String toCamelCase(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private void dropTableForClass(Dialect database,
            Class<?> clazz) throws SQLException {
        //drop any join tables
        //NOTE: because of how we drop these tables, we likely cannot have any actual foreign key constraints
        // or else we'll get all kinds of weird "out of order" issues
        for (Method m : clazz.getMethods()) {
            //clean up the join tables only if this collection is persisted
            if (isPersisted(m) && isCollection(m)) {
                database.execSQL("DROP TABLE IF EXISTS " + buildJoinTableName(getTableNameForClass(clazz), getFieldNameFromMethod(m)));
            }
        }
        //drop the main table
        database.execSQL("DROP TABLE IF EXISTS " + getTableNameForClass(clazz));
    }

    public void upgradeAllTables(int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
        	try {
    	        for (Class<?> entity : entities) {
    	            dropTableForClass(database, entity);
    	        }
    
    	        createAllTables(newVersion);//(database);
        	} catch (SQLException se) {
        		throw new RuntimeException(se);
        	}
        }
    }

    private ValueSet dumpObject(Object o, boolean insertId) {
        //leverage the dumpObjects method..even thogh we're only working on one here
        List<ValueSet> valueSets = dumpObjects(o.getClass(), Arrays.asList(o), insertId);
        return valueSets.get(0);
//        ValueSet values = this.database.prepareValueSet();
//        try {
//            for (Method m : o.getClass().getMethods()) {
//                //process the getters for singular objects here
//                //NOTE: exclude anything from the base Object class here
//                //NOTE: also exclude the primary key
//                //no collections...they get processed later
//                if (isInsertedOrUpdated(m) && !isCollection(m)) {
//                    Object val = m.invoke(o);
//                    setValueIntoContentValues(values, m.getReturnType(), getFieldNameFromMethod(m), val);
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return values;
    }

    private Map<String, List<ValueSet>> dumpObjects(Map<Class<?>, List<Object>> split, boolean includeId) {
        
        Map<String, List<ValueSet>> valueMap = new HashMap<String, List<ValueSet>>();

        //for each class based collection
        for (Map.Entry<Class<?>, List<Object>> entry : split.entrySet()) {
            Class<?>     clazz = entry.getKey();
            List<Object> col   = entry.getValue();

            //dump all of the items in that collection
            List<ValueSet> valueSets = dumpObjects(clazz, col, includeId);
            //if there are value sets to be saved, add it to the map, key'd off of the table name
            if (!valueSets.isEmpty()) {
                valueMap.put(getTableNameForClass(clazz), valueSets);
            }
        }
        return valueMap;
    }

    /**
     * Dump all of the objects for the specified class into ValueSet objects, for passing to a Dialect method.
     * 
     * @param clazz
     * @param objects
     * @param includeId True to include the ID in the value set, false if not.  If you are using the save dialect method, you must include
     * the ID (so it can determine if it needs to insert or update)
     * @return
     */
    private List<ValueSet> dumpObjects(Class<?> clazz, Collection<Object> objects, boolean includeId) {
        List<ValueSet> valueSets = new ArrayList<ValueSet>(objects.size());
        for (Object o : objects) {
            valueSets.add(this.database.prepareValueSet());
        }
        try {
            for (Method m : clazz.getMethods()) {
                //process the getters for singular objects here
                //NOTE: exclude anything from the base Object class here, and possibly the id
                //no collections...they get processed later
                if (isIncludedInValueSet(m, includeId)) {
                    int ii = 0;
                    for (Object o : objects) {
                        Object val = m.invoke(o);
                        ValueSet values = valueSets.get(ii);
                        setValueIntoContentValues(values, m.getReturnType(), getFieldNameFromMethod(m), val);
                        ii++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return valueSets;
    }

    //NOTE: this means we cannot have duplicate entity names...that's ok for now
    private String getTableNameForClass(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    private Class<?> getClassFromTableName(String tableName) {
        Class<?> found = null;
        for (Class<?> entity : this.entities) {
            if (entity.getSimpleName().equals(tableName)) {
                found = entity;
                break;
            }
        }
        return found;
    }
    /**
     * Get a list of the columns that make up this class...this will be used for selecting objects.
     * 
     * @param clazz
     * @return
     */
    private List<String> getColumns(Class<?> clazz) {
        List<String> columns = new LinkedList<String>();
        for (Method m : clazz.getMethods()) {
            //process the getters for singular objects here
            if (isPersisted(m)
                    && !java.util.Collection.class.isAssignableFrom(m.getReturnType())) {
                columns.add(getFieldNameFromMethod(m));
            }
        }

        return columns;
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
     */
    private void setValueIntoContentValues(ValueSet values, Class typeClass, String key, Object value) {
        //use the setter parameter to determine the data type to get from the cursor
        if (int.class.isAssignableFrom(typeClass) || Integer.class.isAssignableFrom(typeClass)) {
            values.put(key, (Integer)value);
        } else if (short.class.isAssignableFrom(typeClass) || Short.class.isAssignableFrom(typeClass)) {
            values.put(key, (Short)value);
        } else if (long.class.isAssignableFrom(typeClass) || Long.class.isAssignableFrom(typeClass)) {
            values.put(key, (Long)value);
        } else if (float.class.isAssignableFrom(typeClass) || Float.class.isAssignableFrom(typeClass)) {
            values.put(key, (Float)value);
        } else if (double.class.isAssignableFrom(typeClass) || Double.class.isAssignableFrom(typeClass)) {
            values.put(key, (Double)value);
        } else if (boolean.class.isAssignableFrom(typeClass) || Boolean.class.isAssignableFrom(typeClass)) {
            values.put(key, (Boolean)value);
        } else if (byte.class.isAssignableFrom(typeClass) || Byte.class.isAssignableFrom(typeClass)) {
            values.put(key, (Byte)value);
        } else if (char.class.isAssignableFrom(typeClass) || Character.class.isAssignableFrom(typeClass)) {
            values.put(key, String.valueOf((Character)value));
        } else if (Enum.class.isAssignableFrom(typeClass)) {
            values.put(key, ((Enum)value).name());
        } else if (String.class.isAssignableFrom(typeClass)) {
            values.put(key, (String)value);
        } else if (Date.class.isAssignableFrom(typeClass)) {
            //NOTE: since dates cannot be < 0, and null long columns are a pain in the butt,
            // use -1 to denote null
            if (value != null) {
                values.put(key, ((Date)value).getTime());
            } else {
                values.put(key, -1);
            }
        } else if (isEntity(typeClass)) {
            //NOTE: since entity ids cannot be < 0, and null long columns are a pain in the butt,
            // use -1 to denote null
        	if (value != null) {
                Entity entity = new Entity(reflector, value);
                //entities: store the ID
        	    long id = (int)entity.getId();
        	    if (id == 0) {
        	        System.out.println("WARN: reference stored to unsaved entity (id=0)");
        	        throw new RuntimeException("Reference stored to unsaved entity (id=0)");
        	    }
                values.put(key, id);
        	} else {
                values.put(key, -1);
        	}
        } else if (Serializable.class.isAssignableFrom(typeClass)) {
            try {
                //unserialize the object
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(baos);
                // Serialize the object
                out.writeObject(value);
                values.put(key, baos.toByteArray());
                out.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            throw new RuntimeException("Unsupported type: " + typeClass.getCanonicalName());
        }
    }


    private void checkIsEntity(Object o) {
        checkIsEntityClass(o.getClass());
    }

    private void checkIsEntityClass(Class<?> clazz) {
        if (!this.isEntity(clazz)) {
            throw new RuntimeException("Class " + clazz.getCanonicalName() + " is not an entity class.  Did you remember to define an id attribute?");
        }
    }

    private void checkIsAllEntityClass(Map<Class<?>, ?> classMap) {
        for (Class<?> clazz : classMap.keySet()) {
            checkIsEntityClass(clazz);
        }
    }

    private void checkReferences(Object o) {
        checkReferences(Arrays.asList(o));
    }

    /**
     * Check that all of the objects within the collection have valid references.
     *  I.e. all fields marked as @Reference contain null or already persisted entities.
     *  
     * This will handle heterogeneous collections by binning the objects by class and testing
     * each class individually.
     * 
     * NOTE: because of efficiency concerns on Android, this is kind of complicated...inline comments
     * should help figure out what it's doing.
     * 
     * @param os
     */
    private void checkReferences(Collection<Object> os) {
        //first, split up the collection by class...in most cases, this will only contain
        // one entry, but it gives us some flexibility
        Map<Class<?>, List<Object>> split = splitByClass(os);
        //for each class, iterate through the methods...we only want to process persisted references
        for (Class<?> clazz : split.keySet()) {
            Collection<Object> col = split.get(clazz);
            for (Method m : clazz.getMethods()) {
                if (isPersisted(m) && isReference(m)) {
                    try {
                        //if it's a referenced collection, we need to check all of the values to make sure theyre saved
                        if (isCollection(m)) {
                            OneToMany c = m.getAnnotation(OneToMany.class);
                            if (c == null || c.value() == null) {
                                throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
                            }
                            if (isEntity(c.value())) {
                                //create a set to contain the aggregated values to check...this will speed up the check
                                // as reflection and other setup code can be very slow (particularly on mobile).
                                Set<Object> toCheck = new HashSet<Object>();
                                //aggregate all of the entities in the collection field, for all objects in the class-based collection
                                for (Object o : col) {
                                    Collection<?> ref = (Collection<?>) m.invoke(o);
                                    toCheck.addAll(ref);
                                }
                                //run a check for unsaved references against the aggregated set 
                                doCheckForUnsavedReferences(clazz, m, c.value(), toCheck);
                            }
                        }
                        //otherwise, check if its an entity reference
                        else if (isEntity(m.getReturnType())) {
                            //run a check for unsaved references against the aggregated set 
                            doCheckForUnsavedReferences(clazz, m, m.getReturnType(), col);
                        }
                    } catch (Exception e) {
                        //if we've accidentally caught an UnsavedReferenceException, just rethrow it
                        if (e instanceof UnsavedReferenceException) {
                            throw (UnsavedReferenceException)e;
                        }
                        //otherwise, wrap the exception in a runtime exception
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Run a check for unsaved references against a collection of objects.
     * 
     * NOTE: if the collection is null or empty, this does nothing.
     * 
     * @param parentClass
     * @param method
     * @param valueClass
     * @param objects
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private void doCheckForUnsavedReferences(Class<?> parentClass, Method method, Class<?> valueClass, Collection<?> objects) throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        if (objects != null && !objects.isEmpty()) {
            for (Object o : objects) {
                Object r = method.invoke(o);
                Entity entity = new Entity(reflector, r);
                if (r != null && !entity.isSaved()) {
                    throw new UnsavedReferenceException(parentClass, getFieldNameFromMethod(method));
                }
            }
        }
    }

    public <T> void refresh(T o) {
    	checkIsOpened();
    	checkIsEntity(o);
    	Entity entity = new Entity(reflector, o);
    	Object persisted = doGet(o.getClass(), entity.getId(), newEntityBuilder());
    	copy(persisted, o);
    }

    /**
     * Copy all persisted attributes from one object to another.
     * 
     * NOTE: This is most likely used on objects of the same type, but that doesnt have to be the case.
     * 
     * @param from
     * @param to
     */
    private void copy(Object from, Object to) {
    	try {
	        for (Method m : to.getClass().getMethods()) {
	            Class<?> typeClass = m.getReturnType();
	            if (isPersisted(m)) {
	            	String fieldName = getFieldNameFromMethod(m);
	            	Method g = this.reflector.getGetter(from.getClass(), fieldName);
	            	Method s = this.reflector.getSetter(to.getClass(), fieldName);
	            	//copy the value from "from" to "to"
	            	s.invoke(to, new Object[]{g.invoke(from)});
	            }
	        }
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
	}

    public long save(Object o) {
        return saveOne(o, true);
    }

    public long saveReferences(Object o) {
        //check to see that we can save this object
        checkIsOpened();
        checkIsEntity(o);
        //check to make sure that all of the references have been saved
        //NOTE: this means that an object cannot hold a reference to an object that will be saved during this operation
        //...the object will have to have been saved already.
        //TODO: this should be ok for now, but I'd like to fix this ...maybe by deferring reference processing until the end
        checkReferences(o);
//        save all dependent entities
//        saveEntities(o);
        //insert the object into the database and update the object with the ID
        ValueSet values = this.dumpObject(o, true);
        try {
            long id = this.database.save(getTableNameForClass(o.getClass()), values);
            Entity entity = new Entity(reflector, o);
            entity.setId(id);
            saveCollections(o, id, true);
            return id;
        } catch (SQLException se) {
            throw new RuntimeException(se);
        } finally {
            saveOneProf.exit();
        }
    }

    Profiler saveOneProf = new Profiler("saveOne", 100);
    /**
     * Save one object.  This will check that the object can be saved, and throw an exception
     * if an integrety constraint is violated.
     * 
     * @param o
     * @param saveCollections
     * @return
     * @throws UnsavedReferenceException
     */
    private long saveOne(Object o, boolean saveCollections) throws UnsavedReferenceException {
        saveOneProf.enter();
    	//check to see that we can save this object
        checkIsOpened();
        checkIsEntity(o);
        //check to make sure that all of the references have been saved
        //NOTE: this means that an object cannot hold a reference to an object that will be saved during this operation
        //...the object will have to have been saved already.
        //TODO: this should be ok for now, but I'd like to fix this ...maybe by deferring reference processing until the end
        checkReferences(o);
        //save all dependent entities
        saveEntities(o);
        //insert the object into the database and update the object with the ID
        ValueSet values = this.dumpObject(o, true);
    	try {
    	    long id = this.database.save(getTableNameForClass(o.getClass()), values);
            Entity entity = new Entity(reflector, o);
            entity.setId(id);
    		if (saveCollections) {
    		    saveCollections(o, id, false);
    		}
    		return id;
		} catch (SQLException se) {
			throw new RuntimeException(se);
		} finally {
		    saveOneProf.exit();
		}
    }

    /**
     * Save singular entities that are attached to this object
     * 
     * @param o
     */
    private void saveEntities(Object o) {
        try {
            String tableName = getTableNameForClass(o.getClass());
            for (Method m : o.getClass().getMethods()) {
                Class<?> typeClass = m.getReturnType();
                if (isPersisted(m) && !isReference(m) && isEntity(typeClass)) {
                    Entity entity = new Entity(reflector, o);
//                    Method idM = getIdGetter(valueClass);
//                    long id = (Long) idM.invoke(o);
                    long id = entity.getId();
                    //NOTE: save every time...saveOne is smart enough to update (instead of just inserting)
                    // and we already filter out references
                    //TODO: I'd love to get away from using the @Reference annotation, and solve that problem
                    // using either reference counting or some other hidden ownership tracking (e.g. relationship
                    // that saves the object manages it, and everything else is a reference)
//                    if (id <= 0) {
                        Object e = m.invoke(o);
                        if (e != null) {
                            saveOne(e, true);
                        }
//                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save singular entities that are attached to this object
     * 
     * @param o
     */
    private <T> void saveEntitiesForAll(List<T> objects) {
        if (objects.isEmpty()) {
            return; //nothing to do
        }
        try {
            Class<?> clazz = objects.get(0).getClass();
            String tableName = getTableNameForClass(clazz);
            for (Method m : clazz.getMethods()) {
                Class<?> typeClass = m.getReturnType();
                if (isPersisted(m) && !isReference(m) && isEntity(typeClass)) {
                    //NOTE: save every time...saveOne is smart enough to update (instead of just inserting)
                    // and we already filter out references
                    //TODO: I'd love to get away from using the @Reference annotation, and solve that problem
                    // using either reference counting or some other hidden ownership tracking (e.g. relationship
                    // that saves the object manages it, and everything else is a reference)
//                    if (id <= 0) {
                    List<Object> entities = new ArrayList<Object>();
                    for (T o : objects) {
                        Object e = m.invoke(o);
                        if (e != null) {
                            entities.add(e);
                        }
                    }

                    if (!entities.isEmpty()) {
                        saveAll(entities);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save all persisted collections for the object passed in.
     * 
     * @param o
     * @param id
     * @param onlyReferences If true, only save the reference objects.  If false, save everything.
     * FIXME: onlyReferences should be replaced with a save filter of some sort...that way, we can have flexibility in what we save and how we save it
     */
    private void saveCollections(Object o, long id, boolean onlyReferences) {
    	try {
	    	String tableName = getTableNameForClass(o.getClass());
	        for (Method m : o.getClass().getMethods()) {
	            if (isPersisted(m) && isCollection(m) && (!onlyReferences || isReference(m))) {
	                OneToMany c = m.getAnnotation(OneToMany.class);
	                if (c == null || c.value() == null) {
	                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
	                }
	
	                String fieldName     = getFieldNameFromMethod(m);
	                String joinTableName = buildJoinTableName(tableName, fieldName);
	                Collection<?> collection = (Collection<?>) m.invoke(o);

	                if (collection != null && !collection.isEmpty()) {
		                //if we're saving entities, we need to save the objects then associate the IDs in the join table
		                //..otherwise, just save the value
		                boolean saveIds = false;
		                if (isEntity(c.value())) {
		                    Map<Long, Collection<?>> map = new HashMap<Long, Collection<?>>();
		                    map.put(id, collection);
		                    //shortcut, to avoid dealing with reflection/annotations
		                    boolean reference = onlyReferences || isReference(m);
		                	//delete all of the old dependent objects for this collection
		                	deleteDependents(joinTableName, c.value(), tableName, fieldName, reference, map);
			                //save all the individual entities (which will populate the objects' ids) 
		                	if (!reference) {
		                	    saveAll(collection);
		                	}
			                saveIds = true;
		                } else {
		                	deleteValuesFromJoinTable(joinTableName, tableName, fieldName, Arrays.asList(id));
		                }
		                for (Object co : collection) {
		                	Object coVal = saveIds ? new Entity(reflector, co).getId() : co;
		                	addToJoinTable(joinTableName, c.value(), tableName, fieldName, id, co);
		                }
	                }
	            }
	        }
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
	}

    private void saveCollectionsForAll(Collection<?> objects) {
        try {
            if (objects.size() == 0) {
                System.out.println("Empty");
                return;
            }
            Class<?> clazz = objects.iterator().next().getClass(); //assume all of them are the same
            Map<Long, Collection<?>> allObjMap = new HashMap<Long, Collection<?>>();
            List<Object> allObj = new ArrayList<Object>();
            String tableName = getTableNameForClass(clazz);
            for (Method m : clazz.getMethods()) {
                if (isPersisted(m) && isCollection(m)) {
                    OneToMany c = m.getAnnotation(OneToMany.class);
                    if (c == null || c.value() == null) {
                        throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
                    }
    
                    String fieldName     = getFieldNameFromMethod(m);
                    String joinTableName = buildJoinTableName(tableName, fieldName);
                    allObj.clear();
                    allObjMap.clear();
                    for (Object o : objects) {
                        Collection<?> collection;
                        try {
                            collection = (Collection<?>) m.invoke(o);
                        } catch(IllegalArgumentException e) {
                            System.out.println("balls");
                            continue;
                        }
                        if (collection != null && !collection.isEmpty()) {
                            allObjMap.put(new Entity(reflector, o).getId(), collection);
                            allObj.addAll(collection);
                        }
                    }

                    //if we're saving entities, we need to save the objects then associate the IDs in the join table
                    //..otherwise, just save the value
                    boolean saveIds = false;
                    if (isEntity(c.value())) {
                        boolean reference = isReference(m);
                        //delete all of the old dependent objects for this collection
                        deleteDependents(joinTableName, c.value(), tableName, fieldName, reference, allObjMap);
                        //save all the individual entities (which will populate the objects' ids)
                        if (!reference) {
                            saveAll(allObj);
                        }
                        saveIds = true;
                    } else {
                        deleteValuesFromJoinTable(joinTableName, tableName, fieldName, allObjMap.keySet());
                    }
                    for (Object o : objects) {
                        Entity entity = new Entity(reflector, o);
                        long id = entity.getId();
                        Collection<?> collection = allObjMap.get(id);
                        if (collection != null && !collection.isEmpty()) {
                            for (Object co : collection) {
                                Object coVal = saveIds ? new Entity(reflector, co).getId() : co;
                                addToJoinTable(joinTableName, c.value(), tableName, fieldName, id, co);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add an entry to a join/collection table.  In this case, value can be a simple or complex type, or an Entity.
     * 
     * This method uses setValueIntoContentValues to handle inserting the appropriate value for the object pasesd
     * in.
     * 
     * @param joinTable
     * @param valueClass
     * @param tableName
     * @param fieldName
     * @param id
     * @param value
     * @throws Exception
     */
    //TODO: this may be inefficient, with separate inserts.  can probably do bulk insert
	private void addToJoinTable(String joinTable, Class<?> valueClass, String tableName, String fieldName, long id, Object value) throws Exception {
		ValueSet values = database.prepareValueSet();
		values.put(getJoinTableIDName(tableName), id);
		setValueIntoContentValues(values, valueClass, getJoinTableValueName(fieldName), value);
		database.insert(joinTable, values);
	}

	/**
	 * Delete old dependent entries, before inserting new entries 
	 * 
	 * @param joinTable The name of the join table that holds the id maps between the owners and the dependents
	 * @param valueClass The class of the dependent objects
	 * @param tableName The raw table name of the parent object
	 * @param fieldName The raw field name of the dependent collection
	 * @param ids The ids of parent objects from which to delete dependents
	 * @param toSave A collection of dependent objects that will be saved (don't delete these)
	 * @throws SQLException 
	 */
	//TODO: this method should be split up into: findDependentsToDelete, deleteFromJoinTable, deleteDependents
	private void deleteDependents(String joinTable, Class<?> valueClass, String tableName, String fieldName, boolean referenceField, Map<Long, Collection<?>> toSaveMap) throws SQLException {
	    
	    //process the toSaveMap into a map of child to collections of referencing parents
	    //...the key set will be all of the join table references we need to pull
	    // and the value set will let us check if we need to delete the object entirely
	    Map<Long, Collection<Long>> childToParentMap = new HashMap<Long, Collection<Long>>();
		//grab the ids of the objects to save...we will not delete these objects
		for (Map.Entry<Long, Collection<?>> e : toSaveMap.entrySet()) {
		    for (Object o : e.getValue()) {
		        Entity entity = new Entity(reflector, o);
    		    if (entity.isSaved()) {
    		        long id = entity.getId();
    		        Collection<Long> newC = childToParentMap.get(id);
    		        if (newC == null) {
    		            newC = new HashSet<Long>();
    		            childToParentMap.put(id, newC);
    		        }
    		        newC.add(e.getKey());
    		    }
		    }
		}
		
		String idName    = getJoinTableIDName(tableName);
		String valueName = getJoinTableValueName(fieldName);

		//using our map of entity references to save, get all of the references that should probably be deleted
		String joinTableWhereClause = idName + " in (" + flattenCollection(toSaveMap.keySet()) + ") and " + valueName + " not in (" + flattenCollection(childToParentMap.keySet()) + ")";
        
		QueryCursor c = database.query(joinTable, new String[] {idName, valueName}, joinTableWhereClause, null, null, null, null);
		try {
		    Map<Long, Collection<Long>> toDeleteMap = new HashMap<Long, Collection<Long>>();
		    Set<Long> toDeleteSet = new HashSet<Long>();
	        if (c != null && !c.isEmpty()) {
				c.moveToFirst();
				while(!c.isAfterLast()) {
				    long id = c.getLong(0);
				    Collection<Long> col = toDeleteMap.get(id);
				    if (col == null) {
				        col = new HashSet<Long>();
				        toDeleteMap.put(id, col);
				    }
				    long refId = c.getLong(1);
				    col.add(refId);
				    toDeleteSet.add(refId);
					c.moveToNext();
				}
	
				//clean up
                c.close();
                c = null;

                //delete the references in the join table
                deleteValuesFromJoinTable(joinTable, tableName, fieldName, toSaveMap.keySet());

                if (!referenceField) {
                    //determine if that created any orphaned entities...if so, we want to delete the actual objects
                    //NOTE: we determine orphans by querying for the set of objects whose references we just deleted...
                    //...the ones we get back are the ones that still have incoming references, and remove those from our
                    // set...what's left are the orphans.
                    c = database.query(joinTable, new String[] {valueName}, valueName + " in (" + flattenCollection(toDeleteSet) + ")", null, null, null, null);
                    if (c != null && !c.isEmpty()) {
                        c.moveToFirst();
                        while(!c.isAfterLast()) {
                            toDeleteSet.remove(c.getLong(0));
                            c.moveToNext();
                        }
    
                        //clean up
                        c.close();
                        c = null;
                        if (!toDeleteSet.isEmpty()) {
            				//delete all the dependent objects (using the where clause built up)
            				StringBuilder builder = new StringBuilder("id in (").append(flattenCollection(toDeleteSet)).append(")");
            				deleteAll(valueClass, builder.toString());
                        }
                    }
                }
	        }
		} finally {
		    //just in case, we still need to clean up
			if (c != null) {
				c.close();
			}
		}
	}

	/**
	 * Delete the specified entity references from the join table.
	 * 
	 * @param joinTable
	 * @param tableName
	 * @param fieldName
	 * @param ids
	 * @param values 
	 */
	private void deleteReferencesFromJoinTable(String joinTable, String tableName,
			String fieldName, Map<Long, Collection<Long>> idMap) {

//	    StringBuilder builder = new StringBuilder();
	    String idName    = getJoinTableIDName   (tableName);
//	    String valueName = getJoinTableValueName(fieldName);
//	    //build up a where clause to delete each parent's removed entities...this segregates the where clause
//	    // by parent id to ensure we dont accidentally remove someone elses reference
//	    for (Map.Entry<Long, Collection<Long>> e : idMap.entrySet()) {
//	        if (builder.length() > 0) {
//	            builder.append(" or ");
//	        }
//	        builder.append("(")    .append(idName)   .append(" = ").append(e.getKey())
//	               .append(" and ").append(valueName).append(" in (").append(flattenCollection(e.getValue())).append(")");
//	        
//	    }
	    StringBuilder builder = new StringBuilder();
	    
    	try {
    		//delete the entries in the join table
    		database.delete(joinTable, idName + " in (" + flattenCollection(idMap.keySet()) + ")", null);
		} catch (SQLException se) {
			throw new RuntimeException(se);
		}
	}

	   /**
     * Bulk delete of all values corresponding to the ids passed in.
     * 
     * @param joinTable
     * @param tableName
     * @param fieldName
     * @param ids
     * @param values 
     */
    private void deleteValuesFromJoinTable(String joinTable, String tableName,
            String fieldName, Collection<Long> ids) {
        String joinTableWhereClause = getJoinTableIDName(tableName) + " in (" + flattenCollection(ids) + ")";
        try {
            //delete the entries in the join table
            database.delete(joinTable, joinTableWhereClause, null);
        } catch (SQLException se) {
            throw new RuntimeException(se);
        }
    }
	/**
	 * Save all items in the collection passed in.
	 * 
	 * @param os
	 * @throws UnsavedReferenceException 
	 */
	//TODO: may be a more efficient way to do this
	public void saveAll(Collection<? extends Object> os) {
	    System.out.println("saveAll: " + os.size() + " objects");
	    if (os.isEmpty()) {
	        return; //nothing to do
	    }
	    
	    Map<Class<?>, List<Object>> split = splitByClass(os);
	    checkIsOpened();
	    checkAllSameClass(os);
	    //NOTE: even though we know os is all in the same class hierarchy, there may be some base class elements that are not
	    // entities...check that here.
        checkIsAllEntityClass(split);
	    checkReferences(split);
	    
        saveEntitiesForAll(new ArrayList<Object>(os));
	    
        //NOTE: need to use Lists here, because order must be preserved (for lining up the IDs)
        //NOTE: must also include the ID in the value set
        Map<String, List<ValueSet>> values = this.dumpObjects(split, true);
        
        //do the bulk insert, and return a map of table names to lists of new ids
        Map<String, List<Long>>      idMap = this.database.bulkSave(values);

        //reconcile the new ids with the original objects...this is a little complicated
        // because of all of the data transofmrations..
        for (Map.Entry<String, List<Long>> entry : idMap.entrySet()) {
            Class<?> clazz = getClassFromTableName(entry.getKey());
            if (clazz == null) {
                throw new RuntimeException("Unable to look up class object from table name directly after an insert.  Something is wrong.");
            }
            List<Object> objects = split.get(clazz);
            if (objects == null || objects.isEmpty()) {
                throw new RuntimeException("Unable to look up list of original objects by class.  Something is definitely wrong");
            }
            List<Long> idList = entry.getValue();
            if (objects.size() != idList.size()) {
                throw new RuntimeException("Size mismatch between original objects and id list: expected " + objects.size() + ", encountered " + idList.size());
            }
            int ii = 0;
            for (Object o : os) {
                Entity entity = new Entity(reflector, o);
                entity.setId(idList.get(ii));
                ii++;
            }
        }

        //clear all of our references before calling saveCollectionsForAll
        values.clear();
        idMap.clear();
        split.clear();
        
        //save all of the collections associated with all of the objects.
        saveCollectionsForAll(os);
    }

    private void checkAllSameClass(Collection<? extends Object> os) {
        Class<?> theClass = null;
        for (Object o : os) {
            if (theClass == null) {
                theClass = o.getClass();
            } else if (!theClass.isAssignableFrom(o.getClass()) && !o.getClass().isAssignableFrom(theClass)) {
                throw new MixedCollectionException(theClass, o.getClass());
            }
        }
    }

    private Map<Class<?>, List<Object>> splitByClass(
            Collection<? extends Object> os) {
        Map<Class<?>, List<Object>> splitMap = new HashMap<Class<?>, List<Object>>();
        for (Object o : os) {
            List<Object> list;
            if (splitMap.containsKey(o.getClass())) {
                list = splitMap.get(o.getClass());
            } else {
                list = new ArrayList<Object>();
                splitMap.put(o.getClass(), list);
            }
            list.add(o);
        }
        return splitMap;
    }

//
//	/**
//	 * Save an updated object to persistent storage.
//	 * 
//	 * This is akin to SQL UPDATE.
//	 * 
//	 * @param o
//	 */
//	public void update(Object o) {
//        checkIsOpened();
//        checkIsEntity(o);
//        long id = this.getId(o);
//        ValueSet values = this.dumpObject(o);
//        try {
//        	database.update(this.getTableNameForClass(o.getClass()), values, "id = " + id, null);
//        	saveCollections(o, id);
//        } catch (SQLException se) {
//        	throw new RuntimeException(se);
//        }
//    }

    /**
     * 
     * @param clazz
     * @param whereClause
     * @param whereParams
     * @return 
     */
    public long count(Class<?> clazz, String whereClause, String[] whereParams) {
        checkIsOpened();
        checkIsEntityClass(clazz);
        try {
            return database.count(this.getTableNameForClass(clazz), whereClause, whereParams);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

	/**
	 * Delete an object from persistent storage.
	 * 
	 * @param o
	 */
	public void delete(Object o) {
        checkIsOpened();
        checkIsEntity(o);
        Entity entity = new Entity(reflector, o);
        long id = entity.getId();
    	try {
    	    //first, delete data from join tables
            deleteCollections(o, id);
            //then, delete the actual record
            System.out.println(o.getClass().getSimpleName() + " deleted with id: " + id);
    		database.delete(this.getTableNameForClass(o.getClass()), "id = " + id, null);
		} catch (SQLException se) {
			throw new RuntimeException(se);
		}
    }

	/**
	 * Helper method to delete the collections associated with an entity.  This will delete
	 * the entries in the appropriate join table and cascade the delete to the dependent
	 * entity.
	 * 
	 * @param o
	 * @param id
	 * @throws SQLException
	 */
    private void deleteCollections(Object o, long id) throws SQLException {
    	String tableName = getTableNameForClass(o.getClass());
        for (Method m : o.getClass().getMethods()) {
            if (isPersisted(m) && isCollection(m)) {
                OneToMany c = m.getAnnotation(OneToMany.class);
                if (c == null || c.value() == null) {
                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
                }

                String fieldName     = getFieldNameFromMethod(m);
                String joinTableName = buildJoinTableName(tableName, fieldName);

                if (isEntity(c.value())) {
                	//delete all of the old dependent objects for this collection
                    Map<Long, Collection<?>> map = new HashMap<Long, Collection<?>>();
                    map.put(id, Collections.EMPTY_LIST);
                	deleteDependents(joinTableName, c.value(), tableName, fieldName, isReference(m), map);
                } else {
                	deleteValuesFromJoinTable(joinTableName, tableName, fieldName, Arrays.asList(id));
                }
            }
        }
	}

	public void deleteAll(Class<?> clazz, String whereClause) {
        checkIsOpened();
        //first, delete data from join tables
        //then, delete the actual record
        System.out.println(clazz.getSimpleName() + " emptied");
    	try {
    		database.delete(this.getTableNameForClass(clazz), whereClause, null);
		} catch (SQLException se) {
			throw new RuntimeException(se);
		}
    }

	/**
	 * Get a single object by id
	 * 
	 * @param clazz
	 * @param id
	 * @return
	 */
	public <T> T get(Class<T> clazz, long id) {
	    //NOTE: create a new EntityCache, since this is the top level of a fetch for an entity
	    return doGet(clazz, id, newEntityBuilder());
	}

    /**
     * @return
     */
    private EntityBuilder newEntityBuilder() {
        return new EntityBuilder(this, this.reflector);
    }
	
	private <T> T doGet(Class<T> clazz, long id, EntityBuilder entityBuilder) {
        checkIsOpened();
        checkIsEntityClass(clazz);
        List<String> columns = this.getColumns(clazz);
        QueryCursor c = null;
        T o = null;
        //if the cache already contains this entity (we've fetched it somewhere up the entity tree), return it's value
        if (entityBuilder.contains(clazz, id)) {
            o = entityBuilder.get(clazz, id);
        } else {
            //otherwise, we need to get the entity from the database.
            try {
                c = database.query(this.getTableNameForClass(clazz), columns.toArray(new String[columns.size()]),
                            "id = " + id, null, null, null, null);
                if (c != null && !c.isEmpty()) {
                    c.moveToFirst();
                    o = entityBuilder.cursorToObject(c, true, clazz);
                }
                //clean up (for GC)
                c = null;
                fillEntities(clazz, Arrays.asList(o), entityBuilder);
                fillCollections(clazz, Arrays.asList(o), entityBuilder);
                return o;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //just in case, clean up after ourselves
                if (c != null) {
                    c.close();
                }
            }
        }
        return o;
    }

//    private void getCollections(Object o, long id) {
//    	try {
//	    	String tableName = getTableNameForClass(o.getClass());
//	        for (Method m : o.getClass().getMethods()) {
//	            if (isPersisted(m) && isCollection(m)) {
//	                OneToMany c = m.getAnnotation(OneToMany.class);
//	                if (c == null || c.value() == null) {
//	                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
//	                }
//	
//	                String fieldName     = getFieldNameFromMethod(m);
//	                String joinTableName = buildJoinTableName(tableName, fieldName);
//
//	                Method a = null;
//	                Method s = null;
//	                try {
//	                    a = this.reflector.getAdder(o.getClass(), fieldName, c.value());
//	                } catch (NoSuchMethodException e) {
//	                    try {
//	                        s = this.reflector.getSetter(o.getClass(), fieldName);
//	                    } catch (NoSuchMethodException e2) {
//	                        throw e;
//	                    }
//	                }
//
//                    //check if we need to add a reference to the child object back to the parent object
////                    Method rm = findReference(c.value(), o.getClass());
//                    
//	                //pull this collection from persistence and set it into the object
//	                Collection<?> collection = getOneCollection(joinTableName, m.getReturnType(), c.value(), tableName, fieldName, id, o);
//
//	                //if the object defines a customer adder, use that here to add each item individually
//	                //NOTE: this does not attempt to add the other side of the relationship...it's assumed
//	                // that if a model object has a custom adder, that adder will set the necessary reciprocal
//	                // references
//	                if (a != null) {
//	                    for (Object e : collection) {
//	                        a.invoke(o, e);
//	                    }
//	                } else {
//	                    //otherwise, use the collection setter
//	                    
//    	                //check if we need to add a reference to the child object back to the parent object
//    	                Method rm = findReference(c.value(), o.getClass());
//    	                
//    	                if (rm != null) {
//    	                	String refFieldName = getFieldNameFromMethod(rm);
//    	                	//a reference exists...set that reference here
//    	                	Method rs = this.reflector.getSetter(c.value(), refFieldName);
//    	                	if (rs == null) {
//    	                		throw new RuntimeException("Unable to set reference, setter for '" + refFieldName + "' does not exist in '" + c.value().getCanonicalName() + "'");
//    	                	}
//    	                	for (Object co : collection) {
//    	                		rs.invoke(co, o);
//    	                	}
//    	                }
//    	        		//set the collection into this class instance, using the field's setter
//    	                s.invoke(o, collection);
//	                }
//	            }
//	        }
//    	} catch (Exception e) {
//    		throw new RuntimeException(e);
//    	}
//    }

    /**
     * Attempt to find a reference back to a parent object in a class.
     * 
     * @param typeClass
     * @param parentClass
     * @return
     */
    private Method findReference(Class<?> typeClass, Class<? extends Object> parentClass) {
    	Method refM = null;
        for (Method m : typeClass.getMethods()) {
	        if (m.isAnnotationPresent(Reference.class) && m.getReturnType().equals(parentClass)) {
	        	refM = m;
	        	break;
	        }
        }
        return refM;
	}

	/**
     * Get one collection and set it into the object.
     * 
     * NOTE: this is pulled out into a separate method to allow us to define the generic param T
     * which helps document the code and gives us compile time type safety.
     * 
     * @param o
     * @param id
     * @param tableName
     * @param valueClass
     * @param fieldName
     * @param joinTableName
     * @return 
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
	 * @throws SQLException 
     */
//	private <T> Collection<T> getOneCollection(String joinTable, Class<?> collectionClass, Class<T> valueClass, String tableName, String fieldName, long id, Object o)
//			throws NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SQLException {
//		//create a new collection to match the type declared in the entity class
//		Collection<T> collection = newCollection(collectionClass, valueClass);
//
//		//get all of the objects, and store in the collection
//		getFromJoinTable(joinTable, valueClass, tableName, fieldName, id, collection);
//		return collection;
//	}
//
//	private <T> void getFromJoinTable(String joinTable, Class<T> valueClass,
//			String tableName, String fieldName, long id,
//			Collection<T> collection) throws SQLException {
//		String joinTableWhereClause = getJoinTableIDName(tableName) + " = " + id;
//		QueryCursor c = database.query(joinTable, new String[] {getJoinTableValueName(fieldName)}, joinTableWhereClause, null, null, null, null);
//
//		try {
//			//only do this if there are dependent objects
//	        if (c != null && !c.isEmpty()) {
//		
//	        	//build a comma separated list of ids from the cursor results
//				StringBuilder ids = new StringBuilder();
//				c.moveToFirst();
//				while(!c.isAfterLast()) {
//					if (ids.length() > 0) {
//						ids.append(",");
//					}
//					ids.append(c.getLong(0));
//					c.moveToNext();
//				}
//				//clean up
//                c.close();
//                c = null;
//
//				//build the in clause, and get all objects for those ids
//				StringBuilder builder = new StringBuilder("id in (").append(ids).append(")");
//				collection.addAll(doGetAll(valueClass, builder.toString()));
//	        }
//		} finally {
//		    //just in case, clean up after ourselves
//			if (c != null) {
//				c.close();
//			}
//		}
//	}

	/**
	 * Contruct a collection of the specified type, to hold the specified value type.
	 * 
	 * NOTE: the value class is passed in for the generics, to ensure we have compile time type safety
	 * 
	 * @param collectionClass
	 * @param valueClass
	 * @return
	 */
	private <T> Collection<T> newCollection(Class<?> collectionClass, Class<T> valueClass) {
		Collection<T> newCol = null;
		//if the collection class is a concrete class (well, we assume its concrete)...
		if (!collectionClass.isInterface()) {
			try {
				//attempt to instantiate the class
				newCol = (Collection<T>) collectionClass.newInstance();
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (InstantiationException e) {
				//we can't instantiate it...fall thru to use some default options based on the type of
				// collection
			}
		}
		//default instances of different types of collections
		if (newCol == null) {
    		if (Set.class.isAssignableFrom(collectionClass)) {
    			newCol = new HashSet<T>();
    		} else if (List.class.isAssignableFrom(collectionClass)) {
    			newCol = new ArrayList<T>();
    		} else if (Collection.class.isAssignableFrom(collectionClass)) {
    			newCol = new ArrayList<T>();
            } else {
                throw new RuntimeException("Unsupported type: " + collectionClass.getCanonicalName());
            }
		}
		return newCol;
	}

	/**
	 * Get all objects that conform to the supplied where clause.
	 * 
	 * @param clazz
	 * @param whereClause The where fragment (e.g. "id in (2,3,4)"), or null to get all of the objects
	 * in the db.  
	 * @return
	 */
	public <T> List<T> getAll(Class<T> clazz, String whereClause) {
	    return doGetAll(clazz, whereClause, newEntityBuilder());
	}

	/**
	 * Perform a bulk fetch of objects that conform to the supplied where clause.
	 * 
	 * This method is called a lot, when building the full entity relationship tree.
	 * It also carries the entity map for this fetch, to ensure we don't chase
	 * circular references, and that we don't fetch the same objects more than once.
	 * 
	 * @param clazz
	 * @param whereClause
	 * @param entityCache
	 * @return
	 */
	private <T> List<T> doGetAll(Class<T> clazz, String whereClause, EntityBuilder entityBuilder) {
	    
        checkIsOpened();
        checkIsEntityClass(clazz);
        List<String> columns = this.getColumns(clazz);
        QueryCursor c = null;
        try {
            Collection<Long> allIds = doGetAllIds(clazz, whereClause, true);
            List<T> list = new LinkedList<T>();
            
            Set<Long> toFetch = new HashSet<Long>();
            for (Long id : allIds) {
                if (entityBuilder.contains(clazz, id)) {
                    list.add(entityBuilder.get(clazz, id));
                } else {
                    toFetch.add(id);
                }
            }

            String where = EntityMetaData.ID_FIELD + " in (" + flattenCollection(toFetch) + ")";
            c = database.query(this.getTableNameForClass(clazz), columns.toArray(new String[columns.size()]),
                        where, null, null, null, null);
            //if there's nothing to do, we'll return an empty list
            if (c != null && !c.isEmpty()) {
                c.moveToFirst();
                list.addAll(entityBuilder.cursorToObjects(c, true, clazz));
                
                fillEntities(clazz, list, entityBuilder);
                fillCollections(clazz, list, entityBuilder);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //just in case, we want to clean up after ourselves
            if (c != null) {
                c.close();
            }
        }
    }

	private Collection<Long> doGetAllIds(Class<?> clazz, String whereClause, boolean uniqueIds) {
        checkIsOpened();
        checkIsEntityClass(clazz);
        QueryCursor c = null;
        try {
            Collection<Long> allIds = uniqueIds ? new HashSet<Long>() : new LinkedList<Long>();
            
            c = database.query(this.getTableNameForClass(clazz), new String[] {EntityMetaData.ID_FIELD},
                        whereClause, null, null, null, null);
            //if there's nothing to do, we'll return an empty list
            if (c != null && !c.isEmpty()) {
                c.moveToFirst();
                do {
                    allIds.add(c.getLong(0));
                } while (c.moveToNext());
            }
            return allIds;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //just in case, we want to clean up after ourselves
            if (c != null) {
                c.close();
            }
        }
	}

	/**
     * Fill in the singular entities for the list of objects.  Done in bulk so we can
     * minimize the number of queries executed.
     * 
     * @param clazz
     * @param list
     */
	private <T> void fillEntities(Class<T> clazz, List<T> list, EntityBuilder entityBuilder) {
        try {
            String tableName = getTableNameForClass(clazz);
            for (Method m : clazz.getMethods()) {
                Class<?> typeClass = m.getReturnType();
                if (isPersisted(m) && isEntity(typeClass)) {
                    //build a map of the parent object ids to objects, for easy look up later
                    Map<Long, T> parentMap = new HashMap<Long, T>(list.size());
                    for (T o : list) {
                        Entity entity = new Entity(reflector, o);
                        parentMap.put((Long) entity.getId(), o);
                        
                    }
                    //build a bulk query to get the child entity id's for all of the objects passed in 
                    String fieldName = getFieldNameFromMethod(m);
                    String where = EntityMetaData.ID_FIELD + " in (" + flattenCollection(parentMap.keySet()) + ")";
                    QueryCursor c = database.query(tableName, new String[] {EntityMetaData.ID_FIELD, fieldName}, where, null, null, null, null);
                    Map<Long, Long> entityToRefMap = new HashMap<Long, Long>();
                    
                    //build a map of child->parent ids here, to allow us to map back from child to parent
                    try {
                        //FIXME: i think there's a bug here, that's preventing a number of the objects from being populated...see VisWeek Rooms, the Building and FloorPlan isnt being populated
                        //FIXME: the bug is that multiple "results" map to a childId...we need a map of collections instead of just a singluar map
                        if (c != null && !c.isEmpty()) {
                            c.moveToFirst();
                            while (!c.isAfterLast()) {
                                Long childId = c.getLong(1);
                                //-1 indicates a null/empty entity...no need to fetch it here
                                if (childId >= 0) {
                                    entityToRefMap.put(c.getLong(0), childId);
                                }
                                c.moveToNext();
                            }
                        }
                    } finally {
                        //clean up after ourselves
                        if (c != null) {
                            c.close();
                        }
                        c = null;
                    }
                    
                    //build a bulk query of all of the referenced entities
                    where = EntityMetaData.ID_FIELD + " in (" + flattenCollection(entityToRefMap.values()) + ")";
                    Map<Long, ?> entityMap = getEntityMap(doGetAll(typeClass, where, entityBuilder));

                    //process the child entities, looking up the parent and 
                    Method s = this.reflector.getSetter(clazz, fieldName);
                    for (Object o : list) {
                        Entity entity = new Entity(reflector, o);
                        Long refId = entityToRefMap.get(entity.getId());
                        //only proceed if there's a referenced entity for this object
                        if (refId != null) {
                            Object ref = entityMap.get(refId);
                            if (ref == null) {
                                System.out.println("WARN: Cannot find referenced object.  This should never happen");
                                continue;
                            }
                            s.invoke(o, ref);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}

	private Map<Long, Object> getEntityMap(List<?> list) throws Exception {
        Map<Long, Object> entityMap = new HashMap<Long, Object>();
	    for (Object o : list) {
	        Entity entity = new Entity(reflector, o);
	        entityMap.put(entity.getId(), o);
	    }
        return entityMap;
    }

    /**
	 * Fill in the collections for the list of objects.  Done in bulk so we can
	 * minimize the number of queries executed.
	 * 
	 * @param clazz
	 * @param objects
	 */
    private <T> void fillCollections(Class<T> clazz, List<T> objects, EntityBuilder entityBuilder) {
        try {
            String tableName = getTableNameForClass(clazz);
            for (Method m : clazz.getMethods()) {
                if (isPersisted(m) && isCollection(m)) {
                    OneToMany c = m.getAnnotation(OneToMany.class);
                    if (c == null || c.value() == null) {
                        throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
                    }
    
                    String fieldName     = getFieldNameFromMethod(m);
                    String joinTableName = buildJoinTableName(tableName, fieldName);
    
                    Method a = null;
                    Method s = null;
                    try {
                        a = this.reflector.getAdder(clazz, fieldName, c.value());
                    } catch (NoSuchMethodException e) {
                        try {
                            s = this.reflector.getSetter(clazz, fieldName);
                        } catch (NoSuchMethodException e2) {
                            throw e;
                        }
                    }
    
                    List<Long> parentIds = new ArrayList<Long>();
                    for (T o : objects) {
                        Entity entity = new Entity(reflector, o);
                        parentIds.add(entity.getId());
                    }

                    //pull this collection from persistence and set it into the object
                    Map<Long, ?> map = getFromJoinTableBulk(joinTableName, m.getReturnType(), c.value(), tableName, fieldName, parentIds, entityBuilder);

                    for (T o : objects) {
                        Entity entity = new Entity(reflector, o);
                        Collection<?> collection = (Collection<?>) map.get(entity.getId());
                        if (collection == null) {
                            continue;
                        }
                        //if the object defines a customer adder, use that here to add each item individually
                        //NOTE: this does not attempt to add the other side of the relationship...it's assumed
                        // that if a model object has a custom adder, that adder will set the necessary reciprocal
                        // references
                        if (a != null) {
                            for (Object e : collection) {
                                a.invoke(o, e);
                            }
                        } else {
                            //otherwise, use the collection setter
                            
                            //check if we need to add a reference to the child object back to the parent object
                            Method rm = findReference(c.value(), o.getClass());
                            
                            if (rm != null) {
                                String refFieldName = getFieldNameFromMethod(rm);
                                //a reference exists...set that reference here
                                Method rs = this.reflector.getSetter(c.value(), refFieldName);
                                if (rs == null) {
                                    throw new RuntimeException("Unable to set reference, setter for '" + refFieldName + "' does not exist in '" + c.value().getCanonicalName() + "'");
                                }
                                for (Object co : collection) {
                                    rs.invoke(co, o);
                                }
                            }
                            //set the collection into this class instance, using the field's setter
                            s.invoke(o, collection);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Fetch items referenced from a join table, to be placed in an entity's collection.  This will fetch
     * child items for many parents at a time.
     * 
     * @param joinTable
     * @param collectionClass
     * @param valueClass
     * @param tableName
     * @param fieldName
     * @param parentIds
     * @param entityBuilder
     * @return A map of parent id to collection of fetched children.
     * @throws SQLException
     */
    private <T> Map<Long, Collection<T>> getFromJoinTableBulk(String joinTable, Class<?> collectionClass, Class<T> valueClass,
            String tableName, String fieldName, List<Long> parentIds, EntityBuilder entityBuilder) throws SQLException {
        String idName = getJoinTableIDName(tableName);
        String joinTableWhereClause = idName + " in (" + flattenCollection(parentIds) + ")";
        QueryCursor c = database.query(joinTable, new String[] {idName, getJoinTableValueName(fieldName)}, joinTableWhereClause, null, null, null, null);

        try {
            Map<Long, Collection<T>> objectMap = new HashMap<Long, Collection<T>>();
            Set<T> existing = new HashSet<T>();
            //only do this if there are dependent objects
            if (c != null && !c.isEmpty()) {
        
                //build a comma separated list of ids from the cursor results
                c.moveToFirst();
                StringBuilder ids = new StringBuilder();
                Map<Long, Long> parentIdMap = new HashMap<Long, Long>();
                while(!c.isAfterLast()) {
                    if (ids.length() > 0) {
                        ids.append(",");
                    }
                    Long id = c.getLong(0);
                    Long fk = c.getLong(1);
                    //if this entity already exists in the cache, just use that object
                    // rather than fetch it again
                    if (entityBuilder.contains(valueClass, fk)) {
                        existing.add(entityBuilder.get(valueClass, fk));
                    } else {
                        parentIdMap.put(fk, id);
                        ids.append(fk);
                    }
                    c.moveToNext();
                }

                //clean up
                c.close();
                c = null;

                //build the in clause, and get all objects for those ids
                StringBuilder builder = new StringBuilder("id in (").append(ids).append(")");
                Collection<T> coll = doGetAll(valueClass, builder.toString(), entityBuilder);
                //join with the existing object set, assembled above
                coll.addAll(existing);
                for (T o : coll) {
                    Entity entity = new Entity(reflector, o);
                    Long parentId = parentIdMap.get(entity.getId());
                    
                    if (!objectMap.containsKey(parentId)) {
                        objectMap.put(parentId, newCollection(collectionClass, valueClass));
                    }
                    objectMap.get(parentId).add(o);
                }                
            }
            return objectMap;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //just in case, we still need to clean up
            if (c != null) {
                c.close();
            }
        }
    }

    private String flattenCollection(Collection<?> collection) {
        StringBuilder ids = new StringBuilder();
        for (Object o : collection) {
            if (ids.length() > 0) {
                ids.append(",");
            }
            ids.append(o.toString());
        }
        return ids.toString();
    }

    private void checkIsOpened() {
        if (!this.database.isOpen()) {
            throw new RuntimeException("You must call open before accessing any ORM methods");
        }
    }

    public boolean isUseORMeta() {
        return this.useORMeta;
    }

    public void setUseORMeta(boolean useORMeta) {
        this.useORMeta = useORMeta;
    }
    
    public ORMeta getMetaData() {
        try {
            return doGetAll(ORMeta.class, null, newEntityBuilder()).iterator().next();
        } catch (Exception e) {
            return null;
        }
    }
}