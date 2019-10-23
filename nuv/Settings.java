import edu.wisc.cs.arc.Logger.Level;
import edu.wisc.cs.arc.repair.RepairTasks.LocalizeMethod;
import edu.wisc.cs.arc.repair.graph.GraphModifier.GraphModifierAlgo;
import edu.wisc.cs.arc.repair.graph.GraphModifier.GraphModifierObj;
import org.apache.commons.cli.*;

/**
 * Stores and parses settings for the ETG generator/verifier.
 * @author Aaron Gember-Jacobson (agember@cs.wisc.edu)
 */
public class Settings {
	public final static String HELP = "help";
	public final static String LOG_LEVEL = "loglevel";

    // Generate ETGs
	private final static String CONFIGS_DIRECTORY = "configs";
	//add
	private final static String CONFIGS_DIRECTORY2 = "configs2";
	private final static String ANONYMIZE = "anon";
	private final static String IGNORED_POLICY_GROUP_SIZE = "minhosts";
	private final static String PARALLELIZE = "parallelize";
	private final static String WARN_ASSUMPTIONS = "warn";
	private final static String ENTIRE_FLOWSPACE = "allflows";
	private final static String INTERNAL_ONLY = "internalonly";
	private final static String BASE_ONLY = "baseonly";
	private final static String DESTINATION_ETGS = "dstetgs";
	private final static String USE_DESCRIPTIONS = "descriptions";
	private final static String ROUTERS_ONLY = "routersonly";
	private final static String WAYPOINTS_FILE = "waypoints";

	// Generate extra output
	private final static String GENERATE_GRAPHVIZ = "graphviz";
	private final static String SERIALIZE_ETGS = "serialize";
	private final static String GENERATE_VIRL = "virl";
	private final static String SERIALIZE_CONFIGS = "serializeconfigs";
	private final static String MINESWEEPER_POLICIES = "minesweeperpolicies";

	//Save Simplified Configs
	private final static String SAVE_SIMPLE_CONFIGS = "savesimplify";
	private final static String SIMPLIFY_CONFIGS = "simplify";
	private final static String TEST_GRAPHS = "testgraphs";

	// Verification
	private final static String SUMMARIZE = "summarize";
	private final static String VERIFY_ALL = "vall";
	private final static String VERIFY_CURRENTLY_BLOCKED = "vcb";
	private final static String VERIFY_ALWAYS_BLOCKED = "vab";
	private final static String VERIFY_ALWAYS_REACHABLE = "var";
	private final static String VERIFY_ALWAYS_WAYPOINT = "vaw";
	private final static String VERIFY_ALWAYS_ISOLATED = "vai";
	private final static String VERIFY_PRIMARY_PATH = "vpp";
	private final static String VERIFY_PATH_EQUIVALENT = "vpe";
	private final static String VERIFY_EQUIVALENCE = "veq";
	private final static String DETAILED_TIMING = "timing";

	//Policy Verification against a serialized policy file
	private final static String CHECK_POLICIES = "checkpolicies";
	private final static String LOCALIZE_FAULTS = "localize";

	// Save policies
	private final static String SAVE_POLICIES = "savepolicies";
	private final static String EDIT_POLICIES = "editpolicies";
	private final static String CONVERT_POLICIES = "convertpolicies";

	// Repair
	private final static String REPAIR = "repair";
	private final static String MODIFIER = "modifier";
	private final static String OBJECTIVE = "objective";
	private final static String COMPARE_ETGS = "compareetgs";
	private final static String ONLY_REPAIR_BROKEN = "onlybroken";
	private final static String COMPARE_CONFIGS = "compareconfigs";
	private final static String TOKEN_STATS = "tokenstats";
	private final static String GENETIC_REPAIR = "genrepair";
	private final static String REPAIR_LINES = "repairlines";
	private final static String EXPORT_DIR = "exportdir";
	private final static String SAVE_MODS = "savemods";

    // Generate ETGs //////////////////////////////////////////////////////////

	/** Which logging level should be enabled */
	private Level logLevel;

	/** Where are the config files store? */
	private String configsDirectory;
	
	/** Where are the config files store? */
	private String configsDirectory2;


	/** Should the output be anonymized? */
	private boolean anonymize;

	/** What is the minimum size policy group? */
	private int minPolicyGroupsSize;

	/** Should ETGs be generated and verified in parallel? */
	private boolean parallelize;

	/** Should violations of assumptions during ETG generation just trigger
	 * warnings? */
	private boolean warnAssumptions;

	/** Should the entire flowspace be added as a policy group? */
	private boolean entireFlowspace;

	/** Should only internal policy groups be considered? */
	private boolean internalOnly;

	/** Should only the base ETG be generated? */
	private boolean baseOnly;

	/** Should destination-based ETG be generated? */
	private boolean destinationEtgs;

	/** Should the device-based ETG be constructed based on interface
	 * descriptions in device configurations? */
	private boolean useDescriptions;

	/** Should only devices with routing stanzas be considered? */
	private boolean routersOnly;
	
	/** Where file contains the list of waypoint edges? */
	private String waypointsFile;

	// Generate extra output //////////////////////////////////////////////////
	/** Where should graph files be stored? */
	private String graphvizDirectory;

	/**Where are the folders where VERIFIED .gv files for each test case are stored? */
	private String testGraphDirectory;

	/** Where should serialized ETGs be stored? */
	private String serializedETGsFile;

	/** Where should a Cisco Virtual Internet Routing Lab (VIRL) file be
	 * stored? */
	private String virlFile;

	/** Where should serialized configs be stored? */
    private String serializedConfigsFile;

    /** Where should serialized modifications be stored? */
    private String serializedModificationsFile;

	/** Should a simple version of config files be printed? */
	private boolean simplify_configs;

	/** Where should simple configs be saved? */
	private String simpleConfigsSaveFile;
	
	/** Where should a list of policies to be checked using minesweeper be 
	 * stored? */
	private String minesweeperPoliciesFile;

	// Verification ///////////////////////////////////////////////////////////

	/** Should the verification results be summarized? */
	private boolean summarize;

	/** Should the currently blocked verifier be run? */
	private boolean verifyCurrentlyBlocked;

	/** Should the always blocked verifier be run? */
	private boolean verifyAlwaysBlocked;

	/** Should the always reachable verifier be run? */
	private int verifyAlwaysReachable;
	
	/** Should the always traverse waypoint verifier be run? */
    private boolean verifyAlwaysWaypoint;

	/** Should the always isolated verifier be run? */
	private boolean verifyAlwaysIsolated;
	
	/** Should the primary path verifier be run? */
	private boolean verifyPrimaryPath;

	/** Should the paths verifier be run? */
	private String verifyPathEquivalent;

	/** Should the equivalence verifier be run? */
	private String verifyEquivalence;

	/** Should per-flow timing information be output during verification? */
	private boolean perflowVerifcationTimes;

	//test flowETGs against a policy File
	private String checkPoliciesFile;

	private LocalizeMethod faultLocalizeMethod;

	// Save policies //////////////////////////////////////////////////////////

	/** Where should the policies be saved? */
	private String policiesSaveFile;

	/** Which file of policies should be edited? */
	private String policiesEditFile;
	
	/** Which file of policies should be converted? */
	private String policiesConvertFile;

	// Repair /////////////////////////////////////////////////////////////////

	/** Where are the policies the network should conform to stored? */
	private String policiesRepairFile;

	/** What algorithm should be used for determining ETG modifications? */
	private GraphModifierAlgo modifierAlgorithm;

	/** What objective should be used for determining ETG modifications? */
	private GraphModifierObj modifierObjective;

	/** Which serialized ETGs should the generated ETGs be compared with? */
	private String compareETGsFile;

	/** Should repair only be attempted for broken ETGs? */
	private boolean onlyRepairBrokenETGs;

	/** Which serialized configs should generated configs be compared with? */
    private String compareConfigsFile;

    /** Should files be parsed to compute stats about tokens/statement similarities*/
    private boolean shouldComputeTokenStats;

    /** Should repair be performed using Genetic Programming*/
    private boolean shouldGeneticRepair;

	/** Which file be used to determine repair lines for Genetic Programming?*/
	private String repairLinesFile;

	/** The directory to export repairs */
	private String repairExportPath;

	/** Logger */
	private Logger logger;

	/**
	 * Obtain settings from command line arguments.
	 * @param args command line arguments
	 * @throws ParseException
	 */
	public Settings(String[] args) throws ParseException {
		Options options = this.getOptions();

		// Output help text, if help argument is passed
		for (String arg : args) {
			if (arg.equals("-"+HELP)) {
		        HelpFormatter formatter = new HelpFormatter();
		        formatter.setLongOptPrefix("-");
		        formatter.printHelp(Driver.class.getName(), options, true);
				System.exit(0);
			}
		}

		// Parse command line arguments
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			System.out.println("lyh---test");
			line = parser.parse(options, args);
			System.out.println("lyh---test");
		}
		catch(MissingOptionException e) {
			throw new ParseException("Missing required argument: -"
					+ e.getMissingOptions().get(0));
		}
		catch(MissingArgumentException e) {
			throw new ParseException("Missing argument for option "
					+ e.getOption().getLongOpt());
		}
		
		this.logLevel = Level.valueOf(line.getOptionValue(LOG_LEVEL, "INFO"));
		this.logger = Logger.getInstance(logLevel);

		// Generate ETGs
		this.configsDirectory = line.getOptionValue(CONFIGS_DIRECTORY);
		this.configsDirectory2 = line.getOptionValue(CONFIGS_DIRECTORY2);
		this.anonymize = line.hasOption(ANONYMIZE);
		try {
			this.minPolicyGroupsSize = Integer.parseInt(
					line.getOptionValue(IGNORED_POLICY_GROUP_SIZE,"0"));
		} catch(NumberFormatException e) {
			throw new ParseException(
					"Ignored policy group size is not a number");
		}
		this.parallelize = line.hasOption(PARALLELIZE);
		this.warnAssumptions = line.hasOption(WARN_ASSUMPTIONS);
		this.entireFlowspace = line.hasOption(ENTIRE_FLOWSPACE);
		this.internalOnly = line.hasOption(INTERNAL_ONLY);
		this.baseOnly = line.hasOption(BASE_ONLY);
		this.destinationEtgs = line.hasOption(DESTINATION_ETGS);
		this.useDescriptions = line.hasOption(USE_DESCRIPTIONS);
		this.routersOnly = line.hasOption(ROUTERS_ONLY);
		this.waypointsFile = line.getOptionValue(WAYPOINTS_FILE);

		// Generate extra output
		this.graphvizDirectory = line.getOptionValue(GENERATE_GRAPHVIZ);
		this.testGraphDirectory = line.getOptionValue(TEST_GRAPHS);
		this.serializedETGsFile = line.getOptionValue(SERIALIZE_ETGS);
		this.serializedConfigsFile = line.getOptionValue(SERIALIZE_CONFIGS);
		this.virlFile = line.getOptionValue(GENERATE_VIRL);
		this.simplify_configs = line.hasOption(SIMPLIFY_CONFIGS);
		this.simpleConfigsSaveFile = line.getOptionValue(SAVE_SIMPLE_CONFIGS);
		if (this.shouldSaveSimpleConfigs()) {
		    this.simplify_configs = true;
		}
		this.minesweeperPoliciesFile = line.getOptionValue(MINESWEEPER_POLICIES);

		// Verification
		this.summarize = line.hasOption(SUMMARIZE);
		if(line.hasOption(VERIFY_ALL)) {
			this.verifyCurrentlyBlocked = true;
			this.verifyAlwaysBlocked = true;
			this.verifyAlwaysReachable = 1;
			this.verifyAlwaysWaypoint = true;
			this.verifyAlwaysIsolated = true;
			this.verifyPrimaryPath = true;
		}
		else {
			this.verifyCurrentlyBlocked =
					line.hasOption(VERIFY_CURRENTLY_BLOCKED);
			this.verifyAlwaysBlocked = line.hasOption(VERIFY_ALWAYS_BLOCKED);
			try {
				this.verifyAlwaysReachable = Integer.parseInt(
						line.getOptionValue(VERIFY_ALWAYS_REACHABLE, "-1"));
			} catch(NumberFormatException e) {
				throw new ParseException(
						"Failure bound is not a number");
			}
			this.verifyAlwaysWaypoint = line.hasOption(VERIFY_ALWAYS_WAYPOINT);
			this.verifyAlwaysIsolated = line.hasOption(VERIFY_ALWAYS_ISOLATED);
			this.verifyPrimaryPath = line.hasOption(VERIFY_PRIMARY_PATH);
			this.verifyPathEquivalent = line.getOptionValue(
					VERIFY_PATH_EQUIVALENT);
			this.verifyEquivalence = line.getOptionValue(VERIFY_EQUIVALENCE);
		}
		this.perflowVerifcationTimes = line.hasOption(DETAILED_TIMING);
		this.checkPoliciesFile = line.getOptionValue(CHECK_POLICIES);
		if (line.hasOption(LOCALIZE_FAULTS)) {
			this.faultLocalizeMethod = LocalizeMethod.values()[0];
			try {
				this.faultLocalizeMethod = LocalizeMethod.valueOf(
						line.getOptionValue(LOCALIZE_FAULTS).toUpperCase());
			} catch (IllegalArgumentException e) {
				this.logger.error("Invalid localize method: "
						+ line.getOptionValue(LOCALIZE_FAULTS) + "; using "
						+ this.faultLocalizeMethod.toString());
			}
		}
			
		// Save policies
		this.policiesSaveFile = line.getOptionValue(SAVE_POLICIES);
		this.policiesEditFile = line.getOptionValue(EDIT_POLICIES);
		this.policiesConvertFile = line.getOptionValue(CONVERT_POLICIES);

		// Repair
		this.policiesRepairFile = line.getOptionValue(REPAIR);
		if (this.shouldRepair()) {
			this.baseOnly = false;
			this.destinationEtgs = true;
			this.modifierAlgorithm = GraphModifierAlgo.values()[0];
			if (line.hasOption(MODIFIER)) {
				try {
					this.modifierAlgorithm = GraphModifierAlgo.valueOf(
							line.getOptionValue(MODIFIER).toUpperCase());
				} catch (IllegalArgumentException e) {
					this.logger.error("Invalid modifier algorithm: "
							+ line.getOptionValue(MODIFIER) + "; using "
							+ this.modifierAlgorithm.toString());
				}
			}
			this.modifierObjective = GraphModifierObj.values()[0];
			if (line.hasOption(OBJECTIVE)) {
				try {
					this.modifierObjective = GraphModifierObj.valueOf(
							line.getOptionValue(OBJECTIVE).toUpperCase());
				} catch (IllegalArgumentException e) {
					this.logger.error("Invalid modifier objective: "
							+ line.getOptionValue(MODIFIER) + "; using "
							+ this.modifierObjective.toString());
				}
			}
		}
		this.compareETGsFile = line.getOptionValue(COMPARE_ETGS);
		this.onlyRepairBrokenETGs = line.hasOption(ONLY_REPAIR_BROKEN);
	    this.compareConfigsFile = line.getOptionValue(COMPARE_CONFIGS);
		this.shouldComputeTokenStats = line.hasOption(TOKEN_STATS);
		this.shouldGeneticRepair = line.hasOption(GENETIC_REPAIR);
		this.repairLinesFile = line.getOptionValue(REPAIR_LINES);

		this.repairExportPath = line.getOptionValue(EXPORT_DIR);
		this.serializedModificationsFile = line.getOptionValue(SAVE_MODS);
	}

	/**
	 * Set up the list of command line arguments the program accepts.
	 * @return arguments the program accepts
	 */
	private Options getOptions() {
		Options options = new Options();
		options.addOption(HELP, false,
				"Print usage information");
		
		Option option = new Option(LOG_LEVEL, true,
				"Set logging level");
		option.setArgName("LEVEL");
		options.addOption(option);
		 

		Option  option2 = new Option(CONFIGS_DIRECTORY2, true,
				"Directory containing configuration files");
		options.addOption(option2);
		// Generate ETGs //////////////////////////////////////////////////////
		option = new Option(CONFIGS_DIRECTORY, true,
				"Directory containing configuration files");
		//option.setRequired(true);
		option.setArgName("DIR");
		options.addOption(option);

		options.addOption(ANONYMIZE, false,
				"Anonymize output");

		option = new Option(IGNORED_POLICY_GROUP_SIZE, true,
				"Ignore policy groups which have no more than SIZE hosts");
		option.setArgName("SIZE");
		options.addOption(option);

		options.addOption(PARALLELIZE, false,
				"Generate and verify ETGs in parallel");

		options.addOption(WARN_ASSUMPTIONS, false,
				"Output a warning when an assumption is violated during ETG "
				+ "construction, rather than ending the process with an error");

		options.addOption(ENTIRE_FLOWSPACE, false,
				"Include the entire flowspace as a policy group");

		options.addOption(INTERNAL_ONLY, false,
				"Only include internal policy groups");

		options.addOption(BASE_ONLY, false,
				"Only generate base ETG");

		options.addOption(DESTINATION_ETGS, false,
				"Generate destination-based ETGs");

		/*options.addOption(PHYSICAL_ONLY, false,
				"Only generate physical topology ETG");*/

		options.addOption(USE_DESCRIPTIONS, false,
				"Construct the device-based ETG based on interface descriptions"
				+ " in device configurations");

		options.addOption(ROUTERS_ONLY, false,
				"Only include devices that containing router stanzas");
		
		option = new Option(WAYPOINTS_FILE, true,
				"File containing list of waypoint edges");
		option.setArgName("FILE");
		options.addOption(option);

		// Generate extra output //////////////////////////////////////////////
		option = new Option(GENERATE_GRAPHVIZ, true,
				"Generate graph files in DIR");
		option.setArgName("DIR");
		options.addOption(option);

		option = new Option(TEST_GRAPHS, true,
				"Test Generated Graphs with .gv files in DIR");
		option.setArgName("DIR");
		options.addOption(option);

		option = new Option(SERIALIZE_ETGS, true,
				"Serialize generated/repaired ETGs to FILE");
		option.setArgName("FILE");
		options.addOption(option);

		option = new Option(GENERATE_VIRL, true,
				"Generate a Cisco Virtual Internet Routing Lab (VIRL) in FILE");
		option.setArgName("FILE");
		options.addOption(option);

		option = new Option(SERIALIZE_CONFIGS, true,
                "Serialize generated/repaired configurations to FILE");
        option.setArgName("FILE");
        options.addOption(option);

		options.addOption(SIMPLIFY_CONFIGS, false,
				"Simplify Configuration Files");

		option = new Option(SAVE_SIMPLE_CONFIGS, true,
				"Save Simplified Configuration files to FILE");
		option.setArgName("FILE");
		options.addOption(option);
		
		option = new Option(MINESWEEPER_POLICIES, true,
				"Save to FILE a list of policies to check using Minesweeper");
		option.setArgName("FILE");
		options.addOption(option);

		// Verification ///////////////////////////////////////////////////////
		options.addOption(SUMMARIZE, false,
				"Only output a summary of verification results");

		options.addOption(VERIFY_ALL, false,
				"Run all verifiers");

		options.addOption(VERIFY_CURRENTLY_BLOCKED, false,
				"Verify currently blocked");

		options.addOption(VERIFY_ALWAYS_BLOCKED, false,
				"Verify always blocked");

		option = new Option(VERIFY_ALWAYS_REACHABLE, true,
				"Verify always reachable under less than K failures");
		option.setArgName("K");
		options.addOption(option);
		
		options.addOption(VERIFY_ALWAYS_WAYPOINT, false,
                "Verify always traverse waypoint");

		options.addOption(VERIFY_ALWAYS_ISOLATED, false,
				"Verify always isolated");
		
		options.addOption(VERIFY_PRIMARY_PATH, false,
				"Verify primary path");

		option = new Option(VERIFY_PATH_EQUIVALENT, true,
				"Verify paths computed using ETGs are equivalent to paths"
				+ " computed using output from Parse Cisco Virtual Internet"
				+ " Routing Lab (VIRL) stored in FILE");
		option.setArgName("FILE");
		options.addOption(option);

		option = new Option(VERIFY_EQUIVALENCE, true,
				"Verify ETGs for the specified configurations are equivalent"
				+ " to the serialized ETGs stored in FILE");
		option.setArgName("FILE");
		options.addOption(option);

		options.addOption(DETAILED_TIMING, false,
				"Output per-flow (pair) verification times information");

		option = new Option(CHECK_POLICIES, true,
				"See if policies listed in file are satisfied");
		option.setArgName("FILE");
		options.addOption(option);

		option  = new Option(LOCALIZE_FAULTS, true,
				"Perform localization to finding faulty stanzas in configurations"
				+ " that cause policy violations.");
		option.setArgName("TYPE");
		options.addOption(option);


		// Save policies //////////////////////////////////////////////////////
		option = new Option(SAVE_POLICIES, true,
				"Save policies derived from verification results in FILE");
		option.setArgName("FILE");
		options.addOption(option);

		option = new Option(EDIT_POLICIES, true,
				"Edit policies in FILE");
		option.setArgName("FILE");
		options.addOption(option);
		
		option = new Option(CONVERT_POLICIES, true,
				"Convert Minesweeper policies in FILE to ARC policies");
		option.setArgName("FILE");
		options.addOption(option);

		// Repair /////////////////////////////////////////////////////////////
		option = new Option(REPAIR, true,
				"Repair the network to satisfy the policies in FILE;"
				+ " implies -noprune");
		option.setArgName("FILE");
		options.addOption(option);

		String[] algos = new String[GraphModifierAlgo.values().length];
		for (int i = 0; i < algos.length; i++) {
			algos[i] = GraphModifierAlgo.values()[i].toString();
		}
		option = new Option(MODIFIER, true,
				"Modify ETGs using one of the following methods: "
				+ String.join(", ", algos)
				+ "; ignored if -repair option is not included");
		option.setArgName("METHOD");
		options.addOption(option);

		String[] objectives = new String[GraphModifierObj.values().length];
		for (int i = 0; i < objectives.length; i++) {
			objectives[i] = GraphModifierObj.values()[i].toString();
		}
		option = new Option(OBJECTIVE, true,
				"Modify ETGs using one of the following objectives: "
				+ String.join(", ", algos)
				+ "; ignored if -repair option is not included");
		option.setArgName("OBJECTIVE");
		options.addOption(option);

		option = new Option(COMPARE_ETGS, true,
				"Compare generated/repaired ETGs to those in FILE");
		option.setArgName("FILE");
		options.addOption(option);

		options.addOption(ONLY_REPAIR_BROKEN, false,
				"Only repair broken ETGs");

		option = new Option(COMPARE_CONFIGS, true,
                "Compare generated/repaired configurations to those in FILE");
        option.setArgName("FILE");
        options.addOption(option);

		option = new Option(TOKEN_STATS, false,
				"Compute token stats over diffs in DIR");
		options.addOption(option);


		option = new Option(GENETIC_REPAIR, false,
				"Repair configurations using genetic programming");
		options.addOption(option);

		option = new Option(REPAIR_LINES, true,
				"File containing lines for repair");
		option.setArgName("FILE");
		options.addOption(option);

		option = new Option(EXPORT_DIR, true,
                "File path for repaired config files.");
        option.setArgName("FILE");
        options.addOption(option);

		option = new Option(SAVE_MODS, true,
				"File path for serialized modifications.");
		option.setArgName("FILE");
		options.addOption(option);

		return options;
	}


	/**
	 * Determine if simplified configs should be printed
	 * @return true if simple configs should be printed
	 */
	public boolean shouldSimplifyConfigs(){
		return (this.simplify_configs);
	}


	/**
	 * Determine whether to save simple configs
	 * in a file.
	 * @return true if simple configs should be saved, otherwise false
	 */
	public boolean shouldSaveSimpleConfigs() {
		return (this.simpleConfigsSaveFile != null);
	}

	/**
	 * Determine if and where simple configs should be saved
	 * @return the path to a file where simple configs should be saved, or null if
	 * 		they should not be saved
	 */
	public String getSimpleConfigsSaveFile() {
		return this.simpleConfigsSaveFile;
	}

	/**
	 * Determine where configuration files are stored.
	 * @return the path to a directory containing the configuration files
	 */
	public String getConfigsDirectory() {
		return this.configsDirectory;
	}

	/**
	 * Determine if output should be anonymized.
	 * @return true the output should be anonymized, otherwise false
	 */
	public boolean shouldAnonymize() {
		return this.anonymize;
	}

	/**
	 * Determine if policy groups should be ignored based on the number of
	 * hosts they contain.
	 * @return true if some policy groups should be ignored, otherwise false
	 */
	public boolean shouldIgnoreSmallPolicyGroups() {
		return (this.minPolicyGroupsSize <= 0);
	}

	/**
	 * Determine the minimum number of hosts a policy group must contain to be
	 * of interest.
	 * @return the minimum size of policy groups of interest
	 */
	public int getMinPolicyGroupsSize() {
		return this.minPolicyGroupsSize;
	}

	/**
	 * Determine if ETGs should be generated/verified in parallel.
	 * @return true these actions should happen in parallel, otherwise false
	 */
	public boolean shouldParallelize() {
		return this.parallelize;
	}

	/**
	 * Determine whether violations of assumptions during ETG generation should
	 * just trigger a warning instead of ending the process with an error.
	 * @return true if assumption violations should only result in warnings,
	 * 		otherwise false
	 */
	public boolean shouldWarnAssumptions() {
		return this.warnAssumptions;
	}

	/**
	 * Determine whether the entire flowspace should be added as a policy group.
	 * @return true if the entire flowspace should be added
	 */
	public boolean shouldIncludeEntireFlowspace() {
		return this.entireFlowspace;
	}

	/**
	 * Determine whether external policy groups should be ignored.
	 * @return true if external policy groups should be ignored; otherwise false
	 */
	public boolean shouldExcludeExternalFlows() {
		return this.internalOnly;
	}

	/**
	 * Determine whether to generate the device-based ETG based on interface
	 * descriptions in device configurations
	 * @return true if interface descriptions should be used, false if the
	 * 		device-based ETG should be generated based on the process-based ETG
	 */
	public boolean shouldUseInterfaceDescriptions() {
		return this.useDescriptions;                // printWriter.println("SRC :" +splitResult[1]+ " DST :" + splitResult[1] + "  WEIGHT " +splitResult[5]);

	}

	/**
	 * Determine whether to generate per-flow ETGs.
	 * @return true if per-flow ETGs should be generated, otherwise false
	 */
	public boolean shouldGenerateFlowETGs() {
		return !this.baseOnly; // || !this.physicalOnly;
	}

	/**
	 * Determine whether to generate per-destination ETGs.
	 * @return true if per-destination ETGs should be generated, otherwise false
	 */
	public boolean shouldGenerateDestinationETGs() {
		return this.destinationEtgs;
	}

	/**
	 * Determine whether to exclude devices that do not containing routing
	 * stanzas.
	 * @return true if devices without routing stanzas should be ignored,
	 * 		otherwise false
	 */
	public boolean shouldExcludeNonRouters() {
		return this.routersOnly;
	}
	
	/**
	 * Determine whether a file containing of waypoint edges is provided.
	 * @return true if a file containing a list of waypoints edges is provided,
	 * 		otherwise false
	 */
	public boolean hasWaypoints() {
		return (this.waypointsFile != null);
	}
	
	/**
	 * Determine where the file of waypoint edges is stored.
	 * @return the path to a file containing a list of waypoint edges
	 */
	public String getWaypointsFile() {
		return this.waypointsFile;
	}

	/**
	 * Determine if graphviz files should be generated.
	 * @return true if graphviz files should be generated, otherwise false
	 */
	public boolean shouldGenerateGraphviz() {
		return (this.graphvizDirectory != null);
	}

	/**
	* Determine where graphviz files should be stored.
	* @return the path to a directory where graphviz files should be stored, or
	* 		null if they should not be generated
	*/
	public String getGraphvizDirectory() {
		return this.graphvizDirectory;
	}

	/**
	 *
	 * Determine if generatedGraphs should be tested with expected .gv files.
	 *@return true if graph files need to be tested, otherwise false
	 */
	public boolean shouldTestGraphs(){
		return (this.testGraphDirectory!=null);
	}

	/**
	* Determine where graph files should be stored.
	* @return the path to a directory where expected graph files should be stored, or
	* 		null if the generated graphs need not be tested.
	*/
	public String getTestGraphsDirectory() {
		return this.testGraphDirectory;
	}

	/**
	 * Determine if ETGs should be serialized and stored.
	 * @return true if ETGs should be serialized, otherwise false
	 */
	public boolean shouldSerializeETGs() {
		return (this.serializedETGsFile != null);
	}

	/**
	 * Determine where serialized ETGs should be stored.
	 * @return the path to a file where serialized ETGs should be stored, or
	 * 		null if they should not be serialized
	 */
	public String getSerializedETGsFile() {
		return this.serializedETGsFile;
	}

	/**
	 * Determine whether a Cisco Virtual Internet Routing Lab (VIRL) file should
	 * be generated.
	 * @return true if a VIRL file should be generated, otherwise false
	 */
	public boolean shouldGenerateVirl() {
		return (this.virlFile != null);
	}

	/**
	 * Determine where a Cisco Virtual Internet Routing Lab (VIRL) file should
	 * be generated.
	 * @return the path to a file where a VIRL file should be stored, or
	 * 		null if no VIRL file should be generated
	 */
	public String getVirlFile() {
		return this.virlFile;
	}

	/**
     * Determine if configs should be serialized and stored.
     * @return true if configs should be serialized, otherwise false
     */
    public boolean shouldSerializeConfigs() {
        return (this.serializedConfigsFile != null);
    }

    /**
     * Determine where serialized configs should be stored.
     * @return the path to a file where serialized configs should be stored, or
     *      null if they should not be serialized
     */
    public String getSerializedConfigsFile() {
        return this.serializedConfigsFile;
    }

	/**
	 * Determine where serialized mods should be stored.
	 * @return the path to a file where serialized mods should be stored, or
	 *      null if they should not be serialized
	 */
	public String getSerializedModificationsFile() {
		return this.serializedModificationsFile;
	}
	
	/**
     * Determine whether to generate a file with a list of policies to check 
     * with Minesweeper.
     * @return true if a list of policies should be stored, otherwise false
     */
    public boolean shouldGenerateMinesweeperPolicies() {
        return (this.minesweeperPoliciesFile != null);
    }

    /**
     * Determine where to store a file with a list of policies to check with
     * Minesweeper.
     * @return the path to a file where the list of policies should be stored, 
     *      or null if the should not be generated
     */
    public String getMinesweeperPoliciesFile() {
        return this.minesweeperPoliciesFile;
    }

	/**
	 * Determine whether to only output a summary of verification results
	 * rather than the results for every (pair of) traffic class(es).
	 * @return true if only a summary should be output, otherwise false
	 */
	public boolean shouldSummarizeVerificationResults() {
		return this.summarize;
	}

	/**
	 * Determine whether the currently blocked verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyCurrentlyBlocked() {
		return this.verifyCurrentlyBlocked;
	}

	/**
	 * Determine whether the always blocked verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyAlwaysBlocked() {
		return this.verifyAlwaysBlocked;
	}

	/**
	 * Determine whether the always reachable verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyAlwaysReachable() {
		return (this.verifyAlwaysReachable >= 0);
	}

	/**
	 * Determine the maximum number of failures allowed for verifying always
	 * reachable.
	 * @return the maximum number of failures to tolerate
	 */
	public int getAlwaysReachableFailureCount() {
		return this.verifyAlwaysReachable;
	}
	
	/**
     * Determine whether the always traverse waypoint verifier should be run.
     * @return true if the verifier should be run, otherwise false
     */
    public boolean shouldVerifyAlwaysWaypoint() {
        return this.verifyAlwaysWaypoint;
    }

	/**
	 * Determine whether the always isolated verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyAlwaysIsolated() {
		return this.verifyAlwaysIsolated;
	}
	
	/**
	 * Determine whether the primary path verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyPrimaryPath() {
		return this.verifyPrimaryPath;
	}

	/**
	 * Determine whether the paths verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyPathEquivalent() {
		return (this.verifyPathEquivalent != null);
	}

	/**
	 * Determine the location of a Cisco Virtual Internet Routing Lab (VIRL)
	 * output file used for verifying paths.
	 * @return the filepath to a VIRL output file, or null if paths should not
	 * 		be verified
	 */
	public String getFIBfile() {
		return this.verifyPathEquivalent;
	}

	/**
	 * Determine whether the equivalence verifier should be run.
	 * @return true if the verifier should be run, otherwise false
	 */
	public boolean shouldVerifyEquivalence() {
		return (this.verifyEquivalence != null);
	}

	/**
	 * Determine the location of a file containing serialized ETGs to compare
	 * against for equivalence checking.
	 * @return the path to a file of serialized ETGs, or null if equivalence
	 * 		should not be checked
	 */
	public String getComparisonETGsFile() {
		return this.verifyEquivalence;
	}

	/**
	 * Determine if per-flow (pair) verification times should be output.
	 * @return true if detailed times should be output, otherwise false
	 */
	public boolean shouldOutputPerflowVerifcationTimes() {
		return this.perflowVerifcationTimes;
	}


	public boolean shouldCheckPolicies() {
		return this.checkPoliciesFile!=null;
	}

	public boolean shouldLocalizeFaults(){
		return (this.faultLocalizeMethod!=null);
	}

	public LocalizeMethod getFaultLocalizeMethod(){
		return this.faultLocalizeMethod;
	}

	public String getCheckPoliciesFile(){
		return this.checkPoliciesFile;
	}
	/**
	 * Determine whether to derive policies from verification results and save
	 * them in a file.
	 * @return true if policies should be derived and saved, otherwise false
	 */
	public boolean shouldSavePolicies() {
		return (this.policiesSaveFile != null);
	}

	/**
	 * Determine which policies should be saved.
	 * @return the path to a file where policies should be saved, or null if
	 * 		they should not be derived and saved
	 */
	public String getPoliciesSaveFile() {
		return this.policiesSaveFile;
	}

	/**
	 * Determine whether to edit policies in a file.
	 * @return true if policies should be edited, otherwise false
	 */
	public boolean shouldEditPolicies() {
		return (this.policiesEditFile != null);
	}

	/**
	 * Determine where policies to edit are stored.
	 * @return the path to a file where policies should be edit, or null if
	 * 		they should not be edited
	 */
	public String getPoliciesEditFile() {
		return this.policiesEditFile;
	}

	/**
	 * Determine whether to convert policies in a file.
	 * @return true if policies should be convert, otherwise false
	 */
	public boolean shouldConvertPolicies() {
		return (this.policiesConvertFile != null);
	}

	/**
	 * Determine where policies to convert are stored.
	 * @return the path to a file containing policies to convert, or null if
	 * 		no policies should be converted
	 */
	public String getPoliciesConvertFile() {
		return this.policiesConvertFile;
	}

	/**
	 * Determine whether to repair the network to satisfy a set of policies
	 * them in a file.
	 * @return true if policies should be derived and saved, otherwise false
	 */
	public boolean shouldRepair() {
		return (this.policiesRepairFile != null);
	}

	/**
	 * Determine where policies should be saved.
	 * @return the path to a file where policies should be saved, or null if
	 * 		they should not be derived and saved
	 */
	public String getPoliciesRepairFile() {
		return this.policiesRepairFile;
	}

	/**
	 * Determine which algorithm to use for modifying ETGs during repair.
	 * @return the algorithm to use
	 */
	public GraphModifierAlgo getModifierAlgorithm() {
		return this.modifierAlgorithm;
	}

	/**
	 * Determine which objective to use for modifying ETGs during repair.
	 * @return the objective to use
	 */
	public GraphModifierObj getModifierObjective() {
		return this.modifierObjective;
	}

	/**
	 * Determine if generated/repaired ETGs should be compared against a
	 * file of serialized ETGs.
	 * @return true if ETGs should be compared, otherwise false
	 */
	public boolean shouldCompareETGs() {
		return (this.compareETGsFile != null);
	}

	/**
	 * Determine where comparison ETGs are stored.
	 * @return the path to a file where serialized ETGs for comparison are
	 * 		stored, or null if they should not be compared
	 */
	public String getCompareETGsFile() {
		return this.compareETGsFile;
	}

	/**
	 * Determine whether to only attempt repair of broken ETGs.
	 * @return true if only broken ETGs should be repaired, otherwise false
	 */
	public boolean shouldOnlyRepairBrokenETGs() {
		return this.onlyRepairBrokenETGs;
	}

	/**
     * Determine if generated/repaired configs should be compared against a
     * file of serialized configs.
     * @return true if configs should be compared, otherwise false
     */
    public boolean shouldCompareConfigs() {
        return (this.compareConfigsFile != null);
    }


    /**
     * Determine where comparison configs are stored.
     * @return the path to a file where serialized configs for comparison are
     *      stored, or null if they should not be compared
     */
    public String getCompareConfigsFile() {
        return this.compareConfigsFile;
    }

    /*comments*/
	public boolean shouldComputeTokenStats() {
		return (this.shouldComputeTokenStats);
	}

	/**
	 * Determine whether to repair the network to satisfy a set of policies
	 * using the Genetic Programming class.
	 * @return true if policies should be fixed using GeneProg, otherwise false
	 */
	public boolean shouldGeneticRepair() {
		return (this.shouldGeneticRepair);
	}

	/**
	 * Determine whether to repair using the repair lines file in
	 * Genetic Programming
	 * @return true if file should be used in GeneProg, otherwise false
	 */
	public boolean shouldUseRepairLines() {
		return (this.repairLinesFile != null);
	}

	/**
	 * Determine where repair lines file is stored
	 * @return path to repair lines file
	 */
	public String getRepairLinesFile() {
		return (this.repairLinesFile);
	}

	/**
     * Determine whether we should export the config repairs to a file.
     * @return true if configs should be exported to a file, otherwise false
     */
    public boolean shouldExportRepairs() {
        return repairExportPath != null;
    }

    /**
     * Determine where the repaired config files are stored.
     * @return the absolute path for the directory where the repaired config files will be stored.
     */
    public String getRepairExportDirectory() {
	    if (!this.repairExportPath.endsWith("/")) {
	        this.repairExportPath += '/';
        }
	    return repairExportPath;
    }

    /**
	 * Get the logger.
	 * @return logger for producing output
	 */
	public Logger getLogger() {
		return this.logger;
	}

	public String toString() {
		String result = "";
		result += "Log level: " + this.logLevel;
		result += "\nConfigs directory: " + this.configsDirectory;
		result += "\nWarn assumptions: " + this.warnAssumptions;
		result += "\nRouters Only: " + this.routersOnly;
		result += "\nWaypoints file: " + this.waypointsFile;
		result += "\nMinimum policy group size: " + this.minPolicyGroupsSize;
		result += "\nInclude entire flowspace: " + this.entireFlowspace;
		result += "\nExclude external policy groups: " + this.internalOnly;
		result += "\nGenerate per-flow ETGs: " + !this.baseOnly;
		result += "\nGenerate per-destination ETGs: " + this.destinationEtgs;
		result += "\nUse interface descriptions to generate device-based ETG: "
				+ this.useDescriptions;
		result += "\nParallelize: " + this.parallelize;
		result += "\nGraphs directory: " + this.graphvizDirectory;
		result += "\nTest files: " + this.testGraphDirectory;
		result += "\nSerialized ETGs file: " + this.serializedETGsFile;
		result += "\nMinesweeper policies file: " 
				+ this.minesweeperPoliciesFile;
		result += "\nVerify currently blocked: " + this.verifyCurrentlyBlocked;
		result += "\nVerify always blocked: " + this.verifyAlwaysBlocked;
		result += "\nVerify always reachable: "
				+ this.shouldVerifyAlwaysReachable() + " K="
				+ this.verifyAlwaysReachable;
		result += "\nVerify always traverse waypoint: " 
				+ this.verifyAlwaysWaypoint;
		result += "\nVerify always isolated: " + this.verifyAlwaysIsolated;
		result += "\nVerify primary path: " + this.verifyPrimaryPath;
		result += "\nVerify path equivalence: " 
		        + this.shouldVerifyPathEquivalent() + " VIRL-log="
				+ this.verifyPathEquivalent;
		result += "\nVerify equivalence: "+this.shouldVerifyEquivalence()
				+ " Comparison ETGs=" + this.getComparisonETGsFile();
		result += "\nFault Localize Method: " +  this.getFaultLocalizeMethod();
		result += "\nCheck Polcies File: "+ this.getCheckPoliciesFile();
		result += "\nSave policies file: " + this.getPoliciesSaveFile();
		result += "\nEdit policies file: " + this.getPoliciesEditFile();
		result += "\nRepair: " + this.shouldRepair()
				+ " Policies=" + this.getPoliciesRepairFile();
		result += "\nModifier: " + this.getModifierAlgorithm();
		result += "\nObjective: " + this.getModifierObjective();
		result += "\nCompare ETGs file: " + this.getCompareETGsFile();
		result += "\nOnly repair broken ETGs: "
				+ this.shouldOnlyRepairBrokenETGs();
		result += "\nCompare configs file: " + this.getCompareConfigsFile();
		return result;
	}
}
