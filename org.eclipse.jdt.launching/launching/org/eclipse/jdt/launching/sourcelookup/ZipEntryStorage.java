package org.eclipse.jdt.launching.sourcelookup;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.JavaModelException;
 
/**
 * Storage implementation for zip entries.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * @see ArchiveSourceLocation
 * @see IStorage
 * @since 2.0
 */
public class ZipEntryStorage extends PlatformObject implements IStorage {
	
	/**
	 * Zip file associated with zip entry
	 */
	private ZipFile fArchive;
	
	/**
	 * Zip entry
	 */
	private ZipEntry fZipEntry;
	
	/**
	 * Constructs a new storage implementation for the
	 * given zip entry in the specified zip file
	 * 
	 * @param archive zip file
	 * @param entry zip entry
	 */
	public ZipEntryStorage(ZipFile archive, ZipEntry entry) {
		setArchive(archive);
		setZipEntry(entry);
	}

	/**
	 * @see IStorage#getContents()
	 */
	public InputStream getContents() throws CoreException {
		try {
			return getArchive().getInputStream(getZipEntry());
		} catch (IOException e) {
			throw new JavaModelException(e, IJavaModelStatusConstants.IO_EXCEPTION);
		}
	}

	/**
	 * @see IStorage#getFullPath()
	 */
	public IPath getFullPath() {
		return new Path(getArchive().getName()).append(getZipEntry().getName());
	}

	/**
	 * @see IStorage#getName()
	 */
	public String getName() {
		int index = getZipEntry().getName().lastIndexOf('\\');
		if (index == -1) {
			index = getZipEntry().getName().lastIndexOf('/');
		}
		if (index == -1) {
			return getZipEntry().getName();
		} else {
			return getZipEntry().getName().substring(index + 1);
		}
	}

	/**
	 * @see IStorage#isReadOnly()
	 */
	public boolean isReadOnly() {
		return true;
	}
	
	/**
	 * Sets the archive containing the zip entry.
	 * 
	 * @param archive a zip file
	 */
	private void setArchive(ZipFile archive) {
		fArchive = archive;
	}
	
	/**
	 * Returns the archive containing the zip entry.
	 * 
	 * @return zip file
	 */
	public ZipFile getArchive() {
		return fArchive;
	}	
	
	/**
	 * Sets the entry that contains the source.
	 * 
	 * @param entry the entry that contains the source
	 */
	private void setZipEntry(ZipEntry entry) {
		fZipEntry = entry;
	}
	
	/**
	 * Returns the entry that contains the source
	 * 
	 * @return zip entry
	 */
	public ZipEntry getZipEntry() {
		return fZipEntry;
	}		

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object object) {		
		return object instanceof ZipEntryStorage &&
			 getArchive().equals(((ZipEntryStorage)object).getArchive()) &&
			 getZipEntry().getName().equals(((ZipEntryStorage)object).getZipEntry().getName());
	}
	
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getZipEntry().getName().hashCode();
	}
}
