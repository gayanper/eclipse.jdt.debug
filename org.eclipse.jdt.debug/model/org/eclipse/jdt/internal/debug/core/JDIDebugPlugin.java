package org.eclipse.jdt.internal.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.Preferences.PropertyChangeEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.internal.core.ListenerList;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaBreakpointListener;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaHotCodeReplaceListener;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.hcr.JavaHotCodeReplaceManager;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;

/**
 * The plugin class for the JDI Debug Model plug-in.
 */

public class JDIDebugPlugin extends Plugin implements Preferences.IPropertyChangeListener {
	
	/**
	 * Status code indicating an unexpected internal error.
	 */
	public static final int INTERNAL_ERROR = 120;
	
	private static JDIDebugPlugin fgPlugin;
	
	/**
	 * Breakpoint listener list.
	 */
	private ListenerList fBreakpointListeners = null;
	
	/**
	 * Breakpoint notification types
	 */
	private static final int ADDING = 1;
	private static final int INSTALLED = 2;
	private static final int REMOVED = 3;
	
	/**
	 * Whether this plug-in is in trace mode.
	 * Extra messages are logged in trace mode.
	 */
	private boolean fTrace = false;
	
	/**
	 * Detected (speculated) JDI interface version
	 */
	private static float fJDIVersion;
	
	/**
	 * Returns whether the debug UI plug-in is in trace
	 * mode.
	 * 
	 * @return whether the debug UI plug-in is in trace
	 *  mode
	 */
	public boolean isTraceMode() {
		return fTrace;
	}
	
	/**
	 * Logs the given message if in trace mode.
	 * 
	 * @param String message to log
	 */
	public static void logTraceMessage(String message) {
		if (getDefault().isTraceMode()) {
			IStatus s = new Status(IStatus.WARNING, JDIDebugPlugin.getUniqueIdentifier(), INTERNAL_ERROR, message, null);
			getDefault().getLog().log(s);
		}
	}	
	
	/**
	 * Return the singleton instance of the JDI Debug Model plug-in.  
	 * @return the singleton instance of JDIDebugPlugin
	 */
	public static JDIDebugPlugin getDefault() {
		return fgPlugin;
	}
	
	/**
	 * Convenience method which returns the unique identifier of this plugin.
	 */
	public static String getUniqueIdentifier() {
		if (getDefault() == null) {
			// If the default instance is not yet initialized,
			// return a static identifier. This identifier must
			// match the plugin id defined in plugin.xml
			return "org.eclipse.jdt.debug"; //$NON-NLS-1$
		}
		return getDefault().getDescriptor().getUniqueIdentifier();
	}
	
	/**
	 * Returns the detected version of JDI support. This
	 * is intended to distinguish between clients that support
	 * JDI 1.4 methods like hot code replace.
	 * 
	 * @since 2.1
	 */
	public static float getJDIVersion() {
		return fJDIVersion;
	}
		
	public JDIDebugPlugin(IPluginDescriptor descriptor) {
		super(descriptor);	
		fgPlugin = this;
	}
	
	/**
	 * @see Plugin#startup()
	 */
	public void startup() throws CoreException {
		fJDIVersion= (float)1.4;
		try {
			// JDI clients before version 1.4 do not support
			// hot code replace.
			Class clazz = Class.forName("com.sun.jdi.VirtualMachine"); //$NON-NLS-1$
			clazz.getMethod("canRedefineClasses", new Class[0]); //$NON-NLS-1$
		} catch (NoSuchMethodException e) {
			fJDIVersion= (float)1.3;
		} catch (ClassNotFoundException e) {
		}	
		JavaHotCodeReplaceManager.getDefault().startup();
		fBreakpointListeners = new ListenerList(5);
		getPluginPreferences().setDefault(JDIDebugModel.PREF_REQUEST_TIMEOUT, JDIDebugModel.DEF_REQUEST_TIMEOUT);
		getPluginPreferences().setDefault(JDIDebugModel.PREF_HCR_WITH_COMPILATION_ERRORS, true);
		getPluginPreferences().addPropertyChangeListener(this);
	}
	
	/**
	 * Adds the given hot code replace listener to the collection of listeners
	 * that will be notified by the hot code replace manager in this plugin.
	 */
	public void addHotCodeReplaceListener(IJavaHotCodeReplaceListener listener) {
		JavaHotCodeReplaceManager.getDefault().addHotCodeReplaceListener(listener);
	}

	/**
	 * Removes the given hot code replace listener from the collection of listeners
	 * that will be notified by the hot code replace manager in this plugin.
	 */	
	public void removeHotCodeReplaceListener(IJavaHotCodeReplaceListener listener) {
		JavaHotCodeReplaceManager.getDefault().removeHotCodeReplaceListener(listener);
	}

	/**
	 * Shutdown the HCR mgr and the Java debug targets.
	 * 
	 * @see Plugin#shutdown()
	 */
	public void shutdown() throws CoreException {
		getPluginPreferences().removePropertyChangeListener(this);
		savePluginPreferences();
		JavaHotCodeReplaceManager.getDefault().shutdown();
		ILaunchManager launchManager= DebugPlugin.getDefault().getLaunchManager();
		IDebugTarget[] targets= launchManager.getDebugTargets();
		for (int i= 0 ; i < targets.length; i++) {
			IDebugTarget target= targets[i];
			if (target instanceof JDIDebugTarget) {
				((JDIDebugTarget)target).shutdown();
			}
		}
		fBreakpointListeners = null;

		fgPlugin = null;
		super.shutdown();
	}
	
	/**
	 * Logs the specified throwable with this plug-in's log.
	 * 
	 * @param t throwable to log 
	 */
	public static void log(Throwable t) {
		Throwable top= t;
		if (t instanceof DebugException) {
			DebugException de = (DebugException)t;
			IStatus status = de.getStatus();
			if (status.getException() != null) {
				top = status.getException();
			}
		} 
		// this message is intentionally not internationalized, as an exception may
		// be due to the resource bundle itself
		log(new Status(IStatus.ERROR, getUniqueIdentifier(), INTERNAL_ERROR, "Internal error logged from JDI Debug: ", top));  //$NON-NLS-1$		
	}
	
	/**
	 * Logs the given message if in debug mode.
	 * 
	 * @param String message to log
	 */
	public static void logDebugMessage(String message) {
		if (getDefault().isDebugging()) {
			// this message is intentionally not internationalized, as an exception may
			// be due to the resource bundle itself
			log(new Status(IStatus.ERROR, getUniqueIdentifier(), INTERNAL_ERROR, "Internal message logged from JDI Debug: " + message, null));  //$NON-NLS-1$		
		}
	}
	
	/**
	 * Logs the specified status with this plug-in's log.
	 * 
	 * @param status status to log
	 */
	public static void log(IStatus status) {
		getDefault().getLog().log(status);
	}
		
	/**
	 * @see IJavaBreakpointListener#breakpointHasRuntimeException(IJavaLineBreakpoint, DebugException)
	 */
	public void fireBreakpointHasCompilationErrors(IJavaLineBreakpoint breakpoint, Message[] errors) {
		Object listeners[]= fBreakpointListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			((IJavaBreakpointListener)listeners[i]).breakpointHasCompilationErrors(breakpoint, errors);
		}
	}
	
	/**
	 * @see IJavaBreakpointListener#breakpointHasCompilationErrors(IJavaLineBreakpoint, Message[])
	 */
	public void fireBreakpointHasRuntimeException(IJavaLineBreakpoint breakpoint, DebugException exception) {
		Object listeners[]= fBreakpointListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			((IJavaBreakpointListener)listeners[i]).breakpointHasRuntimeException(breakpoint, exception);
		}
	}
	
	/**
	 * Adds the given breakpoint listener to the JDI debug model.
	 * 
	 * @param listener breakpoint listener
	 */
	public void addJavaBreakpointListener(IJavaBreakpointListener listener) {
		fBreakpointListeners.add(listener);
	}	

	/**
	 * Removes the given breakpoint listener from the JDI debug model.
	 * 
	 * @param listener breakpoint listener
	 */
	public void removeJavaBreakpointListener(IJavaBreakpointListener listener) {
		fBreakpointListeners.remove(listener);
	}
	
	/**
	 * Notifies listeners that the given breakpoint is about to be
	 * added.
	 * 
	 * @param target Java debug target
	 * @param breakpoint Java breakpoint
	 */
	public void fireBreakpointAdding(IJavaDebugTarget target, IJavaBreakpoint breakpoint) {
		notify(target, breakpoint, ADDING);
	}
	
	/**
	 * Notifies listeners that the given breakpoint has been installed.
	 * 
	 * @param target Java debug target
	 * @param breakpoint Java breakpoint
	 */
	public void fireBreakpointInstalled(IJavaDebugTarget target, IJavaBreakpoint breakpoint) {
		notify(target, breakpoint, INSTALLED);
	}	
	
	/**
	 * Notifies listeners that the given breakpoint has been removed.
	 * 
	 * @param target Java debug target
	 * @param breakpoint Java breakpoint
	 */
	public void fireBreakpointRemoved(IJavaDebugTarget target, IJavaBreakpoint breakpoint) {
		notify(target, breakpoint, REMOVED);
	}
		
	/**
	 * Notifies listeners of the given addition, install, or
	 * remove.
	 * 
	 * @param target debug target
	 * @param breakpoint the associated breakpoint
	 * @param kind one of ADDED, REMOVED, INSTALLED
	 */
	protected void notify(IJavaDebugTarget target, IJavaBreakpoint breakpoint, int kind) {
		Object[] listeners = fBreakpointListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			IJavaBreakpointListener jbpl = (IJavaBreakpointListener)listeners[i];
			switch (kind) {
				case ADDING:
					jbpl.addingBreakpoint(target, breakpoint);
					break;
				case INSTALLED:
					jbpl.breakpointInstalled(target, breakpoint);
					break;
				case REMOVED:
					jbpl.breakpointRemoved(target, breakpoint);
					break;					
			}
		}
	}
	
	/**
	 * Notifies listeners that the given breakpoint has been hit.
	 * Returns whether the thread should suspend.
	 * 
	 * @param target Java debug target
	 * @param breakpoint Java breakpoint
	 */
	public boolean fireBreakpointHit(IJavaThread thread, IJavaBreakpoint breakpoint) {
		Object[] listeners = fBreakpointListeners.getListeners();
		boolean suspend = listeners.length == 0;
		for (int i = 0; i < listeners.length; i++) {
			IJavaBreakpointListener jbpl = (IJavaBreakpointListener)listeners[i];
			suspend = suspend | jbpl.breakpointHit(thread, breakpoint);
		}	
		return suspend;
	}
	
	/**
	 * Notifies listeners that the given breakpoint is about to be installed
	 * in the given type. Returns whether the breakpoint should be
	 * installed.
	 * 
	 * @param target Java debug target
	 * @param breakpoint Java breakpoint
	 * @param type the type the breakpoint is about to be installed in
	 * @return whether the breakpoint should be installed
	 */
	public boolean fireInstalling(IJavaDebugTarget target, IJavaBreakpoint breakpoint, IJavaType type) {
		Object[] listeners = fBreakpointListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			IJavaBreakpointListener jbpl = (IJavaBreakpointListener)listeners[i];
			if (!jbpl.installingBreakpoint(target, breakpoint, type)) {
				return false;
			}
		}	
		return true;
	}	
	
	/**
	 * Save preferences and update all debug targets when the timeout changes.
	 * 
	 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(JDIDebugModel.PREF_REQUEST_TIMEOUT)) {
			savePluginPreferences();
			int value = getPluginPreferences().getInt(JDIDebugModel.PREF_REQUEST_TIMEOUT);
			IDebugTarget[] targets = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
			for (int i = 0; i < targets.length; i++) {
				if (targets[i] instanceof IJavaDebugTarget) {
					((IJavaDebugTarget)targets[i]).setRequestTimeout(value);
				}
			}
		}
	}

}