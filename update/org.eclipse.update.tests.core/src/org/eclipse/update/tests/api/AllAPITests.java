package org.eclipse.update.tests.api;

import org.eclipse.update.tests.UpdateManagerTestCase;
import junit.framework.*;

/**
 * Manages the API tests
 */
public class AllAPITests extends UpdateManagerTestCase {
	/**
	 * Constructor
	 */
	public AllAPITests(String name) {
		super(name);
	}
	
	/**
	 * List of API tests
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.setName("API Tests");

		suite.addTest(new TestSuite(TestSiteAPI.class));
		suite.addTest(new TestSuite(TestDefaultExecutableFeatureAPI.class));
		suite.addTest(new TestSuite(TestDefaultPackageFeatureAPI.class));
		suite.addTest(new TestSuite(TestPluginContainerAPI.class));

		return suite;
	}
}