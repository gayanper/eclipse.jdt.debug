package org.eclipse.jdt.internal.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.IDebugConstants;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.event.WatchpointEvent;

/**
 * Dispatches events generated by a debuggable VM.
 */

class EventDispatcher implements Runnable {
	/**
	 * The debug target associated with this dispatcher.
	 */
	protected JDIDebugTarget fTarget;
	/**
	 * Whether this dispatcher should continue reading events.
	 */
	protected boolean fKeepReading;
	
	protected EventSet fEventSet;
	
	protected EventIterator fIterator;
	
	/**
	 * Creates a new event dispatcher listening for events
	 * originating from the underlying VM.
	 */
	EventDispatcher(JDIDebugTarget process) {
		fTarget= process;
	}

	/**
	 * Dispatch an event set received from the VirtualMachine.
	 */
	protected void dispatch(EventSet eventSet) {
		if (!fKeepReading) {
			return;
		}
		fIterator= eventSet.eventIterator();
		while (fIterator.hasNext()) {
			if (!fKeepReading) {
				return;
			}
			Event event= fIterator.nextEvent();
			if (event == null) {
				continue;
			}
			// The event types are checked in order
			// of their expected frequency, from the most specific type to the more general.
			if (event instanceof StepEvent) {
				dispatchStepEvent((StepEvent)event);
			} else
				if ((event instanceof BreakpointEvent) ||
				(event instanceof LocatableEvent) ||
				(event instanceof ExceptionEvent) ||
				(event instanceof WatchpointEvent) ||
				(event instanceof MethodEntryEvent)) {
					dispatchBreakpointEvent(event);
				} else
					if (event instanceof ThreadStartEvent) {
						fTarget.handleThreadStart((ThreadStartEvent) event);
					} else
						if (event instanceof ThreadDeathEvent) {
							fTarget.handleThreadDeath((ThreadDeathEvent) event);
						} else
							if (event instanceof ClassPrepareEvent) {
								fTarget.handleClassLoad((ClassPrepareEvent) event);
							} else
								if (event instanceof VMDeathEvent) {
									fTarget.handleVMDeath((VMDeathEvent) event);
									fKeepReading= false; // stop listening for events
								} else
									if (event instanceof VMDisconnectEvent) {
										fTarget.handleVMDisconnect((VMDisconnectEvent) event);
										fKeepReading= false; // stop listening for events
									} else if (event instanceof VMStartEvent) {
										fTarget.handleVMStart((VMStartEvent)event);
									} else {
										// Unknown Event Type
									}
		}
	}
	
	protected void dispatchBreakpointEvent(Event event) {
		if (!fKeepReading) {
			return;
		}
		JavaBreakpoint breakpoint= (JavaBreakpoint)event.request().getProperty(IDebugConstants.BREAKPOINT);
		breakpoint.handleEvent(event, fTarget);		
	}

	protected void dispatchStepEvent(StepEvent event) {
		ThreadReference threadRef= event.thread();
		JDIThread thread= findThread(threadRef);
		if (thread == null) {
			fTarget.resume(threadRef);
			return;
		} else {
			thread.handleStep(event);
		}
	}

	/**
	 * Convenience method for finding the model thread for 
	 * an underlying thread reference.
	 */
	protected JDIThread findThread(ThreadReference threadReference) {
		return fTarget.findThread(threadReference);
	}

	/**
	 * Continuously reads events that are coming from the event queue.
	 */
	public void run() {
		EventQueue q= fTarget.fVirtualMachine.eventQueue();
		fKeepReading= true;
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) {
					dispatch(fEventSet);	
				}
			};
			
		while (fKeepReading) {
			try {
				try {
					// Get the next event set.
					fEventSet= q.remove();
					if (fEventSet == null)
						break;
				} catch (VMDisconnectedException e) {
					break;
				}
								
				if(fKeepReading) {
					try {
						workspace.run(runnable, null);
					} catch (CoreException e) {
						DebugJavaUtils.logError(e);
						break;
					}
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	/**
	 * Shutdown the event dispatcher...stops
	 * reading and dispatching events from the event queue.	
	 */
	protected void shutdown() {
		fKeepReading= false;
	}
	
	protected boolean hasPendingEvents() {
		return fIterator.hasNext();
	}
}

