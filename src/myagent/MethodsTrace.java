package myagent;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import com.sun.jdi.Method;
import com.sun.tools.classfile.StackMap_attribute.stack_map_frame;

import java.io.PrintWriter;

public class MethodsTrace {

	public static void main(String[] args) {
        new MethodsTrace(args);
    }
	
	public int debugTraceMode = 0;
	public boolean watchFields = false;
	public String[] excludes = new String[]{"java.*", "javax.*", "sun.*", "com.sun.*"};
    
    public VirtualMachine vm;
    
    PrintWriter writer;
    
//    java.sql.Connection DBconn;
    
    HashMap<Integer, String[]> calls = new HashMap<Integer, String[]>();
    
    Stack<Integer> call_stack = new Stack<Integer>();
    
    groovy.ui.Console groovy;
    

    MethodsTrace(String[] args) {
    	
    	try{
    		writer = new PrintWriter("D:\\somelog.csv");
    	}catch (Exception e){
    		writer = new PrintWriter(System.out);
    	}
    	
//    	try{
//    		Class.forName("org.sqlite.JDBC");
//    		DBconn = java.sql.DriverManager.getConnection("jdbc:sqlite:d:/log.sqlite", "root", "123");
//    		
//    		String [] init_cmd = new String[] { 
//    				"CREATE TABLE IF NOT EXISTS log (id BIGINT PRIMARY KEY, name VARCHAR(255), loc VARCHAR(255), start_time INT, end_time INT)",
//    				"CREATE INDEX IF NOT EXISTS log_name ON log(name)"
//    				};
//    		
//    		for (String sql : init_cmd){
//    			DBconn.createStatement().execute(sql);
//    		}
//    		
//    	}catch(Exception e){ e.printStackTrace(); System.exit(100); }

    	AttachingConnector connector = findConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("port").setValue("1044");
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("timeout").setValue("5000");
        
        try {
            vm = connector.attach(arguments);
            if (vm!=null){
            	
                groovy = new groovy.ui.Console();
                groovy.setVariable("tmp", 123);
                groovy.setVariable("vm", vm);
                groovy.setVariable("calls", calls);
                groovy.run();
            	
                vm.setDebugTraceMode( VirtualMachine.TRACE_NONE );
                setEventRequests( false );
                vm.resume();
                start();
            }
        } catch (Exception exc) {
            exc.printStackTrace();
            return;
        }
        
        
//        for( Method k : calls.keySet()){
//        	Integer v = calls.get(k);
//        	writer.printf("%06d | %s\n", v, k.toString());
//        }
        
        writer.close();
        
//        try{
//        	DBconn.close();
//        }catch(Exception e){ e.printStackTrace(); }
    }

    AttachingConnector findConnector() {
        List<AttachingConnector> connectors = Bootstrap.virtualMachineManager().attachingConnectors();
        for (AttachingConnector connector : connectors) {
	        	
	        	if (connector.name().equals("com.sun.jdi.SocketAttach") ){
	        		return connector;
	        	}
                
        }
        throw new Error("No launching connector");
    }
        
	void setEventRequests(boolean watchFields) {
		EventRequestManager mgr = vm.eventRequestManager();

		MethodEntryRequest menr = mgr.createMethodEntryRequest();
		for (int i = 0; i < excludes.length; ++i) { menr.addClassExclusionFilter(excludes[i]); }
		menr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
		menr.enable();
		
		MethodExitRequest mext = mgr.createMethodExitRequest();
		for (int i = 0; i < excludes.length; ++i) { mext.addClassExclusionFilter(excludes[i]); }
		mext.setSuspendPolicy(EventRequest.SUSPEND_NONE);
		mext.enable();
	}
    
	private void handleEvent(Event event) {
		if (event instanceof MethodEntryEvent) {
			traceMethodEntry((MethodEntryEvent)event);
		}else if (event instanceof MethodExitEvent) {
			traceMethodExit((MethodExitEvent)event);
		}
	}
	
    void start(){
		EventQueue queue = vm.eventQueue();
		while (true) {
			try {
				EventSet eventSet = queue.remove();
				EventIterator it = eventSet.eventIterator();
				while (it.hasNext()) {
					handleEvent(it.nextEvent());
				}
				eventSet.resume();
			} catch (InterruptedException exc) {
				// Ignore
			} catch (VMDisconnectedException exc) {
				break;
			}
		}	
    }
    
//	private void methodEntryEvent(MethodEntryEvent event) {
//		Method tmp = event.method();
//		if (calls.containsKey(tmp)) {
//			calls.put(tmp, calls.get(tmp)+1);
//		}else{
//			calls.put(tmp, 1);
//		}
//	}
	
    private void traceMethodEntry(MethodEntryEvent event) {
    	Method m = event.method();
    	
    	Integer hc = m.hashCode();
    	
    	String[] tmp = new String[]{
        		String.valueOf( m.hashCode() ),
        		"",
        		m.name(),
        		"",
        		String.valueOf( m.location().lineNumber() ) ,
        		m.declaringType().name(),
        		String.valueOf( System.currentTimeMillis() ),
        		""
        		};
    	
    	try{
    		tmp[1] = String.valueOf( event.thread().frameCount() );
    		tmp[2] = m.location().sourcePath();
    	}catch(Exception e){}
		
    	calls.put(hc, tmp);
    	
    	call_stack.push(hc);
    	
//    	try{
//    		java.sql.Statement st = DBconn.createStatement();
//	    	String sql = String.format("INSERT OR IGNORE INTO log(id,name, loc, start_time) VALUES(%d,'%s','%s',%d)",
//	    			tmp.hashCode(),
//	    			tmp.name(),
//	    			tmp.location(),
//	    			System.currentTimeMillis()
//	    			);
//	    	//System.out.println(sql);
//    		st.execute(sql);
//    	}catch(Exception e){ e.printStackTrace(); }
    	
    }
	private void traceMethodExit(MethodExitEvent event) {
		Method m = event.method();
		
		Integer hc = m.hashCode();
		//Integer s_hc = null;
		
		//try{ s_hc = call_stack.pop(); }catch(Exception e){}
		
		//if (s_hc!=null && !s_hc.equals(hc) ){
			//System.out.println("stack trace not working (" + s_hc + "!=" + hc + ")");
		//}
		
		if (calls.containsKey(hc)){
			String[] tmp = calls.get(hc);
			
			if (tmp.length>6){
				tmp[7] =  String.valueOf( System.currentTimeMillis() );
			}
			
			for (int i=0; i<tmp.length; i++){
				if (i!=0){ writer.print( "," ); }
				writer.print( "\"" );
				writer.print( tmp[i].replace("\"", "\\\"") );
				writer.print( "\"" );
			}
			writer.print( "\n" );
			
			calls.remove(hc);
		}
		
//		try{
//	    	java.sql.Statement st = DBconn.createStatement();
//	    	String sql = String.format("UPDATE log SET end_time = %d WHERE id = %d",
//	    			System.currentTimeMillis(),
//	    			tmp.hashCode()
//	    			);
//	    	//System.out.println(sql);
//    		st.execute(sql);
//    	}catch(Exception e){ e.printStackTrace(); }
		
	}
}
