package sessions;
import logger.DebugLogger;

import utilities.Semaphore;
import static logger.DebugLogger.*
/**
 * Persisted Object Functionality
 * @author ldsimonassi
 */
public class PersistentObject {
	def session     = null
	String id       = null
	boolean loaded  = false
	boolean intercepted = false
	boolean dirty   = false
	long version    = 0
	protected Semaphore methodsSemaphore= new Semaphore()

	//***********************
	// Only for collections
	//***********************
	
	@Override
	public int hashCode(){
		return System.identityHashCode (this)
	}
	
	/**
	 * The type of the element to be contained by this collection.
	 */
	protected Class elementsType

	protected void setDirty(boolean dirty){
		//session?.validateConsistencyOfDirtyFlag(this, dirty)
		//if(!this.dirty && dirty ){
		//	if(DebugLogger.logging)
		//		logPosition("DIRTY: $id ${this.dirty} -> ${dirty}")
		//} 
		this.dirty= dirty
	}
}
