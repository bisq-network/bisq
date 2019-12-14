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

import bisq.core.btc.BtcOptionKeys;
import bisq.core.btc.setup.RegTestHost;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.DaoSetup;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.TradeManager;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.ConnectionConfig;

import bisq.common.BisqException;
import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.DevEnv;
import bisq.common.config.BisqHelpFormatter;
import bisq.common.config.Config;
import bisq.common.config.ConfigException;
import bisq.common.config.HelpRequested;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.GracefulShutDownHandler;

import org.springframework.core.env.JOptCommandLinePropertySource;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import joptsimple.util.RegexMatcher;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.app.BisqEnvironment.BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

@Slf4j
public abstract class BisqExecutable implements GracefulShutDownHandler, BisqSetup.BisqSetupListener {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final String HELP_KEY = "help";

    private final String fullName;
    private final String scriptName;
    private final String appName;
    private final String version;

    protected Injector injector;
    protected AppModule module;
    protected BisqEnvironment bisqEnvironment;
    protected Config config;

    public BisqExecutable(String fullName, String scriptName, String appName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.appName = appName;
        this.version = version;
    }

    public void execute(String[] args) throws Exception {

        try {
            config = new Config(appName, args);
        } catch (HelpRequested helpRequested) {
            helpRequested.printHelp(System.out, new BisqHelpFormatter(fullName, scriptName, version));
            System.exit(EXIT_SUCCESS);
        } catch (ConfigException ex) {
            System.err.println("error: " + ex.getMessage());
            System.exit(EXIT_FAILURE);
        }

        initAppDir(config.getAppDataDir());

        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
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
        bisqEnvironment = new BisqEnvironment(
                new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
        configUserThread();
        CoreSetup.setup(config);
        addCapabilities();

        // If application is JavaFX application we need to wait until it is initialized
        launchApplication();
    }

    protected abstract void configUserThread();

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

        setupPersistedDataHosts(injector);
    }

    protected void setupDevEnv() {
        DevEnv.setDevMode(config.isUseDevMode());
        DevEnv.setDaoActivated(config.isDaoActivated());
    }

    protected void setupPersistedDataHosts(Injector injector) {
        try {
            PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));
        } catch (Throwable t) {
            log.error("Error at PersistedDataHost.apply: {}", t.toString(), t);
            // If we are in dev mode we want to get the exception if some db files are corrupted
            // We need to delay it as the stage is not created yet and so popups would not be shown.
            if (DevEnv.isDevMode())
                UserThread.runAfter(() -> {
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
                    log.info("OpenOfferManager shutdown completed");
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        log.info("P2PService shutdown completed");
                        injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                            log.info("WalletsSetup shutdown completed");
                            module.close(injector);
                            resultHandler.handleResult();
                            log.info("Graceful shutdown completed. Exiting now.");
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
        //NetworkOptionKeys
        parser.accepts(NetworkOptionKeys.TORRC_OPTIONS,
                "A list of torrc-entries to amend to Bisq's torrc. Note that torrc-entries," +
                        "which are critical to Bisq's flawless operation, cannot be overwritten. " +
                        "[torrc options line, torrc option, ...]")
                .withRequiredArg()
                .withValuesConvertedBy(RegexMatcher.regex("^([^\\s,]+\\s[^,]+,?\\s*)+$"));

        parser.accepts(NetworkOptionKeys.EXTERNAL_TOR_CONTROL_PORT,
                "The control port of an already running Tor service to be used by Bisq.")
                //.availableUnless(Config.TORRC_FILE, NetworkOptionKeys.TORRC_OPTIONS)
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

        parser.accepts(Config.USE_DEV_PRIVILEGE_KEYS,
                format("If that is true all the privileged features which requires a private key " +
                        "to enable it are overridden by a dev key pair (This is for developers only!) (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        parser.accepts(Config.REFERRAL_ID,
                "Optional Referral ID (e.g. for API users or pro market makers)")
                .withRequiredArg();

        parser.accepts(Config.USE_DEV_MODE,
                format("Enables dev mode which is used for convenience for developer testing (default: %s)", "false"))
                .withRequiredArg()
                .ofType(boolean.class);

        //BtcOptionKeys
        parser.accepts(BtcOptionKeys.REG_TEST_HOST,
                format("Bitcoin regtest host when using BTC_REGTEST network (default: %s)", RegTestHost.DEFAULT_HOST))
                .withRequiredArg()
                .describedAs("host");

        parser.accepts(BtcOptionKeys.IGNORE_LOCAL_BTC_NODE,
                "If set to true a Bitcoin core node running locally will be ignored")
                .withRequiredArg();

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

        parser.accepts(DaoOptionKeys.RPC_HOST,
                "Bitcoind rpc host")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_PORT,
                "Bitcoind rpc port")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT,
                "Bitcoind rpc port for block notifications")
                .withRequiredArg();

        parser.accepts(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST,
                "Bitcoind rpc accepted incoming host for block notifications")
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

    private void initAppDir(File appDataDir) {
        Path dir = appDataDir.toPath();
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
