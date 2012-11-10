ORMada
======

A powerful, dirt simple ORM library.

This project started as a simple ORM package for Android, and has grown to become a general purpose ORM supporting many popular SQL databases.  The goal of this project is simplicity: provide as thin a veneer as possible to allow saving and retrieving objects from a SQL database.  As a result, you will not find the following popular ORM features:

* Session and Transaction management
* Cache
* Infinite tuning options

Additionally, ORMada does not attempt to insulate your code from changes in the underlying database framework.  As a result, there isn't a complicated database-agnostic set of Criteria objects.  Instead, you use database specific syntax and functions, like you would in a regular SQL where clause.  While this removes some of the flexibility of the ORM library, it vastly decreases complexity both to build and use the library.

This library is written to emphasize convention over configuration.  Any object may be persisted as long as it has a long id field with a public getter and a public setter.  The library also provides support for:

* Owned entities (with cascading lifecycle operations)
* Storing Enum values
* Storing Serializable objects as blobs
* Collections with back references
* Transient/unpersisted fields.
* Unknowned references

## Subprojects

* AndrORMeda - Android ORM package.
* ORMada - General purpose ORM library.

## Example
As an example, consider an Android app that flips a coin.  We want to store each flip to compute statistics on the coin flip algorithm.  Here is a model object for a coin flip:

```
public class Flip {

    private long id;

    private long type;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getType() {
        return type;
    }

    public void setType(long type) {
        this.type = type;
    }    
}
```
Using this model, we can store coin flips in the database using the following code:

```
ORMDataSource orm = new ORMDataSource(new SQLiteDialect(context, ""coinflip.db", 10), Flip.class);
		
orm.open();
		
Flip newFlip = new Flip();
newFlip.setType(getResult().equals("heads") ? 0 : 1);
orm.save(newFlip);

```
In this case, context is your Activity or application context.

When we want to retrieve the past coin flips, we can use the following code:

```
List<Flip> flips = orm.getAll(Flip.class, null);
		
```

## How to use

Please note that this guide is incomplete, and is constantly being improved.  If you notice something that is missing, submit a pull request with the updates and I'll roll your changes in.

#### Defining your model

ORMada is implemented to rely entirely on public getter and setter methods to define the database schema, and will use one or the other to determine expected type depending on the situation.  It will not rely on fields defined in the class, or any other implementationd detail of the getter and setter methods.  This gives the object designer incredible flexibility in terms of how the data is defined and accessed.

The only hard requirement for model objects is that they contain an id.  The id must be of type long (or Long) and must have a getter and setter.  Beyond that, pretty much anything goes.

As an example, consider the Flip class from above.  If we decided it would be better for our application to hold the flip type in a String, but still wanted to persist it as a long, we can change the class in the following way:

```
public class Flip {

	...
	
    private String type = "heads";
	
	...

    public long getType() {
        return type.equals("heads") ? 0 : 1;
    }

    public void setType(long type) {
        this.type = type == 0 ? "heads" : "tails";
    }    
}
```

ORMada will still use a long column to hold this value, but the internal representation within the object is String.  This can be very useful if other operations work better with type as a String.

Note that ORMada will only consider getters with no parameters that start with the word "get" or "is", setters with one parameter that start with the word "set", or adders that start with "add" and take a single element of a collection (t)hese will be covered later in this guide).  Other methods will be ignored.

#### Annotations
ORMada relies almost entirely on the class structure (and some conventions) to build the database schema and relationship model.  There are some cases in which ORMada needs a bit more information to understand how to properly handle the data.  These cases are discussed here, and are usually specified in the model using a Java annotation.  Annotations supported by ORMada are:

* @Transient
* @OneToMany(class)
* @Reference

All annotations must be placed above the field getter to be interpreted correctly, and some annotations require parameters to provide additional information to ORMada.

The following sections describe the use of these annotations.

#### Transient fields
There may be cases where you want to define a method that could be treated as a getter (defined above), but you do not want store that as a database field.  These getters can be marked transient, which means they will be ignored by ORMada when saving or retrieving data.

For our Flip class, let's say we want to add a method to get the type as a string:

```
	...

	@Transient
	public String getTypeAsString() {
		return type;
	}	

```

Without the @Transient annotation, ORMada would create a separate column for this getter.  In this case, it is ignored.

#### Collections
Collections can be defined a couple different ways, depending on your needs.  Currently, only one-to-many collections are supported, and they are declared by adding the @OneToMany annotation above the collection getter.  This annotation also takes a parameter that specifies the class to be contained within the collection.  This lets ORMada create the appropriate objects when storing and retrieving data.

The easiest way to use collections is to define a getter and setter, like with other fields:

```
	private List<Flip> flips;
	
	...
	
	@OneToMany(Flip.class)
	public List<Flip> getFlips() {
		return flips;
	}
	
	public void setFlips(List<Flip> flips) {
		this.flips = flips;
	}
```

Alternatively, you can define an adder instead of a setter, to give you more control over the objects as they are retrieved from the database:

```
	private List<Flip> flips = new ArrayList<Flip>();
	
	...
	
	public void addFlip(Flip flip) {
		this.flips.add(flip);
	}

	@OneToMany(Flip.class)
	public List<Flip> getFlips() {
		return flips;
	}
	
```

Using an adder gives you access to each object.  You can then manipulate the data and add to local storage.  You could also filter irrelevant or errant objects, or compute or populate transient fields.

This pattern becomes very useful if you maintain back references from a child object to a parent object.  

```
	private List<Flip> flips = new ArrayList<Flip>();
	
	...
	
	public void addFlip(Flip flip) {
		flip.setParent(this)
		this.flips.add(flip);
	}

	@OneToMany(Flip.class)
	public List<Flip> getFlips() {
		return flips;
	}
	
```

Note that if you use this pattern, you should make the parent object transient to avoid circular references in the database.  Note also that if you use this method to filter or transform data, and then save your object, the results of that filtering or transformation will be saved with the object.  In other words, you can lose data, so be careful.

#### Unowned references
By default, entity to entity relationships imply ownership by one of the entities.  In the following example, consider a model object A that contains a reference to model object B:

```
public class A {

	...
	
    private B b;
	
	...

    public B getB() {
    	return b;
    }

    public void setType(B b) {
		this.b = b;
    }    
```

When an A is saved or deleted, the B member will also be saved or deleted.  In most cases, this is expected and desired.  In other cases, you may want to just hold a simple reference for convenience.  To do this, you can use the @Reference annotation:

```
public class C {

	...
	
    private B b;
	
	...

	@Reference
    public B getB() {
    	return b;
    }

    public void setType(B b) {
		this.b = b;
    }    
```

When C is saved, ORMada saves a lightweight reference to B without saving any changes to B.  This can be useful if your model stores favorites, bookmarks, or other reference lists.  It is inefficient to store entirely new objects, so you define an unknowned reference.

ORMada provides functionality for updating only references in an object model (and not the individual data elements).

## Extending ORMada
There are currently two main ways to extend ORMada: Extending core functionality, and adding support for new data stores.  This documentation will focus on adding support for new data stores.  Extending core functionality will be documented soon.

New data stores are supported by way of three interfaces:  Dialect, QueryCursor, and ValueSet.  While these are designed primarily around typical database operations, they can be used to implement support for any arbitrary data store.

#### Dialect
The Dialect interface defines the main API for a data store within ORMada.  This interface defines general purpose methods for querying, saving, and deleting objects.  Implementations must take in fields and/or selection criteria (where clauses), and execute the specific operation on the underlying data store.

#### QueryCursor
The QueryCursor interface defines an API for iterating over query results.  In many cases, implementations will be a simple wrapper around the Cursor or ResultSet object provided by JDBC or other underlying data store.

#### ValueSet
The ValueSet interface defines an API for defining key/value pairs.  ValueSets are used to save or update model objects in the data store; they are built by the ORMada library and passed into operations defined in the Dialect.

For most relational databases using JDBC, implementations will use a simple Map to hold values.  Some implementations may opt to create classes that extend database specific structures, to simplify their Dialect implementation.

## Current status

As of right now, the ORM is implemented direclty in AndrORMeda, and will only
work with Android's SQLite database.

Once this is complete, the next task will be to pull the reusable bits back into ORMada, and convert AndrORMeda into a library that includes ORMeda.  Further effort will be spent to build other libraries for other popular SQL databases.

#### Currently works
* Save/update/get/refresh/delete (individual entities)  
* Saving/retrieving dependent single entities  
* getAll/saveAll/deleteAll (collections of entities)  
* Managing OneToMany collections  
* Managing Transient declarations  
* Managing unowned references  
* Collections of: Sets, Lists  

#### Currently broken/not implemented
* Maps  
* ManyToMany collections
