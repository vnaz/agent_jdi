package myagent;

import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.Bootstrap;

import java.io.PrintWriter;

public class Trace {

	public static void main(String[] args) {
        new Trace(args);
    }
	
	public int debugTraceMode = 0;
	public boolean watchFields = false;
	public String[] excludes = new String[]{"java.lang.*"}; //{"java.*", "javax.*", "sun.*", "com.sun.*"};

    public static String nextBaseIndent = ""; // Starting indent for next thread
    
    public VirtualMachine vm;
    public boolean connected = true;
    public boolean vmDied = true;
    
    private ThreadReference curThread = null;
    private StringBuffer curIndent = null;
    private String curBaseIndent = null;
    
    private Map<ThreadReference, StringBuffer> indents = new HashMap<ThreadReference, StringBuffer>();
    private Map<ThreadReference, String> baseIndents = new HashMap<ThreadReference, String>();
    
    PrintWriter writer;

    Trace(String[] args) {
    	
    	try{
    		writer = new PrintWriter("D:\\somelog.txt");
    	}catch (Exception e){
    		writer = new PrintWriter(System.out);
    	}

    	AttachingConnector connector = findConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("port").setValue("1044");
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("timeout").setValue("5000");
        
        try {
            vm = connector.attach(arguments);
            if (vm!=null){
                vm.setDebugTraceMode(debugTraceMode);
                setEventRequests(watchFields);
                vm.resume();
                start();
            }
        } catch (Exception exc) {
            exc.printStackTrace();
            return;
        }
        
        writer.close();
    }

    AttachingConnector findConnector() {
        List<AttachingConnector> connectors = Bootstrap.virtualMachineManager().attachingConnectors();
        for (AttachingConnector connector : connectors) {
	        	/*System.out.println("AttachingConnector:  '" + connector.getClass().getName() + "'");
	        	System.out.println("  name: '" + connector.name() + "'");
	        	System.out.println("  description: '" + connector.description() + "'");
	        	System.out.println("  transport name: '" + connector.transport().name() + "'");
	        	System.out.println("  default arguments:");
	        	
	        	Map<String, Connector.Argument> paramsMap = connector.defaultArguments();
	        	java.util.Iterator<String> keyIter = paramsMap.keySet().iterator();
	        	while (keyIter.hasNext()) {
					String nextKey = keyIter.next();
					System.out.println("    key: '" + nextKey + "'; value: '" + paramsMap.get(nextKey) + "'");
				}*/
	        	
	        	if (connector.name().equals("com.sun.jdi.SocketAttach") ){
	        		return connector;
	        	}
                
        }
        throw new Error("No launching connector");
    }
        
	void setEventRequests(boolean watchFields) {
		EventRequestManager mgr = vm.eventRequestManager();

		// want all exceptions
		ExceptionRequest excReq = mgr.createExceptionRequest(null, true, true);
		// suspend so we can step
		excReq.setSuspendPolicy(EventRequest.SUSPEND_ALL);
		excReq.enable();

		MethodEntryRequest menr = mgr.createMethodEntryRequest();
		for (int i = 0; i < excludes.length; ++i) {
			menr.addClassExclusionFilter(excludes[i]);
		}
		menr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
		menr.enable();

		MethodExitRequest mexr = mgr.createMethodExitRequest();
		for (int i = 0; i < excludes.length; ++i) {
			mexr.addClassExclusionFilter(excludes[i]);
		}
		mexr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
		mexr.enable();

		ThreadDeathRequest tdr = mgr.createThreadDeathRequest();
		// Make sure we sync on thread death
		tdr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
		tdr.enable();

		if (watchFields) {
			ClassPrepareRequest cpr = mgr.createClassPrepareRequest();
			for (int i = 0; i < excludes.length; ++i) {
				cpr.addClassExclusionFilter(excludes[i]);
			}
			cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			cpr.enable();
		}
	}
    
	private void handleEvent(Event event) {
		if (event instanceof ExceptionEvent) {
			exceptionEvent((ExceptionEvent) event);
		} else if (event instanceof ModificationWatchpointEvent) {
			fieldWatchEvent((ModificationWatchpointEvent) event);
		} else if (event instanceof MethodEntryEvent) {
			methodEntryEvent((MethodEntryEvent) event);
		} else if (event instanceof MethodExitEvent) {
			methodExitEvent((MethodExitEvent) event);
		} else if (event instanceof StepEvent) {
			stepEvent((StepEvent) event);
		} else if (event instanceof ThreadDeathEvent) {
			threadDeathEvent((ThreadDeathEvent) event);
		} else if (event instanceof ClassPrepareEvent) {
			classPrepareEvent((ClassPrepareEvent) event);
		} else if (event instanceof VMStartEvent) {
			vmStartEvent((VMStartEvent) event);
		} else if (event instanceof VMDeathEvent) {
			vmDeathEvent((VMDeathEvent) event);
		} else if (event instanceof VMDisconnectEvent) {
			vmDisconnectEvent((VMDisconnectEvent) event);
		} else {
			throw new Error("Unexpected event type");
		}
	}
	
    void start(){
		EventQueue queue = vm.eventQueue();
		while (connected) {
			try {
				EventSet eventSet = queue.remove();
				EventIterator it = eventSet.eventIterator();
				while (it.hasNext()) {
					handleEvent(it.nextEvent());
				}
				eventSet.resume();
			} catch (InterruptedException exc) {
				// Ignore
			} catch (VMDisconnectedException discExc) {
				handleDisconnectedException();
				break;
			}
		}	
    }
	
	private void println(String str) {
		writer.print(curIndent);
		writer.println(str);
	}
	
	void threadTrace(ThreadReference newThread) {
		if (newThread != curThread)
		{
			//saving old thread indent
			if (curThread != null && indents.containsKey(curThread)){
				indents.put(curThread, curIndent);
			}
			
			//loading new one
			if (!indents.containsKey(newThread)){
				curIndent = new StringBuffer(nextBaseIndent);
				curBaseIndent = nextBaseIndent; 
				
				indents.put(newThread, curIndent);
				baseIndents.put(newThread, curBaseIndent);
				
				nextBaseIndent += "                     ";
				println("====== " + newThread.name() + " ======");			
			}else{
				curIndent = indents.get(newThread);
				curBaseIndent = baseIndents.get(newThread);
			}
		}
	}
	
	private void vmStartEvent(VMStartEvent event) {
		writer.println("-- VM Started --");
	}

	// Forward event for thread specific processing
	private void methodEntryEvent(MethodEntryEvent event) {
		threadTrace(event.thread());
		println(event.method().name() + "  --  " + event.method().declaringType().name());
		curIndent.append("| ");
	}

	// Forward event for thread specific processing
	private void methodExitEvent(MethodExitEvent event) {
		threadTrace(event.thread());
		curIndent.setLength(curIndent.length() - 2);
	}

	// Forward event for thread specific processing
	private void stepEvent(StepEvent event) {
		
		ThreadReference thread = event.thread(); 
		threadTrace(thread);
		
		// Adjust call depth
		int cnt = 0;
		curIndent = new StringBuffer(curBaseIndent);
		try {
			cnt = thread.frameCount();
		} catch (IncompatibleThreadStateException exc) {}
		
		while (cnt-- > 0) { curIndent.append("| "); }

		EventRequestManager mgr = vm.eventRequestManager();
		mgr.deleteEventRequest(event.request());
		
	}

	// Forward event for thread specific processing
	private void fieldWatchEvent(ModificationWatchpointEvent event) {
		threadTrace(event.thread());
		
		Field field = event.field();
		Value value = event.valueToBe();
		println("    " + field.name() + " = " + value);
	}

	void threadDeathEvent(ThreadDeathEvent event) {
		ThreadReference thread = event.thread();
		
		curIndent = new StringBuffer(curBaseIndent);
		println("====== " + thread.name() + " end ======");
	}

	/**
	 * A new class has been loaded. Set watchpoints on each of its fields
	 */
	private void classPrepareEvent(ClassPrepareEvent event) {
		EventRequestManager mgr = vm.eventRequestManager();
		List<Field> fields = event.referenceType().visibleFields();
		for (Field field : fields) {
			ModificationWatchpointRequest req = mgr
					.createModificationWatchpointRequest(field);
			for (int i = 0; i < excludes.length; ++i) {
				req.addClassExclusionFilter(excludes[i]);
			}
			req.setSuspendPolicy(EventRequest.SUSPEND_NONE);
			req.enable();
		}
	}

	private void exceptionEvent(ExceptionEvent event) {
		println("Exception: " + event.exception() + " catch: " + event.catchLocation());

		// Step to the catch
		EventRequestManager mgr = vm.eventRequestManager();
		StepRequest req = mgr.createStepRequest(event.thread(), StepRequest.STEP_MIN, StepRequest.STEP_INTO);
		req.addCountFilter(1); // next step only
		req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
		req.enable();
	}

	public void vmDeathEvent(VMDeathEvent event) {
		vmDied = true;
		writer.println("-- The application exited --");
	}

	public void vmDisconnectEvent(VMDisconnectEvent event) {
		connected = false;
		if (!vmDied) {
			writer.println("-- The application has been disconnected --");
		}
	}
	
	synchronized void handleDisconnectedException() {
		EventQueue queue = vm.eventQueue();
		while (connected) {
			try {
				EventSet eventSet = queue.remove();
				EventIterator iter = eventSet.eventIterator();
				while (iter.hasNext()) {
					Event event = iter.nextEvent();
					if (event instanceof VMDeathEvent) {
						vmDeathEvent((VMDeathEvent) event);
					} else if (event instanceof VMDisconnectEvent) {
						vmDisconnectEvent((VMDisconnectEvent) event);
					}
				}
				eventSet.resume(); // Resume the VM
			} catch (InterruptedException exc) {
				// ignore
			}
		}
	}
	
}
