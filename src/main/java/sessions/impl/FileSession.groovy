package sessions.impl

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;


import static logger.DebugLogger.*

class FileSession extends AbstractSessionImpl{
	
	public boolean isBulkSupported(){
		return false
	}
	
	public void setBulkSupport(boolean bulkSupport){}
	
	protected LinkedHashMap<String, LinkedHashMap<String, Object>> commitAndReadBeans(LinkedHashMap<String, String> references){
		if(!bulkSupported)
			throw new IllegalAccessException("Bulk is not supported in this session [$this]");
	}
	
	@Override
	public String toString() {
		return "FILE Dir: $base"
	}

	
	protected boolean isBulkReadSupported(){
		return false
	}
	
	protected LinkedHashMap<String, LinkedHashMap<String, Object>> readBeans(Map references){
		return null
	}

	
	public FileSession(String base) {
		this.base= base
	}
	
	static Object globalLock= new Object()
	
	protected String generateId(){
		synchronized (globalLock) {
			def fileName= base+"/id.last"
			File file= new File(fileName)
			def id= 0
			if(file.exists())
				file.eachLine { id= Integer.valueOf(it) }
			id++
			file.write "$id"
			""+id
		}
	}

	def static elements = ["type", "name", "value"]
	
	LinkedHashMap<String, String> parseLine(String line){
		try{
			def ret= [:]
			line.tokenize("|").eachWithIndex { element, index ->
				def key= elements[index]
				ret."${key}"= element
			}
			ret
		} catch (Exception ex){
			throw new IllegalArgumentException("Error parsing [$line]", ex)
		}
	}
	
	protected File getFileFor(String id){
		File file= new File(base+"/files/${id}.obj")
		if(!file.parentFile.exists())
			file.parentFile.mkdirs()
		file
	}
	
	@Override
	protected LinkedHashMap<String, Object> readBean(String id) {
		log "Reading $id"
		def file= getFileFor(id)
		try{
			def entries= []
			file.eachLine { line -> entries.add parseLine(line) }
			
			def ret= defaultTransformEntriesToMap(entries)
		} catch (Exception ex){
			throw new IllegalArgumentException("Error reading [$id] file: [$file]", ex)
		}
	}
	
	
	@Override
	protected void storeBean(String id, LinkedHashMap<String, Object> content) {
		def file= getFileFor(id)

		def pw= file.newPrintWriter()
		def types= getDefaultTypeIds (content)
		def values= getDefaultValueStringRepresentation(content)
		
		try{
			content.keySet().each { key -> 
				def type= types[key]
				def strValue= values[key]
				pw.println("$type|$key|$strValue")
			}
		}
		catch(Exception ex){
			throw new IllegalStateException("Error storing $id in $file", ex)
		} finally {
			pw.close()
		}	
	}
}
