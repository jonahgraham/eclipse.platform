package org.eclipse.update.internal.ui.manager;

import org.eclipse.update.internal.ui.parts.UpdateFormPage;
import org.eclipse.update.ui.forms.*;

public class MainPage extends UpdateFormPage {
	
	public MainPage(DetailsView view, String title) {
		super(view, title);
	}
	
	public Form createForm() {
		return new MainForm(this);
	}
}