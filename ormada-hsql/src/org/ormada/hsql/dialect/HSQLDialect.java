package org.ormada.hsql.dialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.ormada.ORMDataSource;
import org.ormada.dialect.AStandardSQLDialect;
import org.ormada.model.ORMeta;

/**
 * An ORMada dialect for HyperSQL DB.
 * 
 * NOTE: currently only supports file based, in process DBs
 * @author Jesse Rosalia
 *
 */
public class HSQLDialect extends AStandardSQLDialect {

    private static int CURRENT_DIALECT_VERSION = 1;

	private String dbPath;
    private int    dbVersion;

	public HSQLDialect(String dbPath, int dbVersion) {
		this.dbPath    = dbPath;
		this.dbVersion = dbVersion;
	}

	@Override
	public void open(ORMDataSource orm) throws SQLException {

	    //must be done before doing anything else
	    orm.setUseORMeta(true);
	    
		try {
			Class.forName("org.hsqldb.jdbc.JDBCDriver");
		} catch (Exception e) {
			throw new RuntimeException("ERROR: failed to load HSQLDB JDBC driver.", e);
		}

		Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:"
				+ this.dbPath, "SA", "");
		super.setConnection(connection);

		ORMeta meta = orm.getMetaData();
		if (meta == null) {
		    orm.createAllTables(this.dbVersion);
		} else if (meta.getDbVersion() != this.dbVersion) {
		    orm.upgradeAllTAbles(meta.getDbVersion(), this.dbVersion);
		}
	}
}
