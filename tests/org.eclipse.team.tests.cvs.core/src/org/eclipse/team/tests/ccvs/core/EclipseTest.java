package org.eclipse.team.tests.ccvs.core;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.tests.harness.EclipseWorkspaceTest;
import org.eclipse.team.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.CVSTeamProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProvider;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Import;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Command.GlobalOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.resources.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.resources.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.LocalFile;
import org.eclipse.team.internal.ccvs.core.resources.LocalResource;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFile;
import org.eclipse.team.internal.ccvs.core.resources.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.resources.Synchronizer;

public class EclipseTest extends EclipseWorkspaceTest {

	protected static IProgressMonitor DEFAULT_MONITOR = new NullProgressMonitor();
	protected static final int RANDOM_CONTENT_SIZE = 3876;
				
	/**
	 * Constructor for CVSBlackBoxTest.
	 */
	public EclipseTest() {
		super();
	}
	public EclipseTest(String name) {
		super(name);
	}

	/*
	 * Get the resources for the given resource names
	 */
	public IResource[] getResources(IContainer container, String[] hierarchy) throws CoreException {
		IResource[] resources = new IResource[hierarchy.length];
		for (int i=0;i<resources.length;i++) {
			resources[i] = container.findMember(hierarchy[i]);
			if (resources[i] == null) {
				resources[i] = buildResources(container, new String[] {hierarchy[i]})[0];
			}
		}
		return resources;
	}
	
	/**
	 * Add the resources to an existing container and upload them to CVS
	 */
	public IResource[] addResources(IContainer container, String[] hierarchy, boolean checkin) throws CoreException, TeamException {
		IResource[] newResources = buildResources(container, hierarchy, false);
		getProvider(container).add(newResources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		if (checkin)
			getProvider(container).checkin(newResources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		return newResources;
	}
	
	public void appendText(IResource resource, String text, boolean prepend) throws CoreException, IOException {
		IFile file = (IFile)resource;
		InputStream in = file.getContents();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {	
			if (prepend) {
				bos.write(text.getBytes());
			}
			int i;
			while ((i = in.read()) != -1) {
				bos.write(i);
			}
			if (!prepend) {
				bos.write(text.getBytes());
			}
		} finally {
			in.close();
		}
		file.setContents(new ByteArrayInputStream(bos.toByteArray()), false, false, DEFAULT_MONITOR);
	}
	
	/**
	 * Delete the resources from an existing container and the changes to CVS
	 */
	public IResource[] changeResources(IContainer container, String[] hierarchy, boolean checkin) throws CoreException, TeamException {
		List changedResources = new ArrayList(hierarchy.length);
		for (int i=0;i<hierarchy.length;i++) {
			IResource resource = container.findMember(hierarchy[i]);
			if (resource.getType() == IResource.FILE) {
				changedResources.add(resource);
				((IFile)resource).setContents(getRandomContents(), false, false, null);
			}
		}
		IResource[] resources = (IResource[])changedResources.toArray(new IResource[changedResources.size()]);
		if (checkin)
			getProvider(container).checkin(resources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		return resources;
	}
	
	/**
	 * Delete the resources from an existing container and the changes to CVS
	 */
	public IResource[] deleteResources(IContainer container, String[] hierarchy, boolean checkin) throws CoreException, TeamException {
		IResource[] resources = getResources(container, hierarchy);
		getProvider(container).delete(resources, DEFAULT_MONITOR);
		if (checkin)
			getProvider(container).checkin(resources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		return resources;
	}
	
	/**
	 * Unmanage the resources
	 */
	public void unmanageResources(IContainer container, String[] hierarchy) throws CoreException, TeamException {
		IResource[] resources = getResources(container, hierarchy);
		for (int i=0;i<resources.length;i++) {
			Session.getManagedResource(resources[i]).unmanage();
		}
		Synchronizer.getInstance().save(DEFAULT_MONITOR);
	}
	
	/**
	 * Update the resources from an existing container with the changes from the CVS repository
	 */
	public IResource[] updateResources(IContainer container, String[] hierarchy, boolean ignoreLocalChanges) throws CoreException, TeamException {
		IResource[] resources = getResources(container, hierarchy);
		getProvider(container).update(resources, IResource.DEPTH_INFINITE, null, ignoreLocalChanges, DEFAULT_MONITOR);
		return resources;
	}
	
	public void updateProject(IProject project, CVSTag tag, boolean ignoreLocalChanges) throws TeamException {
		getProvider(project).update(new IResource[] {project}, IResource.DEPTH_INFINITE, tag, ignoreLocalChanges, DEFAULT_MONITOR);
	}
	
	/**
	 * Commit the resources from an existing container to the CVS repository
	 */
	public IResource[] commitResources(IContainer container, String[] hierarchy) throws CoreException, TeamException {
		IResource[] resources = getResources(container, hierarchy);
		getProvider(container).checkin(resources, IResource.DEPTH_ZERO, DEFAULT_MONITOR);
		return resources;
	}
	
	/**
	 * Return a collection of resources defined by hierarchy. The resources
	 * are added to the workspace and to the file system. If the manage flag is true, the
	 * resources are auto-managed, if false, they are left un-managed.
	 */
	public IResource[] buildResources(IContainer container, String[] hierarchy, boolean includeContainer) throws CoreException {
		List resources = new ArrayList(hierarchy.length + 1);
		resources.addAll(Arrays.asList(buildResources(container, hierarchy)));
		if (includeContainer)
			resources.add(container);
		IResource[] result = (IResource[]) resources.toArray(new IResource[resources.size()]);
		ensureExistsInWorkspace(result, true);
		for (int i = 0; i < result.length; i++) {
			if (result[i].getType() == IResource.FILE)
				// 3786 bytes is the average size of Eclipse Java files!
				 ((IFile) result[i]).setContents(getRandomContents(RANDOM_CONTENT_SIZE), true, false, null);
		}
		return result;
	}

	public void checkinResources(IContainer container, boolean deep) throws TeamException {
		getProvider(container).checkin(new IResource[] {container}, deep?IResource.DEPTH_INFINITE:IResource.DEPTH_ZERO, DEFAULT_MONITOR);
	}
	/*
	 * Checkout a copy of the project into a project with the given postfix
	 */
	 protected IProject checkoutCopy(IProject project, String postfix) throws TeamException {
	 	// Get the provider and remote module so we can get the proper path for the module
		CVSTeamProvider provider = getProvider(project);
		
		// Check the project out under a different name and validate that the results are the same
		IProject copy = getWorkspace().getRoot().getProject(project.getName() + postfix);
		CVSProviderPlugin.getProvider().checkout(getRepository(), copy, ((ICVSFolder)Session.getManagedResource(project)).getFolderSyncInfo().getRepository(), null, DEFAULT_MONITOR);
		return copy;
	 }
	 
	 protected IProject checkoutCopy(IProject project, CVSTag tag) throws TeamException {
	 	// Get the provider and remote module so we can get the proper path for the module
		CVSTeamProvider provider = getProvider(project);
		
		// Check the project out under a different name and validate that the results are the same
		IProject copy = getWorkspace().getRoot().getProject(project.getName() + tag.getName());
		CVSProviderPlugin.getProvider().checkout(getRepository(), copy, ((ICVSFolder)Session.getManagedResource(project)).getFolderSyncInfo().getRepository(), tag, DEFAULT_MONITOR);
		return copy;
	 }
	 
	/*
	 * This method creates a project with the given resources, imports
	 * it to CVS and checks it out
	 */
	protected IProject createProject(String prefix, String[] resources) throws CoreException, TeamException {
		IProject project = getUniqueTestProject(prefix);
		IResource[] result = buildResources(project, resources, true);
		shareProject(project);
		assertValidCheckout(project);
		return project;
	}
	
	/*
	 * Compare two projects by comparing thier providers
	 */
	protected void assertEquals(String message, IProject project1, IProject project2) throws CoreException, TeamException, IOException {
		assertEquals(message, project1, project2, false, false);
	}
	
	protected void assertEquals(String message, IProject project1, IProject project2, boolean includeTimestamps, boolean includeTags) throws CoreException, TeamException, IOException {
		assertEquals(message, getProvider(project1), getProvider(project2), includeTimestamps, includeTags);
	}
	
	/*
	 * Compare CVS team providers by comparing the cvs resource corresponding to the provider's project
	 */
	protected void assertEquals(String message, CVSTeamProvider provider1, CVSTeamProvider provider2, boolean includeTimestamps, boolean includeTags) throws CoreException, TeamException, IOException {
		assertEquals(message, (ICVSFolder)Session.getManagedFolder(provider1.getProject().getLocation().toFile()), (ICVSFolder)Session.getManagedFolder(provider2.getProject().getLocation().toFile()), includeTimestamps, includeTags);
	}
	
	/*
	 * Compare resources by casting them to their prpoer type
	 */
	protected void assertEquals(String message, ICVSResource resource1, ICVSResource resource2, boolean includeTimestamps, boolean includeTags) throws CoreException, CVSException, IOException {
		assertEquals(message, resource1.isFolder(), resource2.isFolder());
		if (!resource1.isFolder())
			assertEquals(message, (ICVSFile)resource1, (ICVSFile)resource2, includeTimestamps, includeTags);
		else 
			assertEquals(message, (ICVSFolder)resource1, (ICVSFolder)resource2, includeTimestamps, includeTags);
	}
	
	/*
	 * Compare folders by comparing their folder sync info and there children
	 * 
	 * XXX What about unmanaged children?
	 */
	protected void assertEquals(String message, ICVSFolder container1, ICVSFolder container2, boolean includeTimestamps, boolean includeTags) throws CoreException, CVSException, IOException {
		assertEquals(message, container1.getFolderSyncInfo(), container2.getFolderSyncInfo(), includeTags);
		assertTrue(message, container1.getFolders().length == container2.getFolders().length);
		assertTrue(message, container1.getFiles().length == container2.getFiles().length);
		ICVSFolder[] folders = container1.getFolders();
		for (int i= 0;i <folders.length;i++)
			assertEquals(message, folders[i], container2.getFolder(folders[i].getName()), includeTimestamps, includeTags);
		ICVSFile[] files = container1.getFiles();
		for (int i= 0;i <files.length;i++)
			assertEquals(message, files[i], container2.getFile(files[i].getName()), includeTimestamps, includeTags);
	}
	
	/*
	 * Compare the files contents and sync information
	 */
	protected void assertEquals(String message, ICVSFile file1, ICVSFile file2, boolean includeTimestamps, boolean includeTags) throws CoreException, CVSException, IOException {
		// Getting the contents first is important as it will fetch the proper sync info if one of the files is a remote handle
		assertTrue(message, compareContent(getContents(file1), getContents(file2)));
		assertEquals(message, file1.getSyncInfo(), file2.getSyncInfo(), includeTimestamps, includeTags);
	}
	
	/*
	 * Compare sync info by comparing the entry line generated by the sync info
	 */
	protected void assertEquals(String message, ResourceSyncInfo info1, ResourceSyncInfo info2, boolean includeTimestamp, boolean includeTag) throws CoreException, CVSException, IOException {
		if (info1 == null) {
			assertTrue(message, info2 == null);
			return;
		}
		String line1 = info1.getEntryLine(includeTimestamp);
		String line2 = info2.getEntryLine(includeTimestamp);
		if (!includeTag) {
			// Strip everything past the last slash
			line1 = line1.substring(0, line1.lastIndexOf('/'));
			line2 = line2.substring(0, line2.lastIndexOf('/'));
		}
		assertTrue(message, line1.equals(line2));
	}
	
	/*
	 * Use the equals of folder sync info unless the tag is not included in which case we just
	 * compare the root and repository
	 */
	protected void assertEquals(String message, FolderSyncInfo info1, FolderSyncInfo info2, boolean includeTag) throws CoreException, CVSException, IOException {
		if (includeTag) {
			assertTrue(message, info1.equals(info2));
		} else {
			assertTrue(message, info1.getRoot().equals(info2.getRoot()));
			assertTrue(message, info1.getRepository().equals(info2.getRepository()));
		}
	}
	
	/*
	 * Compare the local project with the remote state by checking out a copy of the project.
	 */
	protected void assertLocalStateEqualsRemote(String message, IProject project) throws TeamException, CoreException, IOException {
		assertEquals(message + "The local state does not match remote state", getProvider(project), getProvider(checkoutCopy(project, "-remote")), false, true);
	}
	
	/*
	 * Compare the local project with the remote state indicated by the given tag by checking out a copy of the project.
	 */
	protected void assertLocalStateEqualsRemote(String message, IProject project, CVSTag tag) throws TeamException, CoreException, IOException {
		assertEquals(message + "The local state does not match remote state", getProvider(project), getProvider(checkoutCopy(project, tag)), true, false);
	}
	
	protected void assertHasNoRemote(String prefix, IResource[] resources) throws TeamException {
		for (int i=0;i<resources.length;i++) 
			assertHasNoRemote(prefix, resources[i]);
	}
	
	protected void assertHasNoRemote(String prefix, IResource resource) throws TeamException {
		assertTrue(prefix + " resource should not have a remote", !getProvider(resource).hasRemote(resource));
	}
	
	protected void assertHasRemote(String prefix, IResource[] resources) throws TeamException {
		for (int i=0;i<resources.length;i++) 
			assertHasRemote(prefix, resources[i]);
	}
	
	protected void assertHasRemote(String prefix, IResource resource) throws TeamException {
		assertTrue(prefix + " resource should have a remote", getProvider(resource).hasRemote(resource));
	}
	
	protected void assertIsModified(String prefix, IResource[] resources) throws TeamException {
		for (int i=0;i<resources.length;i++) 
			assertIsModified(prefix, resources[i]);
	}
	
	protected void assertIsModified(String prefix, IResource resource) throws TeamException {
		// Only check for files as CVS doesn't dirty folders
		if (resource.getType() == IResource.FILE)
			assertTrue(prefix + " resource " + resource.getFullPath() + " should be dirty.", ((ICVSFile)getCVSResource(resource)).isModified());
	}
	
	protected void assertNotModified(String prefix, IResource[] resources) throws TeamException {
		for (int i=0;i<resources.length;i++) 
			assertNotModified(prefix, resources[i]);
	}
	
	protected void assertNotModified(String prefix, IResource resource) throws TeamException {
		assertTrue(prefix + " resource should be dirty", !((ICVSFile)getCVSResource(resource)).isModified());
	}
	
	protected void assertValidCheckout(IProject project) {
		// NOTE: Add code to ensure that the project was checkout out properly
		CVSTeamProvider provider = (CVSTeamProvider)TeamPlugin.getManager().getProvider(project);
		assertNotNull(provider);
	}
	protected InputStream getContents(ICVSFile file) throws CVSException, IOException {
		if (file instanceof LocalFile)
			return new BufferedInputStream(new FileInputStream(getFile(file)));
		else
			return ((RemoteFile)file).getContents(DEFAULT_MONITOR);
	}
	
	/*
	 * Get the CVS Resource for the given resource
	 */
	protected ICVSResource getCVSResource(IResource resource) throws CVSException {
		if (resource.getType() == IResource.FILE)
			return Session.getManagedFile(resource.getLocation().toFile());
		else
			return Session.getManagedFolder(resource.getLocation().toFile());
	}
	/*
	 * Get the IO File for the given CVS resource
	 */
	protected File getFile(ICVSResource mResource) {
		return new File(((LocalResource)mResource).getPath());
	}
	
	protected IProject getNamedTestProject(String name) throws CoreException {
		IProject target = getWorkspace().getRoot().getProject(name);
		if (!target.exists()) {
			target.create(null);
			target.open(null);		
		}
		assertExistsInFileSystem(target);
		return target;
	}
	protected CVSTeamProvider getProvider(IResource resource) throws TeamException {
		return (CVSTeamProvider)TeamPlugin.getManager().getProvider(resource);
	}
	protected static InputStream getRandomContents(int sizeAtLeast) {
		StringBuffer randomStuff = new StringBuffer(sizeAtLeast + 100);
		while (randomStuff.length() < sizeAtLeast) {
			randomStuff.append(getRandomSnippet());
		}
		return new ByteArrayInputStream(randomStuff.toString().getBytes());
	}
	/**
	 * Return String with some random text to use
	 * as contents for a file resource.
	 */
	public static String getRandomSnippet() {
		switch ((int) Math.round(Math.random() * 10)) {
			case 0 :
				return "este e' o meu conteudo (portuguese)";
			case 1 :
				return "Dann brauchen wir aber auch einen deutschen Satz!";
			case 2 :
				return "I'll be back";
			case 3 :
				return "don't worry, be happy";
			case 4 :
				return "there is no imagination for more sentences";
			case 5 :
				return "customize yours";
			case 6 :
				return "foo";
			case 7 :
				return "bar";
			case 8 :
				return "foobar";
			case 9 :
				return "case 9";
			default :
				return "these are my contents";
		}
	}
	protected IProject getUniqueTestProject(String prefix) throws CoreException {
		// manage and share with the default stream create by this class
		return getNamedTestProject(prefix + "-" + Long.toString(System.currentTimeMillis()));
	}
	
	protected CVSRepositoryLocation getRepository() {
		return CVSTestSetup.repository;
	}
	protected void importProject(IProject project) throws TeamException {
		
		// Create the root folder for the import operation
		ICVSFolder root = (ICVSFolder)Session.getManagedResource(project);

		// Perform the import
		IStatus status;
		Session s = new Session(getRepository(), root);
		s.open(DEFAULT_MONITOR);
		try {
			status = Command.IMPORT.execute(s,
				Command.NO_GLOBAL_OPTIONS,
				new LocalOption[] {Import.makeMessageOption("Initial Import")},
				new String[] { project.getName(), getRepository().getUsername(), "start" },
				null,
				DEFAULT_MONITOR);
		} finally {
			s.close();
		}

		if (status.getCode() == CVSException.SERVER_ERROR) {
			throw new CVSServerException(status);
		}
	}
	
	protected void shareProject(IProject project) throws TeamException {
		importProject(project);
		((CVSProvider)CVSProviderPlugin.getProvider()).checkout(getRepository(), project, null, null, DEFAULT_MONITOR);
		// We need to checking because of the .vcm_meta file
		getProvider(project).add(new IResource[] {project.getFile(".vcm_meta")}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);
		getProvider(project).checkin(new IResource[] {project}, IResource.DEPTH_INFINITE, DEFAULT_MONITOR);

	}
}

