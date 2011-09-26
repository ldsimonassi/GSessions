package sessions.impl;

import java.util.LinkedHashMap;
import java.util.List;

import sessions.Session;

public class DebugSession extends Session{

	public boolean isBulkSupported(){
		return false
	}
	
	public void setBulkSupport(boolean bulkSupport){}
	
	
	protected LinkedHashMap<String, LinkedHashMap<String, Object>> commitAndReadBeans(LinkedHashMap<String, String> references){
		if(!bulkSupported)
			throw new IllegalAccessException("Bulk is not supported in this session [$this]");
	}

	@Override
	protected LinkedHashMap<String, Object> readBean(String id) {
		if(id.equals("0"))
			return [age:20, name:"Luis Dario Simonassi", order:new Reference("1")];
		else if(id.equals("1"))
			return [address: "Blanco encalada 5058 3ro 18"];
		else 
			throw new IllegalArgumentException("No data for [${id}]")
	}

	protected volatile int i= 0;
	
	protected String generateId(){
		(i++).toString();
	}
	
	@Override
	protected void storeBean(String id, LinkedHashMap<String, Object> content) {
		println "***********************"
		println "Storing $id"
		if(id==null)
			throw new IllegalArgumentException("id cannot be null in order for $content to be stored")

		content.each { key, value ->
			println "$key:[$value]" 
		}
		println "***********************"
	}
}
