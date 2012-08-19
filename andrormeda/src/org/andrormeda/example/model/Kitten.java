package org.andrormeda.example.model;

import org.andrormeda.annotations.Reference;

public class Kitten {

	private long id;
	private String name;

	private Cat parent;

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

	@Reference
	public Cat getParent() {
		return parent;
	}
	
	public void setParent(Cat parent) {
		this.parent = parent;
	}
}
