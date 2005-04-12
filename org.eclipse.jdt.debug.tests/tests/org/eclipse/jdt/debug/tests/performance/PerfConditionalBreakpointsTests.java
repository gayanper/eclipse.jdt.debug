/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests.performance;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.tests.AbstractDebugPerformanceTest;
import org.eclipse.test.performance.Dimension;

/**
 * Tests performance of conditional breakpoints.
 */
public class PerfConditionalBreakpointsTests extends AbstractDebugPerformanceTest {
    private String fTypeName = "PerfLoop";
    private int fHitCount = 0;
    private IJavaLineBreakpoint fBP;
    private IJavaDebugTarget fTarget;
    private Exception fException;
    
    private boolean fConditionalBreakpointSet = false;
    private boolean fWarmUpComplete = false;
    private int fWarmUpRuns = 2;
    private int fMeasuredRuns = 10;

    private class BreakpointListener implements IDebugEventSetListener {
        public void handleDebugEvents(DebugEvent[] events) {
            for (int i = 0; i < events.length; i++) {
                DebugEvent event = events[i];
                if (event.getKind() == DebugEvent.SUSPEND && event.getDetail() == DebugEvent.BREAKPOINT) {
                    IJavaThread source = (IJavaThread) event.getSource();
                    breakpointHit(source);
                }
            }
        }
    };

    public PerfConditionalBreakpointsTests(String name) {
        super(name);
    }

    public void testConditionalBreakpoints() throws Exception {
        tagAsGlobalSummary("Conditional Breakpoint Test", Dimension.ELAPSED_PROCESS);
        // just in case
        removeAllBreakpoints();

        fBP = createLineBreakpoint(22, fTypeName);

        BreakpointListener listener = new BreakpointListener();
		DebugPlugin.getDefault().addDebugEventListener(listener);
        ILaunchConfiguration config = getLaunchConfiguration(fTypeName);
        fTarget = launchAndTerminate(config, 5 * 60 * 1000);
        
        if(fException != null) {
            throw fException;
        }
        
        commitMeasurements();
        assertPerformance();
        
        DebugPlugin.getDefault().removeDebugEventListener(listener);
        removeAllBreakpoints();
    }

    private void breakpointHit(final IJavaThread thread) {
        try {
            if (!fConditionalBreakpointSet) {
                fBP.delete();
                fBP = createConditionalLineBreakpoint(22, fTypeName, "i%100==0", true);
                fConditionalBreakpointSet = true;
            } else if (!fWarmUpComplete) {
                fHitCount++;
                if (fHitCount == fWarmUpRuns) {
                    fWarmUpComplete = true;
                    fHitCount =  0;
                }
                return;
            } else {
                if (fHitCount > 0) {
                    stopMeasuring();
                }
                fHitCount++;
                if (fHitCount <= fMeasuredRuns) {
                    startMeasuring();
                } else {
                	new Job("Breakpoint Delete Job") {
						protected IStatus run(IProgressMonitor monitor) {
							try {
								fBP.delete();
								thread.terminate(); 
							} catch (CoreException e) {
								fException = e;								
							}
							return Status.OK_STATUS;
						}
                		
                	}.schedule();                   
                }
            }
        } catch (Exception e) {
            fException = e;
            removeAllBreakpoints();
        } finally {
            try {
            	if (thread.canResume()) {
            		thread.resume();
            	}
            } catch (DebugException e) {
            	e.printStackTrace();
                fException = e;
            }
        }
    }
}
