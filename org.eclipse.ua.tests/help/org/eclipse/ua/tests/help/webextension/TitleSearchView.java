/*******************************************************************************
 *  Copyright (c) 2009, 2015 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ua.tests.help.webextension;

import java.util.Locale;

import org.eclipse.core.runtime.Platform;
import org.eclipse.help.webapp.AbstractView;
import org.eclipse.ua.tests.plugin.UserAssistanceTestPlugin;

public class TitleSearchView extends AbstractView {

	@Override
	public String getImageURL() {
		return "/titlesearch/icons/sample3.gif";
	}

	@Override
	public char getKey() {
		return 0;
	}

	@Override
	public String getName() {
		return "titlesearch";
	}

	@Override
	public String getTitle(Locale locale) {
		if ("es".equals(locale.getLanguage())) {
			return "Busque en titulos";
		}
		return "Search Topic Title";
	}

	@Override
	public String getURL() {
		return "/titlesearch/jsp/advanced/";
	}

	@Override
	public String getBasicURL() {
		return "/titlesearch/jsp/basic/";
	}

	@Override
	public boolean isVisible() {
		return Platform.getPreferencesService().getBoolean
		    (UserAssistanceTestPlugin.getPluginId(), "extraView", false, null);
	}

	@Override
	public boolean isVisibleBasic() {
		return Platform.getPreferencesService().getBoolean
	        (UserAssistanceTestPlugin.getPluginId(), "extraView", false, null);
	}

}
