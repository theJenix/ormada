package org.ormada;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.ormada.annotations.OneToMany;
import org.ormada.annotations.Reference;
import org.ormada.annotations.Text;
import org.ormada.annotations.Transient;
import org.ormada.dialect.Dialect;
import org.ormada.dialect.QueryCursor;
import org.ormada.dialect.ValueSet;
import org.ormada.model.ORMeta;

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

    private static final String ID_GETTER = "getId";

    // Database creation sql format
    private static final String DATABASE_CREATE_FMT = "create table %s (%s);";

    private static final int CURRENT_ORM_VERSION = 1;

    private List<Class<?>> entities;

    private Dialect database;

    private boolean useORMeta;

    public ORMDataSource(Dialect dialect, Class<?> ... entities) {
    	this.database = dialect;
        this.entities = Arrays.asList(entities);
        for (Class<?> entity : entities) {
            checkIsEntityClass(entity);
        }
    }

    public void open() throws SQLException {
        this.database.open(this);
    }

    public void close() throws SQLException {
    	this.database.close();
    }

    public void createAllTables(int dbVersion) {
    	try {
	        //if we need to use the ORMeta table to keep track of version info, create the table here
	        // and insert the one record
	        if (this.useORMeta) {
	            createTablesForClass(database, ORMeta.class);
	            ORMeta meta = new ORMeta();
	            meta.setDbVersion(dbVersion);
	            meta.setOrmVersion(CURRENT_ORM_VERSION);
	            save(meta);
	        }

	        for (Class<?> entity : entities) {
                createTablesForClass(database, entity);
            }
    	} catch (SQLException se) {
    		throw new RuntimeException(se);
    	}
    }

    private void createTablesForClass(Dialect database,
            Class<?> clazz) throws SQLException {
        List<String> createStmts = new LinkedList<String>();
        //add the main class table
        StringBuilder fieldListBuilder = new StringBuilder();
        for (Method m : clazz.getMethods()) {
            //process the getters for singular objects here
            //no collections...they get processed later
            if (isPersisted(m) && !java.util.Collection.class.isAssignableFrom(m.getReturnType())) {
                if (fieldListBuilder.length() > 0) {
                    fieldListBuilder.append(",");
                }
                fieldListBuilder.append(getFieldNameFromMethod(m)).append(" ");
                if (m.getName().equals(ID_GETTER)) {
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
            if (isPersisted(m) && java.util.Collection.class.isAssignableFrom(m.getReturnType())) {
                OneToMany c = m.getAnnotation(OneToMany.class);
                if (c == null || c.value() == null) {
                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
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
            typeClass.getMethod(ID_GETTER);
            //NOTE: ORMeta will not be in entities, but we want to treat it as an entity if we're using that class to store data.
            return (ORMeta.class.isAssignableFrom(typeClass) && this.useORMeta) || entities.contains(typeClass);
        } catch (Exception e) {}
        return false;
    }

    private String getFieldNameFromMethod(Method m) {
        String stripped = m.getName().startsWith("is") ? m.getName().substring(2) : m.getName().substring(3);
        return toCamelCase(stripped);
    }

    private Class<?> getFieldType(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        return getGetter(clazz, field).getReturnType();
    }

    private Method getGetter(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        Method m = null;
        try {
            String name = "is" + toTitleCase(field);
            m = clazz.getMethod(name);
        } catch (NoSuchMethodException nme) {
            String name = "get" + toTitleCase(field);
            m = clazz.getMethod(name);
            //let this one throw
        }
        return m;
    }

    private Method getAdder(Class<?> clazz, String field, Class<?> fieldType) throws SecurityException, NoSuchMethodException {
        String name = "add" + toTitleCase(toSingular(field));
        return clazz.getMethod(name, fieldType);
    }
    
    private Method getSetter(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        String name = "set" + toTitleCase(field);
        return clazz.getMethod(name, getFieldType(clazz, field));
    }

    /**
     * Test if a field is persisted by looking at it's getter method.
     * 
     * This returns true if:
     * 	The method passed in starts with "get" or "is", is not declared in Object, and is not marked as Transient or Reference
     * 
     * @param m
     * @return
     */
    private boolean isPersisted(Method m) {
        return (m.getName().startsWith("get") || m.getName().startsWith("is")) &&
        		m.getDeclaringClass() != Object.class   &&
        		!m.isAnnotationPresent(Transient.class) &&
        		!m.isAnnotationPresent(Reference.class);
    }

    /**
     * Test if a field should be included in the insert/update ContentValues set
     *
     * NOTE: we test the method here...it's a little funky, but as a general rule
     * if a getter exists (and it's not the id, or in Object), it can be
     * inserted/updated.
     *
     * @param m
     * @return
     */
    private boolean isInsertedOrUpdated(Method m) {
        return isPersisted(m) && !m.getName().equals(ID_GETTER);
    }

    private String toCamelCase(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private String toSingular(String str) {
        int lastInx = str.length() - 1;
        //strip off the trailing s
        if ((str.charAt(lastInx) | 0x60) == 's') {
            lastInx--;
            //if the "singular" ends with an s, the plural will end with es...string that e off here
            if ((str.charAt(lastInx - 1) | 0x60) == 'e') {
                lastInx--;
            }
        }
        return str.substring(0, lastInx + 1);
    }

    private String toTitleCase(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void dropTableForClass(Dialect database,
            Class<?> clazz) throws SQLException {
        //drop any join tables
        //NOTE: because of how we drop these tables, we likely cannot have any actual foreign key constraints
        // or else we'll get all kinds of weird "out of order" issues
        for (Method m : clazz.getMethods()) {
            Class<?> typeClass = m.getReturnType();
            if (isPersisted(m) && java.util.Collection.class.isAssignableFrom(typeClass)) {
                database.execSQL("DROP TABLE IF EXISTS " + buildJoinTableName(getTableNameForClass(clazz), m.getName()));
            }
        }
        //drop the main table
        database.execSQL("DROP TABLE IF EXISTS " + getTableNameForClass(clazz));
    }

    public void upgradeAllTAbles(int oldVersion, int newVersion) {
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

    public long getId(Object o) {
        try {
            return (Long) getGetter(o.getClass(), "id").invoke(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setId(Object o, long id) {
        try {
            getSetter(o.getClass(), "id").invoke(o, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ValueSet dumpObject(Object o) {
        ValueSet values = this.database.prepareValueSet();
        try {
            for (Method m : o.getClass().getMethods()) {
                //process the getters for singular objects here
                //NOTE: exclude anything from the base Object class here
                //NOTE: also exclude the primary key
                if (isInsertedOrUpdated(m)) {
                    Class<?> typeClass = m.getReturnType();
                    //no collections...they get processed later
                    if (!java.util.Collection.class.isAssignableFrom(typeClass)) {
                        Object val = m.invoke(o);
                        setValueIntoContentValues(values, typeClass, getFieldNameFromMethod(m), val);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return values;
    }

    private String getTableNameForClass(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    /**
     * Get a list of the columns that make up this class...this will be used for selecting objects.
     * @param clazz
     * @return
     */
    public List<String> getColumns(Class<?> clazz) {
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
     * @throws Exception
     */
    public void setValueIntoContentValues(ValueSet values, Class typeClass, String key, Object value) throws Exception {
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
                //entities: store the ID
                values.put(key, (int)getId(value));
        	} else {
                values.put(key, -1);
        	}
        } else if (Serializable.class.isAssignableFrom(typeClass)) {
            //unserialize the object
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            // Serialize the object
            out.writeObject(value);
            values.put(key, baos.toByteArray());
            out.close();
        } else {
            throw new RuntimeException("Unsupported type: " + typeClass.getCanonicalName());
        }
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
    public void setValueFromCursor(Object o, QueryCursor c, int col) throws Exception {
        String name = c.getColumnName(col);
        Method m = getSetter(o.getClass(), name);
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
        } else if (isEntity(typeClass)) {
            //NOTE: since entity ids cannot be < 0, and null long columns are a pain in the butt,
            // use -1 to denote null
            long val = c.getLong(col);
            if (val >= 0) {
            	//BIG NOTE: this is a database call, to get the entity
                m.invoke(o, get(typeClass, val));
            } else {
            	m.invoke(o, new Object[]{null});
            }
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

    private void checkIsEntity(Object o) {
        checkIsEntityClass(o.getClass());
    }

    private void checkIsEntityClass(Class<?> clazz) {
        if (!this.isEntity(clazz)) {
            throw new RuntimeException("Class " + clazz.getCanonicalName() + " is not a persisted entity.");
        }
    }

	public <T> void refresh(T o) {
    	checkIsOpened();
    	checkIsEntity(o);
    	Object persisted = get(o.getClass(), getId(o));
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
	            	Method g = getGetter(from.getClass(), fieldName);
	            	Method s = getSetter(to.getClass(), fieldName);
	            	//copy the value from "from" to "to"
	            	s.invoke(to, new Object[]{g.invoke(from)});
	            }
	        }
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
	}

	public long save(Object o) {
    	//check to see that we can save this object
        checkIsOpened();
        checkIsEntity(o);
        //save all dependent entities
        
        //insert the object into the database and update the object with the ID
        ValueSet values = this.dumpObject(o);
    	try {
    		long id = database.insert(this.getTableNameForClass(o.getClass()), values);
    		this.setId(o, id);
    		
    		saveCollections(o, id);
    		return id;
		} catch (SQLException se) {
			throw new RuntimeException(se);
		}
    }

    private void saveCollections(Object o, long id) {
    	try {
	    	String tableName = getTableNameForClass(o.getClass());
	        for (Method m : o.getClass().getMethods()) {
	            Class<?> typeClass = m.getReturnType();
	            if (isPersisted(m) && java.util.Collection.class.isAssignableFrom(typeClass)) {
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
		                	//delete all of the old dependent objects for this collection
		                	deleteDependents(joinTableName, c.value(), tableName, fieldName, id);
			                //save all the individual entities (which will populate the objects' ids) 
			                saveAll(collection);
			                saveIds = true;
		                } else {
		                	deleteValues(joinTableName, tableName, fieldName, id);
		                }
		                for (Object co : collection) {
		                	Object coVal = saveIds ? getId(co) : co;
		                	addToJoinTable(joinTableName, c.value(), tableName, fieldName, id, co);
		                }
	                }
	            }
	        }
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
	}

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
	 * @param joinTable
	 * @param valueClass
	 * @param tableName
	 * @param fieldName
	 * @param id
	 * @throws SQLException 
	 */
	private void deleteDependents(String joinTable, Class<?> valueClass, String tableName, String fieldName, long id) throws SQLException {
		String joinTableWhereClause = getJoinTableIDName(tableName) + " = " + id;
		QueryCursor c = database.query(joinTable, new String[] {getJoinTableValueName(fieldName)}, joinTableWhereClause, null, null, null, null);
		try {
	        if (c != null && !c.isEmpty()) {
	        	//build a comma separated list of ids from the cursor results
				StringBuilder ids = new StringBuilder();
				c.moveToFirst();
				while(!c.isAfterLast()) {
					if (ids.length() > 0) {
						ids.append(",");
					}
					ids.append(c.getLong(0));
					c.moveToNext();
				}
	
				//delete all the dependent objects (using the where clause built up)
				StringBuilder builder = new StringBuilder("id in (").append(ids).append(")");
				deleteAll(valueClass, builder.toString());
				deleteValues(joinTable, tableName, fieldName, id);
	        }
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}


	private void deleteValues(String joinTable, String tableName,
			String fieldName, long id) {
		String joinTableWhereClause = getJoinTableIDName(tableName) + " = " + id;
    	try {
    		//delete the entries in the join table
    		database.delete(joinTable, joinTableWhereClause, null);
		} catch (SQLException se) {
			throw new RuntimeException(se);
		}
	}

	//TODO: may be a more efficient way to do this
	public void saveAll(Collection<? extends Object> os) {
        for (Object o : os) {
            save(o);
        }
    }

	public void update(Object o) {
        checkIsOpened();
        checkIsEntity(o);
        long id = this.getId(o);
        ValueSet values = this.dumpObject(o);
        try {
        	database.update(this.getTableNameForClass(o.getClass()), values, "id = " + id, null);
        	saveCollections(o, id);
        } catch (SQLException se) {
        	throw new RuntimeException(se);
        }
    }

	public void delete(Object o) {
        checkIsOpened();
        checkIsEntity(o);
        long id = this.getId(o);
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

    private void deleteCollections(Object o, long id) throws SQLException {
    	String tableName = getTableNameForClass(o.getClass());
        for (Method m : o.getClass().getMethods()) {
            Class<?> typeClass = m.getReturnType();
            if (isPersisted(m) && java.util.Collection.class.isAssignableFrom(typeClass)) {
                OneToMany c = m.getAnnotation(OneToMany.class);
                if (c == null || c.value() == null) {
                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
                }

                String fieldName     = getFieldNameFromMethod(m);
                String joinTableName = buildJoinTableName(tableName, fieldName);

                if (isEntity(c.value())) {
                	//delete all of the old dependent objects for this collection
                	deleteDependents(joinTableName, c.value(), tableName, fieldName, id);
                } else {
                	deleteValues(joinTableName, tableName, fieldName, id);
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

	public <T> T get(Class<T> clazz, long id) {
        checkIsOpened();
        checkIsEntityClass(clazz);
        List<String> columns = this.getColumns(clazz);
        QueryCursor c = null;
        try {
            c = database.query(this.getTableNameForClass(clazz), columns.toArray(new String[columns.size()]),
                        "id = " + id, null, null, null, null);
            T o = null;
            if (c != null && !c.isEmpty()) {
                c.moveToFirst();
                o = cursorToObject(c, columns, clazz);
                getCollections(o, id);
            }
            return o;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private void getCollections(Object o, long id) {
    	try {
	    	String tableName = getTableNameForClass(o.getClass());
	        for (Method m : o.getClass().getMethods()) {
	            Class<?> typeClass = m.getReturnType();
	            if (isPersisted(m) && java.util.Collection.class.isAssignableFrom(typeClass)) {
	                OneToMany c = m.getAnnotation(OneToMany.class);
	                if (c == null || c.value() == null) {
	                    throw new RuntimeException("Collections must be marked with the appropriate annotation, or @Transient");
	                }
	
	                String fieldName     = getFieldNameFromMethod(m);
	                String joinTableName = buildJoinTableName(tableName, fieldName);

	                Method a = null;
	                Method s = null;
	                try {
	                    a = getAdder(o.getClass(), fieldName, c.value());
	                } catch (NoSuchMethodException e) {
	                    try {
	                        s = getSetter(o.getClass(), fieldName);
	                    } catch (NoSuchMethodException e2) {
	                        throw e;
	                    }
	                }

	                //pull this collection from persistence and set it into the object
	                Collection<?> collection = getOneCollection(joinTableName, c.value(), tableName, fieldName, id, o);

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
    	                	Method rs = getSetter(c.value(), refFieldName);
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
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

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
	private <T> Collection<T> getOneCollection(String joinTable, Class<T> valueClass, String tableName, String fieldName, long id, Object o)
			throws NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, SQLException {
		//create a new collection to match the type declared in the entity class
		Collection<T> collection = newCollection(getFieldType(o.getClass(), fieldName), valueClass);

		//get all of the objects, and store in the collection
		getFromJoinTable(joinTable, valueClass, tableName, fieldName, id, collection);
		return collection;
	}

	private <T> void getFromJoinTable(String joinTable, Class<T> valueClass,
			String tableName, String fieldName, long id,
			Collection<T> collection) throws SQLException {
		String joinTableWhereClause = getJoinTableIDName(tableName) + " = " + id;
		QueryCursor c = database.query(joinTable, new String[] {getJoinTableValueName(fieldName)}, joinTableWhereClause, null, null, null, null);

		try {
			//only do this if there are dependent objects
	        if (c != null && !c.isEmpty()) {
		
	        	//build a comma separated list of ids from the cursor results
				StringBuilder ids = new StringBuilder();
				c.moveToFirst();
				while(!c.isAfterLast()) {
					if (ids.length() > 0) {
						ids.append(",");
					}
					ids.append(c.getLong(0));
					c.moveToNext();
				}
				//build the in clause, and get all objects for those ids
				StringBuilder builder = new StringBuilder("id in (").append(ids).append(")");
				collection.addAll(getAll(valueClass, builder.toString()));
	        }
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

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
		if (Set.class.isAssignableFrom(collectionClass)) {
			return new HashSet<T>();
		} else if (List.class.isAssignableFrom(collectionClass)) {
			return new ArrayList<T>();
		} else if (Collection.class.isAssignableFrom(collectionClass)) {
			return new ArrayList<T>();
        } else {
            throw new RuntimeException("Unsupported type: " + collectionClass.getCanonicalName());
        }
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
        checkIsOpened();
        checkIsEntityClass(clazz);
        List<String> columns = this.getColumns(clazz);
        QueryCursor c = null;
        try {
            List<T> list = new LinkedList<T>();
            c = database.query(this.getTableNameForClass(clazz), columns.toArray(new String[columns.size()]),
                        whereClause, null, null, null, null);
            //if theres nothing to do, we'll return an empty list
            if (c != null && !c.isEmpty()) {
                c.moveToFirst();
                while (!c.isAfterLast()) {
                    T o = cursorToObject(c, columns, clazz);
                    long id = getId(o);
                    getCollections(o, id);
                    list.add(o);
                    c.moveToNext();
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private <T> T cursorToObject(QueryCursor c, List<String> columns, Class<T> clazz) throws Exception {
        T instance = clazz.newInstance();
        for (int ii = 0; ii < c.getColumnCount(); ii++) {
            this.setValueFromCursor(instance, c, ii);
        }

        return instance;
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
            return getAll(ORMeta.class, null).iterator().next();
        } catch (Exception e) {
            return null;
        }
    }
}