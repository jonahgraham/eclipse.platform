package org.eclipse.debug.internal.ui.views.console;

/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.actions.ClearOutputAction;
import org.eclipse.debug.internal.ui.actions.TextViewerAction;
import org.eclipse.debug.internal.ui.actions.TextViewerGotoLineAction;
import org.eclipse.debug.internal.ui.views.AbstractDebugEventHandlerView;
import org.eclipse.debug.internal.ui.views.DebugUIViewsMessages;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.console.*;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextStore;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.FindReplaceAction;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.IUpdate;

public class ConsoleView extends AbstractDebugEventHandlerView implements IDocumentListener, ISelectionListener {

	protected ClearOutputAction fClearOutputAction= null;

	protected Map fGlobalActions= new HashMap(10);
	protected List fSelectionActions = new ArrayList(3);
	
	protected IDocument fCurrentDocument= null;
	
	/**
	 * The current process being viewed, or <code>null</code.
	 */
	private IProcess fProcess;
	
	/**
	 * An empty document.
	 */
	class EmptyConsoleDocument extends ConsoleDocument {
		
		public EmptyConsoleDocument() {
			super(new ConsoleContentProvider());
		}
		
		protected ITextStore newTextStore() {
			return new ConsoleOutputTextStore(0);
		}
	}

	/**
	 * @see AbstractDebugView#createViewer(Composite)
	 */
	protected Viewer createViewer(Composite parent) {
		ConsoleViewer cv = new ConsoleViewer(parent);		
		cv.getSelectionProvider().addSelectionChangedListener(getSelectionChangedListener());
		cv.addTextInputListener(getTextInputListener());
		getSite().setSelectionProvider(cv.getSelectionProvider());
		
		// listen to selection changes in the debug view
		getSite().getPage().addSelectionListener(IDebugUIConstants.ID_DEBUG_VIEW, this);
		
		setEventHandler(new ConsoleViewEventHandler(this));
		//initialize the default instance
		DebugUIPlugin.getConsoleDocumentManager();
		return cv;
	}
	
	/**
	 * @see AbstractDebugView#getHelpContextId()
	 */
	protected String getHelpContextId() {
		return IDebugHelpContextIds.CONSOLE_VIEW;
	}
	
	/**
	 * Sets the input to the current process if no debug view is present
	 * on the current active page or if the current process is <code>null</code>.
	 */
	public void setViewerInputFromConsoleDocumentManager(IProcess process) {
		IViewPart debugView= findView(IDebugUIConstants.ID_DEBUG_VIEW);
		if (debugView == null || process == null) {
			setViewerInput(process);
		}
	}
	
	/** 
	 * Sets the console to view the document's streams
	 * associated with the given process.
	 */
	public void setViewerInput(IProcess process) {
		if (!isAvailable()) {
			return;
		}
		if (getProcess() == process) {
			// do nothing if the input is the same as what is
			// being viewed. If this is the first input, set
			// the console to an empty document
			if (getConsoleViewer().getDocument() == null) {
				getConsoleViewer().setDocument(new EmptyConsoleDocument());
				getConsoleViewer().setEditable(false);
				updateObjects();
			}			
			return;
		}
		
		setProcess(process)	;
		
		Runnable r = new Runnable() {
			public void run() {
				if (!isAvailable()) {
					return;
				}
				IDocument doc = null;
				if (getProcess() != null) {
					doc = DebugUIPlugin.getConsoleDocumentManager().getConsoleDocument(getProcess());
				}
				if (doc == null) {
					doc = new EmptyConsoleDocument();
				}
				getConsoleViewer().setDocument(doc);
				getConsoleViewer().setEditable(getProcess() != null && !getProcess().isTerminated());
				updateTitle();
				updateObjects();
				updateSelectionDependentActions();
			}
		};
		asyncExec(r);
	}
	
	protected void updateTitle() {
		// update view title
		String title = null;
		if (getProcess() == null) { 
			title = DebugUIViewsMessages.getString("ConsoleView.Console_1"); //$NON-NLS-1$
		} else {
			// use debug target title if applicable
			Object obj = getProcess().getAdapter(IDebugTarget.class);
			if (obj == null) {
				obj = getProcess();
			}
			StringBuffer buff= new StringBuffer(DebugUIViewsMessages.getString("ConsoleView.Console_1")); //$NON-NLS-1$
			buff.append(" ["); //$NON-NLS-1$
			buff.append(DebugUIPlugin.getModelPresentation().getText(obj));
			buff.append(']');
			title= buff.toString();
		}
		setTitle(title);
	}
		
	/**
	 * @see AbstractDebugView#createActions()
	 */
	protected void createActions() {
		fClearOutputAction= new ClearOutputAction(getConsoleViewer());
		
		// In order for the clipboard actions to accessible via their shortcuts
		// (e.g., Ctrl-C, Ctrl-V), we *must* set a global action handler for
		// each action		
		IActionBars actionBars= getViewSite().getActionBars();
		TextViewerAction action= new TextViewerAction(getTextViewer(), TextViewer.CUT);
		action.configureAction(DebugUIViewsMessages.getString("ConsoleView.Cu&t@Ctrl+X_3"), DebugUIViewsMessages.getString("ConsoleView.Cut_4"), DebugUIViewsMessages.getString("ConsoleView.Cut_4")); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		setGlobalAction(actionBars, ITextEditorActionConstants.CUT, action);
		action= new TextViewerAction(getTextViewer(), TextViewer.COPY);
		action.configureAction(DebugUIViewsMessages.getString("ConsoleView.&Copy@Ctrl+C_6"), DebugUIViewsMessages.getString("ConsoleView.Copy_7"), DebugUIViewsMessages.getString("ConsoleView.Copy_7")); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		setGlobalAction(actionBars, ITextEditorActionConstants.COPY, action);
		action= new TextViewerAction(getTextViewer(), TextViewer.PASTE);
		action.configureAction(DebugUIViewsMessages.getString("ConsoleView.&Paste@Ctrl+V_9"), DebugUIViewsMessages.getString("ConsoleView.Paste_10"), DebugUIViewsMessages.getString("ConsoleView.Paste_Clipboard_Text_11")); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		setGlobalAction(actionBars, ITextEditorActionConstants.PASTE, action);
		action= new TextViewerAction(getTextViewer(), TextViewer.SELECT_ALL);
		action.configureAction(DebugUIViewsMessages.getString("ConsoleView.Select_&All@Ctrl+A_12"), DebugUIViewsMessages.getString("ConsoleView.Select_All"), DebugUIViewsMessages.getString("ConsoleView.Select_All")); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
		setGlobalAction(actionBars, ITextEditorActionConstants.SELECT_ALL, action);
		
		//XXX Still using "old" resource access
		ResourceBundle bundle= ResourceBundle.getBundle("org.eclipse.debug.internal.ui.views.DebugUIViewsMessages"); //$NON-NLS-1$
		setGlobalAction(actionBars, ITextEditorActionConstants.FIND, new FindReplaceAction(bundle, "find_replace_action.", this)); //$NON-NLS-1$
	
		action= new TextViewerGotoLineAction(getConsoleViewer());
		setGlobalAction(actionBars, ITextEditorActionConstants.GOTO_LINE, action);				
		actionBars.updateActionBars();
		
		getConsoleViewer().getTextWidget().addVerifyKeyListener(new VerifyKeyListener() {
			public void verifyKey(VerifyEvent event) {
				if (event.stateMask == SWT.CTRL && event.keyCode == 0 && event.character == 0x0C) {
					IAction gotoLine= (IAction)fGlobalActions.get(ITextEditorActionConstants.GOTO_LINE);
					if (gotoLine.isEnabled()) {
						gotoLine.run();
						event.doit= false;
					}
				}
			}
		});
		
		fSelectionActions.add(ITextEditorActionConstants.CUT);
		fSelectionActions.add(ITextEditorActionConstants.COPY);
		fSelectionActions.add(ITextEditorActionConstants.PASTE);
				
		// initialize input, after viewer has been created
		setViewerInput(DebugUITools.getCurrentProcess());
	}

	protected void setGlobalAction(IActionBars actionBars, String actionID, IAction action) {
		fGlobalActions.put(actionID, action); 
		actionBars.setGlobalActionHandler(actionID, action);
	}
	
	/**
	 * @see AbstractDebugView#configureToolBar(IToolBarManager)
	 */
	protected void configureToolBar(IToolBarManager mgr) {
		mgr.add(new Separator(IDebugUIConstants.LAUNCH_GROUP));
		mgr.add(fClearOutputAction);
	}

	/**
	 * Adds the text manipulation actions to the <code>ConsoleViewer</code>
	 */
	protected void fillContextMenu(IMenuManager menu) {
		ConsoleDocument doc= (ConsoleDocument)getConsoleViewer().getDocument();
		if (doc == null) {
			return;
		}
		if (doc.isReadOnly()) {
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.COPY));
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.SELECT_ALL));						
		} else {
			updateAction(ITextEditorActionConstants.PASTE);
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.CUT));
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.COPY));
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.PASTE));
			menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.SELECT_ALL));
		}

		menu.add(new Separator("FIND")); //$NON-NLS-1$
		menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.FIND));
		menu.add((IAction)fGlobalActions.get(ITextEditorActionConstants.GOTO_LINE));
		menu.add(fClearOutputAction);
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}


	/**
	 * @see WorkbenchPart#getAdapter(Class)
	 */
	public Object getAdapter(Class required) {
		if (!isAvailable()) {
			return null;
		}
		if (IFindReplaceTarget.class.equals(required)) {
			return getConsoleViewer().getFindReplaceTarget();
		}
		if (Widget.class.equals(required)) {
			return getConsoleViewer().getTextWidget();
		}
		return super.getAdapter(required);
	}


	protected final ISelectionChangedListener getSelectionChangedListener() {
		return new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent event) {
					updateSelectionDependentActions();
				}
			};
	}
	
	protected final ITextInputListener getTextInputListener() {
		return new ITextInputListener() {
				public void inputDocumentAboutToBeChanged(IDocument old, IDocument nw) {
					if (old != null) {
						old.removeDocumentListener(ConsoleView.this);
					}
					fCurrentDocument = nw;
					if (nw != null) {
						nw.addDocumentListener(ConsoleView.this);
					}
				}
				public void inputDocumentChanged(IDocument doc, IDocument doc2) {
					updateAction(ITextEditorActionConstants.FIND);
				}
			};
	}

	protected void updateSelectionDependentActions() {
		Iterator iterator= fSelectionActions.iterator();
		while (iterator.hasNext()) {
			updateAction((String)iterator.next());		
		}
	}

	protected void updateAction(String actionId) {
		if (!isAvailable()) {
			return;
		}
		IAction action= (IAction)fGlobalActions.get(actionId);
		if (action instanceof IUpdate) {
			((IUpdate) action).update();
		}
	}
	
	public void dispose() {
		getSite().getPage().removeSelectionListener(IDebugUIConstants.ID_DEBUG_VIEW, this);
		if (getConsoleViewer() != null) {
			getConsoleViewer().dispose();
		}
		if (fCurrentDocument != null) {
			fCurrentDocument.removeDocumentListener(this);
		}
		super.dispose();
	}
	
	/**
	 * @see IDocumentListener#documentAboutToBeChanged(DocumentEvent)
	 */
	public void documentAboutToBeChanged(DocumentEvent e) {
	}

	/**
	 * @see IDocumentListener#documentChanged(DocumentEvent)
	 */
	public void documentChanged(DocumentEvent e) {
		updateAction(ITextEditorActionConstants.FIND);
	}

	public ConsoleViewer getConsoleViewer() {
		return (ConsoleViewer)getViewer();
	}
	
	/**
	 * Sets the process being viewed
	 * 
	 * @param process process or <code>null</code>
	 */
	private void setProcess(IProcess process) {
		fProcess = process;
	}
	
	/**
	 * Returns the process being viewed, or <code>null</code>
	 * 
	 * @return process
	 */
	public IProcess getProcess() {
		return fProcess;
	}
	
	/**
	 * @see ISelectionListener#selectionChanged(IWorkbenchPart, ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		setViewerInput(DebugUITools.getCurrentProcess());
	}
}