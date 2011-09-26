import sessions.impl.HttpStoreSession;

import java.awt.Color;

import sessions.Session;
import sessions.impl.FileSession;
import usecase.Customer;
import junit.framework.Assert;


class AbstractSessionTests extends Assert{
	static String FILE_TEST= 'FILE_TEST'
	static String HTTP_TEST= 'HTTP_TEST'
	
	static testTypes= [FILE_TEST, HTTP_TEST]
	
	def sessionType= FILE_TEST
	
	public void forAllSessionTypes(Closure closure) {
		testTypes.each { st ->
			this.sessionType= st
			closure.call()
		}
	}
	
	public Session openSession(){
		if(sessionType==FILE_TEST){
			def s= new FileSession("./spool")
			return s
		} else {
			def s= new HttpStoreSession("http://127.0.0.1:8081")
			return s
		}
	}
	
	String buildTestCustomer(){
		Customer c1= new Customer()
		c1.name="Dario"
		c1.age= 29
		c1.order= null
		c1.color= Color.RED
		
		def s= openSession()
		s.attach c1
		
		s.saveAll()
		
		def s2= openSession()
		
		def c2= s2.get(c1.getId(), Customer.class)
		
		assertNotNull c2
		assertEquals c2.name, c1.name
		assertEquals c2.age, c1.age
		assertEquals c2.color, c1.color
		assertEquals c2.id, c1.id
		
		c1.getId()
	}
}
