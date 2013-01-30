package org.andrormeda.test.example;

import java.util.List;

import org.andrormeda.test.example.model.Cat;
import org.andrormeda.test.example.model.Kitten;

import android.test.AndroidTestCase;

public class AppDataSourceTestCase extends AndroidTestCase {

	public void testSave() {
        AppDataSource ds = new AppDataSource(this.getContext());
        ds.open();
        
        Cat cat = new Cat();
        cat.setName("Bella");
        ds.saveCat(cat);
        
        cat = new Cat(); 
        cat.setName("Midnight");
        Kitten kitten = new Kitten();
        kitten.setName("Lucy");
        cat.getKittens().add(kitten);
        kitten = new Kitten();
        kitten.setName("Molly");
        cat.getKittens().add(kitten);
        ds.saveCat(cat);
        
        List<Cat> cats = ds.getAllCats();
        
        for (Cat c : cats) {
        	System.out.println(c.getName());
        	if (c.getKittens() != null) {
        		for (Kitten k : c.getKittens()) {
        			System.out.println("\t" + k.getName());
        		}
        	}        	
        }
	}
}
