/**
 * 
 */
package org.irods.jargon.core.transfer;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple restart manager that exists in an in-memory map. This version is
 * fairly naive and doesn't really support concurrent restarts.
 * 
 * 
 * @author Mike Conway - DICE
 * 
 */
public class MemoryBasedTransferRestartManager extends AbstractRestartManager {

	private static final Logger log = LoggerFactory
			.getLogger(MemoryBasedTransferRestartManager.class);

	private final ConcurrentHashMap<FileRestartInfoIdentifier, FileRestartInfo> cacheOfRestartInfo = new ConcurrentHashMap<FileRestartInfoIdentifier, FileRestartInfo>(
			8, 0.9f, 1);

	public MemoryBasedTransferRestartManager() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.transfer.AbstractRestartManager#storeRestart(org
	 * .irods.jargon.core.transfer.FileRestartInfo)
	 */
	@Override
	public FileRestartInfoIdentifier storeRestart(
			FileRestartInfo fileRestartInfo)
			throws FileRestartManagementException {

		log.info("storeRestart()");
		if (fileRestartInfo == null) {
			throw new IllegalArgumentException("null fileRestartInfo");
		}

		FileRestartInfoIdentifier identifier;

		synchronized (this) {
			identifier = FileRestartInfoIdentifier
					.instanceFromFileRestartInfo(fileRestartInfo);
			cacheOfRestartInfo.put(identifier, fileRestartInfo);
		}
		return identifier;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.transfer.AbstractRestartManager#deleteRestart(org
	 * .irods.jargon.core.transfer.FileRestartInfoIdentifier)
	 */
	@Override
	public void deleteRestart(
			FileRestartInfoIdentifier fileRestartInfoIdentifier)
			throws FileRestartManagementException {

		log.info("deleteRestart()");
		if (fileRestartInfoIdentifier == null) {
			throw new IllegalArgumentException("null fileRestartInfoIdentifier");
		}

		synchronized (this) {
			cacheOfRestartInfo.remove(fileRestartInfoIdentifier);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.transfer.AbstractRestartManager#retrieveRestart
	 * (org.irods.jargon.core.transfer.FileRestartInfoIdentifier)
	 */
	@Override
	public FileRestartInfo retrieveRestart(
			FileRestartInfoIdentifier fileRestartInfoIdentifier)
			throws FileRestartManagementException {

		log.info("retrieveRestart()");
		if (fileRestartInfoIdentifier == null) {
			throw new IllegalArgumentException("null fileRestartInfoIdentifier");
		}
		synchronized (this) {
			return cacheOfRestartInfo.get(fileRestartInfoIdentifier);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.transfer.AbstractRestartManager#updateSegment(org
	 * .irods.jargon.core.transfer.FileRestartInfo,
	 * org.irods.jargon.core.transfer.FileRestartDataSegment)
	 */
	@Override
	public void updateSegment(FileRestartInfo fileRestartInfo,
			FileRestartDataSegment fileRestartDataSegment)
			throws FileRestartManagementException {

		log.info("updateSegment()");

		if (fileRestartInfo == null) {
			throw new IllegalArgumentException("null fileRestartInfo");
		}

		if (fileRestartDataSegment == null) {
			throw new IllegalArgumentException("null fileRestartDataSegment");
		}

		log.info("updating fileRestartInfo:{}", fileRestartInfo);
		log.info("updating fileRestartDataSegment:{}", fileRestartDataSegment);

		synchronized (this) {
			FileRestartInfo actualRestartInfo = this
					.retrieveRestart(fileRestartInfo.identifierFromThisInfo());
			if (actualRestartInfo.getFileRestartDataSegments().size() < fileRestartDataSegment
					.getThreadNumber()) {
				log.error(
						"fileRestartInfo does not contain the given segment:{}",
						fileRestartInfo);
				throw new FileRestartManagementException(
						"unable to find segment");
			}
			FileRestartDataSegment actualSegment = actualRestartInfo
					.getFileRestartDataSegments().get(
							fileRestartDataSegment.getThreadNumber());
			if (actualSegment.getThreadNumber() != fileRestartDataSegment
					.getThreadNumber()) {
				log.error(
						"mismatch in thread number in update request for segment:{}",
						fileRestartDataSegment);
				throw new FileRestartManagementException(
						"file segment does not match thread number");
			}

			/*
			 * Update the segment
			 */

			actualRestartInfo.getFileRestartDataSegments().set(
					actualSegment.getThreadNumber(), fileRestartDataSegment);
			storeRestart(actualRestartInfo);
		}
	}
}