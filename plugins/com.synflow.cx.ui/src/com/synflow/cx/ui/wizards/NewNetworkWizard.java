/*******************************************************************************
 * Copyright (c) 2013-2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cx.ui.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;

/**
 * This class provides a wizard to create a new network design.
 * 
 * @author Matthieu Wipliez
 */
public class NewNetworkWizard extends NewFileWizard {

	public static final String WIZARD_ID = "com.synflow.cx.ui.wizards.newNetwork";

	@Override
	public void addPages() {
		NewFilePage page = new NewNetworkPage("NewNetwork", selection);
		page.setDescription("Creates a new Cx network.");
		addPage(page);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		super.init(workbench, selection);
		setWindowTitle("New Cx network");
	}

}
