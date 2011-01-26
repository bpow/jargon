package org.irods.jargon.datautils.tree;

import junit.framework.TestCase;

import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.irods.jargon.datautils.tree.FileTreeDiffEntry.DiffType;
import org.junit.Test;
import org.mockito.Mockito;


public class FileTreeNodeTest {
	
	@Test
	public void testCreateNodeWithFileTreeDiffEntry() throws Exception {
		CollectionAndDataObjectListingEntry entry = Mockito.mock(CollectionAndDataObjectListingEntry.class);
		FileTreeDiffEntry diffEntry = FileTreeDiffEntry.instance(DiffType.LEFT_HAND_NEWER, entry);
		FileTreeNode fileTreeNode = new FileTreeNode(diffEntry);
		Object userObj = fileTreeNode.getUserObject();
		boolean isFileTreeNode = (userObj instanceof FileTreeDiffEntry);
		TestCase.assertTrue("user object not retrieved", isFileTreeNode);
	}

}