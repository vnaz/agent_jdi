package myagent;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import com.sun.jdi.Method;

import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.PrintWriter;

public class GrovyTrace {
	
	interface VMEventListener { public void handle(Event event); }
	
	public static void main(String[] args) {
        new GrovyTrace(args);
    }
	
	public ThreadReference main_thread = null;
	public boolean do_suspend = false;
	
	public String[] filters  = new String[]{};
	public String[] excludes = new String[]{"java.*", "javax.*", "sun.*", "com.sun.*"};
    
	
	public static final int	STEP_LINE = com.sun.jdi.request.StepRequest.STEP_LINE;
	public static final int	STEP_MIN  = com.sun.jdi.request.StepRequest.STEP_MIN;
	
	public static final int	STEP_INTO = com.sun.jdi.request.StepRequest.STEP_INTO;
	public static final int	STEP_OVER = com.sun.jdi.request.StepRequest.STEP_OVER;
	public static final int	STEP_OUT  = com.sun.jdi.request.StepRequest.STEP_OUT;
	
    public VirtualMachine vm;
    
    groovy.ui.Console groovy;
    
    PrintWriter writer;
    
    
    HashMap<Integer, String[]> calls = new HashMap<Integer, String[]>();
    
    Stack<Integer> call_stack = new Stack<Integer>();
    
    
    HashMap<EventRequest, ArrayList<VMEventListener>> handlers = new HashMap<EventRequest, ArrayList<VMEventListener>>();
    

    //DEBUGGER STARTUP
    //-----
    GrovyTrace(String[] args) {
    	
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
                for (ThreadReference thread  : vm.allThreads()){
                	if (thread.name().equals("main") ){
                		main_thread = thread; break;
                	}
                }
                
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
		groovy = new groovy.ui.Console();
		groovy.setVariable("tr", this);
		groovy.setVariable("vm", vm);
		
		groovy.setVariable("handlers", handlers);
						
		groovy.run();
		
		javax.swing.JFrame frame = (javax.swing.JFrame)groovy.getFrame();
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosed(WindowEvent e) {
		        super.windowClosed(e);
		        System.exit(0);
		    }
        });
		
		javax.swing.JToolBar tb =  (javax.swing.JToolBar) groovy.getToolbar();
		
		javax.swing.JButton but = new javax.swing.JButton("run");
		but.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { run(); }	});
		tb.add(but);
		
		but = new javax.swing.JButton("stop");
		but.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { stop(); }	});
		tb.add(but);
		
		but = new javax.swing.JButton("status");
		but.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { groovy.clearOutput(); status(); }	});
		tb.add(but);
		
		but = new javax.swing.JButton("stack");
        but.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { groovy.clearOutput(); stackTrace(); } });
        tb.add(but);
	}

	private void beforeEnd() {
		// for( Method k : calls.keySet()){
		// 	Integer v = calls.get(k);
		// 	writer.printf("%06d | %s\n", v, k.toString());
		// }
	}


	private void cleanup() {
		groovy.exit();
    	writer.close();
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
        
	void setupVMEvents(boolean watchFields) {

	}
    
	private void handleEvent(Event event) {
		
		for (EventRequest req : handlers.keySet()){
			if (req.equals( event.request() )) {
				for ( VMEventListener listener : handlers.get(req) ){
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
	
    //LISTENERS STUFF
    //-----
    EventRequest addMethodEntryListener(VMEventListener listener, boolean suspend){
    	EventRequestManager mgr = vm.eventRequestManager();
		MethodEntryRequest request = mgr.createMethodEntryRequest();
		
		for (int i = 0; i < excludes.length; i++) { request.addClassExclusionFilter(excludes[i]); }
		for (int i = 0; i < filters.length; i++)  { request.addClassFilter(filters[i]); }
		if (!suspend){ request.setSuspendPolicy(EventRequest.SUSPEND_NONE); }
		request.enable();
			
		handlers.put(request, new ArrayList<VMEventListener>());
    	handlers.get(request).add(listener);
    	
    	return request;
    }
    
    EventRequest addMethodExitListener(VMEventListener listener, boolean suspend){
			EventRequestManager mgr = vm.eventRequestManager();
			MethodExitRequest request = mgr.createMethodExitRequest();
			
			for (int i = 0; i < excludes.length; i++) { request.addClassExclusionFilter(excludes[i]); }
			for (int i = 0; i < filters.length; i++) { request.addClassFilter(filters[i]); }
			if (!suspend){ request.setSuspendPolicy(EventRequest.SUSPEND_NONE); }
			request.enable();
			
			handlers.put(request, new ArrayList<VMEventListener>());
	    	handlers.get(request).add(listener);
	    	
	    	return request;
    }
    
    EventRequest addClassPreparationListener(VMEventListener listener, boolean suspend){
			EventRequestManager mgr = vm.eventRequestManager();
			ClassPrepareRequest request = mgr.createClassPrepareRequest();
			
			for (int i = 0; i < excludes.length; i++) { request.addClassExclusionFilter(excludes[i]); }
			for (int i = 0; i < filters.length; i++) { request.addClassFilter(filters[i]); }
			if (!suspend){ request.setSuspendPolicy(EventRequest.SUSPEND_NONE); }
			request.enable();
			
			handlers.put(request, new ArrayList<VMEventListener>());
	    	handlers.get(request).add(listener);
	    	
	    	return request;
    }
    
    EventRequest addClassUnloadListener(VMEventListener listener, boolean suspend){
			EventRequestManager mgr = vm.eventRequestManager();
			ClassUnloadRequest request = mgr.createClassUnloadRequest();
			
			for (int i = 0; i < excludes.length; i++) { request.addClassExclusionFilter(excludes[i]); }
			for (int i = 0; i < filters.length; i++) { request.addClassFilter(filters[i]); }
			if (!suspend){ request.setSuspendPolicy(EventRequest.SUSPEND_NONE); }
			request.enable();
			
			handlers.put(request, new ArrayList<VMEventListener>());
	    	handlers.get(request).add(listener);
	    	
	    	return request;
    }
    
    EventRequest addStepListener(ThreadReference thread, int step_size, int step_depth, VMEventListener listener, boolean suspend){
			EventRequestManager mgr = vm.eventRequestManager();
			
			if (thread==null){ thread = main_thread; }
			if (thread==null){ thread = vm.allThreads().get(0); }

			StepRequest request = mgr.createStepRequest(thread, step_size, step_depth);
			
			for (int i = 0; i < excludes.length; i++) { request.addClassExclusionFilter(excludes[i]); }
			for (int i = 0; i < filters.length; i++) { request.addClassFilter(filters[i]); }
			if (!suspend){ request.setSuspendPolicy(EventRequest.SUSPEND_NONE); }
			request.enable();
			
			handlers.put(request, new ArrayList<VMEventListener>());
			handlers.get(request).add(listener);
			
			return request;
    }
    
    //GROOVY ORIENTED FUNCTIONS
    //-----
    EventRequest onEnter(String[] classFilter, final groovy.lang.Closure code){
        String[] tmp = filters;
        filters = classFilter;
        EventRequest result = onEnter(code, do_suspend);
        filters = tmp;
        return result;
    }
    EventRequest onEnter(final groovy.lang.Closure code){ return onEnter(code, do_suspend); }
    EventRequest onEnter(final groovy.lang.Closure code, boolean suspend){
        return addMethodEntryListener( new VMEventListener() {
            @Override
            public void handle(Event event) {
                code.call(event);
            }}, suspend);
    }
    
    EventRequest onExit(final groovy.lang.Closure code){ return onExit(code, do_suspend); }
    EventRequest onExit(final groovy.lang.Closure code, boolean suspend){
        return addMethodExitListener( new VMEventListener() {
            @Override
            public void handle(Event event) {
                code.call(event);
            }}, suspend);
    }
    
    EventRequest onClassPrepare(final groovy.lang.Closure code){ return onClassPrepare(code, do_suspend); }
    EventRequest onClassPrepare(final groovy.lang.Closure code, boolean suspend){
    	return addClassPreparationListener( new VMEventListener() {
    		@Override
            public void handle(Event event) {
                code.call(event);
            }}, suspend);
    }
    
    EventRequest onClassUnload(final groovy.lang.Closure code){ return onClassUnload(code, do_suspend); }
    EventRequest onClassUnload(final groovy.lang.Closure code, boolean suspend){
    	return addClassUnloadListener(new VMEventListener() {
    		@Override
            public void handle(Event event) {
                code.call(event);
            }}, suspend);
    }
    
    EventRequest onStep(final groovy.lang.Closure code){ return onStep(main_thread, STEP_LINE, STEP_INTO, code, do_suspend); }
    EventRequest onStep(ThreadReference thread, int step_size, int step_depth, final groovy.lang.Closure code, boolean suspend){
    	return addStepListener(thread, step_size, step_depth,new VMEventListener() {
    		@Override
            public void handle(Event event) {
                code.call(event);
            }}, suspend);
    }
    
    void stepInto(){ do_step(STEP_INTO); }
    void stepOut(){ do_step(STEP_OUT); }
    void stepOver(){ do_step(STEP_OVER); }
    
    void run()  { vm.resume(); }
    void stop() { vm.suspend(); }
    
    ThreadReference selectThread(int identifier){
        for ( ThreadReference thread : vm.allThreads() ){
            if (thread.hashCode() == identifier ){
                main_thread = thread;
                return thread;
            }
        }
        return null;
    }
    
    ThreadReference selectThread(String identifier){
    	for ( ThreadReference thread : vm.allThreads() ){
    	    if (thread.name().equals(identifier)){
                main_thread = thread;
                return thread;
            }
    	}
    	return null;
    }
    
    void status() {
    	for ( ThreadReference thread : vm.allThreads() ){
    		String status = "";
    		switch ( thread.status() ) {
    			case ThreadReference.THREAD_STATUS_MONITOR:
    				status = "monitor"; break;
    			case ThreadReference.THREAD_STATUS_NOT_STARTED:
    				status = "not started"; break;
    			case ThreadReference.THREAD_STATUS_RUNNING:
    				status = "running"; break;
    			case ThreadReference.THREAD_STATUS_SLEEPING:
    				status = "sleep"; break;
    			case ThreadReference.THREAD_STATUS_UNKNOWN:
    				status = "unknown"; break;
    			case ThreadReference.THREAD_STATUS_WAIT:
    				status = "wait"; break;
    			case ThreadReference.THREAD_STATUS_ZOMBIE:
    				status = "zombie"; break;
    		}
    		System.out.println( (thread.equals(main_thread)?">":"") + thread.hashCode() + " '" + thread.name() + "' - " + status + (thread.isSuspended()?" (suspended)":"") );
    	}
    }
    
    void stackTrace() {
        try {
            int i = 0;
            for ( StackFrame frame : main_thread.frames()){
                System.out.println( (i++) + ". " + frame.location().method() + " - " + frame.toString() );
            }
        }catch(Exception e){ e.printStackTrace(); }
    }
    
    void disableRequests(){
    	for( EventRequest req : handlers.keySet() ){
    		req.putProperty("prev_state", req.isEnabled());
    		req.setEnabled(false);
    	}
    }
    
    void restoreRequests(){
    	for( EventRequest req : handlers.keySet() ){
    		Boolean prev_state = (Boolean)req.getProperty("prev_state");
    		if (prev_state==null){ prev_state = true; }
    		req.setEnabled( prev_state );
    	}
    }
    
    void do_step(int mode){
    	disableRequests();
    	addStepListener(null,  STEP_LINE, mode, new VMEventListener() {
            @Override
            public void handle(Event event) {
                event.request().disable();
                handlers.remove(event.request());
                restoreRequests();
            }
        }, true);
    	vm.resume();
    }
}
