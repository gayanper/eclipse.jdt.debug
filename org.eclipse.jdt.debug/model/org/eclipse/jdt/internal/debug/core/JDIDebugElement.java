package org.eclipse.jdt.internal.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdi.TimeoutException;
import org.eclipse.jdi.hcr.OperationRefusedException;
import org.eclipse.jdt.debug.core.JDIDebugModel;

import com.sun.jdi.ClassNotPreparedException;
import com.sun.jdi.InconsistentDebugInfoException;
import com.sun.jdi.InternalException;
import com.sun.jdi.InvalidCodeIndexException;
import com.sun.jdi.InvalidLineNumberException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.NativeMethodException;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VMMismatchException;
import com.sun.jdi.VMOutOfMemoryException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.DuplicateRequestException;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.InvalidRequestStateException;

public abstract class JDIDebugElement extends PlatformObject implements IDebugElement {
			
	/**
	 * Collection of possible JDI exceptions (runtime)
	 */
	private static List fgJDIExceptions;
	
	/**
	 * Debug target associated with this element
	 */
	private JDIDebugTarget fDebugTarget;
	
	static {
		fgJDIExceptions = new ArrayList(15);
		
		// Runtime/unchecked exceptions
		fgJDIExceptions.add(ClassNotPreparedException.class);
		fgJDIExceptions.add(InconsistentDebugInfoException.class);
		fgJDIExceptions.add(InternalException.class);
		fgJDIExceptions.add(InvalidCodeIndexException.class);
		fgJDIExceptions.add(InvalidLineNumberException.class);
		fgJDIExceptions.add(InvalidStackFrameException.class);
		fgJDIExceptions.add(NativeMethodException.class);
		fgJDIExceptions.add(ObjectCollectedException.class);
		fgJDIExceptions.add(TimeoutException.class);
		fgJDIExceptions.add(VMDisconnectedException.class);
		fgJDIExceptions.add(VMMismatchException.class);
		fgJDIExceptions.add(VMOutOfMemoryException.class);
		fgJDIExceptions.add(DuplicateRequestException.class);
		fgJDIExceptions.add(InvalidRequestStateException.class);
		fgJDIExceptions.add(OperationRefusedException.class);
	}
	
	/**
	 * Creates a JDI debug element associated with the
	 * specified debug target.
	 * 
	 * @param target The associated debug target
	 */
	public JDIDebugElement(JDIDebugTarget target) {
		setDebugTarget(target);
	}

	/**
	 * Convenience method to log internal errors
	 */
	protected static void logError(Exception e) {
		JDIDebugPlugin.logError(e);
	}
	
	/**
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (adapter == IDebugElement.class) {
			return this;
		}			
		return super.getAdapter(adapter);
	}
	
	/**
	 * @see org.eclipse.debug.core.model.IDebugElement#getModelIdentifier()
	 */
	public String getModelIdentifier() {
		return JDIDebugModel.getPluginIdentifier();
	}
	
	/**
	 * Fires a debug event marking the creation of this element.
	 */
	protected void fireCreationEvent() {
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	/**
	 * Fires a debug event
	 * 
	 * @param event The debug event to be fired to the listeners
	 * @see org.eclipse.debug.core.DebugEvent
	 */
	protected void fireEvent(DebugEvent event) {
		DebugPlugin.getDefault().fireDebugEvent(event);
	}

	/**
	 * Fires a debug event marking the RESUME of this element with
	 * the associated detail.
	 * 
	 * @param detail The int detail of the event
	 * @see org.eclipse.debug.core.DebugEvent
	 */
	protected void fireResumeEvent(int detail) {
		fireEvent(new DebugEvent(this, DebugEvent.RESUME, detail));
	}

	/**
	 * Fires a debug event marking the SUSPEND of this element with
	 * the associated detail.
	 * 
	 * @param detail The int detail of the event
	 * @see org.eclipse.debug.core.DebugEvent
	 */
	protected void fireSuspendEvent(int detail) {
		fireEvent(new DebugEvent(this, DebugEvent.SUSPEND, detail));
	}
	
	/**
	 * Fires a debug event marking the termination of this element.
	 */
	protected void fireTerminateEvent() {
		fireEvent(new DebugEvent(this, DebugEvent.TERMINATE));
	}

	/**
	 * Fires a debug event marking the CHANGE of this element.
	 */
	protected void fireChangeEvent() {
		fireEvent(new DebugEvent(this, DebugEvent.CHANGE));
	}
	
	/**
	 * Throws a new debug exception with a status code of <code>REQUEST_FAILED</code>.
	 * 
	 * @param message Failure message
	 * @param e Exception that has occurred (<code>can be null</code>)
	 * @throws DebugException The exception with a status code of <code>REQUEST_FAILED</code>
	 */
	protected void requestFailed(String message,  Exception e) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.REQUEST_FAILED, message, e));	
	}
	
	/**
	 * Throws a new debug exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 * with the given underlying exception. If the underlyign exception is not a JDI
	 * exception, the original exception is thrown.
	 * 
	 * @param message Failure message
	 * @param e underlying exception that has occurred
	 * @throws DebugException The exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 */
	protected void targetRequestFailed(String message, RuntimeException e) throws DebugException {
		if (e == null || fgJDIExceptions.contains(e.getClass())) {
			throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
				DebugException.TARGET_REQUEST_FAILED, message, e));
		} else {
			throw e;
		}
	}
	
	/**
	 * Throws a new debug exception with a status code of <code>TARGET_REQUEST_FAILED</code>.
	 * 
	 * @param message Failure message
	 * @param e Throwable that has occurred
	 * @throws DebugException The exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 */
	protected void targetRequestFailed(String message, Throwable e) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.TARGET_REQUEST_FAILED, message, e));
	}
	
	/**
	 * Throws a new debug exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 * with the given underlying exception. The underlying exception is an exception thrown
	 * by a JDI request.
	 * 
	 * @param message Failure message
	 * @param e runtime exception that has occurred
	 * @throws DebugException the exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 */
	protected void jdiRequestFailed(String message, RuntimeException e) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.TARGET_REQUEST_FAILED, message, e));
	}
	
	/**
	 * Throws a new debug exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 * with the given underlying exception. The underlying exception is an exception thrown
	 * by a JDI request.
	 * 
	 * @param message Failure message
	 * @param e throwable exception that has occurred
	 * @throws DebugException the exception with a status code of <code>TARGET_REQUEST_FAILED</code>
	 */
	protected void jdiRequestFailed(String message, Throwable e) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.TARGET_REQUEST_FAILED, message, e));
	}	
	
	/**
	 * Throws a new debug exception with a status code of <code>NOT_SUPPORTED</code>.
	 * 
	 * @param message Failure message
	 * @throws DebugException The exception with a status code of <code>NOT_SUPPORTED</code>.
	 */
	protected void notSupported(String message) throws DebugException {
		throw new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.NOT_SUPPORTED, message, null));
	}
	
	/**
	 * Logs the given exception if it is a JDI exception, otherwise throws the 
	 * runtime exception.
	 * 
	 * @param e The internal runtime exception
	 */
	protected void internalError(RuntimeException e) {
		if (fgJDIExceptions.contains(e.getClass())) {
			logError(e);
		} else {
			throw e;
		}
	}
	
	/**
	 * Logs a debug exception with the given message,
	 * with a status code of <code>INTERNAL_ERROR</code>.
	 * 
	 * @param message The internal error message
	 */
	protected void internalError(String message) {
		logError(new DebugException(new Status(IStatus.ERROR, JDIDebugModel.getPluginIdentifier(),
			DebugException.INTERNAL_ERROR, message, null)));
	}

	/**
	 * Returns the common "<unknown>" message.
	 * 
	 * @return the unknown String
	 */
	protected String getUnknownMessage() {
		return JDIDebugModelMessages.getString("JDIDebugElement.unknown"); //$NON-NLS-1$
	}
	
	/**
	 * @see org.eclipse.debug.core.model.IDebugElement#getDebugTarget()
	 */
	public IDebugTarget getDebugTarget() {
		return fDebugTarget;
	}

	protected VirtualMachine getVM() {
		return ((JDIDebugTarget)getDebugTarget()).getVM();
	}
	
	/**
	 * Returns the underlying VM's event request manager.
	 * 
	 * @return event request manager
	 */
	protected EventRequestManager getEventRequestManager() {
		return getVM().eventRequestManager();
	}
	
	/**
	 * Adds the given listener to this target's event dispatcher's
	 * table of listeners for the specified event request. The listener
	 * will be notified each time the event occurrs.
	 * 
	 * @param listener the listener to register
	 * @param request the event request
	 */
	protected void addJDIEventListener(IJDIEventListener listener, EventRequest request) {
		((JDIDebugTarget)getDebugTarget()).getEventDispatcher().addJDIEventListener(listener, request);
	}
	
	/**
	 * Removes the given listener from this target's event dispatcher's
	 * table of listeners for the specifed event request. The listener
	 * will no longer be notified when the event occurrs. Listeners
	 * are responsible for deleting the event request if desired.
	 * 
	 * @param listener the listener to remove
	 * @param request the event request
	 */
	protected void removeJDIEventListener(IJDIEventListener listener, EventRequest request) {
		((JDIDebugTarget)getDebugTarget()).getEventDispatcher().removeJDIEventListener(listener, request);
	}
	
	/**
	 * @see org.eclipse.debug.core.model.IDebugElement#getLaunch()
	 */
	public ILaunch getLaunch() {
		ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
		return mgr.findLaunch(getDebugTarget());
	}
	
	protected void setDebugTarget(JDIDebugTarget debugTarget) {
		fDebugTarget = debugTarget;
	}

	/**
	 * The VM has disconnected. Notify the target.
	 */
	protected void disconnected() {
		if (fDebugTarget != null) {
			fDebugTarget.disconnected();
		}
	}
}
