package myagent;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import com.sun.jdi.Method;

import java.io.PrintWriter;

public class MethodsTrace {
	
	interface VMEventListener { public void handle(Event event); }
	
	public static void main(String[] args) {
        new MethodsTrace(args);
    }
	
	public int debugTraceMode = 0;
	public boolean watchFields = false;
	public String[] excludes = new String[]{"java.*", "javax.*", "sun.*", "com.sun.*"};
    
    public VirtualMachine vm;
    
    PrintWriter writer;
    
    
    HashMap<Integer, String[]> calls = new HashMap<Integer, String[]>();
    
    Stack<Integer> call_stack = new Stack<Integer>();
    
    
    HashMap<Class, ArrayList<VMEventListener>> handlers = new HashMap<Class, ArrayList<VMEventListener>>();
    

    MethodsTrace(String[] args) {
    	
    	pre_init();

    	AttachingConnector connector = findConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("port").setValue("1044");
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("timeout").setValue("5000");
        
        try {
            vm = connector.attach(arguments);
            if (vm!=null){
            	
            	init();
            	
                vm.setDebugTraceMode( VirtualMachine.TRACE_NONE );
                //vm.resume();
                mainLoop();
            }
        } catch (Exception exc) {
            exc.printStackTrace();
            return;
        }
        
        beforeEnd();
        cleanup();
    }
    
	private void pre_init() {
    	try{
    		writer = new PrintWriter("D:\\somelog.csv");
    	}catch (Exception e){
    		writer = new PrintWriter(System.out);
    	}
    	
	}
	
    private void init() {
    	initGroovy();
    	
    	/*addMethodEntryListener(new VMEventListener() {
			@Override
			public void handle(Event event) { traceMethodEntry((MethodEntryEvent)event); }
		}, true);
    	
    	addMethodExitListener(new VMEventListener() {
			@Override
			public void handle(Event event) { traceMethodExit((MethodExitEvent)event); }
		}, true);*/
	}
    
	private void initGroovy() {
    	groovy.ui.Console groovy;
    	
		groovy = new groovy.ui.Console();
		groovy.setVariable("tr", this);
		groovy.setVariable("vm", vm);
		
		groovy.setVariable("calls", calls);
		groovy.setVariable("handlers", handlers);
		
		groovy.run();
	}

	private void beforeEnd() {
		// for( Method k : calls.keySet()){
		// 	Integer v = calls.get(k);
		// 	writer.printf("%06d | %s\n", v, k.toString());
		// }
	}


	private void cleanup() {
    	writer.close();
	}


	//VM prepare stuff
	AttachingConnector findConnector() {
        List<AttachingConnector> connectors = Bootstrap.virtualMachineManager().attachingConnectors();
        for (AttachingConnector connector : connectors) {
        	if (connector.name().equals("com.sun.jdi.SocketAttach") ){
        		return connector;
        	}
        }
        throw new Error("No launching connector");
    }
        
	void setupVMEvents(boolean watchFields) {

	}
    
	private void handleEvent(Event event) {
		for (Class cls : handlers.keySet()){
			if (cls.isInstance(event)) {
				for ( VMEventListener listener : handlers.get(cls) ){
					listener.handle(event);
				}
			}
		}
	}
	
    void mainLoop(){
		EventQueue queue = vm.eventQueue();
		while (true) {
			try {
				EventSet eventSet = queue.remove();
				EventIterator it = eventSet.eventIterator();
				while (it.hasNext()) {
					handleEvent(it.nextEvent());
				}
				//eventSet.resume();
			} catch (InterruptedException exc) {
				// Ignore
			} catch (VMDisconnectedException exc) {
				break;
			}
		}	
    }
	
    void addMethodEntryListener(VMEventListener listener, Boolean not_suspend){
    	if (!handlers.containsKey(MethodEntryEvent.class)){
	    	EventRequestManager mgr = vm.eventRequestManager();
	    	
			MethodEntryRequest request = mgr.createMethodEntryRequest();
			for (int i = 0; i < excludes.length; ++i) { request.addClassExclusionFilter(excludes[i]); }
			if (not_suspend!=null && not_suspend){
				request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
			}
			request.enable();
			
			handlers.put(MethodEntryEvent.class, new ArrayList<VMEventListener>());
    	}
    	handlers.get(MethodEntryEvent.class).add(listener);
    }
    
    void addMethodExitListener(VMEventListener listener, Boolean not_suspend){
    	if (!handlers.containsKey(MethodExitEvent.class)){
			EventRequestManager mgr = vm.eventRequestManager();
			
			MethodExitRequest request = mgr.createMethodExitRequest();
			for (int i = 0; i < excludes.length; ++i) { request.addClassExclusionFilter(excludes[i]); }
			if (not_suspend!=null && not_suspend){
				request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
			}
			request.enable();
			
			handlers.put(MethodExitEvent.class, new ArrayList<VMEventListener>());
    	}
    	handlers.get(MethodExitEvent.class).add(listener);
    }
    
    void onEnter(final groovy.lang.Closure code, Boolean not_suspend){
        addMethodEntryListener( new VMEventListener() {
            @Override
            public void handle(Event event) {
                code.call(event);
            }}, not_suspend);
    }
    
    void onExit(final groovy.lang.Closure code, Boolean not_suspend){
        addMethodExitListener( new VMEventListener() {
            @Override
            public void handle(Event event) {
                code.call(event);
            }}, not_suspend);
    }
    
    
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
		

		
	}
}
