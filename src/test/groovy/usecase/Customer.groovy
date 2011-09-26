package usecase

import java.awt.Color;

class Customer {
	static persistent= ['age', 'name', 'order', 'friend', 'color']

	int age
	String name
	Order order
	Customer friend
	Color color
	
	public Customer(){
	}
	
	public Customer(String name, int age){
		this.age= age
		this.name= name
	}
	
	public void incrementAge(){
		age++
	}
	
	public void upperCaseName(){
		name= name.toUpperCase();
	}
	
	public String toString(){
		return "Customer: age:$age name:$name"
	}
}
