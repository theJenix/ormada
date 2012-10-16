package org.ormada.hsql.example;

import java.util.List;

import org.ormada.hsql.example.model.Cat;
import org.ormada.hsql.example.model.Kitten;

public class ExampleMain {

    public static void main(String[] args) {
        AppDataSource ds = new AppDataSource();
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
        ds.updateCat(bella);
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
        ds.updateCat(midnight);
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
