/*******************************************************************************
 * Copyright (c) 2003, 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *    Matthieu Wipliez (Synflow SAS) - modified
 *******************************************************************************/
package com.synflow.ngDesign.ui.internal.navigator;

import java.util.Iterator;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.TextActionHandler;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonMenuConstants;

import com.synflow.core.layout.ITreeElement;
import com.synflow.ngDesign.ui.internal.navigator.actions.CopyAction;
import com.synflow.ngDesign.ui.internal.navigator.actions.DeleteAction;
import com.synflow.ngDesign.ui.internal.navigator.actions.PasteAction;

/**
 * This class describes an edit action provider.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class EditActionProvider extends CommonActionProvider {

	private Clipboard clipboard;

	private CopyAction copyAction;

	private DeleteAction deleteAction;

	private PasteAction pasteAction;

	private Shell shell;

	private TextActionHandler textActionHandler;

	@Override
	public void dispose() {
		if (clipboard != null) {
			clipboard.dispose();
			clipboard = null;
		}

		super.dispose();
	}

	@Override
	public void fillActionBars(IActionBars actionBars) {
		if (textActionHandler == null) {
			textActionHandler = new TextActionHandler(actionBars); // hook handlers
		}

		textActionHandler.setCopyAction(copyAction);
		textActionHandler.setPasteAction(pasteAction);
		textActionHandler.setDeleteAction(deleteAction);

		updateActionBars();

		textActionHandler.updateActionBars();
	}

	@Override
	public void fillContextMenu(IMenuManager menu) {
		IStructuredSelection selection = (IStructuredSelection) getContext().getSelection();

		boolean canCopy = true, canPaste = true, canDelete = true;
		Iterator<?> it = selection.iterator();
		while (it.hasNext()) {
			Object obj = it.next();
			if (obj instanceof ITreeElement) {
				// cannot copy a package or source folder
				canCopy = false;

				// cannot paste in or delete source folder
				ITreeElement element = (ITreeElement) obj;
				if (element.isSourceFolder()) {
					canPaste = canDelete = false;
				}
			}
		}

		if (canCopy) {
			copyAction.selectionChanged(selection);
			menu.appendToGroup(ICommonMenuConstants.GROUP_EDIT, copyAction);
		}

		if (canPaste) {
			pasteAction.selectionChanged(selection);
			menu.appendToGroup(ICommonMenuConstants.GROUP_EDIT, pasteAction);
		}

		if (canDelete) {
			deleteAction.selectionChanged(selection);
			menu.appendToGroup(ICommonMenuConstants.GROUP_EDIT, deleteAction);
		}
	}

	@Override
	public void init(ICommonActionExtensionSite site) {
		shell = site.getViewSite().getShell();

		makeActions();
	}

	protected void makeActions() {
		clipboard = new Clipboard(shell.getDisplay());

		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

		IShellProvider sp = new IShellProvider() {
			@Override
			public Shell getShell() {
				return shell;
			}
		};

		copyAction = new CopyAction(shell, clipboard);
		copyAction.setDisabledImageDescriptor(images
				.getImageDescriptor(ISharedImages.IMG_TOOL_COPY_DISABLED));
		copyAction.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
		copyAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);

		pasteAction = new PasteAction(shell, clipboard);
		pasteAction.setDisabledImageDescriptor(images
				.getImageDescriptor(ISharedImages.IMG_TOOL_PASTE_DISABLED));
		pasteAction.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_PASTE));
		pasteAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_PASTE);

		deleteAction = new DeleteAction(sp);
		deleteAction.setDisabledImageDescriptor(images
				.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE_DISABLED));
		deleteAction.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));
		deleteAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_DELETE);
	}

	@Override
	public void updateActionBars() {
		IStructuredSelection selection = (IStructuredSelection) getContext().getSelection();

		copyAction.selectionChanged(selection);
		pasteAction.selectionChanged(selection);
		deleteAction.selectionChanged(selection);
	}

}
