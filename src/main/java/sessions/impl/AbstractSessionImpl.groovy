package sessions.impl

import sessions.Session;;

import groovy.lang.GString;

import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.prefs.Base64;

abstract class AbstractSessionImpl extends Session {
	
	protected LinkedHashMap<String, String> getDefaultTypeIds(LinkedHashMap<String, Object> content) {
		def types= [:]
		content.each { key, value ->
			if(value instanceof Reference) {
				types[key]= "REF"
			}
			else if(value instanceof Integer){
				types[key]="INT"
			}
			else if(value instanceof Double){
				types[key]="DBL"
			}
			else if(value instanceof String || value instanceof GString){
				types[key]="STR"	
			}
			else if(value instanceof Serializable){
				types[key]="SERIALIZABLE"
			}
			else {
				throw new IllegalArgumentException("Unsupported type ${value.getClass().name}")
			}
		}
		return types
	}
	
	protected LinkedHashMap<String, String> getDefaultValueStringRepresentation(LinkedHashMap<String, Object> content) {
		def values= [:]
		content.each { key, value ->
			if(value instanceof Reference) {
				values[key]= "$value.toId"
			}
			else if(value instanceof Integer){
				values[key]= "$value"
			}
			else if(value instanceof Double){
				values[key]= "$value"
			}
			else if(value instanceof String || value instanceof GString){
				values[key]= "$value"
			}
			else if(value instanceof Serializable){
				def serialized= serialize(value)
				values[key]= serialized
			}
			else {
				throw new IllegalArgumentException("Unsupported type ${value.getClass().name}")
			}
		}
		return values
	}
	
	protected LinkedHashMap<String, Object> defaultTransformEntriesToMap(entries){
		def ret= [:]
		entries.each { entry ->
			if(entry.type.equals("REF")){
				if(entry.value.equals("null"))
					ret.put entry.name, Reference.NULL_REFERENCE
				else
					ret.put entry.name, new Reference(entry.value)
			}
			else{
				if(entry.type.equals("INT")){
					int val= Integer.valueOf(entry["value"])
					ret.put entry.name, val
				} else if(entry.type.equals("DBL")){
					double val= Double.valueOf(entry.value)
					ret.put entry.name, val
				} else if(entry.type.equals("STR")) {
					ret.put entry.name, entry["value"]
				} else if(entry.type.equals("SERIALIZABLE")) {
					String content= entry.value
					Object val= deSerialize(content)
					ret.put entry.name, val
				} else{
					def clazz= entry.type
					throw new IllegalArgumentException("Unknow class: ${clazz}")
				}
			}
		}
		ret
	}
	
	protected String serialize(value){
		ByteArrayOutputStream baos= new ByteArrayOutputStream();
		ObjectOutputStream oos= new ObjectOutputStream(baos);
		oos.writeObject value
		oos.close()
		byte[] bytes= baos.toByteArray()
		return Base64.byteArrayToBase64(bytes)
	}
	
	protected Object deSerialize(String content) {
		byte[] bytes= Base64.base64ToByteArray(content)
		ObjectInputStream ois= new ObjectInputStream(new ByteArrayInputStream(bytes))
		Object o= ois.readObject()
		ois.close()
		o
	}

}
