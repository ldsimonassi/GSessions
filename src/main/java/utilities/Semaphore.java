package utilities;

import groovy.lang.Closure;

/**
 * Many users who requires no one to be freezing but allow many users at the same time
 * and one freezer which needs no one to be using neither freezing the semaphore.
 * @author Luis Dario Simonassi
 */
public class Semaphore {
	/**
	 * Writes management
	 */
	int users= 0;
	Thread freezed= null;
	
	private synchronized void freeze(){
		freezed= Thread.currentThread();
		
		while(users > 0){
			try{ wait(); } catch(InterruptedException ie) { }
		}
	}
	
	private synchronized void allow() {
		freezed= null;
		notifyAll();
	}
	
	private synchronized void startUsing(){
		// If someone is freezing and is not me
		while(freezed!=null && freezed!=Thread.currentThread()){
			try{ wait(); } catch(InterruptedException ie) { }
		}
		users++;
	}
	
	private synchronized void stopUsing(){
		users--;
		notifyAll();
	}

	public Object use(Closure closure){
		try{
			startUsing();
			return  closure.call();
		}finally{
			stopUsing();
		}
	}
	
	public Object freeze(Closure closure){
		try{
			freeze();
			return closure.call();
		} finally{
			allow();
		}
	}
}
