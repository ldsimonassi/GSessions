import sessions.Session;



import org.junit.Test;

import usecase.Customer;
import usecase.Item;
import usecase.Order;


class GeneralTest extends AbstractSessionTests {
	
	@Test
	public void sameInstance(){
		forAllSessionTypes {
			def s= openSession()
			def id= buildTestCustomer()
			
			def cust1= s.get(id, Customer.class)
			def cust2= s.get(id, Customer.class)
			
			assertSame(cust1, cust2)
		}
	}

	@Test
	public void isolation() {
		forAllSessionTypes {
			def id= buildTestCustomer()
	
			def s1= openSession()
			def s2= openSession()
	
			def cust1= s1.get(id, Customer.class)
			def cust2= s2.get(id, Customer.class)
	
			assertNotSame(cust1, cust2)
		}
	}

	@Test
	public void read() {
		forAllSessionTypes {
				
			def id= buildTestCustomer()
	
			def s= openSession()
	
			def cust= s.get(id, Customer.class)
	
			assertNotNull cust.getId()
			assertFalse cust.isLoaded()
	
			int age= cust.age
			
			assertEquals cust.age, 29
			assertEquals cust.name, "Dario"
			assertNull   cust.order
	
			assertTrue cust.isLoaded()
			assertNotNull cust.getId()
		}
	}

	@Test
	public void saveAll() {
		forAllSessionTypes {
				
			def s1= openSession()
			def c= new Customer()
			c.age= 29
			c.name= "Julio Iglesias"
			c.order = new Order()
			c.order.address= "Mexico"
			
			s1.attach c
	
			s1.saveAll()
	
			def idC= c.getId()
			def idO= c.order.getId()
	
			def s2= openSession()
	
			def customer= s2.get(idC, Customer.class)
			def order= s2.get(idO, Order.class)
	
			assertNotSame customer, c
			assertNotSame order, c.order 
			assertSame order, customer.order
			assertEquals "Julio Iglesias", customer.name
			assertEquals "Mexico", customer.order.address
		}
	}

	@Test
	public void saveAllCrossReference(){
		forAllSessionTypes {
			
			def cst= new Customer()
			def cst2= new Customer()
			def order= new Order()
			def order2= new Order()
			
			order.address= "Blanco Encalada 5058 3ro 18"
			order2.address= "Alsina 775 Quilmes"
	
			cst.age= 29
			cst.name= "Dario"
			cst.order= order
			cst.friend= cst2
			
			cst2.age= 59
			cst2.name= "Mirta"
			cst2.order= order2
			cst2.friend= cst
			
			def s= openSession()
			s.attach cst
			
			int i= s.saveAll()
			
			def darioId= cst.getId()
			
			def s2= openSession()
			
			def cstCheck= s2.get(darioId, Customer.class)
			
			assertEquals "Dario", cstCheck.name
			assertEquals 29, cstCheck.age
			assertEquals "Mirta", cstCheck.friend.name
			assertEquals "Dario", cstCheck.friend.friend.name
			assertSame   cstCheck, cstCheck.friend.friend
		}
	}
	
	@Test
	public void threeLevelsSaveAll(){
		forAllSessionTypes {
			def c1= new Customer("Dario", 29)
			def c2= new Customer("Mariano", 29)
			def c3= new Customer("Sebastian", 29)
			def c4= new Customer("Gaston", 29)
			
			c1.friend= c2
			c2.friend= c3
			c3.friend= c4
			c4.friend= c1
			
			def s= openSession()
			
			s.attach c1
	
			s.saveAll()
			
			def s2= openSession()
			def cTest= s2.get(c1.getId(), Customer.class)
			
			assertEquals "Dario",     cTest.name
			assertEquals "Mariano",   cTest.friend.name
			assertEquals "Sebastian", cTest.friend.friend.name
			assertEquals "Gaston",    cTest.friend.friend.friend.name
		}
	}
	
	@Test
	public void addDataToAnOpenSession(){
		forAllSessionTypes {
			def c1= new Customer()
			
			c1.name= "Dario"
			c1.age=29
			
			def s1= openSession()
			
			s1.attach c1
			
			int i= s1.saveAll()
			
			assertEquals 1, i
			
			def c2=new Customer("Guille", 31)
	
			c1.friend= c2
	
			i= s1.saveAll()
			
			assertEquals 2, i
			
			def s2= openSession()
			def c3= s2.get(c1.getId(), Customer.class)
			
			assertEquals "Dario", c3.name
			assertEquals 29, c3.age
			assertEquals "Guille", c3.friend.name
			assertEquals 31, c3.friend.age
		}
	}
	
	@Test
	public void internalModification(){
		forAllSessionTypes {
			Customer c= new Customer("Dario", 29)
			
			def s= openSession()
			
			s.attach c
			
			s.saveAll()
			
			assertTrue c.isLoaded()
			assertFalse c.isDirty()
			
			c.incrementAge()
			
			assertTrue c.isDirty()
			
			int saved= s.saveAll()
			
			assertEquals 1, saved
			assertFalse c.isDirty()
			
			c.upperCaseName()
			
			assertTrue c.isDirty()
			
			saved= s.saveAll()
			
			assertEquals 1, saved
			assertFalse c.isDirty()
	
			def s2= openSession()
			
			def c2= s.get(c.getId(), Customer.class)
			
			assertEquals "DARIO", c2.name
			assertEquals 30, c2.age
		}
	}
	
	@Test
	public void correctDirty(){
		forAllSessionTypes {
			def c1= new Customer("Dario", 29)
			def c2= new Customer("Mira", 59)
			
			c1.friend= c2
			c2.friend= c1
			
			c1.order= new Order()
			c1.order.address= "Blanco Encalada 5058 3ro 18"
			c1.order.items= ['MacBook Pro 13', 'Bose SoundLink']
			
			c2.order= new Order()
			c2.order.items= ['Epson Printer', 'Asus Computer']
			
			def s= openSession()
			s.attach c1
			
			int i= s.saveAll()
			
			assertEquals 6, i
	
			def ord= c2.order
			def list= ord.items
			list.add 'Desk'
			i= s.saveAll()
			
			assertEquals 1, i
		}
	}
	
	@Test
	public void bulkOperation(){
		this.setSessionType(HTTP_TEST)
		Session s1= openSession()
		s1.setBulkSupport(true);
		
		Customer c11= new Customer("Dario", 29)
		Customer c12= new Customer("Mirta", 59)
		
		c11.friend= c12
		c12.friend= c11
		
		s1.attach(c11)
		s1.bulkSaveAndRefresh()
		
		// Read with no bulk session
		Session s2= openSession()
		Customer c21= s2.get(c11.id, Customer.class)
		
		assertEquals c11.name, c21.name
		assertEquals c11.age, c21.age
		assertEquals c11.friend.name, c21.friend.name
		assertEquals c11.friend.age, c21.friend.age
		
		// Do some changes using the regular session
		c21.order= new Order()
		Item i= new Item()
		i.name="MacBook Pro"
		i.price= 1199.0
		c21.order.items.add i 
		
		// Do some changes using the bulk session
		c12.name= "Mirta Noemi"
		c12.age= 60
		
		// Now save the regular session
		s2.saveAll()
		
		// Now BulkUpdate the bulk session
		s1.bulkSaveAndRefresh()
		
		// Refresh the s1 changes in the s2 session
		s2.refreshAll()
		
		// Assert the correct values
		assertEquals "MacBook Pro", c11.order.items[0].name
		assertEquals "Mirta Noemi", c21.friend.name
		assertEquals 60, c21.friend.age
	}

	
}