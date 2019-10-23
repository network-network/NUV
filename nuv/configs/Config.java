package edu.wisc.cs.arc.configs;

import edu.wisc.cs.arc.Logger;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.FileUtils;
import org.batfish.common.BatfishException;
import org.batfish.common.BatfishLogger;
import org.batfish.common.RedFlagBatfishException;
import org.batfish.common.Warnings;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.answers.ConvertConfigurationAnswerElement;
import org.batfish.grammar.BatfishCombinedParser;
import org.batfish.grammar.ControlPlaneExtractor;
import org.batfish.grammar.ParseTreePrettyPrinter;
import org.batfish.grammar.VendorConfigurationFormatDetector;
import org.batfish.grammar.cisco.CiscoCombinedParser;
import org.batfish.grammar.cisco.CiscoControlPlaneExtractor;
import org.batfish.main.Batfish;
import org.batfish.main.ParserBatfishException;
import org.batfish.config.Settings.TestrigSettings;
import org.batfish.vendor.VendorConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A device's configuration.
 * @author Aaron Gember-Jacobson (agemberjacobson@colgate.edu)
 *
 */
public class Config {

    /** Settings required for using Batfish's lexer/parser */
    private static org.batfish.config.Settings batfishSettings;

    /** Warnings settings required for using Batfish's lexer/parser */
    private static Warnings batfishWarnings;

    /** Logger */
    private Logger logger;

    /** Hostname for the device with which the configuration is associated */
    private String hostname;

    /** File from which the configuration originated */
    private File file;

    /** Raw text */
    private String text;

    /** Format (i.e., domain-specific language) */
    private ConfigurationFormat format;

    /** Parser and lexer */
    private CiscoCombinedParser combinedParser;

    /** Parse tree walker */
    private ControlPlaneExtractor extractor;

    /** Tokens that make up the configuration */
    private List<? extends Token> tokens;

    /** Configuration's bstract syntax tree */
    private ParserRuleContext parseTree;

    /** Structured, vendor-specific version of the configuration */
    private VendorConfiguration vendorConfiguration;

    /** Structured, vendor-agnostic version of the configuration */
    private Configuration genericConfiguration;

   /**
    * Load a configuration from a file.
    * @param file the file from which to read the configuration
    * @throws IOException
    */
    public Config(File file, Logger logger) throws IOException {
        this(FileUtils.readFileToString(file),
        		file.getName().toString().replaceAll("\\.(cfg|conf)$", ""),
        		logger);
        this.file = file;
    }

    /**
     * Create a configuration from a list of tokens.
     * @param tokens the list of tokens from which to create the configuration
     * @param hostname the name of the device to which the configuration applies
     * @param logger
     */
    public Config(List<? extends Token> tokens, String hostname, Logger logger){
    	this(buildConfigText(tokens), hostname, logger);
    }

    /**
     * Builds a string from a list of tokens
     * @param tokens List of tokens from which to build configuration
     * @return string containing all tokens
     */
    private static String buildConfigText(List<? extends Token> tokens){
        StringBuilder rawText = new StringBuilder();
        for (Token tok : tokens){
            rawText.append(tok.getText());
        }
        return rawText.toString();
    }

    /**
     * Create a configuration from text.
     * @param text the text of the configuration
     * @param hostname the name of the device to which the configuration applies
     * @param logger
     */
    public Config(String text, String hostname, Logger logger) {
        this.text = text;
        this.hostname = hostname;
        this.logger = logger;
        this.format = VendorConfigurationFormatDetector
                .identifyConfigurationFormat(this.text);

        if (null == batfishSettings) {
            // Prepare batfish settings
            batfishSettings = new org.batfish.config.Settings();
            batfishSettings.setLogger(new BatfishLogger(
                    batfishSettings.getLogLevel(),
                    batfishSettings.getTimestamp(), System.out));
            TestrigSettings batfishTestrigSettings = new TestrigSettings();
            batfishTestrigSettings.setBasePath(null); // FIXME?
            batfishSettings.setActiveTestrigSettings(batfishTestrigSettings);
            batfishSettings.setDisableUnrecognized(false);

            batfishWarnings = new Warnings(false, true, false, true,
                    false, false, false);
        }

        switch(this.format) {
        case CISCO_IOS:
        case CISCO_IOS_XR:
        case CISCO_NX:
            // Pre-process banner stanzas
            /*String newText = this.text;
            do {
               this.text = newText;
               try {
                  newText = ParseVendorConfigurationJob.preprocessBanner(
                          this.text, format);
               }
               catch (BatfishException e) {
                   throw new ConfigurationException(
                           "Error pre-processing banner");
               }
            } while (newText != this.text);*/

            this.combinedParser = new CiscoCombinedParser(this.text,
                    batfishSettings, this.format);
            this.extractor = new CiscoControlPlaneExtractor(this.text,
                    this.combinedParser, format, batfishWarnings,
                    batfishSettings.getUnrecognizedAsRedFlag());
            break;
        default:
            throw new ConfigurationException("Unhandled config format: "
                    + this.format);
        }

    }

    /**
     * Get the file from which the configuration originated.
     * @return file from which the configuration originated
     */
    public File getFile() {
        return this.file;
    }

    /**
     * Get the configuration's text.
     * @return configuration's text
     */
    public String getText() {
        return this.text;
    }

    /**
     * Set the configuration's text.
     * @param text configuration's new text.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     *
     * @param text
     */
    public void refresh(String text) {
        setText(text);
        refresh();
    }

    /**
     * Re-Parse the config's text.
     */
    public void refresh() {
        this.parseTree = null;
        this.extractor = null;
        this.tokens = null;
        this.vendorConfiguration = null;
        this.genericConfiguration = null;
        switch(this.format) {
            case CISCO_IOS:
            case CISCO_IOS_XR:
            case CISCO_NX:
                // Pre-process banner stanzas
                this.combinedParser = new CiscoCombinedParser(this.text,
                        batfishSettings, this.format);
                this.extractor = new CiscoControlPlaneExtractor(this.text,
                        this.combinedParser, format, batfishWarnings,
                        batfishSettings.getUnrecognizedAsRedFlag());
                break;
            default:
                throw new ConfigurationException("Unhandled config format: "
                        + this.format);
        }
    }

    /**
     * Get the configuration's tokens.
     * @return configuration's tokens
     */
    public List<? extends Token> getTokens() {
        if (null == tokens) {
            this.combinedParser.getLexer().reset();
            this.tokens = this.combinedParser.getLexer().getAllTokens();
        }
        return this.tokens;
    }

    /**
     * Get a subset of the configuration's tokens.
     * @param start starting index
     * @param end ending index
     * @return configuration's tokens
     */
    public List<? extends Token> getTokens(int start, int end) {
        return this.getTokens().subList(start, end);
    }

    /**
     * Get the configuration's parse tree.
     * @return configuration's parse tree
     */
    public ParserRuleContext getParseTree() {
        if (null == this.parseTree) {
        	logger.infoNoNL("Parsing " + this.getHostname() + "...");
            //CommonTokenStream tokenStream = new CommonTokenStream(
            //        new ListTokenSource(this.getTokens()));
            //this.combinedParser.getParser().setTokenStream(tokenStream);
			this.combinedParser.getLexer().reset();
			try {
			    this.parseTree = Batfish.parse(this.combinedParser,
			            batfishSettings.getLogger(), batfishSettings);
			} catch(ParserBatfishException e) {
			    logger.info("FAILED");
			    e.printStackTrace();
			}
			//logger.info(ParseTreePrettyPrinter.print(this.parseTree,
			//        this.combinedParser));
        }
        return this.parseTree;
    }
    
    /**
     * Print the configuration's parse tree.
     */
    public void printParseTree() {
    	logger.info(ParseTreePrettyPrinter.print(this.getParseTree(), 
    			this.combinedParser));
    }

    /**
     * Get a structured, vendor-specific version of the configuration.
     * @return a structured, vendor-specific version of the configuration
     */
    public VendorConfiguration getVendorConfiguration() {
        if (null == this.vendorConfiguration) {
			try{
	            extractor.processParseTree(this.getParseTree());
			} catch(RedFlagBatfishException rfbe){
				logger.error(rfbe.getMessage());
			}
			catch(BatfishException be){
				logger.error(be.getMessage());
			}
            this.vendorConfiguration = extractor.getVendorConfiguration();
            this.vendorConfiguration.setAnswerElement(
                    new ConvertConfigurationAnswerElement());
            this.vendorConfiguration.setVendor(this.format);
            this.vendorConfiguration.setWarnings(this.batfishWarnings);
        }
        return this.vendorConfiguration;
    }

    /**
     * Get a structured, vendor-agnostic version of the configuration.
     * @return a structured, vendor-agnostic version of the configuration
     */
    public Configuration getGenericConfiguration() {
        if (null == this.genericConfiguration) {
            this.genericConfiguration = this.getVendorConfiguration()
                    .toVendorIndependentConfiguration();
        }
        return this.genericConfiguration;
    }

    /**
     * Get the hostname of the device to which the configuration applies.
     * @return hostname of the device
     */
    public String getHostname() {
        return this.hostname;
    }
}
