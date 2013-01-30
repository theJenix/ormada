package org.andrormeda.example;

import java.util.List;

import org.andrormeda.R;
import org.andrormeda.R.layout;
import org.andrormeda.example.model.Cat;
import org.andrormeda.example.model.Kitten;

import android.app.Activity;
import android.os.Bundle;

/**
 * This activity serves as an example of how to use the ORM to manage Cat and Kitten objects
 * 
 * TODO: things that dont work:
 * 	OneToMany and single object references of the same class will cause children to be returned in getAll calls
 * 
 * @author thejenix
 *
 */
public class ExampleActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        AppDataSource ds = new AppDataSource(this);
        ds.open();
        ds.clear();
        Cat cat = new Cat();
        cat.setName("Bella");
        
        Cat otherCat = new Cat();
        otherCat.setName("Monty");
        cat.setOtherCat(otherCat);
        ds.saveCat(cat);
        //save this cat...cuz we're gonna delete it
        long id = cat.getId();
        
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
        	System.out.println(c.getFamily());
        }

        Cat bella = ds.getCat(id);
    	System.out.println(bella.getFamily());
    	bella.setOtherCat(null);
    	ds.saveCat(bella);
    	ds.refreshCat(bella);
    	System.out.println(bella.getFamily());
    	ds.deleteCat(bella);
        
        cats = ds.getAllCats();
        
        for (Cat c : cats) {
        	System.out.println(c.getFamily());
        }

        Cat midnight = ds.getCat(cats.get(0).getId());
    	System.out.println(midnight.getFamily());
    	midnight.getKittens().remove(0);
    	ds.saveCat(midnight);
    	ds.refreshCat(midnight);
    	System.out.println(midnight.getFamily());
    	ds.deleteCat(midnight);

    	cats = ds.getAllCats();
  
    	if (cats.isEmpty()) {
    		System.out.println("No more cats");
    	} else {
	        for (Cat c : cats) {
	        	System.out.println(c.getFamily());
	        }
    	}	
    }
}