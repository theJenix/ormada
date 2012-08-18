ormada
======

ORMada: A powerful, dirt simple ORM library.

This project started as a simple ORM package for Android, and has grown to
become a general purpose ORM supporting many popular SQL databases.  The goal of
this project is simplicity: provide as thin a veneer as possible to allow saving
and retrieving objects from a SQL database.  As a result, you will not find the
following popular ORM features:

* Session and Transaction management
* Cache
* Infinite tuning options

This library is written to emphasize convention over configuration.  Any object
may be persisted as long as it has a long id field with a public getter and a
public setter.  The library also provides support for:

* Entity references
* Storing Serializable objects as blobs
* Collections with back references
* Transient/unpersisted fields.

Subprojects:

* AndrORMeda - Android ORM package.
* ORMada - General purpose ORM library.

Implementation notes:

The ORM is implemented to rely entirely on public getter and setter methods, and
will use one or the other to determine expected type depending on the situation.
This gives the object designer incredible flexibility in terms of how the data
is defined and accessed.

Current status:

As of right now, the ORM is implemented direclty in AndrORMeda, and will only
work with Android's SQLite database.

Once this is complete, the next task will be to pull the reusable bits back into
ORMada, and convert AndrORMeda into a library that includes ORMeda.  Further
effort will be spent to build other libraries for other popular SQL databases.

Currently works:
Save/update/get/refresh/delete individual entities
getAll/saveAll/deleteAll collections of entities
Managing OneToMany collections
Managing Transient declarations
Collections of: Sets, Lists

Currently broken/not implemented:
Saving/retrieving dependent single entities
Maps
ManyToMany collections
