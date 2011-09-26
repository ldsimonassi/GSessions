import org.junit.Assert;


abstract class MultithreadedTest extends Assert {
	def assertNoLongerThan(delay, closure) {
		Thread t= new Thread(){
			public void run(){
				closure.call()
			}	
		} 
		
		def start= System.currentTimeMillis()
		
		t.start()
		
		t.join(delay)
		
		assertFalse ((System.currentTimeMillis()-delay) >= start)
		if(t.isAlive()){
			t.interrupt()
			t.join(100)
		}
	}
	
	def assertLongerThan(delay, closure) {
		Thread t= new Thread(){
			public void run(){
				closure.call()
			}
		}
		
		def start= System.currentTimeMillis()
		
		t.start()
		
		t.join(delay*5)
		
		assertFalse ((System.currentTimeMillis()-delay) <= start)
		
		if(t.isAlive()){
			t.interrupt()
			t.join(100)
		}
	}
	
	
	def parallel(closure){
		Thread t= new Thread(){
			public void run(){
				closure.call()
			}
		}
		t.start()
	}
	
	def assertWillTakeBetween(minDelay, maxDelay, closure) {
		Thread t= new Thread(){
			public void run(){
				closure.call()
			}
		}
		
		def tStart= System.currentTimeMillis()
		
		t.start()
		
		t.join(maxDelay)
		
		def tDelay= System.currentTimeMillis()-tStart
		
		assertTrue tDelay >= minDelay
		assertTrue tDelay <= maxDelay
		
		if(t.isAlive()){
			t.interrupt()
			t.join(100)
		}
	}
}
