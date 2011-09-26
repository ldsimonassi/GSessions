package sessions.impl;

import groovyx.net.http.ContentType;
import groovyx.net.http.RESTClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSON;



public class HttpStoreSession extends AbstractSessionImpl {
	private def restClient
	private String baseId
	private volatile int counter
	boolean bulkSupported
	
	LinkedHashMap<String, LinkedHashMap<String, String>> storePending= new LinkedHashMap<String, LinkedHashMap<String, String>>();
	
	
	public boolean isBulkSupported(){
		return bulkSupported;
	}
	
	public void setBulkSupport(boolean bulkSupport){
		this.bulkSupported= bulkSupport;
	}
	
	
	protected LinkedHashMap<String, LinkedHashMap<String, Object>> commitAndReadBeans(LinkedHashMap<String, String> references){
		if(!bulkSupported)
			throw new IllegalAccessException("Bulk is not supported in this session [$this]");

		// Build POST structure
		def toSend= [:]
		toSend['save']= storePending;
		if(references!=null && references.size()>0)
			toSend['refresh']= references;

		// Execute REST Call to /sync.json
		def resp= restClient.post(path : "sync.json",
			body: toSend,
			requestContentType: ContentType.JSON);

		// Some validations to the response
		assert resp.status==200

		// Assert all objects were saved
		if(storePending!=null && storePending.size()>0)
			assert resp.data.saved!=null
		else
			assert resp.data.saved.size()==0
			
		if(resp.data.saved!=null) {
			resp.data.saved.keySet().each { id ->
				assert 'Ok'.equals(resp.data.saved[id])
			}
		}
		
		LinkedHashMap<String, LinkedHashMap<String, Object>> ret= new LinkedHashMap<String, LinkedHashMap<String, Object>>()
		// Now go for the fresh objects
		if(resp.data.refreshed!=null){
			resp.data.refreshed.keySet().each{ id->
				ret[id]= defaultTransformEntriesToMap(resp.data.refreshed[id])
			}
		}
		storePending.clear();
		ret
	}
	
	
	
	public HttpStoreSession(String base, bulkSupported= false){
		this.base= base
		this.bulkSupported= bulkSupported
		restClient= new RESTClient(base)
		//restClient.setContentType ContentType.JSON
		def resp= restClient.get(path:'/newSessionId.json')
		assert resp.status == 200
		this.baseId= resp.data.id
		counter=0
	}
	
	@Override
	protected String generateId() {
		return "$baseId-${counter++}";
	}

	@Override
	protected LinkedHashMap<String, Object> readBean(String id) {
		try {
			def resp= restClient.get( path : "${id}.json")
			
			// Some validations to the response
			assert resp.status==200
			assert resp.data instanceof net.sf.json.JSON
	
			def ret= defaultTransformEntriesToMap(resp.data)
	
			return ret;
		} catch (Exception ex){
			throw new RuntimeException("Error reading bean $id $ex.message", ex)
		}
	}

	@Override
	protected void storeBean(String id, LinkedHashMap<String, Object> attributes) {
		def types= getDefaultTypeIds(attributes)
		def strValues= getDefaultValueStringRepresentation(attributes)
		
		def properties= []
		
		attributes.keySet().each { key ->
			def prop= [:]
			prop.put "name", "$key".toString()
			prop.put "value", "${strValues[key]}".toString()
			prop.put "type", "${types[key]}".toString()
			properties.add prop
		}
		
		if(!bulkSupported){
			def resp= restClient.post(path : "${id}.json",
									  body: properties, 
									  requestContentType: ContentType.JSON);
			
			// Some validations to the response
			assert resp.status==200
			assert "OK".equals(resp.data.stored)
		} else {
			storePending.put(id, properties);
		}
	}
	
	public String toString() {
		return "HTTP Server: $base"
	}
	
	public static void main(String[] args) {
		try{
			def s= new HttpStoreSession("http://localhost:8080/")
			def toSend= [name:"Dario", age:29]
			s.storeBean "1", toSend
			def ret= s.readBean("1")
		} catch(Exception ex){
			ex.printStackTrace()
		}
	}
}
