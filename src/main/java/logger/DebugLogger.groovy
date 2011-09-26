package logger

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class DebugLogger {
	public static boolean logging= false
	
	public static void startLogging () {
		logging= true
	}
	
	
	public static void stopLogging() {
		logging= false
	}
	
	public static def log = { toPrint ->
		if(logging)
			println toPrint
		//else
		//	println "              $toPrint"
	}
	
	public static void logPosition(desc){
		def e= new Exception(desc)
		def byteStream= new ByteArrayOutputStream();
		def stream= new PrintStream(byteStream)
		e.printStackTrace(stream)
		String exception= byteStream.toString()
		
		exception.eachLine {
			if(!(it.contains("sun.reflect")||
				 it.contains("java.lang.reflect")||
				 it.contains("org.codehaus.groovy")||
				 it.contains("logger.DebugLogger")||
				 it.contains("groovy.lang")||
				 it.contains("groovy.ui")||
				 it.contains("Unknown Source")
				 ))
				println "${it}"
		}
		
		
	}
	
	
	public static void main(String[] args) {
		log "a"
		startLogging ()
		
		log "b"
		stopLogging ()
		
		log "c"
	}
}
