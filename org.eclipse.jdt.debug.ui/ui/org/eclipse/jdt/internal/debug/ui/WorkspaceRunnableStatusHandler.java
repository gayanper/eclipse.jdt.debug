package org.eclipse.jdt.internal.debug.ui;

/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

/**
 * Status handler that runs an <code>IWorkspaceRunnable</code> and shows the 
 * progress in a progress monitor dialog.
 */
public class WorkspaceRunnableStatusHandler implements IStatusHandler {

	/**
	 * @see org.eclipse.debug.core.IStatusHandler#handleStatus(org.eclipse.core.runtime.IStatus, java.lang.Object)
	 */
	public Object handleStatus(IStatus status, Object source) throws CoreException {
		
		// Verify we're being asked to run an IWorkspaceRunnable
		if (!(source instanceof IWorkspaceRunnable)) {
			return null;
		}
		final IWorkspaceRunnable runnable = (IWorkspaceRunnable) source;
		
		// Construct a progress monitor dialog and use it to run the runnable
		Runnable r = new Runnable() {
			public void run() {
				ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());						
				try {
					dialog.run(true, true, new WorkspaceModifyOperation() {
						public void execute(IProgressMonitor monitor) throws InvocationTargetException{
							try {
								runnable.run(monitor);
							} catch (CoreException ce) {
								throw new InvocationTargetException(ce);
							}
						}
					});
				} catch (InterruptedException ie) {
					// operation canceled by user
				} catch (InvocationTargetException ite) {
					ExceptionHandler.handle(ite, getShell(), LauncherMessages.getString("VMPreferencePage.Installed_JREs_1"), LauncherMessages.getString("VMPreferencePage.Build_failed._1")); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		};
		JDIDebugUIPlugin.getStandardDisplay().asyncExec(r);
		
		return null;
	}
	
	private Shell getShell() {
		return JDIDebugUIPlugin.getActiveWorkbenchShell();
	}

}
