import java.util.ArrayList;
import java.util.LinkedList;

import org.junit.Test;

import usecase.Customer;
import usecase.Item;
import usecase.Order;


class CollectionsTest extends AbstractSessionTests  {
	
	@Test
	public void generalTest(){
		forAllSessionTypes { 
			List lst= new ArrayList<Customer>();
			def c= new Customer()
			c.name= "Dario"
			c.age= 29
	
			lst.add(c)
			
			def c2= new Customer()
			c2.name= "Mirta"
			c2.age= 59
	
			c2.friend= c
			c.friend= c2
			
			lst.add(c2)
	
			def s= openSession()
	
			s.attach lst
			
			def lstId= lst.getId()
			s.saveAll()
			
			s= openSession()
			
			def list2= s.getCollection(lstId, LinkedList.class, Customer.class)
			
			assertFalse list2.isLoaded()
			assertFalse list2[0].isLoaded()
			assertFalse list2[1].isLoaded()
			assertEquals "Dario", list2[0].name
			assertEquals "Mirta", list2[1].name
			assertTrue list2.isLoaded()
			assertTrue list2[0].isLoaded()
			assertTrue list2[1].isLoaded()
			assertSame list2[0].friend, list2[1]
			assertNotSame list2[0], list2[1]
			assertEquals 2, list2.size()
		}
	}
	
	@Test
	public void refreshAll() {
		forAllSessionTypes {
			def lst= new ArrayList<Customer>();
			def c1= new Customer()
			c1.name= "Dario"
			c1.age= 29
	
			lst.add(c1)
			
			def c2= new Customer()
			c2.name= "Mirta"
			c2.age= 59
	
			c2.friend= c1
			c1.friend= c2
			
			lst.add(c2)
	
			def s= openSession()
	
			s.attach lst
			
			def lstId= lst.getId()
			
			s.saveAll()
			
			
			def s2= openSession()
			
			def list2= s2.getCollection(lstId, ArrayList.class, Customer.class)
			
			assertFalse list2.isLoaded()
			assertFalse list2[0].isLoaded()
			assertFalse list2[1].isLoaded()
			
			def bk0= list2[0]
			def bk1= list2[1]
			assertEquals "Dario", list2[0].name
			assertEquals "Mirta", list2[1].name
			assertTrue list2.isLoaded()
			assertTrue list2[0].isLoaded()
			assertTrue list2[1].isLoaded()
			assertSame list2[0].friend, list2[1]
			assertNotSame list2[0], list2[1]
			assertEquals 2, list2.size()
	
			//No changes to refresh		
			s2.refreshAll()
			assertEquals 0, s2.getDirtyObjectsCount()
			
			assertSame bk0, list2[0]
			assertSame bk1, list2[1]
			
			assertEquals "Dario", list2[0].name
			assertEquals "Mirta", list2[1].name
			
			assertTrue list2.isLoaded()
			assertTrue list2[0].isLoaded()
			assertTrue list2[1].isLoaded()
			assertSame list2[0].friend, list2[1]
			
			assertNotSame list2[0], list2[1]
			assertEquals 2, list2.size()
	
			//Now change in the original session
			
			
			def tmp= new Customer("Rolando Rivas", 55)
	
			lst[0].name= "Roberto"
			lst[1].name= "Carlos"
			lst.add(tmp)
			
			s.saveAll()
			
			//Now assert no changes performed in s2
			
			assertSame bk0, list2[0]
			assertSame bk1, list2[1]
			assertEquals 2, list2.size()
			assertEquals "Dario", list2[0].name
			assertEquals "Mirta", list2[1].name
			assertSame list2[0].friend, list2[1]
			assertNotSame list2[0], list2[1]
	
			
			//Now refresh all and verify freshness of structures
			s2.refreshAll()
	
			assertEquals 0, s2.getDirtyObjectsCount()
			
			assertSame bk0, list2[0]
			assertSame bk1, list2[1]
			assertEquals "Roberto", list2[0].name
			assertEquals "Carlos", list2[1].name
			assertEquals "Rolando Rivas", list2[2].name
			assertSame list2[0].friend, list2[1]
			assertNotSame list2[0], list2[1]
			assertEquals 3, list2.size()
	
			//Now refresh all and verify freshness of structures
			s2.refreshAll()
			
			assertEquals s2.getDirtyObjectsCount(), 0
			
			assertSame bk0, list2[0]
			assertSame bk1, list2[1]
			assertEquals "Roberto", list2[0].name
			assertEquals "Carlos", list2[1].name
			assertEquals "Rolando Rivas", list2[2].name
			assertSame list2[0].friend, list2[1]
			assertNotSame list2[0], list2[1]
			assertEquals 3, list2.size()
		}
	}
	
	@Test
	public void addDataToAnOpenSession(){
		forAllSessionTypes {
			def c1= new Customer("Dario", 29)
			def c2= new Customer("Guille", 31)
			def c3= new Customer("Mirta", 59)
			
			List lst= new ArrayList()
			
			lst.add c1
			lst.add c2
			
			def s1= openSession()
			
			s1.attach lst
			
			s1.saveAll()
	
			lst.add c3		
	
			s1.saveAll()
			
			def s2= openSession()
			
			def lst2= s2.getCollection(lst.getId(), ArrayList.class, Customer.class)
			
			assertEquals 3, lst2.size()
			
			assertEquals "Dario", lst2[0].name
			assertEquals "Guille", lst2[1].name
			assertEquals "Mirta", lst2[2].name
			
			assertEquals 29, lst2[0].age
			assertEquals 31, lst2[1].age
			assertEquals 59, lst2[2].age
		}
	}
	
	@Test
	public void embeddedCollectionTest(){
		forAllSessionTypes {
			
			def cust= new Customer("Dario", 29)
			cust.order= new Order()
			cust.order.address= "Blanco Encalada 5058 3ro 18"
			cust.order.items.add new Item()
			cust.order.items.add new Item()
			
			cust.order.items[0].name= "Reproductor de DVD"
			cust.order.items[0].price= 40.5
			
			cust.order.items[1].name= "Videocamara Full HD"
			cust.order.items[1].price= 250.5
			
			def s= openSession()
			s.attach cust
			s.saveAll()
			
			
			def s2= openSession()
			
			def c= s2.get(cust.getId(), Customer.class)
			
			assertEquals "Dario", c.name
			assertEquals "Blanco Encalada 5058 3ro 18", c.order.address
			assertEquals "Reproductor de DVD", c.order.items[0].name
			assertEquals "Videocamara Full HD", c.order.items[1].name
			assertEquals(40.5, c.order.items[0].price, 0.1)
			assertEquals(250.5, c.order.items[1].price, 0.1)
		}
	}

	@Test 
	public void directCollection(){
		forAllSessionTypes {	
			def list= new ArrayList()
			
			list.add(new Customer("Dario", 29))
			list.add(new Customer("Roberto", 30))
			list.add(new Customer("Rodrigo", 45))
	
			list[0].friend= list[1]
			list[1].friend= list[2]
			list[2].friend= list[0]
	
			def s= openSession()
	
			s.attach list
	
			s.saveAll()
	
			def s2= openSession()
	
			def myList= s2.getCollection(list.getId(), LinkedList.class, Customer.class)
	
			assertEquals(LinkedList.class, myList.getClass())
	
			assertEquals("Dario", myList[0].name)
			assertEquals("Roberto", myList[1].name)
			assertEquals("Rodrigo", myList[2].name)
	
			assertSame(myList[0].friend, myList[1])
			assertSame(myList[1].friend, myList[2])
			assertSame(myList[2].friend, myList[0])
		}
	} 
	
	@Test
	public void directPrimitiveCollection(){
		forAllSessionTypes {	
			List lst= new ArrayList()
			6.times {
				lst.add "Number:${it}"
			}
			
			def s= openSession()
			
			s.attach lst
			
			s.saveAll()
			
			def s2= openSession()
			
			def lst2= s2.getCollection(lst.getId(), ArrayList.class)
			
			6.times {
				assertEquals "Number:${it}",lst2[it] 
			}
		}
	}
	
	

	@Test
	public void collectionOfCollectionTest() {
		forAllSessionTypes {
		}
		// Functionality not implemented yet. Dont know how to manage the type of internal objects
	}
}
