package bisq.common.config;

import bisq.common.util.Utilities;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;

import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class Config {

    public static final String APP_NAME = "appName";
    public static final String BASE_CURRENCY_NETWORK = "baseCurrencyNetwork";
    public static final String REFERRAL_ID = "referralId";
    public static final String USE_DEV_MODE = "useDevMode";
    public static final String TOR_DIR = "torDir";
    public static final String STORAGE_DIR = "storageDir";
    public static final String KEY_STORAGE_DIR = "keyStorageDir";
    public static final String WALLET_DIR = "walletDir";
    public static final String USE_DEV_PRIVILEGE_KEYS = "useDevPrivilegeKeys";
    public static final String DUMP_STATISTICS = "dumpStatistics";

    static final String DEFAULT_CONFIG_FILE_NAME = "bisq.properties";
    static final int DEFAULT_NODE_PORT = 9999;

    public static File CURRENT_APP_DATA_DIR;

    // default data dir properties
    private final String defaultAppName;
    private final File defaultUserDataDir;
    private final File defaultAppDataDir;
    private final File defaultConfigFile;

    // cli options
    private final File configFile;
    private final String appName;
    private final File userDataDir;
    private final File appDataDir;
    private final int nodePort;
    private final List<String> bannedBtcNodes;
    private final List<String> bannedPriceRelayNodes;
    private final List<String> bannedSeedNodes;
    private final BaseCurrencyNetwork baseCurrencyNetwork;
    private final boolean ignoreLocalBtcNode;
    private final String bitcoinRegtestHost;
    private final boolean daoActivated;
    private final boolean fullDaoNode;
    private final String logLevel;
    private final Path torrcFile;
    private final String referralId;
    private final boolean useDevMode;
    private final boolean useDevPrivilegeKeys;
    private final boolean dumpStatistics;

    // properties derived from cli options, but not exposed as cli options themselves
    private boolean localBitcoinNodeIsRunning = false; // FIXME: eliminate mutable state
    private final File torDir;
    private final File walletDir;
    private final File storageDir;
    private final File keyStorageDir;

    public Config(String defaultAppName) throws HelpRequested {
        this(defaultAppName, new String[]{});
    }

    public Config(String defaultAppName, String[] args) throws HelpRequested {
        this.defaultAppName = defaultAppName;
        this.defaultUserDataDir = getDefaultUserDataDir();
        this.defaultAppDataDir = new File(defaultUserDataDir, this.defaultAppName);
        this.defaultConfigFile = new File(defaultAppDataDir, DEFAULT_CONFIG_FILE_NAME);

        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        AbstractOptionSpec<Void> helpOpt =
                parser.accepts("help", "Print this help text")
                        .forHelp();

        ArgumentAcceptingOptionSpec<String> configFileOpt =
                parser.accepts("configFile", "Specify configuration file. " +
                        "Relative paths will be prefixed by appDataDir location.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(DEFAULT_CONFIG_FILE_NAME);

        ArgumentAcceptingOptionSpec<File> userDataDirOpt =
                parser.accepts("userDataDir", "User data directory")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(defaultUserDataDir);

        ArgumentAcceptingOptionSpec<String> appNameOpt =
                parser.accepts(APP_NAME, "Application name")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(this.defaultAppName);

        ArgumentAcceptingOptionSpec<File> appDataDirOpt =
                parser.accepts("appDataDir", "Application data directory")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(defaultAppDataDir);

        ArgumentAcceptingOptionSpec<Integer> nodePortOpt =
                parser.accepts("nodePort", "Port to listen on")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(DEFAULT_NODE_PORT);

        ArgumentAcceptingOptionSpec<String> bannedBtcNodesOpt =
                parser.accepts("bannedBtcNodes", "List Bitcoin nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> bannedPriceRelayNodesOpt =
                parser.accepts("bannedPriceRelayNodes", "List Bisq price nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> bannedSeedNodesOpt =
                parser.accepts("bannedSeedNodes", "List Bisq seed nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<Enum> baseCurrencyNetworkOpt =
                parser.accepts(BASE_CURRENCY_NETWORK, "Base currency network")
                        .withRequiredArg()
                        .ofType(BaseCurrencyNetwork.class)
                        .withValuesConvertedBy(new EnumValueConverter(BaseCurrencyNetwork.class))
                        .defaultsTo(BaseCurrencyNetwork.BTC_MAINNET);

        ArgumentAcceptingOptionSpec<Boolean> ignoreLocalBtcNodeOpt =
                parser.accepts("ignoreLocalBtcNode", "If set to true a Bitcoin Core node running locally will be ignored")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> bitcoinRegtestHostOpt =
                parser.accepts("bitcoinRegtestHost", "Bitcoin Core node when using BTC_REGTEST network")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("host[:port]")
                        .defaultsTo("localhost");

        ArgumentAcceptingOptionSpec<Boolean> daoActivatedOpt =
                parser.accepts("daoActivated", "Developer flag. If true it enables dao phase 2 features.")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(true);

        ArgumentAcceptingOptionSpec<Boolean> fullDaoNodeOpt =
                parser.accepts("fullDaoNode", "If set to true the node requests the blockchain data via RPC requests " +
                        "from Bitcoin Core and provide the validated BSQ txs to the network. It requires that the " +
                        "other RPC properties are set as well.")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> logLevelOpt =
                parser.accepts("logLevel", "Set logging level")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("OFF|ALL|ERROR|WARN|INFO|DEBUG|TRACE")
                        .defaultsTo(Level.INFO.levelStr);

        ArgumentAcceptingOptionSpec<Path> torrcFileOpt =
                parser.accepts("torrcFile", "An existing torrc-file to be sourced for Tor. Note that torrc-entries, " +
                        "which are critical to Bisq's correct operation, cannot be overwritten.")
                        .withRequiredArg()
                        .withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));

        ArgumentAcceptingOptionSpec<String> referralIdOpt =
                parser.accepts(REFERRAL_ID, "Optional Referral ID (e.g. for API users or pro market makers)")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Boolean> useDevModeOpt =
                parser.accepts(USE_DEV_MODE,
                        "Enables dev mode which is used for convenience for developer testing")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> useDevPrivilegeKeysOpt =
                parser.accepts(USE_DEV_PRIVILEGE_KEYS, "If set to true all privileged features requiring a private " +
                        "key to be enabled are overridden by a dev key pair (This is for developers only!)")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> dumpStatisticsOpt =
                parser.accepts(DUMP_STATISTICS, "If set to true dump trade statistics to a json file in appDataDir")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        try {
            OptionSet cliOpts = parser.parse(args);

            if (cliOpts.has(helpOpt))
                throw new HelpRequested(parser);

            CompositeOptionSet options = new CompositeOptionSet();
            options.addOptionSet(cliOpts);

            File configFile = null;
            final boolean cliHasConfigFileOpt = cliOpts.has(configFileOpt);
            boolean configFileHasBeenProcessed = false;
            if (cliHasConfigFileOpt) {
                configFile = new File(cliOpts.valueOf(configFileOpt));
                Optional<OptionSet> configFileOpts = parseOptionsFrom(configFile, parser, helpOpt, configFileOpt);
                if (configFileOpts.isPresent()) {
                    options.addOptionSet(configFileOpts.get());
                    configFileHasBeenProcessed = true;
                }
            }

            this.appName = options.valueOf(appNameOpt);
            this.userDataDir = options.valueOf(userDataDirOpt);
            this.appDataDir = options.has(appDataDirOpt) ?
                    options.valueOf(appDataDirOpt) :
                    new File(this.userDataDir, this.appName);

            CURRENT_APP_DATA_DIR = appDataDir;

            if (!configFileHasBeenProcessed) {
                configFile = cliHasConfigFileOpt && !configFile.isAbsolute() ?
                        new File(this.appDataDir, configFile.getPath()) : // TODO: test
                        new File(this.appDataDir, DEFAULT_CONFIG_FILE_NAME);
                Optional<OptionSet> configFileOpts = parseOptionsFrom(configFile, parser, helpOpt, configFileOpt);
                configFileOpts.ifPresent(options::addOptionSet);
            }

            this.configFile = configFile;
            this.nodePort = options.valueOf(nodePortOpt);
            this.bannedBtcNodes = options.valuesOf(bannedBtcNodesOpt);
            this.bannedPriceRelayNodes = options.valuesOf(bannedPriceRelayNodesOpt);
            this.bannedSeedNodes = options.valuesOf(bannedSeedNodesOpt);
            this.baseCurrencyNetwork = (BaseCurrencyNetwork) options.valueOf(baseCurrencyNetworkOpt);
            BaseCurrencyNetwork.CURRENT_NETWORK = baseCurrencyNetwork;
            BaseCurrencyNetwork.CURRENT_PARAMETERS = baseCurrencyNetwork.getParameters();
            this.ignoreLocalBtcNode = options.valueOf(ignoreLocalBtcNodeOpt);
            this.bitcoinRegtestHost = options.valueOf(bitcoinRegtestHostOpt);
            this.daoActivated = options.valueOf(daoActivatedOpt) || !baseCurrencyNetwork.isMainnet();
            this.fullDaoNode = options.valueOf(fullDaoNodeOpt);
            this.logLevel = options.valueOf(logLevelOpt);
            this.torrcFile = options.valueOf(torrcFileOpt);
            this.referralId = options.valueOf(referralIdOpt);
            this.useDevMode = options.valueOf(useDevModeOpt);
            this.useDevPrivilegeKeys = options.valueOf(useDevPrivilegeKeysOpt);
            this.dumpStatistics = options.valueOf(dumpStatisticsOpt);
        } catch (OptionException ex) {
            throw new ConfigException(format("problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage()));
        }

        File btcNetworkDir = new File(appDataDir, baseCurrencyNetwork.name().toLowerCase());
        if (!btcNetworkDir.exists())
            btcNetworkDir.mkdir();

        this.torDir = new File(btcNetworkDir, "tor");
        this.walletDir = btcNetworkDir;
        this.storageDir = new File(btcNetworkDir, "db");
        this.keyStorageDir = new File(btcNetworkDir, "keys");
    }

    private Optional<OptionSet> parseOptionsFrom(File file, OptionParser parser, OptionSpec<?>... disallowedOpts) {
        if (!file.isAbsolute() || !file.exists())
            return Optional.empty();

        ConfigFileReader configFileReader = new ConfigFileReader(file);
        String[] optionLines = configFileReader.getOptionLines().stream()
                .map(o -> "--" + o) // prepend dashes expected by jopt parser below
                .collect(toList())
                .toArray(new String[]{});

        OptionSet configFileOpts = parser.parse(optionLines);
        for (OptionSpec<?> disallowedOpt : disallowedOpts)
            if (configFileOpts.has(disallowedOpt))
                throw new IllegalArgumentException(
                        format("The '%s' option is disallowed in config files", disallowedOpt.options().get(0)));

        return Optional.of(configFileOpts);
    }

    public String getDefaultAppName() {
        return defaultAppName;
    }

    public File getDefaultUserDataDir() {
        if (Utilities.isWindows())
            return new File(System.getenv("APPDATA"));

        if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toFile();

        // *nix
        return Paths.get(System.getProperty("user.home"), ".local", "share").toFile();
    }

    public File getDefaultAppDataDir() {
        return defaultAppDataDir;
    }

    public File getDefaultConfigFile() {
        return defaultConfigFile;
    }

    public File getConfigFile() {
        return configFile;
    }

    public String getAppName() {
        return appName;
    }

    public File getUserDataDir() {
        return userDataDir;
    }

    public File getAppDataDir() {
        return appDataDir;
    }

    public int getNodePort() {
        return nodePort;
    }

    public List<String> getBannedBtcNodes() {
        return bannedBtcNodes;
    }

    public List<String> getBannedPriceRelayNodes() {
        return bannedPriceRelayNodes;
    }

    public List<String> getBannedSeedNodes() {
        return bannedSeedNodes;
    }

    public BaseCurrencyNetwork getBaseCurrencyNetwork() {
        return baseCurrencyNetwork;
    }

    public boolean isIgnoreLocalBtcNode() {
        return ignoreLocalBtcNode;
    }

    public String getBitcoinRegtestHost() {
        return bitcoinRegtestHost;
    }

    public boolean isDaoActivated() {
        return daoActivated;
    }

    public boolean isFullDaoNode() {
        return fullDaoNode;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public boolean isLocalBitcoinNodeIsRunning() {
        return localBitcoinNodeIsRunning;
    }

    public void setLocalBitcoinNodeIsRunning(boolean value) {
        this.localBitcoinNodeIsRunning = value;
    }

    public Path getTorrcFile() {
        return torrcFile;
    }

    public String getReferralId() {
        return referralId;
    }

    public boolean isUseDevMode() {
        return useDevMode;
    }

    public File getTorDir() {
        return torDir;
    }

    public File getWalletDir() {
        return walletDir;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public File getKeyStorageDir() {
        return keyStorageDir;
    }

    public boolean isUseDevPrivilegeKeys() {
        return useDevPrivilegeKeys;
    }

    public boolean isDumpStatistics() {
        return dumpStatistics;
    }
}
