package sessions

import java.util.List;

import groovy.lang.Closure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import java.lang.reflect.Method;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;

import org.codehaus.groovy.runtime.MetaClassHelper 
import utilities.Semaphore;
import static logger.DebugLogger.*

/**
 * A session to persist and share a statefull object graph
 * @author Luis Dar’o Simonassi
 */
public abstract class Session {
	
	String base

	/**
	 * Abstract methods that need to be implemented in a store specific way
	 */
	protected abstract void storeBean(String id, LinkedHashMap<String, Object> attributes);
	protected abstract LinkedHashMap<String, Object> readBean(String id);
	protected abstract String generateId();
	protected abstract LinkedHashMap<String, LinkedHashMap<String, Object>> commitAndReadBeans(LinkedHashMap<String, String> references);
	public abstract boolean isBulkSupported();
	public abstract void setBulkSupport(boolean bulkSupport);

	public final static HashSet collectionWriteMethods= ["add", "addAll", "remove", "removeAll", "clear", "retainAll"]
	public final static HashSet persistentObjectMethods
	public final static HashSet persistentObjectProps
	
	static {
		persistentObjectMethods= new HashSet(PersistentObject.class.getMethods().collect{ it.name })
		persistentObjectMethods.addAll(Object.class.getMethods().collect{ it.name })
		persistentObjectMethods.add "hasProperty"
		persistentObjectMethods.remove "setProperty"
		persistentObjectMethods.remove "getProperty"
		persistentObjectMethods.add "getMetaPropertyValues"
		persistentObjectProps= new PersistentObject().getProperties().keySet()
		persistentObjectProps.add "persistent"
	}
	
	/**
	 * Same as get but for Collections
	 */
	public Object getCollection(String id, Class<? extends Collection> collectionType, Class elementType=null){
		try{
			Object o= getCurrentReference(id)
			
			if(o!=null && !collectionType.isInstance(o))
				throw IllegalArgumentException("Found reference with class ${o.getClass().name} not compatible with required ${collectionType.name}")
			
			if(o!=null)
				return o
			
			if(collectionType!=null){
				try{
					o= collectionType.newInstance()
				} catch (Exception ex){
					o= new ArrayList()
				}
			}
			else {
				o= new ArrayList()
			}
			
			this._attach(o, id, false, elementType)
		} catch(Exception ex) {
			throw new IllegalStateException("Error getting [$id] with class $type?.name", ex)
		}
	}
	
	
	/**
	 * Returns an object of type c (if not previously loaded under another class)
	 * The object is not loaded unless the same id was previously loaded for this session.
	 * The object returned is attached to the session, and is not new.
	 * The object returned has setters and getter behaviors. Those provide the loading
	 * functionality to be activated under the first method call to the new object.
	 */
	public Object get(String id, Class type){
		try{
			Object o= getCurrentReference(id)
			
			if(o!=null && !type.isInstance(o))
				throw IllegalArgumentException("Found reference with class ${o.getClass().name} not compatible with required ${type.name}")
			
			if(o!=null)
				return o
			
			if(type!=null)
				o= type.newInstance()
			else {
				o= new Object()
			}
			return this._attach(o, id, false, null)
		} catch(Exception ex) {
			throw new IllegalStateException("Error getting [$id] with class ${type}", ex)
		}
	}
	
	/**
	 * Attachs an object to the session, the object shouldnt be previously attached
	 * The returned object will have an id assigned and will be: loaded and dirty
	 * The object will be registered to the weak references list
	 * @param o
	 */
	public Object attach(Object o, Class elementType=null) {
		if(o==null)
			throw new IllegalArgumentException("Cannot attach a null object")
		
		if(isAttached(o))
			return o
		
		// Get an ID for this new object
		String newId= generateId()
		
		// If no elementType provided and trying to attach a collection
		// I'll try to figure this out inspecting one of the elements
		if(o instanceof Collection && elementType==null && o.size()>0 && o[0]!=null)
			elementType= o[0].getClass()
		
		// Attach it
		_attach(o, newId, true, elementType)
	}
	
	/**
	* Saves all the objects in this session.
	* For references which are not attached, they will be attached and saved too.
	* Multithreading:
	* While saving, all write operations will be locked waiting for this operation to end.
	* @return
	*/
   public int saveAll(){
	   def ret= 0
	   sessionWriteSemaphore.use {
		   ret= _saveAll()
		   if(isBulkSupported())
			   commitAndReadBeans([:]);
	   }
	   ret
   }
   
   protected int _saveAll() {
	   def ret
	   int attachedRefs= 1;
	   
	   while(attachedRefs>0){
		   attachedRefs= attachAllUnattachedReferencesInDirtyObjects()
	   }
	   
	   synchronized (dirtyObjects) {
		   ret= dirtyObjects.size()
		   
		   dirtyObjects.each(){ pointer ->
			   def dirty= pointer.referred
			   
			   if(dirty instanceof Collection){
				   checkAttached dirty
				   saveCollection dirty
			   }
			   else {
				   checkAttached dirty
				   saveBean dirty
			   }
		   }
		   clearDirty()
	   }
	   ret
   }
   
   
   public void bulkSaveAndRefresh(){
	   sessionWriteSemaphore.use {
		   // Get the current objects list
		   def toRefresh= new HashMap(references)
		   
		   // Attach all the unattached but referenced objects in the tree and save them
		   _saveAll();
		   
		   // Prepare structure for bulk refresh operation
		   // A map of (id:version)
		   def refs= [:]
		   int i= 0
		   toRefresh.each { id, o ->
			   refs[id]= 0; // TODO Add version management
		   }
		   
		   // Perform provider operation
		   def mapOfBeans= commitAndReadBeans(refs)
		   
		   // Copy information to the objecs
		   _refreshAll(toRefresh, mapOfBeans)
	   }
   }
   
   
   protected void _refreshAll(toRefresh=null, mapOfBeans=null) {
	   if(toRefresh==null)
	   	  toRefresh= new HashMap(references)

	   toRefresh.each { id, o ->
		   o.methodsSemaphore.freeze() {
			   //assert !o.isIntercepted()
			   o.setIntercepted(true)
			   try{
				   if(mapOfBeans!=null){
					  // For bulk operation
					  if(mapOfBeans[id]!=null)
					  	refresh(o, mapOfBeans[id])
				   }else{
					 def bean= readBean(id)
					 refresh(o, bean)
				   }
			   } finally {
				   o.setIntercepted(false)
			   }
		   }
	   }
   }
 
	
	/**
	* Will refresh all the non dirty objects weak referenced by this object
	* Multithreading:
	* This wont block anything rather than the object being refreshed at once
	*/
   public void refreshAll(){
	   _refreshAll(null);
   }

	
	protected Object _attach(Object o, String id, boolean isNew, Class elementType) {
		validatePojo o
		
		// Install Managed Object Behavior
		o.metaClass.mixin PersistentObject.class
		o.setElementsType(elementType)
		o.setSession(this)
		o.setId(id)
		
		if(isNew){
			addToDirty o
			o.setLoaded(true)
		}
		
		
		// Add extra behavior to the managed object
		if(o instanceof Collection)
			installCollectionBehavior o
		else
			installBeanMethodCallBehavior o
		
		// Register reference
		registerReference o
	}
	
	/**
	 * Refresh only an object
	 * @param o
	 */
	protected void refresh(o, map){
		try{
			checkAttached o

			if(o.dirty)
				return

			// TODO: Add version check
			Map<String, Class> types

			if(o instanceof Collection)
				o.clear()
			else
				types= getReferencesTypeMap(o)

			map.each { String field, value ->
				if(value instanceof Reference) {
					if(value==Reference.NULL_REFERENCE) {
						// Set to null
						if(o instanceof Collection){
							o.add null
						}
						else
							o.setProperty(field, null)
					} else {
						// Get a reference from this session
						Object reference= null
						Class fieldType= null

						// If i'm refreshing a Collection
						if(o instanceof Collection){
							fieldType= o.getElementsType()
							if(fieldType==null)
								throw new IllegalStateException("Managed collection ${o} has no element type specified")
						}
						else{
							// If I'm refreshing a bean
							fieldType= types.get(field)

							if(fieldType==null) 
								throw new IllegalStateException("No type found for property $field in class ${o.class.name}")
						}

						// If the field is a collection (Not necessary the object to be refreshed
						if(Collection.class.isAssignableFrom(fieldType)){
							def elementsType= getCollectionGenericType(o, field)
							reference= getCollection (value.toId, fieldType, elementsType)
						} else {
							reference= get(value.toId, fieldType)
						}

						// Set the value
						if(o instanceof Collection){
							o.add reference
						}
						else{
							o.setProperty(field, reference)
						}
					}
				}else {
					// Direct attribute
					if(o instanceof Collection){
						o.add value
					}
					else{
						o.setProperty(field, value)
					}
				}
			}
			//removeFromDirty o
			o.setLoaded(true)
		} catch(Exception ex){
			throw new RuntimeException("Error refreshing $o with ${o.getId()}", ex)
		}
	}

	protected Class getCollectionGenericType(Object o, String collectionPropertyName){
		try{
			def methodName= "get" + MetaClassHelper.capitalize(collectionPropertyName);
			Method getMethod= o.getClass().getMethod(methodName)
			Type retType= getMethod.getGenericReturnType();
			if(!retType instanceof ParameterizedType)
				throw new IllegalArgumentException("Collection $collectionPropertyName from $o has no generic type definition")
			return ((ParameterizedType)retType).getActualTypeArguments()[0]
		} catch (Exception ex) {
			throw new RuntimeException("Error trying to get type of property: $collectionPropertyName from object $o", ex);
		}
	}

	protected Map<String, Class> getReferencesTypeMap(o){
		def lst= o.getMetaPropertyValues()
		def ret= [:]
		lst.each{ PropertyValue pv ->
			if(o.persistent.contains(pv.name)){
				ret[pv.getName()]= pv.getType()
			}
		}
		ret
	}
	
	protected void load(o){
		if(o.isLoaded()) return
			def beanInfo= readBean(o.id)
			refresh o, beanInfo
	}
	
	
	protected void saveCollection(Object po) {
		def fields= [:]
		def id= po.getId()
		// Serialize the object primitive attributes
		int i= 0
		po.each { value ->
			if(isAttribute(value)){
				fields.put i, value
			}
			else {
				if(value!=null && !isAttached(value))
					throw new IllegalArgumentException("The reference $i of [$po] is [${po[i]}] should be attached!")
				fields.put i, Reference.buildReference(value)
			}
			i++
		}
		storeBean(id, fields)
	}

	protected LinkedHashMap<String, Object> getReferences(Object po) {
		def ret= [:]

		if(po instanceof Collection){
			
			po.eachWithIndex { value, index ->
				if(!isAttribute(value)) {
					ret.putAt index, value
				}
			}
		}
		else{
			def pers= po.persistent
			pers.each { String field ->
				Object value= po.getProperty(field)
				
				if(!isAttribute(value))
					ret.putAt field, value
			}
		}
		ret
	}
	
	protected void saveBean(Object po){
		def fields= [:]
		def id= po.getId()
		// Serialize the object primitive attributes
		po.persistent.each { String field ->
			Object value= po.getProperty(field)
			if(isAttribute(value)){
				fields.put field, value
			}
			else {
				if(value!=null && !isAttached(value))
					throw new IllegalArgumentException("The reference $field of [$po] with value [$value] should be attached!")
				
				fields.put field, Reference.buildReference(value)
			}
		}
		storeBean(id, fields)
	}

	protected boolean isAttribute(Object value){
		if(value==null)
			return false

		if(value instanceof Number ||
		   value instanceof Date   ||
		   value instanceof String ||
		   value instanceof GString ||
		   value instanceof Date)
			return true;

		if(value instanceof Collection)
			return false

		if(!value.hasProperty('persistent'))
			return true

		return false
	}

	
	protected int attachAllUnattachedReferencesInDirtyObjects(){
		int ret= 0
		new LinkedHashSet(dirtyObjects).each { pointer ->
			def o= pointer.referred
			def refs= getReferences(o)
			refs.each { field, ref->
				def elementType= null
				
				if(ref instanceof Collection){
					elementType= getCollectionGenericType(o, field)
				}
				
				if(ref!=null && !isAttached (ref)){
					attach(ref, elementType)
					ret ++
				}
			}
		}
		ret
	}
	
	protected boolean isCollectionReadingMethod(String name, args){
		return !persistentObjectMethods.contains(name) && !isCollectionModificationMethod(name, args)
	}
	
	protected void installCollectionBehavior(Object o){
		o.metaClass.invokeMethod= { String name, args ->
			def metaMethod = o.metaClass.getMetaMethod(name, args)
			def result
			
			if(!metaMethod) 
				throw new IllegalArgumentException("Method ${name} for ${args} not found")
			
			if(persistentObjectMethods.contains(name) || o.isIntercepted())
				return metaMethod.invoke(delegate,args)
			
			def reading= isCollectionReadingMethod(name, args)
			def writing= isCollectionModificationMethod(name, args)
			
			o.setIntercepted true
			try{
				if(!reading && !writing)
					return metaMethod.invoke(delegate,args)
				
				if(reading){
					checkAttached o
					if(!o.isLoaded())
						load o
					
					result = metaMethod.invoke(delegate,args)
					return result
				}
				
				if(writing){
					checkAttached o
					def mm= metaMethod
					def del= delegate
					sessionWriteSemaphore.use { // Flag the session as writing
						// Assertion
						if(o.isDirty() && !o.isLoaded())
							throw new IllegalStateException("ASSERT: The object $o is dirty and is not loaded nor loading This shouldn't happen")
													
						if(!o.isDirty() && !o.isLoaded())
							load o
						o.methodsSemaphore.use {
							// Perform the write operation
							result = mm.invoke(del, args)
						} 
						
						// Add the object to the dirty list if necessary
						if(!o.isDirty())
							addToDirty o
						return result
					}
				}
			}
			finally {
				o.setIntercepted false
			}
		}
	}
	
	protected boolean isCollectionModificationMethod(name, args) {
		return collectionWriteMethods.contains(name) 
	}
	
	protected void installBeanMethodCallBehavior(Object o){
		o.metaClass.setProperty = { String name, value ->
			def metaProperty = o.metaClass.getMetaProperty(name)
			if(!metaProperty)
				throw new IllegalArgumentException("property $name not found")

			def del = delegate

			if(persistentObjectProps.contains(name)||o.isIntercepted()){
				metaProperty.setProperty(del, value)
			}else{
				def ret2= beanIntercept(o) {
					metaProperty.setProperty(del, value)
				}
			}
		}
		
		o.metaClass.getProperty = { String name ->
			def metaProperty = o.metaClass.getMetaProperty(name)
			if(!metaProperty)
				throw new IllegalArgumentException("property $name not found on $o")
			
			def del = delegate
				
			if(persistentObjectProps.contains(name)||o.isIntercepted()){
				return metaProperty.getProperty(del)
			}else{
				def ret2= beanIntercept(o) {
					def ret= metaProperty.getProperty(del)
					return ret
				}
				return ret2
			}
			
		}
		
		o.metaClass.invokeMethod= { String name, args ->
			def metaMethod = o.metaClass.getMetaMethod(name, args)
			def del = delegate
			
			if(metaMethod==null)
				throw new IllegalArgumentException("Method ${name} for ${args} not found")
			
			try{
				if(persistentObjectMethods.contains(name) || o.isIntercepted())
					return metaMethod.invoke(del, args)
	
				return beanIntercept(o) {
					metaMethod.invoke(del, args)
				}
			} catch(RuntimeException re){
				throw new RuntimeException("Error while calling to $o.id with method $name($args)", re)
			}

		}
	}
	
	def beanIntercept(o,closure){
		try{
			return sessionWriteSemaphore.use() { // Flag the session as writing
				def ret= null
				o.setIntercepted true
				o.methodsSemaphore.use() {
					// Assertion
					if(o.isDirty() && !o.isLoaded())
						throw new IllegalStateException("ASSERT: The object $o is dirty and is not loaded nor loading This shouldn't happen")
					
					if(!o.isDirty() && !o.isLoaded())
						load o
					
					Map props= null
	
					props= getMeaningfulProperties(o)
						
					// Perform the write operation
					ret= closure.call()

					if(!o.isDirty() && hasChanged(o, props))
						addToDirty o
				}
				return ret
			}
		}finally{
			o.setIntercepted false
		}
	}
	
	protected boolean hasChanged(Object o, Map properties){
		Map properties2= getMeaningfulProperties(o)
		for (String name : properties.keySet()) {
			def val2= properties2.get(name)
			def val1= properties.get(name)
			
			if(val1!=null) {
				if(isAttribute(val1)) {
					if(!val1.equals(val2)){
						return true
					}
				}
				else{
					if(val1!=val2){
						return true
					}
				}
			}
			else {
				if(val2!=null){
					return true
				}
			}
		}
		return false
	}
	
	
	protected Map getMeaningfulProperties(Object o) {
		def ret= [:]
		o.persistent.each { name ->
			def value= o.getProperty(name)
			ret.put name, value
		}
		return ret
	}
	
	protected Semaphore sessionWriteSemaphore= new Semaphore()
	
	/**
	 * Dirty objects management
	 */
	protected LinkedHashSet<Object> dirtyObjects= new LinkedHashSet()
	
	public int getDirtyObjectsCount(){
		dirtyObjects.size()
	}
	
	
	protected clearDirty(){
		synchronized (dirtyObjects) {
			dirtyObjects.each { Pointer pointer ->
				pointer.referred.setDirty false
			}
			dirtyObjects.clear()
		}
	}
	
	protected void addToDirty(Object o){
		synchronized (dirtyObjects) {
			boolean ret= dirtyObjects.add(new Pointer(o))
			if(ret)
				o.setDirty(true)	
			
		}
	}
	
	protected void removeFromDirty(Object o){
		synchronized (dirtyObjects) {
			boolean ret= dirtyObjects.remove(new Pointer(o))
			if(ret)
				o.setDirty(false)
		}
	}

	
	/**
	 * Validation & Consistency
	 * Utilities
	 */
	protected void validatePojo(Object pojo){
		pojo.properties.keySet().each {
			if(it.equals("id") ||
			it.equals("version") ||
			it.equals("session") ||
			it.equals("dirty") ||
			it.equals("loaded")){
				def b= isAttached(pojo)
				throw new IllegalArgumentException("Pojo $pojo shouldnt contain property $it in order to work with sessions")
			}
		}
	}
	
	
	protected static boolean isReservedProperty(String property){
		['session', 'id', 'dirty', 'loaded', 'version', 'persistent', 'loading'].contains(property)
	}
	
	protected void checkAttached(Object o){
		if(!isAttached(o))
			throw new IllegalStateException("Object [$o] with id=${o.getId()} is not attached to session [$this]");
	}
	
	public boolean isAttached(Object o){
		if(o==null)
			return false // Null is not attached
		
		def s= null
		try{
			s= o.getSession()
		} catch(Exception ex){ }
		
		if(s==null)
			return false
		
		if(s==this) 
			return true
		else
			return false
	}
	
	/**
	 * References management methods
	 */
	public static class Reference {
		public static final Reference NULL_REFERENCE= new Reference("null")
		String toId
		
		private Reference(String id){
			toId= id
		}
		
		public String toString(){
			return toId
		}
		
		public static Reference buildReference(Object object){
			try{
				if(object==null){
					return NULL_REFERENCE
				}
				return new Reference(object.getId())
			} catch (Exception ex) {
				throw new RuntimeException("Error building reference to $object", ex)
			}
		}
	}
	
	
	protected Object getCurrentReference(String id){
		synchronized (references) {
			return references.get(id)
		}
	}
	
	WeakHashMap<String, Object> references= new WeakHashMap<String, Object>();
	
	protected Object registerReference(Object o){
		def id= o.getId()
		synchronized (references){
			if(references[id])
				return references[id]
			else {
				references[id]= o
				return o
			}
		}
	}

	public String getIdFrom(Object o) {
		return o.getId();
	}
	
	/**
	* A class which represents a pointer to an object.
	* Equals and hashcode compares object in a by instance basis
	*/
   public static class Pointer {
	   Object referred
	   
	   public Pointer(Object o){
		   this.referred= o
	   }
	   
	   @Override
	   public boolean equals(obj){
		   return referred==obj.referred;
	   }
	   
	   @Override
	   public int hashCode() {
		   return System.identityHashCode(referred);
	   }
	   
	   @Override
	   public String toString(){
		   return "Pointer to: ${referred} hashCode: ${hashCode()}"
	   }
   }
   
   protected Timer t= null;
   
   public void startAutoSync(long tRefresh, Closure callback){
	   if(t!=null)
	      throw new IllegalStateException('An automatic refresh to this session has been alredy issued, stop it first.')

	   t= new Timer("Refresh for session $this");
	   t.scheduleAtFixedRate(new TimerTask() {
		   @Override
		   public void run() {
			   if(isBulkSupported()){
				   bulkSaveAndRefresh();
			   }else{
				   this.saveAll();
				   this.refreshAll();
			   }
			   try{
				   callback.call();
			   } catch (Exception ex) {
			   	   ex.printStackTrace();
			   }
		   }
	   }, tRefresh, tRefresh);
   }
   
   public void stopAutoSync(){
	   if(t!=null){
		   t.cancel();
		   t=null;
	   }
   }

}