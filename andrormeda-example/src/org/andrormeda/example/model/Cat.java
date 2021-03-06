package org.andrormeda.example.model;

import java.util.ArrayList;
import java.util.List;

import org.ormada.annotations.OneToMany;
import org.ormada.annotations.Reference;
import org.ormada.annotations.Transient;

public class Cat {

	private long id;
	private String name;
	
	private Cat otherCat;
	
	private List<Kitten> kittens = new ArrayList<Kitten>();

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Cat getOtherCat() {
		return otherCat;
	}
	
	public void setOtherCat(Cat otherCat) {
		this.otherCat = otherCat;
	}
	
	@OneToMany(Kitten.class)
	public List<Kitten> getKittens() {
		return kittens;
	}

	public void setKittens(List<Kitten> kittens) {
		this.kittens = kittens;
	}
	
	@Transient
	public String getFamily() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.name);
		if (kittens != null) {
			for (Kitten kitten : kittens) {
				builder.append(", ").append(kitten.getName());
			}
		}
		if (otherCat != null) {
			builder.append("\n\t").append(otherCat.getFamily());
		}
		
		return builder.toString();
	}
}
