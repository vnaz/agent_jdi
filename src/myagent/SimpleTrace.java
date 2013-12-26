package myagent;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import com.sun.jdi.Method;

import java.io.PrintWriter;

public class SimpleTrace {
       
    public static void main(String[] args) {
        new SimpleTrace(args);
    }
    
    public ThreadReference cur_thread = null;
    public boolean do_suspend = false;
    
    public String[] filters  = new String[]{"java.io.File"};
    public String[] excludes = new String[]{};//{"java.*", "javax.*", "sun.*", "com.sun.*"};
    
    public VirtualMachine vm;
    
    public MethodExitRequest req_exit;
    public MethodEntryRequest req_enter;
    
    PrintWriter writer;
    
    
    SimpleTrace(String[] args) {
        
        try{
            writer = new PrintWriter("D:\\somelog.csv");
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
                
                vm.setDebugTraceMode( VirtualMachine.TRACE_NONE );
                for (ThreadReference thread  : vm.allThreads()){
                    if (thread.name().equals("main") ){
                        cur_thread = thread; break;
                    }
                }
                setupVMEvents();
                vm.resume();
                mainLoop();
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
            if (connector.name().equals("com.sun.jdi.SocketAttach") ){
                return connector;
            }
        }
        throw new Error("No launching connector");
    }
        
    void setupVMEvents() {
        EventRequestManager mgr = vm.eventRequestManager();
        
        req_exit = mgr.createMethodExitRequest();
        req_enter = mgr.createMethodEntryRequest();
        
        //        for (int i = 0; i < excludes.length; i++) { 
        //            req_enter.addClassExclusionFilter(excludes[i]);
        //            req_exit.addClassExclusionFilter(excludes[i]);
        //        }
        for (int i = 0; i < filters.length; i++) { 
            req_enter.addClassFilter(filters[i]);
            req_exit.addClassFilter(filters[i]);
        }
        
        req_enter.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        req_exit.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        
        req_enter.enable();
        req_exit.enable();
    }
    
    private void handleEvent(Event event) {
        if (event instanceof MethodEntryEvent){
            methodEntry((MethodEntryEvent)event);
        }else if(event instanceof MethodExitEvent){
            methodExit((MethodExitEvent)event);
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
                eventSet.resume();
            } catch (InterruptedException exc) {
                // Ignore
            } catch (VMDisconnectedException exc) {
                break;
            }
        }   
    }
    
    private void methodEntry(MethodEntryEvent event) {
        Method m = event.method();
        writer.println(m);
    }
    private void methodExit(MethodExitEvent event) {
        Method m = event.method();
    }
}
