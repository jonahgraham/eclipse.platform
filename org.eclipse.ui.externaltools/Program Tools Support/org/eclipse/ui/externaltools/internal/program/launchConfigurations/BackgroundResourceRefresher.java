/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.externaltools.internal.program.launchConfigurations;


import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.ui.RefreshTab;
import org.eclipse.ui.externaltools.internal.model.ExternalToolsPlugin;

/**
 * Refreshes resources as specified by a launch configuration, when 
 * an associated process terminates.
 */
public class BackgroundResourceRefresher implements IDebugEventSetListener  {

	private ILaunchConfiguration fConfiguration;
	private IProcess fProcess;
	
	public BackgroundResourceRefresher(ILaunchConfiguration configuration, IProcess process) {
		fConfiguration = configuration;
		fProcess = process;
	}
	
	/**
	 * If the process has already terminated, resource refreshing is done
	 * immediately in the current thread. Otherwise, refreshing is done when the
	 * process terminates.
	 */
	public void startBackgroundRefresh() {
		synchronized (fProcess) {
			if (fProcess.isTerminated()) {
				refresh();
			} else {
				DebugPlugin.getDefault().addDebugEventListener(this);
			}
		}
	}
	
	/**
	 * @see org.eclipse.debug.core.IDebugEventSetListener#handleDebugEvents(org.eclipse.debug.core.DebugEvent)
	 */
	public void handleDebugEvents(DebugEvent[] events) {
		for (int i = 0; i < events.length; i++) {
			DebugEvent event = events[i];
			if (event.getSource() == fProcess && event.getKind() == DebugEvent.TERMINATE) {
				DebugPlugin.getDefault().removeDebugEventListener(this);
				refresh();
				break;
			}
		}
	}
	
	/**
	 * Submits a job to do the refresh
	 */
	protected void refresh() {
		Job job= new Job(ExternalToolsProgramMessages.getString("BackgroundResourceRefresher.0")) { //$NON-NLS-1$
			public IStatus run(IProgressMonitor monitor) {
				try {
					RefreshTab.refreshResources(fConfiguration, monitor);
				} catch (CoreException e) {
					ExternalToolsPlugin.getDefault().log(e);
					return e.getStatus();
				}	
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}