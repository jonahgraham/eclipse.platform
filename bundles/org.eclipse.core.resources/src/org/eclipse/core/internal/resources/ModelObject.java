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
package org.eclipse.core.internal.resources;

public abstract class ModelObject implements Cloneable {
	protected String name;
public ModelObject() {
}
public ModelObject(String name) {
	setName(name);
}
public Object clone() {
	try {
		return super.clone();
	} catch (CloneNotSupportedException e) {
		return null; // won't happen
	}
}
public String getName() {
	return name;
}
public void setName(String value) {
	name = value;
}
}
