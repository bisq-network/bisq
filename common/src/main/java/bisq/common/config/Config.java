package bisq.common.config;

import org.bitcoinj.core.NetworkParameters;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.HelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import joptsimple.util.RegexMatcher;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * Parses and provides access to all Bisq configuration options specified at the command
 * line and/or via the {@value DEFAULT_CONFIG_FILE_NAME} config file, including any
 * default values. Constructing a {@link Config} instance is generally side-effect free,
 * with one key exception being that {@value APP_DATA_DIR} and its subdirectories will
 * be created if they do not already exist. Care is taken to avoid inadvertent creation or
 * modification of the actual system user data directory and/or the production Bisq
 * application data directory. Calling code must explicitly specify these values; they are
 * never assumed.
 * <p/>
 * Note that this class deviates from typical JavaBean conventions in that fields
 * representing configuration options are public and have no corresponding accessor
 * ("getter") method. This is because all such fields are final and therefore not subject
 * to modification by calling code and because eliminating the accessor methods means
 * eliminating hundreds of lines of boilerplate code and one less touchpoint to deal with
 * when adding or modifying options. Furthermore, while accessor methods are often useful
 * when mocking an object in a testing context, this class is designed for testability
 * without needing to be mocked. See {@code ConfigTests} for examples.
 * @see #Config(String...)
 * @see #Config(String, File, String...)
 */
public class Config {

    // Option name constants
    public static final String HELP = "help";
    public static final String APP_NAME = "appName";
    public static final String USER_DATA_DIR = "userDataDir";
    public static final String APP_DATA_DIR = "appDataDir";
    public static final String CONFIG_FILE = "configFile";
    public static final String MAX_MEMORY = "maxMemory";
    public static final String LOG_LEVEL = "logLevel";
    public static final String BANNED_BTC_NODES = "bannedBtcNodes";
    public static final String BANNED_PRICE_RELAY_NODES = "bannedPriceRelayNodes";
    public static final String BANNED_SEED_NODES = "bannedSeedNodes";
    public static final String BASE_CURRENCY_NETWORK = "baseCurrencyNetwork";
    public static final String REFERRAL_ID = "referralId";
    public static final String USE_DEV_MODE = "useDevMode";
    public static final String USE_DEV_MODE_HEADER = "useDevModeHeader";
    public static final String TOR_DIR = "torDir";
    public static final String STORAGE_DIR = "storageDir";
    public static final String KEY_STORAGE_DIR = "keyStorageDir";
    public static final String WALLET_DIR = "walletDir";
    public static final String USE_DEV_PRIVILEGE_KEYS = "useDevPrivilegeKeys";
    public static final String DUMP_STATISTICS = "dumpStatistics";
    public static final String IGNORE_DEV_MSG = "ignoreDevMsg";
    public static final String PROVIDERS = "providers";
    public static final String SEED_NODES = "seedNodes";
    public static final String BAN_LIST = "banList";
    public static final String NODE_PORT = "nodePort";
    public static final String USE_LOCALHOST_FOR_P2P = "useLocalhostForP2P";
    public static final String MAX_CONNECTIONS = "maxConnections";
    public static final String SOCKS_5_PROXY_BTC_ADDRESS = "socks5ProxyBtcAddress";
    public static final String SOCKS_5_PROXY_HTTP_ADDRESS = "socks5ProxyHttpAddress";
    public static final String USE_TOR_FOR_BTC = "useTorForBtc";
    public static final String TORRC_FILE = "torrcFile";
    public static final String TORRC_OPTIONS = "torrcOptions";
    public static final String TOR_CONTROL_PORT = "torControlPort";
    public static final String TOR_CONTROL_PASSWORD = "torControlPassword";
    public static final String TOR_CONTROL_COOKIE_FILE = "torControlCookieFile";
    public static final String TOR_CONTROL_USE_SAFE_COOKIE_AUTH = "torControlUseSafeCookieAuth";
    public static final String TOR_STREAM_ISOLATION = "torStreamIsolation";
    public static final String MSG_THROTTLE_PER_SEC = "msgThrottlePerSec";
    public static final String MSG_THROTTLE_PER_10_SEC = "msgThrottlePer10Sec";
    public static final String SEND_MSG_THROTTLE_TRIGGER = "sendMsgThrottleTrigger";
    public static final String SEND_MSG_THROTTLE_SLEEP = "sendMsgThrottleSleep";
    public static final String IGNORE_LOCAL_BTC_NODE = "ignoreLocalBtcNode";
    public static final String BITCOIN_REGTEST_HOST = "bitcoinRegtestHost";
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
    public static final String DUMP_DELAYED_PAYOUT_TXS = "dumpDelayedPayoutTxs";
    public static final String ALLOW_FAULTY_DELAYED_TXS = "allowFaultyDelayedTxs";
    public static final String API_PASSWORD = "apiPassword";
    public static final String API_PORT = "apiPort";
    public static final String PREVENT_PERIODIC_SHUTDOWN_AT_SEED_NODE = "preventPeriodicShutdownAtSeedNode";
    public static final String REPUBLISH_MAILBOX_ENTRIES = "republishMailboxEntries";
    public static final String BTC_TX_FEE = "btcTxFee";
    public static final String BTC_MIN_TX_FEE = "btcMinTxFee";
    public static final String BTC_FEES_TS = "bitcoinFeesTs";
    public static final String BYPASS_MEMPOOL_VALIDATION = "bypassMempoolValidation";

    // Default values for certain options
    public static final int UNSPECIFIED_PORT = -1;
    public static final String DEFAULT_REGTEST_HOST = "localhost";
    public static final int DEFAULT_NUM_CONNECTIONS_FOR_BTC = 9; // down from BitcoinJ default of 12
    public static final boolean DEFAULT_FULL_DAO_NODE = false;
    static final String DEFAULT_CONFIG_FILE_NAME = "bisq.properties";

    // Static fields that provide access to Config properties in locations where injecting
    // a Config instance is not feasible. See Javadoc for corresponding static accessors.
    private static File APP_DATA_DIR_VALUE;
    private static BaseCurrencyNetwork BASE_CURRENCY_NETWORK_VALUE = BaseCurrencyNetwork.BTC_MAINNET;

    // Default "data dir properties", i.e. properties that can determine the location of
    // Bisq's application data directory (appDataDir)
    public final String defaultAppName;
    public final File defaultUserDataDir;
    public final File defaultAppDataDir;
    public final File defaultConfigFile;

    // Options supported only at the command-line interface (cli)
    public final boolean helpRequested;
    public final File configFile;

    // Options supported on cmd line and in the config file
    public final String appName;
    public final File userDataDir;
    public final File appDataDir;
    public final int nodePort;
    public final int maxMemory;
    public final String logLevel;
    public final List<String> bannedBtcNodes;
    public final List<String> bannedPriceRelayNodes;
    public final List<String> bannedSeedNodes;
    public final BaseCurrencyNetwork baseCurrencyNetwork;
    public final NetworkParameters networkParameters;
    public final boolean ignoreLocalBtcNode;
    public final String bitcoinRegtestHost;
    public final boolean daoActivated;
    public final String referralId;
    public final boolean useDevMode;
    public final boolean useDevModeHeader;
    public final boolean useDevPrivilegeKeys;
    public final boolean dumpStatistics;
    public final boolean ignoreDevMsg;
    public final List<String> providers;
    public final List<String> seedNodes;
    public final List<String> banList;
    public final boolean useLocalhostForP2P;
    public final int maxConnections;
    public final String socks5ProxyBtcAddress;
    public final String socks5ProxyHttpAddress;
    public final File torrcFile;
    public final String torrcOptions;
    public final int torControlPort;
    public final String torControlPassword;
    public final File torControlCookieFile;
    public final boolean useTorControlSafeCookieAuth;
    public final boolean torStreamIsolation;
    public final int msgThrottlePerSec;
    public final int msgThrottlePer10Sec;
    public final int sendMsgThrottleTrigger;
    public final int sendMsgThrottleSleep;
    public final String btcNodes;
    public final boolean useTorForBtc;
    public final boolean useTorForBtcOptionSetExplicitly;
    public final String socks5DiscoverMode;
    public final boolean useAllProvidedNodes;
    public final String userAgent;
    public final int numConnectionsForBtc;
    public final String rpcUser;
    public final String rpcPassword;
    public final String rpcHost;
    public final int rpcPort;
    public final int rpcBlockNotificationPort;
    public final String rpcBlockNotificationHost;
    public final boolean dumpBlockchainData;
    public final boolean fullDaoNode;
    public final boolean fullDaoNodeOptionSetExplicitly;
    public final String genesisTxId;
    public final int genesisBlockHeight;
    public final long genesisTotalSupply;
    public final boolean dumpDelayedPayoutTxs;
    public final boolean allowFaultyDelayedTxs;
    public final String apiPassword;
    public final int apiPort;
    public final boolean preventPeriodicShutdownAtSeedNode;
    public final boolean republishMailboxEntries;
    public final boolean bypassMempoolValidation;

    // Properties derived from options but not exposed as options themselves
    public final File torDir;
    public final File walletDir;
    public final File storageDir;
    public final File keyStorageDir;

    // The parser that will be used to parse both cmd line and config file options
    private final OptionParser parser = new OptionParser();

    /**
     * Create a new {@link Config} instance using a randomly-generated default
     * {@value APP_NAME} and a newly-created temporary directory as the default
     * {@value USER_DATA_DIR} along with any command line arguments. This constructor is
     * primarily useful in test code, where no references or modifications should be made
     * to the actual system user data directory and/or real Bisq application data
     * directory. Most production use cases will favor calling the
     * {@link #Config(String, File, String...)} constructor directly.
     * @param args zero or more command line arguments in the form "--optName=optValue"
     * @throws ConfigException if any problems are encountered during option parsing
     * @see #Config(String, File, String...)
     */
    public Config(String... args) {
        this(randomAppName(), tempUserDataDir(), args);
    }

    /**
     * Create a new {@link Config} instance with the given default {@value APP_NAME} and
     * {@value USER_DATA_DIR} values along with any command line arguments, typically
     * those supplied via a Bisq application's main() method.
     * <p/>
     * This constructor performs all parsing of command line options and config file
     * options, assuming the default config file exists or a custom config file has been
     * specified via the {@value CONFIG_FILE} option and exists. For any options that
     * are present both at the command line and in the config file, the command line value
     * will take precedence. Note that the {@value HELP} and {@value CONFIG_FILE} options
     * are supported only at the command line and are disallowed within the config file
     * itself.
     * @param defaultAppName typically "Bisq" or similar
     * @param defaultUserDataDir typically the OS-specific user data directory location
     * @param args zero or more command line arguments in the form "--optName=optValue"
     * @throws ConfigException if any problems are encountered during option parsing
     */
    public Config(String defaultAppName, File defaultUserDataDir, String... args) {
        this.defaultAppName = defaultAppName;
        this.defaultUserDataDir = defaultUserDataDir;
        this.defaultAppDataDir = new File(defaultUserDataDir, defaultAppName);
        this.defaultConfigFile = absoluteConfigFile(defaultAppDataDir, DEFAULT_CONFIG_FILE_NAME);

        AbstractOptionSpec<Void> helpOpt =
                parser.accepts(HELP, "Print this help text")
                        .forHelp();

        ArgumentAcceptingOptionSpec<String> configFileOpt =
                parser.accepts(CONFIG_FILE, format("Specify configuration file. " +
                        "Relative paths will be prefixed by %s location.", APP_DATA_DIR))
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(DEFAULT_CONFIG_FILE_NAME);

        ArgumentAcceptingOptionSpec<String> appNameOpt =
                parser.accepts(APP_NAME, "Application name")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(this.defaultAppName);

        ArgumentAcceptingOptionSpec<File> userDataDirOpt =
                parser.accepts(USER_DATA_DIR, "User data directory")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(this.defaultUserDataDir);

        ArgumentAcceptingOptionSpec<File> appDataDirOpt =
                parser.accepts(APP_DATA_DIR, "Application data directory")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(defaultAppDataDir);

        ArgumentAcceptingOptionSpec<Integer> nodePortOpt =
                parser.accepts(NODE_PORT, "Port to listen on")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(9999);

        ArgumentAcceptingOptionSpec<Integer> maxMemoryOpt =
                parser.accepts(MAX_MEMORY, "Max. permitted memory (used only by headless versions)")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(1200);

        ArgumentAcceptingOptionSpec<String> logLevelOpt =
                parser.accepts(LOG_LEVEL, "Set logging level")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("OFF|ALL|ERROR|WARN|INFO|DEBUG|TRACE")
                        .defaultsTo(Level.INFO.levelStr);

        ArgumentAcceptingOptionSpec<String> bannedBtcNodesOpt =
                parser.accepts(BANNED_BTC_NODES, "List Bitcoin nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> bannedPriceRelayNodesOpt =
                parser.accepts(BANNED_PRICE_RELAY_NODES, "List Bisq price nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        ArgumentAcceptingOptionSpec<String> bannedSeedNodesOpt =
                parser.accepts(BANNED_SEED_NODES, "List Bisq seed nodes to ban")
                        .withRequiredArg()
                        .ofType(String.class)
                        .withValuesSeparatedBy(',')
                        .describedAs("host:port[,...]");

        //noinspection rawtypes
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
                parser.accepts(BITCOIN_REGTEST_HOST, "Bitcoin Core node when using BTC_REGTEST network")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("host[:port]")
                        .defaultsTo("");

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

        ArgumentAcceptingOptionSpec<Boolean> useDevModeHeaderOpt =
                parser.accepts(USE_DEV_MODE_HEADER,
                        "Use dev mode css scheme to distinguish dev instances.")
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

        ArgumentAcceptingOptionSpec<Boolean> ignoreDevMsgOpt =
                parser.accepts(IGNORE_DEV_MSG, "If set to true all signed " +
                        "network_messages from bisq developers are ignored (Global " +
                        "alert, Version update alert, Filters for offers, nodes or " +
                        "trading account data)")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> providersOpt =
                parser.accepts(PROVIDERS, "List custom pricenodes")
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
                parser.accepts(USE_LOCALHOST_FOR_P2P, "Use localhost P2P network for development. Only available for non-BTC_MAINNET configuration.")
                        .availableIf(BASE_CURRENCY_NETWORK)
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
                parser.accepts(MSG_THROTTLE_PER_SEC, "Message throttle per sec for connection class")
                        .withRequiredArg()
                        .ofType(int.class)
                        // With PERMITTED_MESSAGE_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
                        .defaultsTo(200);

        ArgumentAcceptingOptionSpec<Integer> msgThrottlePer10SecOpt =
                parser.accepts(MSG_THROTTLE_PER_10_SEC, "Message throttle per 10 sec for connection class")
                        .withRequiredArg()
                        .ofType(int.class)
                        // With PERMITTED_MESSAGE_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
                        .defaultsTo(1000);

        ArgumentAcceptingOptionSpec<Integer> sendMsgThrottleTriggerOpt =
                parser.accepts(SEND_MSG_THROTTLE_TRIGGER, "Time in ms when we trigger a sleep if 2 messages are sent")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(20); // Time in ms when we trigger a sleep if 2 messages are sent

        ArgumentAcceptingOptionSpec<Integer> sendMsgThrottleSleepOpt =
                parser.accepts(SEND_MSG_THROTTLE_SLEEP, "Pause in ms to sleep if we get too many messages to send")
                        .withRequiredArg()
                        .ofType(int.class)
                        .defaultsTo(50); // Pause in ms to sleep if we get too many messages to send

        ArgumentAcceptingOptionSpec<String> btcNodesOpt =
                parser.accepts(BTC_NODES, "Custom nodes used for BitcoinJ as comma separated IP addresses.")
                        .withRequiredArg()
                        .describedAs("ip[,...]")
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Boolean> useTorForBtcOpt =
                parser.accepts(USE_TOR_FOR_BTC, "If set to true BitcoinJ is routed over tor (socks 5 proxy).")
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

        ArgumentAcceptingOptionSpec<Boolean> dumpDelayedPayoutTxsOpt =
                parser.accepts(DUMP_DELAYED_PAYOUT_TXS, "Dump delayed payout transactions to file")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> allowFaultyDelayedTxsOpt =
                parser.accepts(ALLOW_FAULTY_DELAYED_TXS, "Allow completion of trades with faulty delayed " +
                        "payout transactions")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<String> apiPasswordOpt =
                parser.accepts(API_PASSWORD, "gRPC API password")
                        .withRequiredArg()
                        .defaultsTo("");

        ArgumentAcceptingOptionSpec<Integer> apiPortOpt =
                parser.accepts(API_PORT, "gRPC API port")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(9998);

        ArgumentAcceptingOptionSpec<Boolean> preventPeriodicShutdownAtSeedNodeOpt =
                parser.accepts(PREVENT_PERIODIC_SHUTDOWN_AT_SEED_NODE,
                        "Prevents periodic shutdown at seed nodes")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> republishMailboxEntriesOpt =
                parser.accepts(REPUBLISH_MAILBOX_ENTRIES,
                        "Republish mailbox messages at startup")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> bypassMempoolValidationOpt =
                parser.accepts(BYPASS_MEMPOOL_VALIDATION,
                        "Prevents mempool check of trade parameters")
                        .withRequiredArg()
                        .ofType(boolean.class)
                        .defaultsTo(false);

        try {
            CompositeOptionSet options = new CompositeOptionSet();

            // Parse command line options
            OptionSet cliOpts = parser.parse(args);
            options.addOptionSet(cliOpts);

            // Option parsing is strict at the command line, but we relax it now for any
            // subsequent config file processing. This is for compatibility with pre-1.2.6
            // versions that allowed unrecognized options in the bisq.properties config
            // file and because it follows suit with Bitcoin Core's config file behavior.
            parser.allowsUnrecognizedOptions();

            // Parse config file specified at the command line only if it was specified as
            // an absolute path. Otherwise, the config file will be processed later below.
            File configFile = null;
            OptionSpec<?>[] disallowedOpts = new OptionSpec<?>[]{helpOpt, configFileOpt};
            final boolean cliHasConfigFileOpt = cliOpts.has(configFileOpt);
            boolean configFileHasBeenProcessed = false;
            if (cliHasConfigFileOpt) {
                configFile = new File(cliOpts.valueOf(configFileOpt));
                if (configFile.isAbsolute()) {
                    Optional<OptionSet> configFileOpts = parseOptionsFrom(configFile, disallowedOpts);
                    if (configFileOpts.isPresent()) {
                        options.addOptionSet(configFileOpts.get());
                        configFileHasBeenProcessed = true;
                    }
                }
            }

            // Assign values to the following "data dir properties". If a
            // relatively-pathed config file was specified at the command line, any
            // entries it has for these options will be ignored, as it has not been
            // processed yet.
            this.appName = options.valueOf(appNameOpt);
            this.userDataDir = options.valueOf(userDataDirOpt);
            this.appDataDir = mkAppDataDir(options.has(appDataDirOpt) ?
                    options.valueOf(appDataDirOpt) :
                    new File(userDataDir, appName));

            // If the config file has not yet been processed, either because a relative
            // path was provided at the command line, or because no value was provided at
            // the command line, attempt to process the file now, falling back to the
            // default config file location if none was specified at the command line.
            if (!configFileHasBeenProcessed) {
                configFile = cliHasConfigFileOpt && !configFile.isAbsolute() ?
                        absoluteConfigFile(appDataDir, configFile.getPath()) :
                        absoluteConfigFile(appDataDir, DEFAULT_CONFIG_FILE_NAME);
                Optional<OptionSet> configFileOpts = parseOptionsFrom(configFile, disallowedOpts);
                configFileOpts.ifPresent(options::addOptionSet);
            }

            // Assign all remaining properties, with command line options taking
            // precedence over those provided in the config file (if any)
            this.helpRequested = options.has(helpOpt);
            this.configFile = configFile;
            this.nodePort = options.valueOf(nodePortOpt);
            this.maxMemory = options.valueOf(maxMemoryOpt);
            this.logLevel = options.valueOf(logLevelOpt);
            this.bannedBtcNodes = options.valuesOf(bannedBtcNodesOpt);
            this.bannedPriceRelayNodes = options.valuesOf(bannedPriceRelayNodesOpt);
            this.bannedSeedNodes = options.valuesOf(bannedSeedNodesOpt);
            this.baseCurrencyNetwork = (BaseCurrencyNetwork) options.valueOf(baseCurrencyNetworkOpt);
            this.networkParameters = baseCurrencyNetwork.getParameters();
            this.ignoreLocalBtcNode = options.valueOf(ignoreLocalBtcNodeOpt);
            this.bitcoinRegtestHost = options.valueOf(bitcoinRegtestHostOpt);
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
            this.useDevModeHeader = options.valueOf(useDevModeHeaderOpt);
            this.useDevPrivilegeKeys = options.valueOf(useDevPrivilegeKeysOpt);
            this.dumpStatistics = options.valueOf(dumpStatisticsOpt);
            this.ignoreDevMsg = options.valueOf(ignoreDevMsgOpt);
            this.providers = options.valuesOf(providersOpt);
            this.seedNodes = options.valuesOf(seedNodesOpt);
            this.banList = options.valuesOf(banListOpt);
            this.useLocalhostForP2P = !this.baseCurrencyNetwork.isMainnet() && options.valueOf(useLocalhostForP2POpt);
            this.maxConnections = options.valueOf(maxConnectionsOpt);
            this.socks5ProxyBtcAddress = options.valueOf(socks5ProxyBtcAddressOpt);
            this.socks5ProxyHttpAddress = options.valueOf(socks5ProxyHttpAddressOpt);
            this.msgThrottlePerSec = options.valueOf(msgThrottlePerSecOpt);
            this.msgThrottlePer10Sec = options.valueOf(msgThrottlePer10SecOpt);
            this.sendMsgThrottleTrigger = options.valueOf(sendMsgThrottleTriggerOpt);
            this.sendMsgThrottleSleep = options.valueOf(sendMsgThrottleSleepOpt);
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
            this.daoActivated = options.valueOf(daoActivatedOpt);
            this.dumpDelayedPayoutTxs = options.valueOf(dumpDelayedPayoutTxsOpt);
            this.allowFaultyDelayedTxs = options.valueOf(allowFaultyDelayedTxsOpt);
            this.apiPassword = options.valueOf(apiPasswordOpt);
            this.apiPort = options.valueOf(apiPortOpt);
            this.preventPeriodicShutdownAtSeedNode = options.valueOf(preventPeriodicShutdownAtSeedNodeOpt);
            this.republishMailboxEntries = options.valueOf(republishMailboxEntriesOpt);
            this.bypassMempoolValidation = options.valueOf(bypassMempoolValidationOpt);
        } catch (OptionException ex) {
            throw new ConfigException("problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage());
        }

        // Create all appDataDir subdirectories and assign to their respective properties
        File btcNetworkDir = mkdir(appDataDir, baseCurrencyNetwork.name().toLowerCase());
        this.keyStorageDir = mkdir(btcNetworkDir, "keys");
        this.storageDir = mkdir(btcNetworkDir, "db");
        this.torDir = mkdir(btcNetworkDir, "tor");
        this.walletDir = mkdir(btcNetworkDir, "wallet");

        // Assign values to special-case static fields
        APP_DATA_DIR_VALUE = appDataDir;
        BASE_CURRENCY_NETWORK_VALUE = baseCurrencyNetwork;
    }

    private static File absoluteConfigFile(File parentDir, String relativeConfigFilePath) {
        return new File(parentDir, relativeConfigFilePath);
    }

    private Optional<OptionSet> parseOptionsFrom(File configFile, OptionSpec<?>[] disallowedOpts) {
        if (!configFile.exists()) {
            if (!configFile.equals(absoluteConfigFile(appDataDir, DEFAULT_CONFIG_FILE_NAME)))
                throw new ConfigException("The specified config file '%s' does not exist.", configFile);
            return Optional.empty();
        }

        ConfigFileReader configFileReader = new ConfigFileReader(configFile);
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

    public void printHelp(OutputStream sink, HelpFormatter formatter) {
        try {
            parser.formatHelpWith(formatter);
            parser.printHelpOn(sink);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }


    // == STATIC UTILS ===================================================================

    private static String randomAppName() {
        try {
            File file = Files.createTempFile("Bisq", "Temp").toFile();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return file.toPath().getFileName().toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static File tempUserDataDir() {
        try {
            return Files.createTempDirectory("BisqTempUserData").toFile();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Creates {@value APP_DATA_DIR} including any nonexistent parent directories. Does
     * nothing if the directory already exists.
     * @return the given directory, now guaranteed to exist
     */
    private static File mkAppDataDir(File dir) {
        if (!dir.exists()) {
            try {
                Files.createDirectories(dir.toPath());
            } catch (IOException ex) {
                throw new UncheckedIOException(format("Application data directory '%s' could not be created", dir), ex);
            }
        }
        return dir;
    }

    /**
     * Creates child directory assuming parent directories already exist. Does nothing if
     * the directory already exists.
     * @return the child directory, now guaranteed to exist
     */
    private static File mkdir(File parent, String child) {
        File dir = new File(parent, child);
        if (!dir.exists()) {
            try {
                Files.createDirectory(dir.toPath());
            } catch (IOException ex) {
                throw new UncheckedIOException(format("Directory '%s' could not be created", dir), ex);
            }
        }
        return dir;
    }


    // == STATIC ACCESSORS ======================================================================

    /**
     * Static accessor that returns the same value as the non-static
     * {@link #appDataDir} property. For use only in the {@code Overlay} class, where
     * because of its large number of subclasses, injecting the Guice-managed
     * {@link Config} class is not worth the effort. {@link #appDataDir} should be
     * favored in all other cases.
     * @throws NullPointerException if the static value has not yet been assigned, i.e. if
     * the Guice-managed {@link Config} class has not yet been instantiated elsewhere.
     * This should never be the case, as Guice wiring always happens before any
     * {@code Overlay} class is instantiated.
     */
    public static File appDataDir() {
        return checkNotNull(APP_DATA_DIR_VALUE, "The static appDataDir has not yet " +
                "been assigned. A Config instance must be instantiated (usually by " +
                "Guice) before calling this method.");
    }

    /**
     * Static accessor that returns either the default base currency network value of
     * {@link BaseCurrencyNetwork#BTC_MAINNET} or the value assigned via the
     * {@value BASE_CURRENCY_NETWORK} option. The non-static
     * {@link #baseCurrencyNetwork} property should be favored whenever possible and
     * this static accessor should be used only in code locations where it is infeasible
     * or too cumbersome to inject the normal Guice-managed singleton {@link Config}
     * instance.
     */
    public static BaseCurrencyNetwork baseCurrencyNetwork() {
        return BASE_CURRENCY_NETWORK_VALUE;
    }

    /**
     * Static accessor that returns the value of
     * {@code baseCurrencyNetwork().getParameters()} for convenience and to avoid violating
     * the <a href="https://en.wikipedia.org/wiki/Law_of_Demeter">Law of Demeter</a>. The
     * non-static {@link #baseCurrencyNetwork} property should be favored whenever
     * possible.
     * @see #baseCurrencyNetwork()
     */
    public static NetworkParameters baseCurrencyNetworkParameters() {
        return BASE_CURRENCY_NETWORK_VALUE.getParameters();
    }
}
