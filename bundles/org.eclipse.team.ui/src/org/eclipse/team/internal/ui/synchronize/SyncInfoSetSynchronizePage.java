/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.team.internal.ui.Policy;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.synchronize.actions.DirectionFilterActionGroup;
import org.eclipse.team.ui.synchronize.ISynchronizePage;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.ISynchronizePageSite;
import org.eclipse.team.ui.synchronize.SynchronizePageActionGroup;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.part.ShowInContext;

/**
 * Abstract synchronize page that populates the view using a sync info set
 * that is possibly filtered by a working set and a mode incoming, outgoing,
 * both or conflicting).
 * <p>
 * The particpant creating this page must set the particpant set property
 * of the page configuration before the page is created. Subclasses
 * should set the working set sync info set and output sync info set 
 * in the configuration. These sets are used by the page to display
 * appropriate messages when the view is empty.
 */
public abstract class SyncInfoSetSynchronizePage extends Page implements ISynchronizePage, IAdaptable {
	
	private ISynchronizePageConfiguration configuration;
	private ISynchronizePageSite site;
	
	// Parent composite of this view. It is remembered so that we can dispose of its children when 
	// the viewer type is switched.
	private Composite composite;
	private ChangesSection changesSection;
	private Viewer changesViewer;
	private StructuredViewerAdvisor viewerAdvisor;
	
	/*
	 * Contribute actions for changing modes to the page.
	 */
	class SyncInfoSetActions extends SynchronizePageActionGroup {
		private DirectionFilterActionGroup modes;
		public void initialize(ISynchronizePageConfiguration configuration) {
			super.initialize(configuration);
			if (isThreeWay()) {
				modes = new DirectionFilterActionGroup(configuration);
			}
		}
		public void fillActionBars(IActionBars actionBars) {
			super.fillActionBars(actionBars);
			if (modes == null) return;
			IToolBarManager manager = actionBars.getToolBarManager();
			IContributionItem group = findGroup(manager, ISynchronizePageConfiguration.MODE_GROUP);
			if (manager != null && group != null) {
				modes.fillToolBar(group.getId(), manager);
			}
			IMenuManager viewMenu = actionBars.getMenuManager();
			group = findGroup(manager, ISynchronizePageConfiguration.MODE_GROUP);
			if (viewMenu != null && group != null) {
				MenuManager modesItem = new MenuManager(Policy.bind("action.modes.label")); //$NON-NLS-1$
				viewMenu.appendToGroup(group.getId(), modesItem);	
				modes.fillMenu(modesItem);
			}
		}
		private boolean isThreeWay() {
			return ISynchronizePageConfiguration.THREE_WAY.equals(configuration.getComparisonType());
		}
	}
	
	/**
	 * Create a new instance of the page
	 * @param configuration a synchronize page configuration
	 */
	protected SyncInfoSetSynchronizePage(ISynchronizePageConfiguration configuration) {
		this.configuration = configuration;
		configuration.setPage(this);
		configuration.addActionContribution(new SyncInfoSetActions());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE); 
		//sc.setContent(composite);
		GridLayout gridLayout= new GridLayout();
		gridLayout.makeColumnsEqualWidth= false;
		gridLayout.marginWidth= 0;
		gridLayout.marginHeight = 0;
		gridLayout.verticalSpacing = 0;
		composite.setLayout(gridLayout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessVerticalSpace = true;
		composite.setLayoutData(data);
		
		// Create the changes section which, in turn, creates the changes viewer and its configuration
		this.changesSection = new ChangesSection(composite, this, configuration);
		this.changesViewer = createChangesViewer(changesSection.getComposite());
		changesSection.setViewer(changesViewer);
	}
	
	protected Viewer createChangesViewer(Composite parent) {
		viewerAdvisor = new TreeViewerAdvisor(parent, configuration);
		return viewerAdvisor.getViewer();
	}
	
	public StructuredViewerAdvisor getViewerAdvisor() {
		return viewerAdvisor;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#getControl()
	 */
	public Control getControl() {
		return composite;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setFocus()
	 */
	public void setFocus() {
		changesSection.setFocus();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizePage#init(org.eclipse.team.ui.synchronize.ISynchronizePageSite)
	 */
	public void init(ISynchronizePageSite site) {
		this.site = site;
		IDialogSettings settings = getSettings();
		if (settings != null) {
			try {
				int mode = settings.getInt(ISynchronizePageConfiguration.P_MODE);
				if (mode != 0) {
					configuration.setMode(mode);
				}
			} catch (NumberFormatException e) {
				// The mode settings does not exist.
				// Leave the mode as is (assuming the 
				// participant initialized it to an
				// appropriate value
			}
			String workingSetName = settings.get(ISynchronizePageConfiguration.P_WORKING_SET);
			if (workingSetName != null) {
				IWorkingSetManager manager = TeamUIPlugin.getPlugin().getWorkbench().getWorkingSetManager();
				IWorkingSet set = manager.getWorkingSet(workingSetName);
				configuration.setWorkingSet(set);
			} else {
				configuration.setWorkingSet(null);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IPage#setActionBars(org.eclipse.ui.IActionBars)
	 */
	public void setActionBars(IActionBars actionBars) {
		// Delegate menu creation to the advisor
		viewerAdvisor.setActionBars(actionBars);		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.Page#dispose()
	 */
	public void dispose() {
		changesSection.dispose();
		composite.dispose();
		super.dispose();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizePage#getViewer()
	 */
	public Viewer getViewer() {
		return changesViewer;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizePage#aboutToChangeProperty(org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration, java.lang.String, java.lang.Object)
	 */
	public boolean aboutToChangeProperty(
			ISynchronizePageConfiguration configuration, String key,
			Object newValue) {
		if (key.equals(ISynchronizePageConfiguration.P_MODE)) {
			return (internalSetMode(configuration.getMode(), ((Integer)newValue).intValue()));
		}
		if (key.equals(ISynchronizePageConfiguration.P_WORKING_SET)) {
			return (internalSetWorkingSet(configuration.getWorkingSet(), (IWorkingSet)newValue));
		}
		return true;
	}

	private boolean internalSetMode(int oldMode, int mode) {
		if(oldMode == mode) return false;
		updateMode(mode);
		IDialogSettings settings = getSettings();
		if (settings != null) {
			settings.put(ISynchronizePageConfiguration.P_MODE, mode);
		}
		return true;
	}

	private boolean internalSetWorkingSet(IWorkingSet oldSet, IWorkingSet workingSet) {
		if (workingSet == null && oldSet == null) return false;
		if (workingSet == null || !workingSet.equals(oldSet)) {
			updateWorkingSet(workingSet);
			IDialogSettings settings = getSettings();
			if (settings != null) {
				String name = null;
				if (workingSet != null) {
					name = workingSet.getName();
				}
				settings.put(ISynchronizePageConfiguration.P_WORKING_SET, name);
			}
			return true;
		}
		return false;
	}

	/*
	 * This method enables "Show In" support for this view
	 * 
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class key) {
		if (key.equals(ISelectionProvider.class))
			return changesViewer;
		if (key == IShowInSource.class) {
			return new IShowInSource() {
				public ShowInContext getShowInContext() {					
					StructuredViewer v = (StructuredViewer)changesViewer;
					if (v == null) return null;
					ISelection s = v.getSelection();
					if (s instanceof IStructuredSelection) {
						Object[] resources = Utils.getResources(((IStructuredSelection)s).toArray());
						return new ShowInContext(null, new StructuredSelection(resources));
					}
					return null;
				}
			};
		}
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { IPageLayout.ID_RES_NAV };
				}

			};
		}
		return null;
	}
	
	/**
	 * Return the page site that was assigned to this page.
	 * @return the page site that was assigned to this page
	 */
	public ISynchronizePageSite getSynchronizePageSite() {
		return site;
	}
	
	/**
	 * Return the synchronize page configuration that was used to create
	 * this page.
	 * @return Returns the configuration.
	 */
	public ISynchronizePageConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Return the settings for the page from the configuration
	 * os <code>null</code> if settings can not be persisted
	 * for the page
	 * @return the persisted page settings
	 */
	protected IDialogSettings getSettings() {
		return configuration.getSite().getPageSettings();
	}

	/**
	 * Callback from the changes section that indicates that the 
	 * user has chosen to reset the view contents after an error 
	 * has occurred
	 */
	public abstract void reset();
	
	/**
	 * Change the mode to the given mode. This method is invoked
	 * when the mode in the configuration is changed by a client.
	 * @param mode the mode to be used
	 */
	protected abstract void updateMode(int mode);
	
	/**
	 * Filter the view by the given working set. If the set is <code>null</code>
	 * then any existing working set filtering should be removed.
	 * @param workingSet a working set or <code>null</code>
	 */
	protected abstract void updateWorkingSet(IWorkingSet workingSet);
}
