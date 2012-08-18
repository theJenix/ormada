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