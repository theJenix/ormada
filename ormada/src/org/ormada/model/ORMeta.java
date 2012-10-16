package org.ormada.model;

/**
 * A built in model class used to hold metadata about the ORM database structure.
 * This allows us to do database/ORM versioning, and upgrade/replace tables
 * accordingly.
 * 
 * NOTE: Some databases/APIs have this built in...this is for those that do not.
 * 
 * @author Jesse Rosalia
 *
 */
public class ORMeta {

    private long id;
    
    private int  ormVersion;

    private int  dbVersion;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getOrmVersion() {
        return ormVersion;
    }

    public void setOrmVersion(int ormVersion) {
        this.ormVersion = ormVersion;
    }

    public int getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(int dbVersion) {
        this.dbVersion = dbVersion;
    }
}
