/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ormada.hsql.example;

import java.util.List;

import org.ormada.ORMDataSource;
import org.ormada.hsql.dialect.HSQLDialect;
import org.ormada.hsql.example.model.Cat;
import org.ormada.hsql.example.model.Kitten;

/**
 *
 * @author jesse.rosalia
 */
public class AppDataSource {

    private static final String DATABASE_NAME   = "felines.db";
    private static final int   DATABASE_VERSION = 1;

    private Class<?> [] entities = {
        Cat.class,
        Kitten.class
    };
    private final ORMDataSource orm;

    public AppDataSource() {
        this.orm = new ORMDataSource(new HSQLDialect(DATABASE_NAME, DATABASE_VERSION), entities);
    }

    public void open() {
    	try {
    		this.orm.open();
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

    public void clear() {
    	this.orm.deleteAll(Cat.class,    null);
    	this.orm.deleteAll(Kitten.class, null);
    }

    public void close() {
    	try {
	        this.orm.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    public List<Cat> getAllCats() {
        return (List<Cat>) this.orm.getAll(Cat.class, null);
    }

    public Cat getCat(long id) {
    	return this.orm.get(Cat.class, id);
	}

    public void saveCat(Cat newCat) {
        this.orm.save(newCat);
    }

    public void updateCat(Cat cat) {
        this.orm.update(cat);
    }
    
    public void deleteCat(Cat cat) {
    	this.orm.delete(cat);
    }

	public void refreshCat(Cat bella) {
		this.orm.refresh(bella);
	}
}
