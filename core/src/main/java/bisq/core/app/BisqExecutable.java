/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.BtcOptionKeys;
import bisq.core.btc.setup.RegTestHost;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.DaoSetup;
import bisq.core.exceptions.BisqException;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.trade.TradeManager;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.ConnectionConfig;

import bisq.common.CommonOptionKeys;
import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.DevEnv;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.storage.CorruptedDatabaseFilesHandler;
import bisq.common.storage.Storage;

import org.springframework.core.env.JOptCommandLinePropertySource;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import joptsimple.util.RegexMatcher;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.app.BisqEnvironment.DEFAULT_APP_NAME;
import static bisq.core.app.BisqEnvironment.DEFAULT_USER_DATA_DIR;
import static bisq.core.btc.BaseCurrencyNetwork.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

@Slf4j
public abstract class BisqExecutable implements GracefulShutDownHandler, BisqSetup.BisqSetupListener {

    private final String fullName;
    private final String scriptName;
    private final String version;

    protected Injector injector;
    protected AppModule module;
    protected BisqEnvironment bisqEnvironment;

    public BisqExecutable(String fullName, String scriptName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.version = version;
    }

    public static boolean setupInitialOptionParser(String[] args) throws IOException {
        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY,
                format("User data directory (default: %s)", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.APP_NAME_KEY,
                format("Application name (default: %s)", DEFAULT_APP_NAME))
                .withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.err.println("error: " + ex.getMessage());
            System.exit(EXIT_FAILURE);
            return false;
        }
        BisqEnvironment bisqEnvironment = getBisqEnvironment(options);

        // need to call that before BisqAppMain().execute(args)
        BisqExecutable.initAppDir(bisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));
        return true;
    }


    private static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;
    private static final String HELP_KEY = "help";

    public void execute(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.formatHelpWith(new BisqHelpFormatter(fullName, scriptName, version));
        parser.accepts(HELP_KEY, "This help text").forHelp();

        this.customizeOptionParsing(parser);

        OptionSet options;
        try {
            options = parser.parse(args);
            if (options.has(HELP_KEY)) {
                parser.printHelpOn(System.out);
                System.exit(EXIT_SUCCESS);
                return;
            }
        } catch (OptionException ex) {
            System.err.println("error: " + ex.getMessage());
            System.exit(EXIT_FAILURE);
            return;
        }

        this.doExecute(options);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doExecute(OptionSet options) {
        setupEnvironment(options);
        configUserThread();
        configCoreSetup(options);
        addCapabilities();

        // If application is JavaFX application we need to wait until it is initialized
        launchApplication();
    }

    protected abstract void configUserThread();

    protected void setupEnvironment(OptionSet options) {
        /*
         * JOptSimple does support input parsing. However, doing only options = parser.parse(args) isn't enough to trigger the parsing.
         * The parsing is done when the actual value is going to be retrieved, i.e. options.valueOf(attributename).
         *
         * In order to keep usability high, we work around the aforementioned characteristics by catching the exception below
         * (valueOf is called somewhere in getBisqEnvironment), thus, neatly inform the user of a ill-formed parameter and stop execution.
         *
         * Might be changed when the project features more user parameters meant for the user.
         */
        try {
            bisqEnvironment = getBisqEnvironment(options);
        } catch (OptionException e) {
            // unfortunately, the OptionArgumentConversionException is not visible so we cannot catch only those.
            // hence, workaround
            if (e.getCause() != null)
                // get something like "Error while parsing application parameter '--torrcFile': File [/path/to/file] does not exist"
                System.err.println("Error while parsing application parameter '--" + e.options().get(0) + "': " + e.getCause().getMessage());
            else
                System.err.println("Error while parsing application parameter '--" + e.options().get(0));

            // we only tried to load some config until now, so no graceful shutdown is required
            System.exit(1);
        }
    }

    protected void configCoreSetup(OptionSet options) {
        CoreSetup.setup(getBisqEnvironment(options));
    }

    protected void addCapabilities() {
    }

    // The onApplicationLaunched call must map to UserThread, so that all following methods are running in the
    // thread the application is running and we don't run into thread interference.
    protected abstract void launchApplication();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // If application is a JavaFX application we need wait for onApplicationLaunched
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Headless versions can call inside launchApplication the onApplicationLaunched() manually
    protected void onApplicationLaunched() {
        setupGuice();
        startApplication();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupGuice() {
        module = getModule();
        injector = getInjector();
        applyInjector();
    }

    protected abstract AppModule getModule();

    protected Injector getInjector() {
        return Guice.createInjector(module);
    }

    protected void applyInjector() {
        setupDevEnv();

        setCorruptedDataBaseFilesHandler();

        setupPersistedDataHosts(injector);
    }

    protected void setupDevEnv() {
        DevEnv.setDevMode(injector.getInstance(Key.get(Boolean.class, Names.named(CommonOptionKeys.USE_DEV_MODE))));
        DevEnv.setDaoActivated(BisqEnvironment.isDaoActivated(bisqEnvironment));
    }

    private void setCorruptedDataBaseFilesHandler() {
        CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler = injector.getInstance(CorruptedDatabaseFilesHandler.class);
        Storage.setCorruptedDatabaseFilesHandler(corruptedDatabaseFilesHandler);
    }

    protected void setupPersistedDataHosts(Injector injector) {
        try {
            PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));
        } catch (Throwable t) {
            // If we are in dev mode we want to get the exception if some db files are corrupted
            // We need to delay it as the stage is not created yet and so popups would not be shown.
            if (DevEnv.isDevMode())
                UserThread.runAfter(() -> {
                    log.error("Error at PersistedDataHost.apply: {}", t.toString());
                    throw t;
                }, 2);
        }
    }

    protected abstract void startApplication();

    // Once the application is ready we get that callback and we start the setup
    protected void onApplicationStarted() {
        startAppSetup();
    }

    protected void startAppSetup() {
        BisqSetup bisqSetup = injector.getInstance(BisqSetup.class);
        bisqSetup.addBisqSetupListener(this);
        bisqSetup.start();
    }

    public abstract void onSetupComplete();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GracefulShutDownHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // This might need to be overwritten in case the application is not using all modules
    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(TradeManager.class).shutDown();
                injector.getInstance(DaoSetup.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                            module.close(injector);
                            log.debug("Graceful shutdown completed");
                            resultHandler.handleResult();

                            System.exit(0);
                        });
                        injector.getInstance(WalletsSetup.class).shutDown();
                        injector.getInstance(BtcWalletService.class).shutDown();
                        injector.getInstance(BsqWalletService.class).shutDown();
                    });
                });
                // we wait max 20 sec.
                UserThread.runAfter(() -> {
                    log.warn("Timeout triggered resultHandler");
                    resultHandler.handleResult();
                }, 20);
            } else {
                log.warn("injector == null triggered resultHandler");
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.error("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // customizeOptionParsing
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void customizeOptionParsing(OptionParser parser) {
        //CommonOptionKeys
        parser.accepts(CommonOptionKeys.LOG_LEVEL_KEY,
                format("Log level (default: %s)", BisqEnvironment.LOG_LEVEL_DEFAULT))
                .withRequiredArg()
                .describedAs("OFF|ALL|ERROR|WARN|INFO|DEBUG|TRACE");

        //NetworkOptionKeys
        parser.accepts(NetworkOptionKeys.SEED_NODES_KEY,
                "Override hard coded seed nodes as comma separated list e.g. " +
                        "'rxdkppp3vicnbgqt.onion:8002,mfla72c4igh5ta2t.onion:8002'")
                .withRequiredArg()
                .describedAs("host:port[,...]");

        parser.accepts(NetworkOptionKeys.BAN_LIST,
                "Nodes to exclude from network connections.")
                .withRequiredArg()
                .describedAs("host:port[,...]");

        // use a fixed port as arbitrator use that for his ID
        parser.accepts(NetworkOptionKeys.PORT_KEY,
                format("Port to listen on (default: %s)", "9999"))
                .withRequiredArg()
                .ofType(int.class);

        parser.accepts(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P,
                format("Use localhost P2P network for development (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(NetworkOptionKeys.MAX_CONNECTIONS,
                format("Max. connections a peer will try to keep (default: %s)", P2PService.MAX_CONNECTIONS_DEFAULT))
                .withRequiredArg()
                .ofType(int.class);

        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS,
                "A proxy address to be used for Bitcoin network.")
                .withRequiredArg()
                .describedAs("host:port");

        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS,
                "A proxy address to be used for Http requests (should be non-Tor)")
                .withRequiredArg()
                .describedAs("host:port");

        parser.accepts(NetworkOptionKeys.TORRC_FILE,
                "An existing torrc-file to be sourced for Tor. Note that torrc-entries, " +
                        "which are critical to Bisq's flawless operation, cannot be overwritten.")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));

        parser.accepts(NetworkOptionKeys.TORRC_OPTIONS,
                "A list of torrc-entries to amend to Bisq's torrc. Note that torrc-entries," +
                        "which are critical to Bisq's flawless operation, cannot be overwritten. " +
                        "[torrc options line, torrc option, ...]")
                .withRequiredArg()
                .withValuesConvertedBy(RegexMatcher.regex("^([^\\s,]+\\s[^,]+,?\\s*)+$"));

        parser.accepts(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT,
                "The control port of an already running Tor service to be used by Bisq.")
                .availableUnless(NetworkOptionKeys.TORRC_FILE, NetworkOptionKeys.TORRC_OPTIONS)
                .withRequiredArg()
                .ofType(int.class)
                .describedAs("port");

        parser.accepts(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD,
                "The password for controlling the already running Tor service.")
                .availableIf(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT)
                .withRequiredArg();

        parser.accepts(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE,
                "The cookie file for authenticating against the already running Tor service. " +
                        "Use in conjunction with --" + NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE)
                .availableIf(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT)
                .availableUnless(NetworkOptionKeys.EXTERNAL_TOR_PASSWORD)
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING, PathProperties.READABLE));

        parser.accepts(NetworkOptionKeys.EXTERNAL_TOR_USE_SAFECOOKIE,
                "Use the SafeCookie method when authenticating to the already running Tor service.")
                .availableIf(NetworkOptionKeys.EXTERNAL_TOR_COOKIE_FILE);

        parser.accepts(NetworkOptionKeys.TOR_STREAM_ISOLATION,
                "Use stream isolation for Tor [experimental!].");

        parser.accepts(NetworkOptionKeys.MSG_THROTTLE_PER_SEC,
                format("Message throttle per sec for connection class (default: %s)",
                        String.valueOf(ConnectionConfig.MSG_THROTTLE_PER_SEC)))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC,
                format("Message throttle per 10 sec for connection class (default: %s)",
                        String.valueOf(ConnectionConfig.MSG_THROTTLE_PER_10_SEC)))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER,
                format("Time in ms when we trigger a sleep if 2 messages are sent (default: %s)",
                        String.valueOf(ConnectionConfig.SEND_MSG_THROTTLE_TRIGGER)))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP,
                format("Pause in ms to sleep if we get too many messages to send (default: %s)",
                        String.valueOf(ConnectionConfig.SEND_MSG_THROTTLE_SLEEP)))
                .withRequiredArg()
                .ofType(int.class);

        //AppOptionKeys
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY,
                format("User data directory (default: %s)", BisqEnvironment.DEFAULT_USER_DATA_DIR))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.APP_NAME_KEY,
                format("Application name (default: %s)", BisqEnvironment.DEFAULT_APP_NAME))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.MAX_MEMORY,
                format("Max. permitted memory (used only at headless versions) (default: %s)", "600"))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.APP_DATA_DIR_KEY,
                format("Application data directory (default: %s)", BisqEnvironment.DEFAULT_APP_DATA_DIR))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.IGNORE_DEV_MSG_KEY,
                format("If set to true all signed network_messages from bisq developers are ignored " +
                        "(Global alert, Version update alert, Filters for offers, nodes or trading account data) (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.DESKTOP_WITH_HTTP_API,
                format("If set to true Bisq Desktop starts with Http API (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.DESKTOP_WITH_GRPC_API,
                format("If set to true Bisq Desktop starts with gRPC API (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS,
                format("If that is true all the privileged features which requires a private key " +
                        "to enable it are overridden by a dev key pair (This is for developers only!) (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.REFERRAL_ID,
                "Optional Referral ID (e.g. for API users or pro market makers)")
                .withRequiredArg();

        parser.accepts(AppOptionKeys.HTTP_API_EXPERIMENTAL_FEATURES_ENABLED, "Enable experimental features of HTTP API (disabled by default)");
        parser.accepts(AppOptionKeys.HTTP_API_HOST, "Optional HTTP API host")
                .withRequiredArg()
                .defaultsTo("127.0.0.1");
        parser.accepts(AppOptionKeys.HTTP_API_PORT, "Optional HTTP API port")
                .withRequiredArg()
                .ofType(int.class)
                .defaultsTo(8080);

        parser.accepts(CommonOptionKeys.USE_DEV_MODE,
                format("Enables dev mode which is used for convenience for developer testing (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.DUMP_STATISTICS,
                format("If set to true the trade statistics are stored as json file in the data dir. (default: %s)",
                        "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(AppOptionKeys.PROVIDERS,
                "Custom providers (comma separated)")
                .withRequiredArg()
                .describedAs("host:port[,...]");

        //BtcOptionKeys
        parser.accepts(BtcOptionKeys.BASE_CURRENCY_NETWORK,
                format("Base currency network (default: %s)", BisqEnvironment.getDefaultBaseCurrencyNetwork().name()))
                .withRequiredArg()
                .ofType(String.class)
                .describedAs(format("%s|%s|%s|%s", BTC_MAINNET, BTC_TESTNET, BTC_REGTEST, BTC_DAO_TESTNET, BTC_DAO_BETANET, BTC_DAO_REGTEST));

        parser.accepts(BtcOptionKeys.REG_TEST_HOST,
                format("Bitcoin regtest host when using BTC_REGTEST network (default: %s)", RegTestHost.DEFAULT_HOST))
                .withRequiredArg()
                .describedAs("host");

        parser.accepts(BtcOptionKeys.BTC_NODES,
                "Custom nodes used for BitcoinJ as comma separated IP addresses.")
                .withRequiredArg()
                .describedAs("ip[,...]");

        parser.accepts(BtcOptionKeys.USE_TOR_FOR_BTC,
                "If set to true BitcoinJ is routed over tor (socks 5 proxy).")
                .withRequiredArg();

        parser.accepts(BtcOptionKeys.SOCKS5_DISCOVER_MODE,
                format("Specify discovery mode for Bitcoin nodes. One or more of: [ADDR, DNS, ONION, ALL]" +
                        " (comma separated, they get OR'd together). (default: %s)", "ALL"))
                .withRequiredArg()
                .describedAs("mode[,...]");

        parser.accepts(BtcOptionKeys.USE_ALL_PROVIDED_NODES,
                "Set to true if connection of bitcoin nodes should include clear net nodes")
                .withRequiredArg();

        parser.accepts(BtcOptionKeys.USER_AGENT,
                "User agent at btc node connections")
                .withRequiredArg();

        parser.accepts(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC,
                format("Number of connections to the Bitcoin network (default: %s)", "9"))
                .withRequiredArg();

        //RpcOptionKeys
        parser.accepts(DaoOptionKeys.RPC_USER,
                "Bitcoind rpc username")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_PASSWORD,
                "Bitcoind rpc password")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_PORT,
                "Bitcoind rpc port")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT,
                "Bitcoind rpc port for block notifications")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA,
                format("If set to true the blockchain data from RPC requests to Bitcoin Core are " +
                        "stored as json file in the data dir. (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(DaoOptionKeys.FULL_DAO_NODE,
                "If set to true the node requests the blockchain data via RPC requests " +
                        "from Bitcoin Core and provide the validated BSQ txs to the network. " +
                        "It requires that the other RPC properties are set as well.")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.GENESIS_TX_ID,
                "Genesis transaction ID when not using the hard coded one")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.GENESIS_BLOCK_HEIGHT,
                format("Genesis transaction block height when not using the hard coded one (default: %s)", "-1"))
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.GENESIS_TOTAL_SUPPLY,
                format("Genesis total supply when not using the hard coded one (default: %s)", "-1"))
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.DAO_ACTIVATED,
                format("Developer flag. If true it enables dao phase 2 features. (default: %s)", "true"))
                .withRequiredArg()
                .ofType(boolean.class);
    }

    public static BisqEnvironment getBisqEnvironment(OptionSet options) {
        return new BisqEnvironment(new JOptCommandLinePropertySource(BisqEnvironment.BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
    }

    public static void initAppDir(String appDir) {
        Path dir = Paths.get(appDir);
        if (Files.exists(dir)) {
            if (!Files.isWritable(dir))
                throw new BisqException("Application data directory '%s' is not writeable", dir);
            else
                return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new BisqException(ex, "Application data directory '%s' could not be created", dir);
        }
    }
}
