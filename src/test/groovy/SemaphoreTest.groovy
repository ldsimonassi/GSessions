import junit.framework.Assert;

import org.junit.Test;

import utilities.Semaphore;


class SemaphoreTest extends MultithreadedTest{

	@Test
	public void shouldNotLockWithNoBlock(){
		Semaphore s= new Semaphore()
		
		assertNoLongerThan (100) {
			s.use {
				
			}
			
			s.use {
				
			}
		}
	}
	
	@Test
	public void shouldNotLockAfterAllow(){
		Semaphore s= new Semaphore()
		
		s.freeze { 
		}
		
		assertNoLongerThan (100) {
			s.use {
				 
			}

			s.use {
				
			}
		}
	}
	
	
	@Test
	public void shoudLockAfterBlock(){
		Semaphore s= new Semaphore()

		parallel(){
			s.freeze(){
				sleep 60
			}
		}
				
		assertWillTakeBetween(30, 120) {
			s.use {
				println "Now using!" 
			}
		}
	}
}
