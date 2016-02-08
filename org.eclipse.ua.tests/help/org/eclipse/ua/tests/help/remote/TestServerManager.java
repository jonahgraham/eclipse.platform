/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ua.tests.help.remote;

import org.eclipse.core.runtime.CoreException;

/**
 * Class which allows two servers to be started independently
 */

public class TestServerManager {

	private static JettyTestServer[] server = new JettyTestServer[2];
	private static boolean serverRunning[] = new boolean[] { false, false };

	private static JettyTestServer getHelpServer(int index) {
		if (server[index] == null) {
			server[index] = new JettyTestServer();
		}
		return server[index];
	}

	public static void start(String webappName, int index) throws Exception {
		if (!serverRunning[index]) {
			getHelpServer(index).start(webappName);
			serverRunning[index] = true;
		}
	}

	public static void stop(String webappName, int index) throws CoreException {
		if (serverRunning[index]) {
		    getHelpServer(index).stop(webappName);
		    serverRunning[index] = false;
		}
	}

	public static int getPort(int index) {
		return getHelpServer(index).getPort();
	}

	public static String getHost(int index) {
        return getHelpServer(index).getHost();
	}

}
