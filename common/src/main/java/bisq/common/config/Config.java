package bisq.common.config;

import bisq.common.util.Utilities;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import joptsimple.util.RegexMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.File;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final String IGNORE_DEV_MSG = "ignoreDevMsg";
    public static final String PROVIDERS = "providers";
    public static final String LOG_LEVEL = "logLevel";
    public static final String SEED_NODES = "seedNodes";
    public static final String BAN_LIST = "banList";
    public static final String NODE_PORT = "nodePort";
    public static final String USE_LOCALHOST_FOR_P2P = "useLocalhostForP2P";
    public static final String MAX_CONNECTIONS = "maxConnections";
    public static final String SOCKS_5_PROXY_BTC_ADDRESS = "socks5ProxyBtcAddress";
    public static final String SOCKS_5_PROXY_HTTP_ADDRESS = "socks5ProxyHttpAddress";
    public static final String TORRC_FILE = "torrcFile";
    public static final String TORRC_OPTIONS = "torrcOptions";
    public static final String TOR_CONTROL_PORT = "torControlPort";
    public static final String TOR_CONTROL_PASSWORD = "torControlPassword";
    public static final String TOR_CONTROL_COOKIE_FILE = "torControlCookieFile";
    public static final String TOR_CONTROL_USE_SAFE_COOKIE_AUTH = "torControlUseSafeCookieAuth";
    public static final String TOR_STREAM_ISOLATION = "torStreamIsolation";
    public static final String IGNORE_LOCAL_BTC_NODE = "ignoreLocalBtcNode";
    public static final String BTC_NODES = "btcNodes";
    public static final String SOCKS5_DISCOVER_MODE = "socks5DiscoverMode";
    public static final String USE_ALL_PROVIDED_NODES = "useAllProvidedNodes";
    public static final String USER_AGENT = "userAgent";
    public static final String NUM_CONNECTIONS_FOR_BTC = "numConnectionsForBtc";
    public static final String RPC_USER = "rpcUser";
    public static final String RPC_PASSWORD = "rpcPassword";
    public static final String RPC_HOST = "rpcHost";
    public static final String RPC_PORT = "rpcPort";
    public static final String RPC_BLOCK_NOTIFICATION_PORT = "rpcBlockNotificationPort";
    public static final String RPC_BLOCK_NOTIFICATION_HOST = "rpcBlockNotificationHost";
    public static final String DUMP_BLOCKCHAIN_DATA = "dumpBlockchainData";
    public static final String FULL_DAO_NODE = "fullDaoNode";
    public static final String GENESIS_TX_ID = "genesisTxId";
    public static final String GENESIS_BLOCK_HEIGHT = "genesisBlockHeight";
    public static final String GENESIS_TOTAL_SUPPLY = "genesisTotalSupply";
    public static final String DAO_ACTIVATED = "daoActivated";

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    public static final int UNSPECIFIED_PORT = -1;
    static final String DEFAULT_CONFIG_FILE_NAME = "bisq.properties";
    public static final String DEFAULT_REGTEST_HOST = "localhost";
    public static final int DEFAULT_NUM_CONNECTIONS_FOR_BTC = 9; // down from BitcoinJ default of 12
    public static final boolean DEFAULT_FULL_DAO_NODE = false;

    public static File CURRENT_APP_DATA_DIR;

    // default data dir properties
    private final String defaultAppName;
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
    private final String logLevel;
    private final String referralId;
    private final boolean useDevMode;
    private final boolean useDevPrivilegeKeys;
    private final boolean dumpStatistics;
    private final int maxMemory;
    private final boolean ignoreDevMsg;
    private final List<String> providers;
    private final List<String> seedNodes;
    private final List<String> banList;
    private final boolean useLocalhostForP2P;
    private final int maxConnections;
    private final String socks5ProxyBtcAddress;
    private final String socks5ProxyHttpAddress;
    private final File torrcFile;
    private final String torrcOptions;
    private final int torControlPort;
    private final String torControlPassword;
    private final File torControlCookieFile;
    private final boolean useTorControlSafeCookieAuth;
    private final boolean torStreamIsolation;
    private final int msgThrottlePerSec;
    private final int msgThrottlePer10Sec;
    private final int sendMsgThrottleTrigger;
    private final int sendMsgThrottleSleep;
    private final String btcNodes;
    private final boolean useTorForBtc;
    private final boolean useTorForBtcOptionSetExplicitly;
    private final String socks5DiscoverMode;
    private final boolean useAllProvidedNodes;
    private final String userAgent;
    private final int numConnectionsForBtc;
    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcHost;
    private final int rpcPort;
    private final int rpcBlockNotificationPort;
    private final String rpcBlockNotificationHost;
    private final boolean dumpBlockchainData;
    private final boolean fullDaoNode;
    private final boolean fullDaoNodeOptionSetExplicitly;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private final long genesisTotalSupply;

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
        File defaultUserDataDir = getDefaultUserDataDir();
        this.defaultAppName = defaultAppName;
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
                parser.accepts(NODE_PORT, "Port to listen on")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(9999);

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
                parser.accepts(IGNORE_LOCAL_BTC_NODE,
                        "If set to true a Bitcoin Core node running locally will be ignored")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> bitcoinRegtestHostOpt =
                parser.accepts("bitcoinRegtestHost", "Bitcoin Core node when using BTC_REGTEST network")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("host[:port]")
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<String> logLevelOpt =
                parser.accepts(LOG_LEVEL, "Set logging level")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("OFF|ALL|ERROR|WARN|INFO|DEBUG|TRACE")
                        .defaultsTo(Level.INFO.levelStr);

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

        ArgumentAcceptingOptionSpec<Integer> maxMemoryOpt =
                parser.accepts("maxMemory", "Max. permitted memory (used only by headless versions)")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(1200);

        ArgumentAcceptingOptionSpec<Boolean> ignoreDevMsgOpt =
                parser.accepts(IGNORE_DEV_MSG, "If set to true all signed " +
                        "network_messages from bisq developers are ignored (Global " +
                        "alert, Version update alert, Filters for offers, nodes or " +
                        "trading account data)")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> providersOpt =
                parser.accepts(PROVIDERS, "List custom providers")
                        .withRequiredArg()
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> seedNodesOpt =
                parser.accepts(SEED_NODES, "Override hard coded seed nodes as comma separated list e.g. " +
                        "'rxdkppp3vicnbgqt.onion:8002,mfla72c4igh5ta2t.onion:8002'")
                        .withRequiredArg()
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> banListOpt =
                parser.accepts(BAN_LIST, "Nodes to exclude from network connections.")
                        .withRequiredArg()
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<Boolean> useLocalhostForP2POpt =
                parser.accepts(USE_LOCALHOST_FOR_P2P, "Use localhost P2P network for development")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Integer> maxConnectionsOpt =
                parser.accepts(MAX_CONNECTIONS, "Max. connections a peer will try to keep")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(12);

        ArgumentAcceptingOptionSpec<String> socks5ProxyBtcAddressOpt =
                parser.accepts(SOCKS_5_PROXY_BTC_ADDRESS, "A proxy address to be used for Bitcoin network.")
                        .withRequiredArg()
                        .describedAs("host:port")
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<String> socks5ProxyHttpAddressOpt =
                parser.accepts(SOCKS_5_PROXY_HTTP_ADDRESS,
                        "A proxy address to be used for Http requests (should be non-Tor)")
                        .withRequiredArg()
                        .describedAs("host:port")
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Path> torrcFileOpt =
                parser.accepts(TORRC_FILE, "An existing torrc-file to be sourced for Tor. Note that torrc-entries, " +
                        "which are critical to Bisq's correct operation, cannot be overwritten.")
                        .withRequiredArg()
                        .describedAs("File")
                        .withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));

        ArgumentAcceptingOptionSpec<String> torrcOptionsOpt =
                parser.accepts(TORRC_OPTIONS, "A list of torrc-entries to amend to Bisq's torrc. Note that " +
                        "torrc-entries, which are critical to Bisq's flawless operation, cannot be overwritten. " +
                        "[torrc options line, torrc option, ...]")
                        .withRequiredArg()
                        .withValuesConvertedBy(RegexMatcher.regex("^([^\\s,]+\\s[^,]+,?\\s*)+$"))
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Integer> torControlPortOpt =
                parser.accepts(TOR_CONTROL_PORT,
                        "The control port of an already running Tor service to be used by Bisq.")
                        .availableUnless(TORRC_FILE, TORRC_OPTIONS)
                        .withRequiredArg()
                        .ofType(int.class)
                        .describedAs("port")
                        .defaultsTo(UNSPECIFIED_PORT);

        ArgumentAcceptingOptionSpec<String> torControlPasswordOpt =
                parser.accepts(TOR_CONTROL_PASSWORD, "The password for controlling the already running Tor service.")
                        .availableIf(TOR_CONTROL_PORT)
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Path> torControlCookieFileOpt =
                parser.accepts(TOR_CONTROL_COOKIE_FILE, "The cookie file for authenticating against the already " +
                        "running Tor service. Use in conjunction with --" + TOR_CONTROL_USE_SAFE_COOKIE_AUTH)
                        .availableIf(TOR_CONTROL_PORT)
                        .availableUnless(TOR_CONTROL_PASSWORD)
                        .withRequiredArg()
                        .describedAs("File")
                        .withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));

        OptionSpecBuilder torControlUseSafeCookieAuthOpt =
                parser.accepts(TOR_CONTROL_USE_SAFE_COOKIE_AUTH,
                        "Use the SafeCookie method when authenticating to the already running Tor service.")
                        .availableIf(TOR_CONTROL_COOKIE_FILE);

        OptionSpecBuilder torStreamIsolationOpt =
                parser.accepts(TOR_STREAM_ISOLATION, "Use stream isolation for Tor [experimental!].");

        ArgumentAcceptingOptionSpec<Integer> msgThrottlePerSecOpt =
                parser.accepts("msgThrottlePerSec", "Message throttle per sec for connection class")
                        .withRequiredArg()
                        .ofType(int.class)
                        // With PERMITTED_MESSAGE_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
                        .defaultsTo(200);

        ArgumentAcceptingOptionSpec<Integer> msgThrottlePer10SecOpt =
                parser.accepts("msgThrottlePer10Sec", "Message throttle per 10 sec for connection class")
                        .withRequiredArg()
                        .ofType(int.class)
                        // With PERMITTED_MESSAGE_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
                        .defaultsTo(1000);

        ArgumentAcceptingOptionSpec<Integer> sendMsgThrottleTriggerOpt =
                parser.accepts("sendMsgThrottleTrigger", "Time in ms when we trigger a sleep if 2 messages are sent")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(20); // Time in ms when we trigger a sleep if 2 messages are sent

        ArgumentAcceptingOptionSpec<Integer> sendMsgThrottleSleepOpt =
                parser.accepts("sendMsgThrottleSleep", "Pause in ms to sleep if we get too many messages to send")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(50); // Pause in ms to sleep if we get too many messages to send

        ArgumentAcceptingOptionSpec<String> btcNodesOpt =
                parser.accepts(BTC_NODES, "Custom nodes used for BitcoinJ as comma separated IP addresses.")
                        .withRequiredArg()
                        .describedAs("ip[,...]")
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Boolean> useTorForBtcOpt =
                parser.accepts("useTorForBtc", "If set to true BitcoinJ is routed over tor (socks 5 proxy).")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> socks5DiscoverModeOpt =
                parser.accepts(SOCKS5_DISCOVER_MODE, "Specify discovery mode for Bitcoin nodes. " +
                        "One or more of: [ADDR, DNS, ONION, ALL] (comma separated, they get OR'd together).")
                        .withRequiredArg()
                        .describedAs("mode[,...]")
                        .defaultsTo("ALL");

        ArgumentAcceptingOptionSpec<Boolean> useAllProvidedNodesOpt =
                parser.accepts(USE_ALL_PROVIDED_NODES,
                        "Set to true if connection of bitcoin nodes should include clear net nodes")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> userAgentOpt =
                parser.accepts(USER_AGENT,
                        "User agent at btc node connections")
                        .withRequiredArg()
                        .defaultsTo("Bisq");

        ArgumentAcceptingOptionSpec<Integer> numConnectionsForBtcOpt =
                parser.accepts(NUM_CONNECTIONS_FOR_BTC, "Number of connections to the Bitcoin network")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(DEFAULT_NUM_CONNECTIONS_FOR_BTC);

        ArgumentAcceptingOptionSpec<String> rpcUserOpt =
                parser.accepts(RPC_USER, "Bitcoind rpc username")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<String> rpcPasswordOpt =
                parser.accepts(RPC_PASSWORD, "Bitcoind rpc password")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<String> rpcHostOpt =
                parser.accepts(RPC_HOST, "Bitcoind rpc host")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Integer> rpcPortOpt =
                parser.accepts(RPC_PORT, "Bitcoind rpc port")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(UNSPECIFIED_PORT);

        ArgumentAcceptingOptionSpec<Integer> rpcBlockNotificationPortOpt =
                parser.accepts(RPC_BLOCK_NOTIFICATION_PORT, "Bitcoind rpc port for block notifications")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(UNSPECIFIED_PORT);

        ArgumentAcceptingOptionSpec<String> rpcBlockNotificationHostOpt =
                parser.accepts(RPC_BLOCK_NOTIFICATION_HOST,
                        "Bitcoind rpc accepted incoming host for block notifications")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Boolean> dumpBlockchainDataOpt =
                parser.accepts(DUMP_BLOCKCHAIN_DATA, "If set to true the blockchain data " +
                        "from RPC requests to Bitcoin Core are stored as json file in the data dir.")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> fullDaoNodeOpt =
                parser.accepts(FULL_DAO_NODE, "If set to true the node requests the blockchain data via RPC requests " +
                        "from Bitcoin Core and provide the validated BSQ txs to the network. It requires that the " +
                        "other RPC properties are set as well.")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(DEFAULT_FULL_DAO_NODE);

        ArgumentAcceptingOptionSpec<String> genesisTxIdOpt =
                parser.accepts(GENESIS_TX_ID, "Genesis transaction ID when not using the hard coded one")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Integer> genesisBlockHeightOpt =
                parser.accepts(GENESIS_BLOCK_HEIGHT,
                        "Genesis transaction block height when not using the hard coded one")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(-1);

        ArgumentAcceptingOptionSpec<Long> genesisTotalSupplyOpt =
                parser.accepts(GENESIS_TOTAL_SUPPLY, "Genesis total supply when not using the hard coded one")
                        .withRequiredArg()
                        .ofType(long.class)
                        .defaultsTo(-1L);

        ArgumentAcceptingOptionSpec<Boolean> daoActivatedOpt =
                parser.accepts(DAO_ACTIVATED, "Developer flag. If true it enables dao phase 2 features.")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(true);

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
            this.logLevel = options.valueOf(logLevelOpt);
            this.torrcFile = options.has(torrcFileOpt) ? options.valueOf(torrcFileOpt).toFile() : null;
            this.torrcOptions = options.valueOf(torrcOptionsOpt);
            this.torControlPort = options.valueOf(torControlPortOpt);
            this.torControlPassword = options.valueOf(torControlPasswordOpt);
            this.torControlCookieFile = options.has(torControlCookieFileOpt) ?
                    options.valueOf(torControlCookieFileOpt).toFile() : null;
            this.useTorControlSafeCookieAuth = options.has(torControlUseSafeCookieAuthOpt);
            this.torStreamIsolation = options.has(torStreamIsolationOpt);
            this.referralId = options.valueOf(referralIdOpt);
            this.useDevMode = options.valueOf(useDevModeOpt);
            this.useDevPrivilegeKeys = options.valueOf(useDevPrivilegeKeysOpt);
            this.dumpStatistics = options.valueOf(dumpStatisticsOpt);
            this.maxMemory = options.valueOf(maxMemoryOpt);
            this.ignoreDevMsg = options.valueOf(ignoreDevMsgOpt);
            this.providers = options.valuesOf(providersOpt);
            this.seedNodes = options.valuesOf(seedNodesOpt);
            this.banList = options.valuesOf(banListOpt);
            this.useLocalhostForP2P = options.valueOf(useLocalhostForP2POpt);
            this.maxConnections = options.valueOf(maxConnectionsOpt);
            this.socks5ProxyBtcAddress = options.valueOf(socks5ProxyBtcAddressOpt);
            this.socks5ProxyHttpAddress = options.valueOf(socks5ProxyHttpAddressOpt);
            this.msgThrottlePerSec = options.valueOf(msgThrottlePerSecOpt);
            this.msgThrottlePer10Sec = options.valueOf(msgThrottlePer10SecOpt);
            this.sendMsgThrottleTrigger = options.valueOf(sendMsgThrottleTriggerOpt);
            this.sendMsgThrottleSleep = options.valueOf(sendMsgThrottleSleepOpt);
            // Preserve log output from now-removed ConnectionConfig class TODO: remove
            log.info("ConnectionConfig{\n" +
                            "    msgThrottlePerSec={},\n    msgThrottlePer10Sec={},\n" +
                            "    sendMsgThrottleTrigger={},\n    sendMsgThrottleSleep={}\n}",
                    msgThrottlePerSec, msgThrottlePer10Sec, sendMsgThrottleTrigger, sendMsgThrottleSleep);
            this.btcNodes = options.valueOf(btcNodesOpt);
            this.useTorForBtc = options.valueOf(useTorForBtcOpt);
            this.useTorForBtcOptionSetExplicitly = options.has(useTorForBtcOpt);
            this.socks5DiscoverMode = options.valueOf(socks5DiscoverModeOpt);
            this.useAllProvidedNodes = options.valueOf(useAllProvidedNodesOpt);
            this.userAgent = options.valueOf(userAgentOpt);
            this.numConnectionsForBtc = options.valueOf(numConnectionsForBtcOpt);
            this.rpcUser = options.valueOf(rpcUserOpt);
            this.rpcPassword = options.valueOf(rpcPasswordOpt);
            this.rpcHost = options.valueOf(rpcHostOpt);
            this.rpcPort = options.valueOf(rpcPortOpt);
            this.rpcBlockNotificationPort = options.valueOf(rpcBlockNotificationPortOpt);
            this.rpcBlockNotificationHost = options.valueOf(rpcBlockNotificationHostOpt);
            this.dumpBlockchainData = options.valueOf(dumpBlockchainDataOpt);
            this.fullDaoNode = options.valueOf(fullDaoNodeOpt);
            this.fullDaoNodeOptionSetExplicitly = options.has(fullDaoNodeOpt);
            this.genesisTxId = options.valueOf(genesisTxIdOpt);
            this.genesisBlockHeight = options.valueOf(genesisBlockHeightOpt);
            this.genesisTotalSupply = options.valueOf(genesisTotalSupplyOpt);
            this.daoActivated = options.valueOf(daoActivatedOpt) || !baseCurrencyNetwork.isMainnet();
        } catch (OptionException ex) {
            throw new ConfigException("problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage());
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
                throw new ConfigException("The '%s' option is disallowed in config files",
                        disallowedOpt.options().get(0));

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

    public String getLogLevel() {
        return logLevel;
    }

    public boolean isLocalBitcoinNodeIsRunning() {
        return localBitcoinNodeIsRunning;
    }

    public void setLocalBitcoinNodeIsRunning(boolean value) {
        this.localBitcoinNodeIsRunning = value;
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

    public int getMaxMemory() {
        return maxMemory;
    }

    public boolean isIgnoreDevMsg() {
        return ignoreDevMsg;
    }

    public List<String> getProviders() {
        return providers;
    }

    public List<String> getSeedNodes() {
        return seedNodes;
    }

    public List<String> getBanList() {
        return banList;
    }

    public boolean isUseLocalhostForP2P() {
        return useLocalhostForP2P;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public String getSocks5ProxyBtcAddress() {
        return socks5ProxyBtcAddress;
    }

    public String getSocks5ProxyHttpAddress() {
        return socks5ProxyHttpAddress;
    }

    public File getTorrcFile() {
        return torrcFile;
    }

    public String getTorrcOptions() {
        return torrcOptions;
    }

    public int getTorControlPort() {
        return torControlPort;
    }

    public String getTorControlPassword() {
        return torControlPassword;
    }

    public File getTorControlCookieFile() {
        return torControlCookieFile;
    }

    public boolean isUseTorControlSafeCookieAuth() {
        return useTorControlSafeCookieAuth;
    }

    public boolean isTorStreamIsolation() {
        return torStreamIsolation;
    }

    public int getMsgThrottlePerSec() {
        return msgThrottlePerSec;
    }

    public int getMsgThrottlePer10Sec() {
        return msgThrottlePer10Sec;
    }

    public int getSendMsgThrottleTrigger() {
        return sendMsgThrottleTrigger;
    }

    public int getSendMsgThrottleSleep() {
        return sendMsgThrottleSleep;
    }

    public String getBtcNodes() {
        return btcNodes;
    }

    public boolean isUseTorForBtc() {
        return useTorForBtc;
    }

    public boolean isUseTorForBtcOptionSetExplicitly() {
        return useTorForBtcOptionSetExplicitly;
    }

    public String getSocks5DiscoverMode() {
        return socks5DiscoverMode;
    }

    public boolean isUseAllProvidedNodes() {
        return useAllProvidedNodes;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public int getNumConnectionsForBtc() {
        return numConnectionsForBtc;
    }

    public String getRpcUser() {
        return rpcUser;
    }

    public String getRpcPassword() {
        return rpcPassword;
    }

    public String getRpcHost() {
        return rpcHost;
    }

    public int getRpcPort() {
        return rpcPort;
    }

    public int getRpcBlockNotificationPort() {
        return rpcBlockNotificationPort;
    }

    public String getRpcBlockNotificationHost() {
        return rpcBlockNotificationHost;
    }

    public boolean isDumpBlockchainData() {
        return dumpBlockchainData;
    }

    public boolean isFullDaoNode() {
        return fullDaoNode;
    }

    public boolean isFullDaoNodeOptionSetExplicitly() {
        return fullDaoNodeOptionSetExplicitly;
    }

    public String getGenesisTxId() {
        return genesisTxId;
    }

    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    public long getGenesisTotalSupply() {
        return genesisTotalSupply;
    }

    public boolean isDaoActivated() {
        return daoActivated;
    }
}
