package org.semanticweb.ontop.owlrefplatform.core;

/*
 * #%L
 * ontop-reformulation-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.openrdf.query.parser.ParsedQuery;
import org.semanticweb.ontop.exception.DuplicateMappingException;
import org.semanticweb.ontop.injection.NativeQueryLanguageComponentFactory;
import org.semanticweb.ontop.injection.OBDAFactoryWithException;
import org.semanticweb.ontop.io.PrefixManager;
import org.semanticweb.ontop.model.*;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.model.impl.RDBMSourceParameterConstants;
import org.semanticweb.ontop.nativeql.DBMetadataExtractor;
import org.semanticweb.ontop.ontology.Ontology;
import org.semanticweb.ontop.owlrefplatform.core.abox.IRDBMSSIRepositoryManager;
import org.semanticweb.ontop.owlrefplatform.core.abox.RDBMSSIRepositoryManager;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.LinearInclusionDependencies;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.UriTemplateMatcher;
import org.semanticweb.ontop.owlrefplatform.core.basicoperations.VocabularyValidator;
import org.semanticweb.ontop.owlrefplatform.core.dagjgrapht.TBoxReasoner;
import org.semanticweb.ontop.owlrefplatform.core.dagjgrapht.TBoxReasonerImpl;
import org.semanticweb.ontop.owlrefplatform.core.queryevaluation.*;
import org.semanticweb.ontop.owlrefplatform.core.reformulation.*;
import org.semanticweb.ontop.owlrefplatform.core.sql.SQLGenerator;
import org.semanticweb.ontop.owlrefplatform.core.srcquerygeneration.NativeQueryGenerator;
import org.semanticweb.ontop.owlrefplatform.core.tboxprocessing.SigmaTBoxOptimizer;
import org.semanticweb.ontop.owlrefplatform.core.translator.MappingVocabularyFixer;
import org.semanticweb.ontop.owlrefplatform.injection.QuestComponentFactory;
import org.semanticweb.ontop.sql.DBMetadata;
import org.semanticweb.ontop.sql.ImplicitDBConstraints;
import org.semanticweb.ontop.sql.TableDefinition;
import org.semanticweb.ontop.sql.api.Attribute;
import org.semanticweb.ontop.mapping.MappingSplitter;
import org.semanticweb.ontop.utils.MetaMappingExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.net.URI;
import java.security.InvalidParameterException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

//import com.hp.hpl.jena.query.Query;

public class Quest implements Serializable, IQuest {

	private static final long serialVersionUID = -6074403119825754295L;

	private PoolProperties poolProperties;
	private DataSource tomcatPool;

	private boolean isSemanticIdx = false;
	// Tomcat pool default properties
	// These can be changed in the properties file
	private int maxPoolSize = 20;
	private int startPoolSize = 2;
	private boolean removeAbandoned = true;
	private boolean logAbandoned = false;
	private int abandonedTimeout = 60; // 60 seconds
	private boolean keepAlive = true;

	// Whether to print primary and foreign keys to stdout.
	private boolean printKeys;

	/***
	 * Internal components
	 */

	/* The active ABox repository, might be null */
	private RDBMSSIRepositoryManager dataRepository;

	// /* The query answering engine */
	// private TechniqueWrapper techwrapper = null;

	private VocabularyValidator vocabularyValidator;

	/* The active connection used to get metadata from the DBMS */
	private transient Connection localConnection;

	/* The active query rewriter */
	private QueryRewriter rewriter;

	/* Native query generator */
	private NativeQueryGenerator dataSourceQueryGenerator;

	/* The active query evaluation engine */
	//private EvaluationEngine evaluationEngine;

	/* The TBox used for query reformulation (ROMAN: not really, it can be reduced by Sigma) */
	private TBoxReasoner reformulationReasoner;

	private LinearInclusionDependencies sigma;

	/* The merge and translation of all loaded ontologies */
	private final Ontology inputOntology;

	/* The input OBDA model */
	private OBDAModel inputOBDAModel;

	/* The input OBDA model */
	private OBDAModel unfoldingOBDAModel;

	private QuestUnfolder unfolder;

	/*
     * Index of the function symbols that have multiple types.
     * This index, built from the mappings, is immutable.
     *
     * TODO: not in version 1, check if still relevant.
     *
     */
	private ImmutableMultimap<Predicate,Integer> multiTypedFunctionSymbolIndex;

	/**
	 * This represents user-supplied constraints, i.e. primary
	 * and foreign keys not present in the database metadata
	 */
	private ImplicitDBConstraints userConstraints;

	/*
	 * Whether to apply the user-supplied database constraints given above
	 * userConstraints must be initialized and non-null whenever this is true
	 */
	private boolean applyUserConstraints;

	/***
	 * General flags and fields
	 */

	private Logger log = LoggerFactory.getLogger(Quest.class);

	/***
	 * Configuration
	 */

	private boolean reformulate = false;

	private String reformulationTechnique = QuestConstants.UCQBASED;

	private boolean bOptimizeEquivalences = true;

	private boolean bOptimizeTBoxSigma = true;

	private boolean bObtainFromOntology = true;

	private boolean bObtainFromMappings = true;

	private String aboxMode = QuestConstants.CLASSIC;

	private String aboxSchemaType = QuestConstants.SEMANTIC_INDEX;

	private OBDADataSource obdaSource;

	private Properties preferences;

	private boolean inmemory;

	private String aboxJdbcURL;

	private String aboxJdbcUser;

	private String aboxJdbcPassword;

	private String aboxJdbcDriver;
				
	/*
	 * The following are caches to queries that Quest has seen in the past. They
	 * are used by the statements
	 */

	private Map<String, String> queryCache = new ConcurrentHashMap<>();

	private Map<String, List<String>> signatureCache = new ConcurrentHashMap<>();

	private Map<String, ParsedQuery> sesameQueryCache = new ConcurrentHashMap<>();

	private Map<String, Boolean> isBooleanCache = new ConcurrentHashMap<>();

	private Map<String, Boolean> isConstructCache = new ConcurrentHashMap<>();

	private Map<String, Boolean> isDescribeCache = new ConcurrentHashMap<>();

	private DBMetadata metadata;

	/**
	 * TODO: explain
	 */
	private final NativeQueryLanguageComponentFactory nativeQLFactory;
	private final QuestComponentFactory questComponentFactory;
	private final OBDAFactoryWithException obdaFactory;
	private final MappingVocabularyFixer mappingVocabularyFixer;

	/***
	 * Will prepare an instance of quest in classic or virtual ABox mode. If the
	 * mappings are not null, then org.obda.owlreformulationplatform.aboxmode
	 * must be set to "virtual", if they are null it must be set to "classic".
	 *
	 * <p>
	 * You must still call setupRepository() after creating the instance.
	 *
	 * @param tbox
	 *            . The TBox must not be null, even if its empty. At least, the
	 *            TBox must define all the vocabulary of the system.
	 *            Should not be null.
	 * @param mappings
	 *            . The mappings of the system. The vocabulary of the mappings
	 *            must be subset or equal to the vocabulary of the ontology.
	 *            Can be null.
	 * @param config
	 *            . The configuration parameters for quest. See
	 *            QuestDefaults.properties for a description (in
	 *            src/main/resources). Should not be null.
	 *
	 * @param metadata TODO: describe
	 * @param nativeQLFactory
	 *
	 * TODO: describe nativeQLFactory
	 */
	@Inject
	private Quest(@Assisted Ontology tbox, @Assisted @Nullable OBDAModel mappings, @Assisted @Nullable DBMetadata metadata,
				  @Assisted Properties config, NativeQueryLanguageComponentFactory nativeQLFactory,
				  OBDAFactoryWithException obdaFactory, QuestComponentFactory questComponentFactory,
				  MappingVocabularyFixer mappingVocabularyFixer) throws DuplicateMappingException {
		if (tbox == null)
			throw new InvalidParameterException("TBox cannot be null");

		this.nativeQLFactory = nativeQLFactory;
		this.obdaFactory = obdaFactory;
		this.questComponentFactory = questComponentFactory;
		this.mappingVocabularyFixer = mappingVocabularyFixer;

		inputOntology = tbox;

		setPreferences(config);

		if (mappings == null && !aboxMode.equals(QuestConstants.CLASSIC)) {
			throw new IllegalArgumentException(
					"When working without mappings, you must set the ABox mode to \""
							+ QuestConstants.CLASSIC
							+ "\". If you want to work with no mappings in virtual ABox mode you must at least provide an empty but not null OBDAModel");
		}
		if (mappings != null && !aboxMode.equals(QuestConstants.VIRTUAL)) {
			throw new IllegalArgumentException(
					"When working with mappings, you must set the ABox mode to \""
							+ QuestConstants.VIRTUAL
							+ "\". If you want to work in \"classic abox\" mode, that is, as a triple store, you may not provide mappings (quest will take care of setting up the mappings and the database), set them to null.");
		}

		loadOBDAModel(mappings);
		this.metadata = metadata;
	}

	/**
	 * Supply user constraints: that is primary and foreign keys not in the database
	 * Can be useful for eliminating self-joins
	 *
	 * @param userConstraints User supplied primary and foreign keys (only useful if these are not in the metadata)
	 * 						May be used by ontop to eliminate self-joins
	 */
	@Override
	public void setImplicitDBConstraints(ImplicitDBConstraints userConstraints){
		assert(userConstraints != null);
		this.userConstraints = userConstraints;
	}

	public IRDBMSSIRepositoryManager getDataRepository() {
		return dataRepository;
	}

	/**
	 * Clones the SQL generator.
	 */
	@Override
	public NativeQueryGenerator cloneIfNecessaryNativeQueryGenerator() {
		return dataSourceQueryGenerator.cloneIfNecessary();
	}

	protected Map<String, String> getSQLCache() {
		return queryCache;
	}

	protected Map<String, List<String>> getSignatureCache() {
		return signatureCache;
	}

	public TBoxReasoner getReasoner() {
		return reformulationReasoner;
	}

	@Override
	public QueryRewriter getRewriter() {
		return this.rewriter;
	}

	public LinearInclusionDependencies getDataDependencies() {
		return sigma;
	}

	public VocabularyValidator getVocabularyValidator() {
		return this.vocabularyValidator;
	}

//	protected Map<String, Query> getJenaQueryCache() {
//		return jenaQueryCache;
//	}

	protected Map<String, ParsedQuery> getSesameQueryCache() {
		return sesameQueryCache;
	}

	protected Map<String, Boolean> getIsBooleanCache() {
		return isBooleanCache;
	}

	protected Map<String, Boolean> getIsConstructCache() {
		return isConstructCache;
	}

	@Override
	public Map<String, Boolean> getIsDescribeCache() {
		return isDescribeCache;
	}

	@Override
	public IQuestUnfolder getQuestUnfolder() {
		return unfolder;
	}

	private void loadOBDAModel(OBDAModel model) throws DuplicateMappingException {

		if (model == null) {
			//model = OBDADataFactoryImpl.getInstance().getOBDAModel();
			// TODO: refactor this pretty bad practice.
			//TODO: add the prefix.
			PrefixManager defaultPrefixManager = nativeQLFactory.create(new HashMap<String, String>());

			model = obdaFactory.createOBDAModel(new HashSet<OBDADataSource>(),
					new HashMap<URI, ImmutableList<OBDAMappingAxiom>>(), defaultPrefixManager);
		}
		inputOBDAModel = model;
	}

	@Override
	public OBDAModel getOBDAModel() {
		return inputOBDAModel;
	}

	/**
	 * Returns a mutable copy of the index of the multi-typed function symbols.
	 */
	@Override
	public Multimap<Predicate,Integer> copyMultiTypedFunctionSymbolIndex() {
		return ArrayListMultimap.create(multiTypedFunctionSymbolIndex);
	}

	@Override
	public void dispose() {
/*		try {
			if (evaluationEngine != null)
				this.evaluationEngine.dispose();
		} catch (Exception e) {
			log.debug("Error during disconnect: " + e.getMessage());
		}
*/
		try {
			if (localConnection != null && !localConnection.isClosed())
				disconnect();
		} catch (Exception e) {
			log.debug("Error during disconnect: " + e.getMessage());
		}
	}

	@Override
	public Properties getPreferences() {
		return preferences;
	}


	private void setPreferences(Properties preferences) {
		this.preferences = preferences;

		keepAlive = Boolean.valueOf((String) preferences.get(QuestPreferences.KEEP_ALIVE));
		removeAbandoned = Boolean.valueOf((String) preferences.get(QuestPreferences.REMOVE_ABANDONED));
		abandonedTimeout = Integer.valueOf((String) preferences.get(QuestPreferences.ABANDONED_TIMEOUT));
		startPoolSize = Integer.valueOf((String) preferences.get(QuestPreferences.INIT_POOL_SIZE));
		maxPoolSize = Integer.valueOf((String) preferences.get(QuestPreferences.MAX_POOL_SIZE));

		reformulate = Boolean.valueOf((String) preferences.get(QuestPreferences.REWRITE));
		reformulationTechnique = (String) preferences.get(QuestPreferences.REFORMULATION_TECHNIQUE);
		bOptimizeEquivalences = Boolean.valueOf((String) preferences.get(QuestPreferences.OPTIMIZE_EQUIVALENCES));
		bOptimizeTBoxSigma = Boolean.valueOf((String) preferences.get(QuestPreferences.OPTIMIZE_TBOX_SIGMA));
		bObtainFromOntology = Boolean.valueOf((String) preferences.get(QuestPreferences.OBTAIN_FROM_ONTOLOGY));
		bObtainFromMappings = Boolean.valueOf((String) preferences.get(QuestPreferences.OBTAIN_FROM_MAPPINGS));
		aboxMode = (String) preferences.get(QuestPreferences.ABOX_MODE);
		aboxSchemaType = (String) preferences.get(QuestPreferences.DBTYPE);
		inmemory = preferences.getProperty(QuestPreferences.STORAGE_LOCATION).equals(QuestConstants.INMEMORY);

		printKeys = Boolean.valueOf((String) preferences.get(QuestPreferences.PRINT_KEYS));

		if (!inmemory) {
			aboxJdbcURL = preferences.getProperty(QuestPreferences.JDBC_URL);
			aboxJdbcUser = preferences.getProperty(QuestPreferences.DBUSER);
			aboxJdbcPassword = preferences.getProperty(QuestPreferences.DBPASSWORD);
			aboxJdbcDriver = preferences.getProperty(QuestPreferences.JDBC_DRIVER);
		}

		log.debug("Quest configuration:");
		log.debug("Reformulation technique: {}", reformulationTechnique);
		log.debug("Optimize equivalences: {}", bOptimizeEquivalences);
		log.debug("Optimize TBox: {}", bOptimizeTBoxSigma);
		log.debug("ABox mode: {}", aboxMode);
		if (!aboxMode.equals("virtual")) {
			log.debug("Use in-memory database: {}", inmemory);
			log.debug("Schema configuration: {}", aboxSchemaType);
			log.debug("Get ABox assertions from OBDA models: {}", bObtainFromMappings);
			log.debug("Get ABox assertions from ontology: {}", bObtainFromOntology);
		}

	}

	/***
	 * Starts the local connection that Quest maintains to the DBMS. This
	 * connection belongs only to Quest and is used to get information from the
	 * DBMS. At the moment this connection is mainly used during initialization,
	 * to get metadata about the DBMS or to create repositories in classic mode.
	 *
	 * @return
	 * @throws SQLException
	 */
	private boolean connect() throws SQLException {
		if (localConnection != null && !localConnection.isClosed()) {
			return true;
		}
		String url = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_URL);
		String username = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_USERNAME);
		String password = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD);
		String driver = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_DRIVER);

		localConnection = DriverManager.getConnection(url, username, password);

		if (localConnection != null) {
			return true;
		}
		return false;
	}

	public void disconnect() throws SQLException {
		try {
			localConnection.close();
		} catch (Exception e) {
			log.debug(e.getMessage());
		}
	}

	/***
	 * Method that starts all components of a Quest instance. Call this after
	 * creating the instance.
	 *
	 * @throws Exception
	 */
	public void setupRepository() throws Exception {

		OBDADataFactory fac = OBDADataFactoryImpl.getInstance();

		log.debug("Initializing Quest...");

		/*
		 * Input checking (we need to extend this)
		 */

		if (aboxMode.equals(QuestConstants.VIRTUAL) && inputOBDAModel == null) {
			throw new Exception("ERROR: Working in virtual mode but no OBDA model has been defined.");
		}

		/*
		 * Fixing the typing of predicates, in case they are not properly given.
		 */
		if (inputOBDAModel != null && !inputOntology.getVocabulary().isEmpty()) {
			inputOBDAModel = mappingVocabularyFixer.fixOBDAModel(inputOBDAModel,
					inputOntology.getVocabulary());
		}

		// TODO: better use this constructor.
		unfoldingOBDAModel = obdaFactory.createOBDAModel(new HashSet<OBDADataSource>(),
				new HashMap<URI, ImmutableList<OBDAMappingAxiom>>(),
				nativeQLFactory.create(new HashMap<String, String>()));

		/*
		 * Simplifying the vocabulary of the TBox
		 */

		reformulationReasoner = new TBoxReasonerImpl(inputOntology);

		if (bOptimizeEquivalences) {
			// generate a new TBox with a simpler vocabulary
			reformulationReasoner = TBoxReasonerImpl.getEquivalenceSimplifiedReasoner(reformulationReasoner);
		}
		vocabularyValidator = new VocabularyValidator(reformulationReasoner);

		try {

			/*
			 * Preparing the data source
			 */

			if (aboxMode.equals(QuestConstants.CLASSIC)) {
				//isSemanticIdx = true;

				if (inmemory) {
					String driver = "org.h2.Driver";
					String url = "jdbc:h2:mem:questrepository:" + System.currentTimeMillis()
							+ ";LOG=0;CACHE_SIZE=65536;LOCK_MODE=0;UNDO_LOG=0";
					String username = "sa";
					String password = "";

					obdaSource = fac.getDataSource(URI.create("http://www.obda.org/ABOXDUMP" + System.currentTimeMillis()));
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_DRIVER, driver);
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD, password);
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_URL, url);
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_USERNAME, username);
					obdaSource.setParameter(RDBMSourceParameterConstants.IS_IN_MEMORY, "true");
					obdaSource.setParameter(RDBMSourceParameterConstants.USE_DATASOURCE_FOR_ABOXDUMP, "true");
				} else {
					obdaSource = fac.getDataSource(URI.create("http://www.obda.org/ABOXDUMP" + System.currentTimeMillis()));

					if (aboxJdbcURL.trim().equals(""))
						throw new OBDAException("Found empty JDBC_URL parametery. Quest in CLASSIC/JDBC mode requires a JDBC_URL value.");

					if (aboxJdbcDriver.trim().equals(""))
						throw new OBDAException(
								"Found empty JDBC_DRIVER parametery. Quest in CLASSIC/JDBC mode requires a JDBC_DRIVER value.");

					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_DRIVER, aboxJdbcDriver.trim());
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD, aboxJdbcPassword);
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_URL, aboxJdbcURL.trim());
					obdaSource.setParameter(RDBMSourceParameterConstants.DATABASE_USERNAME, aboxJdbcUser.trim());
					obdaSource.setParameter(RDBMSourceParameterConstants.IS_IN_MEMORY, "false");
					obdaSource.setParameter(RDBMSourceParameterConstants.USE_DATASOURCE_FOR_ABOXDUMP, "true");
				}

				if (!aboxSchemaType.equals(QuestConstants.SEMANTIC_INDEX)) {
					throw new Exception(aboxSchemaType
							+ " is unknown or not yet supported Data Base type. Currently only the direct db type is supported");
				}

				// TODO one of these is redundant??? check
				connect();
				// setup connection pool
				setupConnectionPool();

				dataRepository = new RDBMSSIRepositoryManager(/*reformulationVocabulary*/);
				dataRepository.addRepositoryChangedListener(this);

				dataRepository.setTBox(reformulationReasoner);

				if (inmemory) {

					/*
					 * in this case we we work in memory (with H2), the database
					 * is clean and Quest will insert new Abox assertions into
					 * the database.
					 */

					/* Creating the ABox repository */

					if (!dataRepository.isDBSchemaDefined(localConnection)) {
						dataRepository.createDBSchema(localConnection, false);
						dataRepository.insertMetadata(localConnection);
					}

				} else {
					/*
					 * Here we expect the repository to be already created in
					 * the database, we will restore the repository and we will
					 * NOT insert any data in the repo, it should have been
					 * inserted already.
					 */
					dataRepository.loadMetadata(localConnection);

					// TODO add code to verify that the existing semantic index
					// repository can be used
					// with the current ontology, e.g., checking the vocabulary
					// of URIs, checking the
					// ranges w.r.t. to the ontology entailments, etc.

				}

				/* Setting up the OBDA model */

				URI sourceID = obdaSource.getSourceID();

				Map<URI, ImmutableList<OBDAMappingAxiom>> mappings = new HashMap<>();

				ImmutableList<OBDAMappingAxiom> joinedMappings = dataRepository.getMappings();
				mappings.put(sourceID, joinedMappings);
				Set<OBDADataSource> dataSources = new HashSet<>();
				dataSources.add(obdaSource);

				unfoldingOBDAModel = unfoldingOBDAModel.newModel(dataSources, mappings);
			}
			else if (aboxMode.equals(QuestConstants.VIRTUAL)) {
				// log.debug("Working in virtual mode");

				Set<OBDADataSource> sources = this.inputOBDAModel.getSources();
				if (sources == null || sources.size() == 0)
					throw new Exception(
							"No datasource has been defined. Virtual ABox mode requires exactly 1 data source in your OBDA model.");
				if (sources.size() > 1)
					throw new Exception(
							"Quest in virtual ABox mode only supports OBDA models with 1 single data source. Your OBDA model contains "
									+ sources.size() + " data sources. Please remove the aditional sources.");

				/* Setting up the OBDA model */

				obdaSource = sources.iterator().next();

				log.debug("Testing DB connection...");
				connect();

				// setup connection pool
				setupConnectionPool();

				/*
				 * Processing mappings with respect to the vocabulary
				 * simplification
				 */



				URI sourceUri = obdaSource.getSourceID();
				ImmutableList<OBDAMappingAxiom> originalMappings = inputOBDAModel.getMappings(sourceUri);
				ImmutableList<OBDAMappingAxiom> translatedMappings =
						vocabularyValidator.replaceEquivalences(inputOBDAModel.getMappings(obdaSource.getSourceID()));

				Map<URI, ImmutableList<OBDAMappingAxiom>> mappings = new HashMap<>();
				mappings.put(sourceUri, translatedMappings);

				// TODO: create the OBDA model here normally
				unfoldingOBDAModel = unfoldingOBDAModel.newModel(sources, mappings);
			}

			// NOTE: Currently the system only supports one data source.
			//
			OBDADataSource datasource = unfoldingOBDAModel.getSources().iterator().next();
			URI sourceId = datasource.getSourceID();


			// TODO: make the code generic enough so that this boolean is not needed.

			/**
			 * if the metadata was not already set,
			 * extracts DB metadata completely.
			 */

			if (metadata == null) {
				DBMetadataExtractor dbMetadataExtractor = nativeQLFactory.create();
				metadata = dbMetadataExtractor.extract(datasource, localConnection, unfoldingOBDAModel,
						userConstraints);
			}
			/**
			 * Otherwise, if partially configured, complete it by applying
			 * the user-defined constraints.
			 */
			else {
				//Adds keys from the text file
				if (userConstraints != null) {
					userConstraints.addConstraints(metadata);
				}
			}


			// This is true if the QuestDefaults.properties contains PRINT_KEYS=true
			// Very useful for debugging of User Constraints (also for the end user)
			if (printKeys) {
				// Prints all primary keys
				log.debug("\n====== Primary keys ==========");
				List<TableDefinition> table_list = metadata.getTableList();
				for (TableDefinition dd : table_list) {
					log.debug("\n" + dd.getName() + ":");
					for (Attribute attr : dd.getPrimaryKeys()) {
						log.debug(attr.getName() + ",");
					}
				}
				// Prints all foreign keys
				log.debug("\n====== Foreign keys ==========");
				for (TableDefinition dd : table_list) {
					log.debug("\n" + dd.getName() + ":");
					Map<String, List<Attribute>> fkeys = dd.getForeignKeys();
					for (String fkName : fkeys.keySet()) {
						log.debug("(" + fkName + ":");
						for (Attribute attr : fkeys.get(fkName)) {
							log.debug(attr.getName() + ",");
						}
						log.debug("),");
					}
				}
			}

            /*
             * We do not clone metadata here but because it will be updated during
             * the repository setup.
             * TODO: see if this comment is still relevant.
             *
             * However, please note that SQL Generator will never be used directly
             * but cloned for each QuestStatement.
             * When cloned, metadata is also cloned, so it should be "safe".
             */
			if (isSemanticIdx) {
				dataSourceQueryGenerator = questComponentFactory.create(metadata, datasource, dataRepository.getUriMap());
			}
			else {
				dataSourceQueryGenerator = questComponentFactory.create(metadata, datasource);
			}

			/**
			 * TODO: find a way to isolate (or remove if possible) this SQL-specific horror.
			 */
			boolean isSQL = preferences.getProperty(NativeQueryGenerator.class.getCanonicalName()).equals(
					SQLGenerator.class.getCanonicalName());
			if (isSQL) {
				String parameter = datasource.getParameter(RDBMSourceParameterConstants.DATABASE_DRIVER);
				SQLDialectAdapter sqladapter = SQLAdapterFactory.getSQLDialectAdapter(parameter, (QuestPreferences) preferences);
				preprocessProjection(localConnection, unfoldingOBDAModel.getMappings(sourceId), fac, sqladapter);
			}


			/***
			 * Starting mapping processing
			 */


			/**
			 * Split the mapping
			 */
			MappingSplitter mappingSplitler = new MappingSplitter();
			unfoldingOBDAModel = mappingSplitler.splitMappings(unfoldingOBDAModel, sourceId);


			/**
			 * Expand the meta mapping 
			 */
			MetaMappingExpander metaMappingExpander = new MetaMappingExpander(localConnection);
			unfoldingOBDAModel = metaMappingExpander.expand(unfoldingOBDAModel, sourceId);



			List<OBDAMappingAxiom> mappings = unfoldingOBDAModel.getMappings(obdaSource.getSourceID());
			unfolder = new QuestUnfolder(mappings, metadata);

			/***
			 * T-Mappings and Fact mappings
			 */


			if ((aboxMode.equals(QuestConstants.VIRTUAL))) {
				log.debug("Original mapping size: {}", unfolder.getRules().size());
				// Normalizing language tags: make all LOWER CASE

				unfolder.normalizeLanguageTagsinMappings();

				// Normalizing equalities
				unfolder.normalizeEqualities();

				// Apply TMappings
				unfolder.applyTMappings(reformulationReasoner, true);

				// Adding ontology assertions (ABox) as rules (facts, head with no body).
				unfolder.addClassAssertionsAsFacts(inputOntology.getClassAssertions());
				unfolder.addObjectPropertyAssertionsAsFacts(inputOntology.getObjectPropertyAssertions());
				unfolder.addDataPropertyAssertionsAsFacts(inputOntology.getDataPropertyAssertions());

				// Adding data typing on the mapping axioms.
				unfolder.extendTypesWithMetadata(reformulationReasoner);


				// Adding NOT NULL conditions to the variables used in the head
				// of all mappings to preserve SQL-RDF semantics
				unfolder.addNOTNULLToMappings();
			}

			// Initializes the unfolder
			unfolder.setup();

			//if ((aboxMode.equals(QuestConstants.VIRTUAL))) {
			multiTypedFunctionSymbolIndex = ImmutableMultimap.copyOf(unfolder.processMultipleTemplatePredicates());
			//}


			log.debug("DB Metadata: \n{}", metadata);

			/* The active ABox dependencies */
			sigma = LinearInclusionDependencies.getABoxDependencies(reformulationReasoner, true);
			
			/*
			 * Setting up the TBox we will use for the reformulation
			 */
			TBoxReasoner reasoner = reformulationReasoner;
			if (bOptimizeTBoxSigma) {
				SigmaTBoxOptimizer reducer = new SigmaTBoxOptimizer(reformulationReasoner);
				reasoner = new TBoxReasonerImpl(reducer.getReducedOntology());
			} 

			/*
			 * Setting up the reformulation engine
			 */

			if (reformulate == false) {
				rewriter = new DummyReformulator();
			}
			else if (QuestConstants.TW.equals(reformulationTechnique)) {
				rewriter = new TreeWitnessRewriter();
			}
			else {
				throw new IllegalArgumentException("Invalid value for argument: " + QuestPreferences.REFORMULATION_TECHNIQUE);
			}

			rewriter.setTBox(reasoner, sigma);

			/*
			 * Done, sending a new reasoner with the modules we just configured
			 */

			log.debug("... Quest has been initialized.");
		} catch (Exception e) {
			OBDAException ex = new OBDAException(e);
			if (e instanceof SQLException) {
				SQLException sqle = (SQLException) e;
				SQLException e1 = sqle.getNextException();
				while (e1 != null) {
					log.error("NEXT EXCEPTION");
					log.error(e1.getMessage());
					e1 = e1.getNextException();
				}
			}
			throw ex;
		} finally {
			if (!(aboxMode.equals(QuestConstants.CLASSIC) && (inmemory))) {
				/*
				 * If we are not in classic + inmemory mode we can disconnect
				 * the house-keeping connection, it has already been used.
				 */
				disconnect();
			}
		}
	}




	/**
	 * Has side-effects! Dangerous for concurrency when is called by a Quest statement!
	 *
	 * TODO: isolate it if this feature is really needed
	 */
	public void updateSemanticIndexMappings() throws DuplicateMappingException, OBDAException {
		/* Setting up the OBDA model */

		// TODO: is it necessary to copy mappings of other datasources??
		Map<URI, ImmutableList<OBDAMappingAxiom>> mappings = new HashMap<>(unfoldingOBDAModel.getMappings());
		mappings.put(obdaSource.getSourceID(), dataRepository.getMappings());
		unfoldingOBDAModel = unfoldingOBDAModel.newModel(unfoldingOBDAModel.getSources(), mappings);

		unfolder.updateSemanticIndexMappings(unfoldingOBDAModel.getMappings(obdaSource.getSourceID()),
				reformulationReasoner);

		//updateSigmaFromReasoner is not needed -- the reasoner does not change
		// Ontology aboxDependencies =  SigmaTBoxOptimizer.getSigmaOntology(reformulationReasoner);	
		// sigma.addEntities(aboxDependencies.getVocabulary());
		// sigma.addAssertions(aboxDependencies.getAssertions());	
	}


	/***
	 * Expands a SELECT * into a SELECT with all columns implicit in the *
	 *
	 * @param mappings
	 * @param factory
	 * @param adapter
	 * @throws SQLException
	 */
	private static void preprocessProjection(Connection localConnection, List<OBDAMappingAxiom> mappings,
											 OBDADataFactory factory, SQLDialectAdapter adapter)
			throws SQLException {

		// TODO this code seems buggy, it will probably break easily (check the
		// part with
		// parenthesis in the beginning of the for loop.

		Statement st = null;
		try {
			st = localConnection.createStatement();
			for (OBDAMappingAxiom axiom : mappings) {
				String sourceString = axiom.getSourceQuery().toString();

				/*
				 * Check if the projection contains select all keyword, i.e.,
				 * 'SELECT * [...]'.
				 */
				if (containSelectAll(sourceString)) {
					StringBuilder sb = new StringBuilder();

					// If the SQL string has sub-queries in its statement
					if (containChildParentSubQueries(sourceString)) {
						int childquery1 = sourceString.indexOf("(");
						int childquery2 = sourceString.indexOf(") AS child");
						String childquery = sourceString.substring(childquery1 + 1, childquery2);

						String copySourceQuery = createDummyQueryToFetchColumns(childquery, adapter);
						if (st.execute(copySourceQuery)) {
							ResultSetMetaData rsm = st.getResultSet().getMetaData();
							boolean needComma = false;
							for (int pos = 1; pos <= rsm.getColumnCount(); pos++) {
								if (needComma) {
									sb.append(", ");
								}
								String col = rsm.getColumnName(pos);
								//sb.append("CHILD." + col );
								sb.append("child.\"" + col + "\" AS \"child_" + (col)+"\"");
								needComma = true;
							}
						}
						sb.append(", ");

						int parentquery1 = sourceString.indexOf(", (", childquery2);
						int parentquery2 = sourceString.indexOf(") AS parent");
						String parentquery = sourceString.substring(parentquery1 + 3, parentquery2);

						copySourceQuery = createDummyQueryToFetchColumns(parentquery, adapter);
						if (st.execute(copySourceQuery)) {
							ResultSetMetaData rsm = st.getResultSet().getMetaData();
							boolean needComma = false;
							for (int pos = 1; pos <= rsm.getColumnCount(); pos++) {
								if (needComma) {
									sb.append(", ");
								}
								String col = rsm.getColumnName(pos);
								//sb.append("PARENT." + col);
								sb.append("parent.\"" + col + "\" AS \"parent_" + (col)+"\"");
								needComma = true;
							}
						}

						//If the SQL string doesn't have sub-queries
					} else

					{
						String copySourceQuery = createDummyQueryToFetchColumns(sourceString, adapter);
						if (st.execute(copySourceQuery)) {
							ResultSetMetaData rsm = st.getResultSet().getMetaData();
							boolean needComma = false;
							for (int pos = 1; pos <= rsm.getColumnCount(); pos++) {
								if (needComma) {
									sb.append(", ");
								}
								sb.append("\"" + rsm.getColumnName(pos) + "\"");
								needComma = true;
							}
						}
					}

					/*
					 * Replace the asterisk with the proper column names
					 */
					String columnProjection = sb.toString();
					String tmp = axiom.getSourceQuery().toString();
					int fromPosition = tmp.toLowerCase().indexOf("from");
					int asteriskPosition = tmp.indexOf('*');
					if (asteriskPosition != -1 && asteriskPosition < fromPosition) {
						String str = sourceString.replaceFirst("\\*", columnProjection);
						axiom.setSourceQuery(factory.getSQLQuery(str));
					}
				}
			}
		} finally {
			if (st != null) {
				st.close();
			}
		}
	}

	private static final String selectAllPattern = "(S|s)(E|e)(L|l)(E|e)(C|c)(T|t)\\s+\\*";
	private static final String subQueriesPattern = "\\(.*\\)\\s+(A|a)(S|s)\\s+(C|c)(H|h)(I|i)(L|l)(D|d),\\s+\\(.*\\)\\s+(A|a)(S|s)\\s+(P|p)(A|a)(R|r)(E|e)(N|n)(T|t)";

	private static boolean containSelectAll(String sql) {
		final Pattern pattern = Pattern.compile(selectAllPattern);
		return pattern.matcher(sql).find();
	}

	private static boolean containChildParentSubQueries(String sql) {
		final Pattern pattern = Pattern.compile(subQueriesPattern);
		return pattern.matcher(sql).find();
	}

	private static String createDummyQueryToFetchColumns(String originalQuery, SQLDialectAdapter adapter) {
		String toReturn = String.format("select * from (%s) view20130219 ", originalQuery);
		if (adapter instanceof SQLServerSQLDialectAdapter) {
			SQLServerSQLDialectAdapter sqlServerAdapter = (SQLServerSQLDialectAdapter) adapter;
			toReturn = sqlServerAdapter.sqlLimit(toReturn, 1);
		} else {
			toReturn += adapter.sqlSlice(0, Long.MIN_VALUE);
		}
		return toReturn;
	}



	private void setupConnectionPool() {
		String url = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_URL);
		String username = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_USERNAME);
		String password = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD);
		String driver = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_DRIVER);

		poolProperties = new PoolProperties();
		poolProperties.setUrl(url);
		poolProperties.setDriverClassName(driver);
		poolProperties.setUsername(username);
		poolProperties.setPassword(password);
		poolProperties.setJmxEnabled(true);

		// TEST connection before using it
		poolProperties.setTestOnBorrow(keepAlive);
		if (keepAlive) {
			if (driver.contains("oracle"))
				poolProperties.setValidationQuery("select 1 from dual");
			else if (driver.contains("db2"))
				poolProperties.setValidationQuery("select 1 from sysibm.sysdummy1");
			else
				poolProperties.setValidationQuery("select 1");
		}

		poolProperties.setTestOnReturn(false);
		poolProperties.setMaxActive(maxPoolSize);
		poolProperties.setMaxIdle(maxPoolSize);
		poolProperties.setInitialSize(startPoolSize);
		poolProperties.setMaxWait(30000);
		poolProperties.setRemoveAbandonedTimeout(abandonedTimeout);
		poolProperties.setMinEvictableIdleTimeMillis(30000);
		poolProperties.setLogAbandoned(logAbandoned);
		poolProperties.setRemoveAbandoned(removeAbandoned);
		poolProperties.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;"
				+ "org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer");
		tomcatPool = new DataSource();
		tomcatPool.setPoolProperties(poolProperties);

		log.debug("Connection Pool Properties:");
		log.debug("Start size: " + startPoolSize);
		log.debug("Max size: " + maxPoolSize);
		log.debug("Remove abandoned connections: " + removeAbandoned);

	}

	public void close() {
		tomcatPool.close();
	}

	public void releaseSQLPoolConnection(Connection co) {
		try {
			co.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public synchronized Connection getSQLPoolConnection() throws OBDAException {
		Connection conn = null;
		try {
			conn = tomcatPool.getConnection();
		} catch (SQLException e) {
			throw new OBDAException(e);
		}
		return conn;
	}

	/***
	 * Establishes a new connection to the data source. This is a normal JDBC
	 * connection. Used only internally to get metadata at the moment.
	 *
	 * @return
	 * @throws OBDAException
	 */
	private Connection getSQLConnection() throws OBDAException {
		Connection conn;

		String url = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_URL);
		String username = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_USERNAME);
		String password = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD);
		String driver = obdaSource.getParameter(RDBMSourceParameterConstants.DATABASE_DRIVER);

		try {
			conn = DriverManager.getConnection(url, username, password);
		} catch (SQLException e) {
			throw new OBDAException(e.getMessage());
		} catch (Exception e) {
			throw new OBDAException(e.getMessage());
		}
		return conn;
	}

	// get a real (non pool) connection - used for protege plugin
	public QuestConnection getNonPoolConnection() throws OBDAException {

		return new QuestConnection(this, getSQLConnection());
	}

	/***
	 * Returns a QuestConnection, the main object that a client should use to
	 * access the query answering services of Quest. With the QuestConnection
	 * you can get a QuestStatement to execute queries.
	 *
	 * <p>
	 * Note, the QuestConnection is not a normal JDBC connection. It is a
	 * wrapper of one of the N JDBC connections that quest's connection pool
	 * starts on initialization. Calling .close() will not actually close the
	 * connection, with will just release it back to the pool.
	 * <p>
	 * to close all connections you must call Quest.close().
	 *
	 * @return
	 * @throws OBDAException
	 */
	public QuestConnection getConnection() throws OBDAException {

		return new QuestConnection(this, getSQLPoolConnection());
	}

	public DBMetadata getMetaData() {
		return metadata;
	}

	public UriTemplateMatcher getUriTemplateMatcher() {
		return unfolder.getUriTemplateMatcher();
	}

	@Override
	public DatalogProgram unfold(DatalogProgram query, String targetPredicate) throws OBDAException {
		return unfolder.unfold(query, targetPredicate);
	}

	public void repositoryChanged() {
		// clear cache
		this.queryCache.clear();
	}

	public boolean isSemIdx() {
		return (dataRepository != null);
	}

	public RDBMSSIRepositoryManager getSemanticIndexRepository() {
		return dataRepository;
	}
}
