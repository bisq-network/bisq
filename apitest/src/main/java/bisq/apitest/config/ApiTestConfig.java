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

package bisq.apitest.config;

import bisq.common.config.CompositeOptionSet;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.HelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.net.InetAddress;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static joptsimple.internal.Strings.EMPTY;

@Slf4j
public class ApiTestConfig {

    // Global constants
    public static final String BSQ = "BSQ";
    public static final String BTC = "BTC";
    public static final String ARBITRATOR = "arbitrator";
    public static final String MEDIATOR = "mediator";
    public static final String REFUND_AGENT = "refundagent";

    // Option name constants
    static final String HELP = "help";
    static final String BASH_PATH = "bashPath";
    static final String BERKELEYDB_LIB_PATH = "berkeleyDbLibPath";
    static final String BITCOIN_PATH = "bitcoinPath";
    static final String BITCOIN_RPC_PORT = "bitcoinRpcPort";
    static final String BITCOIN_RPC_USER = "bitcoinRpcUser";
    static final String BITCOIN_RPC_PASSWORD = "bitcoinRpcPassword";
    static final String BITCOIN_REGTEST_HOST = "bitcoinRegtestHost";
    static final String CONFIG_FILE = "configFile";
    static final String ROOT_APP_DATA_DIR = "rootAppDataDir";
    static final String API_PASSWORD = "apiPassword";
    static final String RUN_SUBPROJECT_JARS = "runSubprojectJars";
    static final String BISQ_APP_INIT_TIME = "bisqAppInitTime";
    static final String SKIP_TESTS = "skipTests";
    static final String SHUTDOWN_AFTER_TESTS = "shutdownAfterTests";
    static final String SUPPORTING_APPS = "supportingApps";
    static final String CALL_RATE_METERING_CONFIG_PATH = "callRateMeteringConfigPath";
    static final String ENABLE_BISQ_DEBUGGING = "enableBisqDebugging";
    static final String REGISTER_DISPUTE_AGENTS = "registerDisputeAgents";

    // Default values for certain options
    static final String DEFAULT_CONFIG_FILE_NAME = "apitest.properties";

    // Static fields that provide access to Config properties in locations where injecting
    // a Config instance is not feasible.
    public static String BASH_PATH_VALUE;

    public final File defaultConfigFile;

    // Options supported only at the command line, not within a config file.
    public final boolean helpRequested;
    public final File configFile;

    // Options supported at the command line and a config file.
    public final File rootAppDataDir;
    public final String bashPath;
    public final String berkeleyDbLibPath;
    public final String bitcoinPath;
    public final String bitcoinRegtestHost;
    public final int bitcoinRpcPort;
    public final String bitcoinRpcUser;
    public final String bitcoinRpcPassword;
    // Daemon instances can use same gRPC password, but each needs a different apiPort.
    public final String apiPassword;
    public final boolean runSubprojectJars;
    public final long bisqAppInitTime;
    public final boolean skipTests;
    public final boolean shutdownAfterTests;
    public final List<String> supportingApps;
    public final String callRateMeteringConfigPath;
    public final boolean enableBisqDebugging;
    public final boolean registerDisputeAgents;

    // Immutable system configurations set in the constructor.
    public final String bitcoinDatadir;
    public final String userDir;
    public final boolean isRunningTest;
    public final String rootProjectDir;
    public final String baseBuildResourcesDir;
    public final String baseSrcResourcesDir;

    // The parser that will be used to parse both cmd line and config file options
    private final OptionParser parser = new OptionParser();

    public ApiTestConfig(String... args) {
        this.userDir = getProperty("user.dir");
        // If running a @Test, the current working directory is the :apitest subproject
        // folder.  If running ApiTestMain, the current working directory is the
        // bisq root project folder.
        this.isRunningTest = Paths.get(userDir).getFileName().toString().equals("apitest");
        this.rootProjectDir = isRunningTest
                ? Paths.get(userDir).getParent().toFile().getAbsolutePath()
                : Paths.get(userDir).toFile().getAbsolutePath();
        this.baseBuildResourcesDir = Paths.get(rootProjectDir, "apitest", "build", "resources", "main")
                .toFile().getAbsolutePath();
        this.baseSrcResourcesDir = Paths.get(rootProjectDir, "apitest", "src", "main", "resources")
                .toFile().getAbsolutePath();

        this.defaultConfigFile = absoluteConfigFile(baseBuildResourcesDir, DEFAULT_CONFIG_FILE_NAME);
        this.bitcoinDatadir = Paths.get(baseBuildResourcesDir, "Bitcoin-regtest").toFile().getAbsolutePath();

        AbstractOptionSpec<Void> helpOpt =
                parser.accepts(HELP, "Print this help text")
                        .forHelp();

        ArgumentAcceptingOptionSpec<String> configFileOpt =
                parser.accepts(CONFIG_FILE, format("Specify configuration file. " +
                        "Relative paths will be prefixed by %s location.", userDir))
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(DEFAULT_CONFIG_FILE_NAME);

        ArgumentAcceptingOptionSpec<File> appDataDirOpt =
                parser.accepts(ROOT_APP_DATA_DIR, "Application data directory")
                        .withRequiredArg()
                        .ofType(File.class)
                        .defaultsTo(new File(baseBuildResourcesDir));

        ArgumentAcceptingOptionSpec<String> bashPathOpt =
                parser.accepts(BASH_PATH, "Bash path")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo(
                                (getenv("SHELL") == null || !getenv("SHELL").contains("bash"))
                                        ? "/bin/bash"
                                        : getenv("SHELL"));

        ArgumentAcceptingOptionSpec<String> berkeleyDbLibPathOpt =
                parser.accepts(BERKELEYDB_LIB_PATH, "Berkeley DB lib path")
                        .withRequiredArg()
                        .ofType(String.class).defaultsTo(EMPTY);

        ArgumentAcceptingOptionSpec<String> bitcoinPathOpt =
                parser.accepts(BITCOIN_PATH, "Bitcoin path")
                        .withRequiredArg()
                        .ofType(String.class).defaultsTo("/usr/local/bin");

        ArgumentAcceptingOptionSpec<String> bitcoinRegtestHostOpt =
                parser.accepts(BITCOIN_REGTEST_HOST, "Bitcoin Core regtest host")
                        .withRequiredArg()
                        .ofType(String.class).defaultsTo(InetAddress.getLoopbackAddress().getHostAddress());

        ArgumentAcceptingOptionSpec<Integer> bitcoinRpcPortOpt =
                parser.accepts(BITCOIN_RPC_PORT, "Bitcoin Core rpc port (non-default)")
                        .withRequiredArg()
                        .ofType(Integer.class).defaultsTo(19443);

        ArgumentAcceptingOptionSpec<String> bitcoinRpcUserOpt =
                parser.accepts(BITCOIN_RPC_USER, "Bitcoin rpc user")
                        .withRequiredArg()
                        .ofType(String.class).defaultsTo("apitest");

        ArgumentAcceptingOptionSpec<String> bitcoinRpcPasswordOpt =
                parser.accepts(BITCOIN_RPC_PASSWORD, "Bitcoin rpc password")
                        .withRequiredArg()
                        .ofType(String.class).defaultsTo("apitest");

        ArgumentAcceptingOptionSpec<String> apiPasswordOpt =
                parser.accepts(API_PASSWORD, "gRPC API password")
                        .withRequiredArg()
                        .defaultsTo("xyz");

        ArgumentAcceptingOptionSpec<Boolean> runSubprojectJarsOpt =
                parser.accepts(RUN_SUBPROJECT_JARS,
                        "Run subproject build jars instead of full build jars")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Long> bisqAppInitTimeOpt =
                parser.accepts(BISQ_APP_INIT_TIME,
                        "Amount of time (ms) to wait on a Bisq instance's initialization")
                        .withRequiredArg()
                        .ofType(Long.class)
                        .defaultsTo(5000L);

        ArgumentAcceptingOptionSpec<Boolean> skipTestsOpt =
                parser.accepts(SKIP_TESTS,
                        "Start apps, but skip tests")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> shutdownAfterTestsOpt =
                parser.accepts(SHUTDOWN_AFTER_TESTS,
                        "Terminate all processes after tests")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(true);

        ArgumentAcceptingOptionSpec<String> supportingAppsOpt =
                parser.accepts(SUPPORTING_APPS,
                        "Comma delimited list of supporting apps (bitcoind,seednode,arbdaemon,...")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon");

        ArgumentAcceptingOptionSpec<String> callRateMeteringConfigPathOpt =
                parser.accepts(CALL_RATE_METERING_CONFIG_PATH,
                        "Install a ratemeters.json file to configure call rate metering interceptors")
                        .withRequiredArg()
                        .defaultsTo(EMPTY);

        ArgumentAcceptingOptionSpec<Boolean> enableBisqDebuggingOpt =
                parser.accepts(ENABLE_BISQ_DEBUGGING,
                        "Start Bisq apps with remote debug options")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> registerDisputeAgentsOpt =
                parser.accepts(REGISTER_DISPUTE_AGENTS,
                        "Register dispute agents in arbitration daemon")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(true);
        try {
            CompositeOptionSet options = new CompositeOptionSet();

            // Parse command line options
            OptionSet cliOpts = parser.parse(args);
            options.addOptionSet(cliOpts);

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

            // If the config file has not yet been processed, either because a relative
            // path was provided at the command line, or because no value was provided at
            // the command line, attempt to process the file now, falling back to the
            // default config file location if none was specified at the command line.
            if (!configFileHasBeenProcessed) {
                configFile = cliHasConfigFileOpt && !configFile.isAbsolute() ?
                        absoluteConfigFile(userDir, configFile.getPath()) :
                        defaultConfigFile;
                Optional<OptionSet> configFileOpts = parseOptionsFrom(configFile, disallowedOpts);
                configFileOpts.ifPresent(options::addOptionSet);
            }


            // Assign all remaining properties, with command line options taking
            // precedence over those provided in the config file (if any)
            this.helpRequested = options.has(helpOpt);
            this.configFile = configFile;
            this.rootAppDataDir = options.valueOf(appDataDirOpt);
            bashPath = options.valueOf(bashPathOpt);
            this.berkeleyDbLibPath = options.valueOf(berkeleyDbLibPathOpt);
            this.bitcoinPath = options.valueOf(bitcoinPathOpt);
            this.bitcoinRegtestHost = options.valueOf(bitcoinRegtestHostOpt);
            this.bitcoinRpcPort = options.valueOf(bitcoinRpcPortOpt);
            this.bitcoinRpcUser = options.valueOf(bitcoinRpcUserOpt);
            this.bitcoinRpcPassword = options.valueOf(bitcoinRpcPasswordOpt);
            this.apiPassword = options.valueOf(apiPasswordOpt);
            this.runSubprojectJars = options.valueOf(runSubprojectJarsOpt);
            this.bisqAppInitTime = options.valueOf(bisqAppInitTimeOpt);
            this.skipTests = options.valueOf(skipTestsOpt);
            this.shutdownAfterTests = options.valueOf(shutdownAfterTestsOpt);
            this.supportingApps = asList(options.valueOf(supportingAppsOpt).split(","));
            this.callRateMeteringConfigPath = options.valueOf(callRateMeteringConfigPathOpt);
            this.enableBisqDebugging = options.valueOf(enableBisqDebuggingOpt);
            this.registerDisputeAgents = options.valueOf(registerDisputeAgentsOpt);

            // Assign values to special-case static fields.
            BASH_PATH_VALUE = bashPath;

        } catch (OptionException ex) {
            throw new IllegalStateException(format("Problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage()));
        }
    }

    public boolean hasSupportingApp(String... supportingApp) {
        return stream(supportingApp).anyMatch(this.supportingApps::contains);
    }

    public void printHelp(OutputStream sink, HelpFormatter formatter) {
        try {
            parser.formatHelpWith(formatter);
            parser.printHelpOn(sink);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Optional<OptionSet> parseOptionsFrom(File configFile, OptionSpec<?>[] disallowedOpts) {
        if (!configFile.exists() && !configFile.equals(absoluteConfigFile(userDir, DEFAULT_CONFIG_FILE_NAME)))
            throw new IllegalStateException(format("The specified config file '%s' does not exist.", configFile));

        Properties properties = getProperties(configFile);
        List<String> optionLines = new ArrayList<>();
        properties.forEach((k, v) -> {
            optionLines.add("--" + k + "=" + v); // dashes expected by jopt parser below
        });

        OptionSet configFileOpts = parser.parse(optionLines.toArray(new String[0]));
        for (OptionSpec<?> disallowedOpt : disallowedOpts)
            if (configFileOpts.has(disallowedOpt))
                throw new IllegalStateException(
                        format("The '%s' option is disallowed in config files",
                                disallowedOpt.options().get(0)));

        return Optional.of(configFileOpts);
    }

    private Properties getProperties(File configFile) {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(configFile.getAbsolutePath()));
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    format("Could not load properties from config file %s",
                            configFile.getAbsolutePath()), ex);
        }
    }

    private static File absoluteConfigFile(String parentDir, String relativeConfigFilePath) {
        return new File(parentDir, relativeConfigFilePath);
    }
}
