/**
 * 
 */
package org.irods.jargon.core.pub;

import static org.irods.jargon.core.pub.aohelper.AOHelper.AND;
import static org.irods.jargon.core.pub.aohelper.AOHelper.COMMA;
import static org.irods.jargon.core.pub.aohelper.AOHelper.EQUALS_AND_QUOTE;
import static org.irods.jargon.core.pub.aohelper.AOHelper.QUOTE;
import static org.irods.jargon.core.pub.aohelper.AOHelper.SPACE;
import static org.irods.jargon.core.pub.aohelper.AOHelper.WHERE;

import java.util.ArrayList;
import java.util.List;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.packinstr.ModAccessControlInp;
import org.irods.jargon.core.packinstr.ModAvuMetadataInp;
import org.irods.jargon.core.protovalues.FilePermissionEnum;
import org.irods.jargon.core.pub.aohelper.CollectionAOHelper;
import org.irods.jargon.core.pub.domain.AvuData;
import org.irods.jargon.core.pub.domain.Collection;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.pub.io.IRODSFileFactoryImpl;
import org.irods.jargon.core.query.AVUQueryElement;
import org.irods.jargon.core.query.IRODSGenQuery;
import org.irods.jargon.core.query.IRODSQueryResultRow;
import org.irods.jargon.core.query.IRODSQueryResultSetInterface;
import org.irods.jargon.core.query.JargonQueryException;
import org.irods.jargon.core.query.MetaDataAndDomainData;
import org.irods.jargon.core.query.MetaDataAndDomainData.MetadataDomain;
import org.irods.jargon.core.query.RodsGenQueryEnum;
import org.irods.jargon.core.query.UserFilePermission;
import org.irods.jargon.core.utils.AccessObjectQueryProcessingUtils;
import org.irods.jargon.core.utils.IRODSDataConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access object handles various operations for an IRODS Collection.
 * <p/>
 * Note that traditional file io per the java.io.* interfaces is handled through
 * the objects in the <code>org.irods.jargon.core.pub.io</code> package. This
 * class represents operations that are outside of the contracts one would
 * expect from an <code>java.io.File</code> object or the various streams.
 * 
 * @author Mike Conway - DICE (www.irods.org)
 * 
 */
public final class CollectionAOImpl extends IRODSGenericAO implements
		CollectionAO {

	private static final String QUERY_STRING_FOR_AVU_QUERY = "query string for AVU query: {}";
	private static final String QUERY_EXCEPTION_FOR_QUERY = "query exception for query:";
	public static final String ERROR_IN_COLECTION_QUERY = "An error occurred in the query for the collection";
	private IRODSFileFactory irodsFileFactory = new IRODSFileFactoryImpl(
			getIRODSSession(), getIRODSAccount());
	private IRODSGenQueryExecutor irodsGenQueryExecutor = new IRODSGenQueryExecutorImpl(
			getIRODSSession(), getIRODSAccount());
	public static final Logger log = LoggerFactory
			.getLogger(CollectionAOImpl.class);
	public static final int DEFAULT_REC_COUNT = 5000;

	/**
	 * Default constructor
	 * 
	 * @param irodsSession
	 * @param irodsAccount
	 * @throws JargonException
	 */
	protected CollectionAOImpl(final IRODSSession irodsSession,
			final IRODSAccount irodsAccount) throws JargonException {
		super(irodsSession, irodsAccount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#instanceIRODSFileForCollectionPath
	 * (java.lang.String)
	 */
	@Override
	public IRODSFile instanceIRODSFileForCollectionPath(
			final String collectionPath) throws JargonException {
		log.info("returning a collection for path: {}", collectionPath);
		final IRODSFile collection = irodsFileFactory
				.instanceIRODSFile(collectionPath);

		if (collection.exists() && !collection.isDirectory()) {
			log.error(
					"collection cannot be returned, the given path is not a collection: {}",
					collectionPath);
			throw new IllegalArgumentException("the given path is not a collection");
		}

		return collection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#findDomainByMetadataQuery(java
	 * .util.List)
	 */
	@Override
	public List<Collection> findDomainByMetadataQuery(
			final List<AVUQueryElement> avuQueryElements)
			throws JargonQueryException, JargonException {

		return findDomainByMetadataQuery(avuQueryElements, 0);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#findDomainByMetadataQuery(java
	 * .util.List, int)
	 */
	@Override
	public List<Collection> findDomainByMetadataQuery(
			final List<AVUQueryElement> avuQueryElements,
			final int partialStartIndex) throws JargonQueryException,
			JargonException {

		log.info("building a metadata query for: {}", avuQueryElements);

		final StringBuilder query = new StringBuilder();
		query.append(CollectionAOHelper.buildSelects());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_META_COLL_ATTR_NAME.getName());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_META_COLL_ATTR_VALUE.getName());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_META_COLL_ATTR_UNITS.getName());

		query.append(WHERE);
		boolean previousElement = false;

		for (AVUQueryElement queryElement : avuQueryElements) {

			if (previousElement) {
				query.append(AND);
			}
			previousElement = true;
			query.append(CollectionAOHelper.buildConditionPart(queryElement));
		}

		final String queryString = query.toString();
		log.debug(QUERY_STRING_FOR_AVU_QUERY, queryString);

		final IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;
		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryWithPaging(
					irodsQuery, partialStartIndex);

		} catch (JargonQueryException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY, queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return CollectionAOHelper.buildListFromResultSet(resultSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.CollectionAO#findAll(java.lang.String)
	 */
	@Override
	public List<Collection> findAll(final String absolutePathOfParent)
			throws JargonException {

		return findAll(absolutePathOfParent, 0);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.CollectionAO#findAll(java.lang.String,
	 * int)
	 */
	@Override
	public List<Collection> findAll(final String absolutePathOfParent,
			final int partialStartIndex) throws JargonException {

		if (absolutePathOfParent == null) {
			throw new IllegalArgumentException("null absolutePathOfParent");
		}

		String parentPath = "/";
		if (!absolutePathOfParent.isEmpty()) {
			parentPath = absolutePathOfParent;
		}

		final StringBuilder query = new StringBuilder();
		query.append(CollectionAOHelper.buildSelects());
		query.append(" WHERE ");
		query.append(RodsGenQueryEnum.COL_COLL_PARENT_NAME.getName());
		query.append(" = '");
		query.append(IRODSDataConversionUtil.escapeSingleQuotes(parentPath));
		query.append("'");

		final String queryString = query.toString();

		if (log.isInfoEnabled()) {
			log.info("coll query:" + queryString);
		}

		IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;

		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryWithPaging(
					irodsQuery, partialStartIndex);
		} catch (JargonQueryException e) {
			log.error("query exception for:" + queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return CollectionAOHelper.buildListFromResultSet(resultSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.CollectionAO#findWhere(java.lang.String,
	 * int)
	 */
	@Override
	public List<Collection> findWhere(final String whereClause,
			final int partialStartIndex) throws JargonException {

		if (whereClause == null) {
			throw new IllegalArgumentException("null where clause");
		}

		final StringBuilder query = new StringBuilder();
		query.append(CollectionAOHelper.buildSelects());
		query.append(" WHERE ");
		query.append(whereClause);
		final String queryString = query.toString();

		log.info("coll query:{}", queryString);

		IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;

		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryWithPaging(
					irodsQuery, partialStartIndex);
		} catch (JargonQueryException e) {
			log.error("query exception for:" + queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return CollectionAOHelper.buildListFromResultSet(resultSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#findMetadataValuesByMetadataQuery
	 * (java.util.List)
	 */
	@Override
	public List<MetaDataAndDomainData> findMetadataValuesByMetadataQuery(
			final List<AVUQueryElement> avuQuery) throws JargonQueryException,
			JargonException {
		return findMetadataValuesByMetadataQueryForCollection(avuQuery, "");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.irods.jargon.core.pub.CollectionAO#
	 * findMetadataValuesByMetadataQueryForCollection(java.util.List,
	 * java.lang.String)
	 */
	@Override
	public List<MetaDataAndDomainData> findMetadataValuesByMetadataQueryForCollection(
			final List<AVUQueryElement> avuQuery,
			final String collectionAbsolutePath) throws JargonQueryException,
			JargonException {

		if (avuQuery == null || avuQuery.isEmpty()) {
			throw new IllegalArgumentException("null or empty query");
		}

		if (collectionAbsolutePath == null) {
			throw new IllegalArgumentException("Null absolutePath for collection");
		}

		final IRODSGenQueryExecutor irodsGenQueryExecutorImpl = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());

		log.info("building a metadata query for: {}", avuQuery);

		final StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		query.append(RodsGenQueryEnum.COL_COLL_ID.getName());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		query.append(COMMA);
		query.append(CollectionAOHelper.buildMetadataSelects());

		query.append(WHERE);
		boolean previousElement = false;
		@SuppressWarnings("unused")
		StringBuilder queryCondition;

		for (AVUQueryElement queryElement : avuQuery) {

			if (previousElement) {
				query.append(AND);
			}
			previousElement = true;
			query.append(CollectionAOHelper.buildConditionPart(queryElement));
		}

		if (collectionAbsolutePath.isEmpty()) {
			log.info("no absolute path, ignore this in the where clause");
		} else {
			log.info("adding abs path to query");

			if (previousElement) {
				query.append(AND);
			} else {
				query.append(SPACE);
			}

			query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
			query.append(EQUALS_AND_QUOTE);
			query.append(IRODSDataConversionUtil
					.escapeSingleQuotes(collectionAbsolutePath));
			query.append(QUOTE);
		}

		final String queryString = query.toString();
		log.debug(QUERY_STRING_FOR_AVU_QUERY, queryString);

		final IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;
		try {
			resultSet = irodsGenQueryExecutorImpl.executeIRODSQueryAndCloseResult(irodsQuery,
					0);

		} catch (JargonQueryException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY + queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return AccessObjectQueryProcessingUtils
				.buildMetaDataAndDomainDatalistFromResultSet(
						MetadataDomain.COLLECTION, resultSet);
	}

	// FIXME: add partial start version or add to this method
	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.irods.jargon.core.pub.CollectionAO#
	 * findMetadataValuesByMetadataQueryWithAdditionalWhere(java.util.List,
	 * java.lang.String)
	 */
	@Override
	public List<MetaDataAndDomainData> findMetadataValuesByMetadataQueryWithAdditionalWhere(
			final List<AVUQueryElement> avuQuery, final String additionalWhere)
			throws JargonQueryException, JargonException {

		if (avuQuery == null || avuQuery.isEmpty()) {
			throw new IllegalArgumentException("null or empty query");
		}

		if (additionalWhere == null) {
			throw new IllegalArgumentException(
					"null additional where clause, set to blank if unused");
		}

		final IRODSGenQueryExecutor irodsGenQueryExecutorImpl = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());

		log.info("building a metadata query for: {}", avuQuery);
		log.info("additional where data: {}", additionalWhere);

		final StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		query.append(RodsGenQueryEnum.COL_COLL_ID.getName());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		query.append(COMMA);
		query.append(CollectionAOHelper.buildMetadataSelects());

		query.append(WHERE);
		boolean previousElement = false;
		@SuppressWarnings("unused")
		StringBuilder queryCondition = null;

		for (AVUQueryElement queryElement : avuQuery) {

			if (previousElement) {
				query.append(AND);
			}
			previousElement = true;
			query.append(CollectionAOHelper.buildConditionPart(queryElement));
		}

		if (additionalWhere.isEmpty()) {
			log.info("no additionalWhere, ignore this in the where clause");
		} else {
			log.info("adding additional where to query");

			if (previousElement) {
				query.append(AND);
			} else {
				query.append(SPACE);
			}

			query.append(additionalWhere);
		}

		final String queryString = query.toString();
		log.debug(QUERY_STRING_FOR_AVU_QUERY, queryString);

		final IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;
		try {
			resultSet = irodsGenQueryExecutorImpl.executeIRODSQueryAndCloseResult(irodsQuery,
					0);

		} catch (JargonQueryException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY + queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return AccessObjectQueryProcessingUtils
				.buildMetaDataAndDomainDatalistFromResultSet(
						MetadataDomain.COLLECTION, resultSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.CollectionAO#
	 * findMetadataValuesByMetadataQueryForCollection(java.util.List,
	 * java.lang.String, int)
	 */
	@Override
	public List<MetaDataAndDomainData> findMetadataValuesByMetadataQueryForCollection(
			final List<AVUQueryElement> avuQuery,
			final String collectionAbsolutePath, final int partialStartIndex)
			throws JargonQueryException, JargonException {

		if (avuQuery == null) {
			throw new IllegalArgumentException("null query");
		}

		if (collectionAbsolutePath == null) {
			throw new IllegalArgumentException("Null absolutePath for collection");
		}

		final IRODSGenQueryExecutor irodsGenQueryExecutorImpl = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());

		log.info("building a metadata query for: {}", avuQuery);

		final StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		query.append(RodsGenQueryEnum.COL_COLL_ID.getName());
		query.append(COMMA);
		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		query.append(COMMA);
		query.append(CollectionAOHelper.buildMetadataSelects());

		query.append(WHERE);
		boolean previousElement = false;
		@SuppressWarnings("unused")
		StringBuilder queryCondition;

		for (AVUQueryElement queryElement : avuQuery) {

			if (previousElement) {
				query.append(AND);
			}
			previousElement = true;
			query.append(CollectionAOHelper.buildConditionPart(queryElement));
		}

		if (collectionAbsolutePath.isEmpty()) {
			log.info("no absolute path, ignore this in the where clause");
		} else {
			log.info("adding abs path to query");

			if (previousElement) {
				query.append(AND);
			} else {
				query.append(SPACE);
			}

			query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
			query.append(EQUALS_AND_QUOTE);
			query.append(IRODSDataConversionUtil
					.escapeSingleQuotes(collectionAbsolutePath));
			query.append(QUOTE);
		}

		final String queryString = query.toString();
		log.debug(QUERY_STRING_FOR_AVU_QUERY, queryString);

		final IRODSGenQuery irodsQuery = IRODSGenQuery.instance(queryString,
				DEFAULT_REC_COUNT);

		IRODSQueryResultSetInterface resultSet;
		try {
			resultSet = irodsGenQueryExecutorImpl.executeIRODSQueryAndCloseResult(irodsQuery,
					0);

		} catch (JargonQueryException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY + queryString, e);
			throw new JargonException(ERROR_IN_COLECTION_QUERY);
		}

		return AccessObjectQueryProcessingUtils
				.buildMetaDataAndDomainDatalistFromResultSet(
						MetadataDomain.COLLECTION, resultSet);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#addAVUMetadata(java.lang.String,
	 * org.irods.jargon.core.pub.domain.AvuData)
	 */
	@Override
	public void addAVUMetadata(final String absolutePath, final AvuData avuData)
			throws DataNotFoundException, JargonException {

		if (absolutePath == null || absolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty absolutePath");
		}

		if (avuData == null) {
			throw new IllegalArgumentException("null AVU data");
		}

		log.info("adding avu metadata to collection: {}", avuData);
		log.info("absolute path: {}", absolutePath);

		final ModAvuMetadataInp modifyAvuMetadataInp = ModAvuMetadataInp
				.instanceForAddCollectionMetadata(absolutePath, avuData);

		log.debug("sending avu request");

		try {

			getIRODSProtocol().irodsFunction(modifyAvuMetadataInp);

		} catch (JargonException je) {

			if (je.getMessage().indexOf("-814000") > -1) {
				throw new DataNotFoundException(
						"Target collection was not found, could not add AVU");
			}

			log.error("jargon exception adding AVU metadata", je);
			throw je;
		}

		log.debug("metadata added");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#deleteAVUMetadata(java.lang.String
	 * , org.irods.jargon.core.pub.domain.AvuData)
	 */
	@Override
	public void deleteAVUMetadata(final String absolutePath,
			final AvuData avuData) throws DataNotFoundException,
			JargonException {
		if (absolutePath == null || absolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty absolutePath");
		}

		if (avuData == null) {
			throw new IllegalArgumentException("null AVU data");
		}

		log.info("deleting avu metadata from collection: {}", avuData);
		log.info("absolute path: {}", absolutePath);

		final ModAvuMetadataInp modifyAvuMetadataInp = ModAvuMetadataInp
				.instanceForDeleteCollectionMetadata(absolutePath, avuData);

		log.debug("sending avu request");

		try {
			getIRODSProtocol().irodsFunction(modifyAvuMetadataInp);
		} catch (JargonException je) {

			if (je.getMessage().indexOf("-814000") > -1) {
				throw new DataNotFoundException(
						"Target collection was not found, could not remove AVU");
			}

			log.error("jargon exception removing AVU metadata", je);
			throw je;
		}

		log.debug("metadata removed");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#overwriteAVUMetadata(java.lang
	 * .String, org.irods.jargon.core.pub.domain.AvuData)
	 */
	@Override
	public void overwriteAVUMetadata(final String absolutePath,
			final AvuData avuData) throws DataNotFoundException,
			JargonException {

		// FIXME: implement via 'mod', see jargon2.3.1

		if (absolutePath == null || absolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty absolutePath");
		}

		if (avuData == null) {
			throw new IllegalArgumentException("null AVU data");
		}

		log.info("overwrite avu metadata for collection: {}", avuData);
		log.info("absolute path: {}", absolutePath);

		this.deleteAVUMetadata(absolutePath, avuData);
		this.addAVUMetadata(absolutePath, avuData);

		log.debug("metadata rewritten");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#findMetadataValuesForCollection
	 * (java.lang.String, int)
	 */
	@Override
	public List<MetaDataAndDomainData> findMetadataValuesForCollection(
			final String collectionAbsolutePath, final int partialStartIndex)
			throws JargonException, JargonQueryException {

		if (collectionAbsolutePath == null || collectionAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty collectionAbsolutePath");
		}

		if (partialStartIndex < 0) {
			throw new IllegalArgumentException(
					"partialStartIndex must be 0 or greater, set to 0 if no offset desired");
		}

		log.info("find metadata values for collection:{}",
				collectionAbsolutePath);
		log.info("with partial start of:{}", partialStartIndex);

		List<AVUQueryElement> avuQuery = new ArrayList<AVUQueryElement>();
		return findMetadataValuesByMetadataQueryForCollection(avuQuery,
				collectionAbsolutePath, partialStartIndex);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.CollectionAO#findByAbsolutePath(java.lang.String
	 * )
	 */
	@Override
	public Collection findByAbsolutePath(
			final String irodsCollectionAbsolutePath) throws DataNotFoundException, JargonException {

		if (irodsCollectionAbsolutePath == null
				|| irodsCollectionAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"null or empty irodsCollectionAbsolutePath");
		}

		StringBuilder sb = new StringBuilder();
		sb.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		sb.append(" = '");
		sb.append(IRODSDataConversionUtil
				.escapeSingleQuotes(irodsCollectionAbsolutePath));
		sb.append("'");
		List<Collection> collectionList = this.findWhere(sb.toString(), 0);
		
		if (collectionList.size() == 0) {
			throw new DataNotFoundException("no collection found for path:" + irodsCollectionAbsolutePath);
		} else {
			return collectionList.get(0);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.irods.jargon.core.pub.CollectionAO#
	 * countAllFilesUnderneathTheGivenCollection(java.lang.String)
	 */
	@Override
	public int countAllFilesUnderneathTheGivenCollection(
			final String irodsCollectionAbsolutePath) throws JargonException {

		if (irodsCollectionAbsolutePath == null) {
			throw new IllegalArgumentException("irodsCollectionAbsolutePath is null");
		}

		log.info("countAllFilesUnderneathTheGivenCollection: {}",
				irodsCollectionAbsolutePath);
		IRODSFile irodsFile = irodsFileFactory
				.instanceIRODSFile(irodsCollectionAbsolutePath);

		// I cannot get children if this is not a directory (a file has no
		// children)
		if (!irodsFile.isDirectory()) {
			log.error(
					"this is a file, not a directory, and therefore I cannot get a count of the children: {}",
					irodsCollectionAbsolutePath);
			throw new JargonException(
					"attempting to count children under a file at path:"
							+ irodsCollectionAbsolutePath);
		}

		IRODSGenQueryExecutor irodsGenQueryExecutor = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());

		StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		query.append(RodsGenQueryEnum.COL_DATA_REPL_NUM.getName());
		query.append(", COUNT(");
		query.append(RodsGenQueryEnum.COL_DATA_NAME.getName());
		query.append(") WHERE ");
		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		query.append(" LIKE '");
		query.append(IRODSDataConversionUtil
				.escapeSingleQuotes(irodsCollectionAbsolutePath));
		query.append("%'");
		IRODSGenQuery irodsQuery = IRODSGenQuery.instance(query.toString(), 1);
		IRODSQueryResultSetInterface resultSet;

		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryAndCloseResult(irodsQuery, 0);
		} catch (JargonQueryException e) {
			log.error("query exception for  query:" + query.toString(), e);
			throw new JargonException("error in exists query");
		}

		int queryWithLikeCtr = 0;

		if (resultSet.getResults().size() > 0) {
			queryWithLikeCtr = IRODSDataConversionUtil
					.getIntOrZeroFromIRODSValue(resultSet.getFirstResult()
							.getColumn(1));
		}

		return queryWithLikeCtr;

	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#setAccessPermissionInherit(java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setAccessPermissionInherit(final String zone, final String absolutePath, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("setAccessPermissionInherit on absPath:{}", absolutePath);
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetInheritOnACollection(collNeedsRecursive, zone, absolutePath);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#setAccessPermissionToNotInherit(java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setAccessPermissionToNotInherit(final String zone, final String absolutePath, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("setAccessPermissionToNotInherit on absPath:{}", absolutePath);
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetNoInheritOnACollection(collNeedsRecursive, zone, absolutePath);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#setAccessPermissionRead(java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setAccessPermissionRead(final String zone, final String absolutePath, final String userName, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("setAccessPermissionRead on absPath:{}", absolutePath);
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetPermission(collNeedsRecursive, zone, absolutePath, userName, ModAccessControlInp.READ_PERMISSION);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#setAccessPermissionWrite(java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setAccessPermissionWrite(final String zone, final String absolutePath, final String userName, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("setAccessPermissionRead on absPath:{}", absolutePath);
		// overhead iRODS behavior, if you set perm with recursive when no children, then won't take
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetPermission(collNeedsRecursive, zone, absolutePath, userName, ModAccessControlInp.WRITE_PERMISSION);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#setAccessPermissionOwn(java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setAccessPermissionOwn(final String zone, final String absolutePath, final String userName, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("setAccessPermissionOwn on absPath:{}", absolutePath);
		// overhead iRODS behavior, if you set perm with recursive when no children, then won't take
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetPermission(collNeedsRecursive, zone, absolutePath, userName, ModAccessControlInp.OWN_PERMISSION);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#removeAccessPermissionForUser(java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void removeAccessPermissionForUser(final String zone, final String absolutePath, final String userName, final boolean recursive) throws JargonException {
		
		// pi tests parameters
		log.info("removeAccessPermission on absPath:{}", absolutePath);
		log.info("for user:{}", userName);
		// overhead iRODS behavior, if you set perm with recursive when no children, then won't take
		boolean collNeedsRecursive = adjustRecursiveOption(absolutePath,
				recursive);
		
		ModAccessControlInp modAccessControlInp = ModAccessControlInp.instanceForSetPermission(collNeedsRecursive, zone, absolutePath, userName, ModAccessControlInp.NULL_PERMISSION);
		getIRODSProtocol().irodsFunction(modAccessControlInp);
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#isCollectionSetForPermissionInheritance(java.lang.String)
	 */
	@Override
	public boolean isCollectionSetForPermissionInheritance(final String absolutePath) throws JargonException {
		
		if (absolutePath == null || absolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty absolutePathToCollection");
		}
		
		IRODSGenQueryExecutor irodsGenQueryExecutor = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());
		
		IRODSGenQuery irodsQuery = IRODSGenQuery.instance(CollectionAOHelper.buildInheritanceQueryForCollectionAbsolutePath(absolutePath), 1);
		IRODSQueryResultSetInterface resultSet;

		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryAndCloseResult(irodsQuery, 0);
		} catch (JargonQueryException e) {
			throw new JargonException("error querying for inheritance flag", e);
		}
		
		String inheritanceFlag = resultSet.getFirstResult().getColumn(0);
		boolean returnInheritanceVal = false;
		
		if (inheritanceFlag.trim().equals("1")) { 
			returnInheritanceVal = true;
		}
		
		return returnInheritanceVal;
		
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#getPermissionForCollection(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public FilePermissionEnum getPermissionForCollection(final String irodsAbsolutePath, final String userName, final String zone) throws JargonException {
		
		if (irodsAbsolutePath == null || irodsAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty irodsAbsolutePath");
		}
		
		if (userName == null || userName.isEmpty()) {
			throw new IllegalArgumentException("null or empty userName");
		}
		
		if (zone == null) {
			throw new IllegalArgumentException("null zone");
		}
		
		log.info("getPermissionForCollection for absPath:{}", irodsAbsolutePath);
		log.info("userName:{}", userName);
		
		IRODSFileSystemAO irodsFileSystemAO = getIRODSAccessObjectFactory().getIRODSFileSystemAO(getIRODSAccount());
		IRODSFileFactory irodsFileFactory = this.getIRODSFileFactory();
		int permissionVal = irodsFileSystemAO.getDirectoryPermissions(irodsFileFactory.instanceIRODSFile(irodsAbsolutePath));
		FilePermissionEnum filePermissionEnum = FilePermissionEnum.valueOf(permissionVal);
		return filePermissionEnum;
		
	}

	/**
	 * Method overheads an iRODS protocol issue where recursive flag when collection has no children causes no permissions to be set.
	 * @param absolutePath
	 * @param recursive
	 * @return
	 * @throws JargonException
	 */
	private boolean adjustRecursiveOption(final String absolutePath,
			final boolean recursive) throws JargonException {
		
		IRODSFile collFile = this.getIRODSFileFactory().instanceIRODSFile(absolutePath);
		
		if (!collFile.exists()) {
			throw new JargonException("irodsFile does not exist for given path, cannot set permissions on it");
		}
		
		boolean collNeedsRecursive = recursive;
		
		if (recursive) {
			if (collFile.list().length == 0) {
				log.info("overridding recursive flag, file has no children");
				collNeedsRecursive = false;
			}
		}
		return collNeedsRecursive;
	}
	
	/* (non-Javadoc)
	 * @see org.irods.jargon.core.pub.CollectionAO#listPermissionsForCollection(java.lang.String)
	 */
	@Override
	public List<UserFilePermission> listPermissionsForCollection(final String irodsCollectionAbsolutePath) throws JargonException {
		
		if (irodsCollectionAbsolutePath == null || irodsCollectionAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException("null or empty collectionAbsolutePath");
		}
		
		log.info("listPermissionsForCollection: {}", irodsCollectionAbsolutePath);
		List<UserFilePermission> userFilePermissions = new ArrayList<UserFilePermission>();
		
		IRODSGenQueryExecutor irodsGenQueryExecutor = getIRODSAccessObjectFactory().getIRODSGenQueryExecutor(getIRODSAccount());

		StringBuilder query = new StringBuilder();
		query.append("SELECT ");
		query.append(RodsGenQueryEnum.COL_COLL_ACCESS_USER_NAME.getName());
		query.append(",");
		query.append(RodsGenQueryEnum.COL_COLL_ACCESS_USER_ID.getName());
		query.append(",");
		query.append(RodsGenQueryEnum.COL_COLL_ACCESS_TYPE.getName());
		query.append(" WHERE ");
		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName());
		query.append(" = '");
		query.append(IRODSDataConversionUtil
				.escapeSingleQuotes(irodsCollectionAbsolutePath));
		query.append("'");
		IRODSGenQuery irodsQuery = IRODSGenQuery.instance(query.toString(), this.getJargonProperties().getMaxFilesAndDirsQueryMax());
		IRODSQueryResultSetInterface resultSet;

		try {
			resultSet = irodsGenQueryExecutor.executeIRODSQueryAndCloseResult(irodsQuery, 0);
			
			UserFilePermission userFilePermission = null;
			for (IRODSQueryResultRow row : resultSet.getResults()) {
				userFilePermission = new UserFilePermission(row.getColumn(0), row.getColumn(1), FilePermissionEnum.valueOf(IRODSDataConversionUtil.getIntOrZeroFromIRODSValue(row.getColumn(2))));
				log.debug("loaded filePermission:{}", userFilePermission);
				userFilePermissions.add(userFilePermission);
			}
			
		} catch (JargonQueryException e) {
			log.error("query exception for  query:{}",query.toString(), e);
			throw new JargonException("error in query loading user file permissions for collection", e);
		}
		
		return userFilePermissions;
		
	}

}