/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.andrormeda.example;

import java.util.ArrayList;
import java.util.List;

import org.andrormeda.ORMDataSource;
import org.andrormeda.example.model.Cat;
import org.andrormeda.example.model.Kitten;

import android.content.Context;

/**
 *
 * @author jesse.rosalia
 */
public class AppDataSource {

    private static final String DATABASE_NAME = "felines.db";
    private static final int DATABASE_VERSION = 1;

    private Class<?> [] entities = {
        Cat.class,
        Kitten.class
    };
    private final ORMDataSource orm;

    public AppDataSource(Context context) {
        this.orm = new ORMDataSource(context, DATABASE_NAME, DATABASE_VERSION, entities);
    }

    public void open() {
        this.orm.open();
    }

    public void clear() {
    	this.orm.deleteAll(Cat.class,    null);
    	this.orm.deleteAll(Kitten.class, null);
    }

    public void close() {
        this.orm.close();
    }

    public List<Cat> getAllCats() {
        return (List<Cat>) this.orm.getAll(Cat.class, null);
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

}
