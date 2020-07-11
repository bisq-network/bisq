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

import bisq.common.storage.FileUtil;

import joptsimple.AbstractOptionSpec;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.HelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static joptsimple.internal.Strings.EMPTY;

@Slf4j
public class ApiTestConfig {

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
    static final String RUN_ARB_NODE_AS_DESKTOP = "runArbNodeAsDesktop";
    static final String RUN_ALICE_NODE_AS_DESKTOP = "runAliceNodeAsDesktop";
    static final String RUN_BOB_NODE_AS_DESKTOP = "runBobNodeAsDesktop";
    static final String SKIP_TESTS = "skipTests";
    static final String SHUTDOWN_AFTER_TESTS = "shutdownAfterTests";
    static final String NUM_SETUP_TASKS = "numSetupTasks";

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
    public final boolean runArbNodeAsDesktop;
    public final boolean runAliceNodeAsDesktop;
    public final boolean runBobNodeAsDesktop;
    public final boolean skipTests;
    public final boolean shutdownAfterTests;
    public final int numSetupTasks;

    // Immutable system configurations.
    public final String bitcoinDatadir;
    public final String userDir;

    // The parser that will be used to parse both cmd line and config file options
    private final OptionParser parser = new OptionParser();

    public ApiTestConfig(String... args) {
        this.defaultConfigFile = absoluteConfigFile(
                Paths.get("apitest", "build", "resources", "main").toFile().getAbsolutePath(),
                DEFAULT_CONFIG_FILE_NAME);
        this.bitcoinDatadir = Paths.get("apitest", "build", "resources", "main", "Bitcoin-regtest")
                .toFile().getAbsolutePath();
        this.userDir = getProperty("user.dir");

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
                        .defaultsTo(Paths.get("apitest", "build", "resources", "main")
                                .toFile().getAbsoluteFile());

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
                        .ofType(String.class).defaultsTo("localhost");

        ArgumentAcceptingOptionSpec<Integer> bitcoinRpcPortOpt =
                parser.accepts(BITCOIN_RPC_PORT, "Bitcoin Core rpc port")
                        .withRequiredArg()
                        .ofType(Integer.class).defaultsTo(18443);

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

        ArgumentAcceptingOptionSpec<Boolean> runArbNodeAsDesktopOpt =
                parser.accepts(RUN_ARB_NODE_AS_DESKTOP,
                        "Run Arbitration node as desktop")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false); // TODO how do I register arbitrator?

        ArgumentAcceptingOptionSpec<Boolean> runAliceNodeAsDesktopOpt =
                parser.accepts(RUN_ALICE_NODE_AS_DESKTOP,
                        "Run Alice node as desktop")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

        ArgumentAcceptingOptionSpec<Boolean> runBobNodeAsDesktopOpt =
                parser.accepts(RUN_BOB_NODE_AS_DESKTOP,
                        "Run Bob node as desktop")
                        .withRequiredArg()
                        .ofType(Boolean.class)
                        .defaultsTo(false);

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

        ArgumentAcceptingOptionSpec<Integer> numSetupTasksOpt =
                parser.accepts(NUM_SETUP_TASKS,
                        "Number of test setup tasks")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(4);

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
            this.runArbNodeAsDesktop = options.valueOf(runArbNodeAsDesktopOpt);
            this.runAliceNodeAsDesktop = options.valueOf(runAliceNodeAsDesktopOpt);
            this.runBobNodeAsDesktop = options.valueOf(runBobNodeAsDesktopOpt);
            this.skipTests = options.valueOf(skipTestsOpt);
            this.shutdownAfterTests = options.valueOf(shutdownAfterTestsOpt);
            this.numSetupTasks = options.valueOf(numSetupTasksOpt);

            // Assign values to special-case static fields.
            BASH_PATH_VALUE = bashPath;

            // Write and save bitcoin.conf to disk, with the correct path to
            // the blocknotify script.
            installBitcoinConf();
            installBitcoinBlocknotify();

        } catch (OptionException ex) {
            throw new IllegalStateException(format("Problem parsing option '%s': %s",
                    ex.options().get(0),
                    ex.getCause() != null ?
                            ex.getCause().getMessage() :
                            ex.getMessage()));
        }
    }

    public void printHelp(OutputStream sink, HelpFormatter formatter) {
        try {
            parser.formatHelpWith(formatter);
            parser.printHelpOn(sink);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void installBitcoinConf() {
        // We write out and install a bitcoin.conf file for regtest/dao mode because
        // the path to the blocknotify script is not known until runtime.
        String bitcoinConf = "\n"
                + "regtest=1\n"
                + "[regtest]\n"
                + "peerbloomfilters=1\n"
                + "rpcport=18443\n"
                + "server=1\n"
                + "txindex=1\n"
                + "debug=net\n"
                + "deprecatedrpc=generate\n"
                + "rpcuser=apitest\n"
                + "rpcpassword=apitest\n"
                + "blocknotify=" + bashPath + " " + bitcoinDatadir + "/blocknotify %\n";
        String chmod644Perms = "rw-r--r--";
        saveToFile(bitcoinConf, bitcoinDatadir, "bitcoin.conf", chmod644Perms);
        log.info("Installed {} with perms {}.", bitcoinDatadir + "/bitcoin.conf", chmod644Perms);
    }

    private void installBitcoinBlocknotify() {
        // gradle is not working for this
        try {
            Path srcPath = Paths.get("apitest", "src", "main", "resources", "blocknotify");
            Path destPath = Paths.get(bitcoinDatadir, "blocknotify");
            Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
            String chmod700Perms = "rwx------";
            Files.setPosixFilePermissions(destPath, PosixFilePermissions.fromString(chmod700Perms));
            log.info("Installed {} with perms {}.", destPath.toString(), chmod700Perms);
        } catch (IOException e) {
            e.printStackTrace();
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

    private void saveToFile(String content,
                            String parentDir,
                            @SuppressWarnings("SameParameterValue") String relativeConfigFilePath,
                            String posixFilePermissions) {
        File tempFile = null;
        File file;
        try {
            file = absoluteConfigFile(parentDir, relativeConfigFilePath);
            tempFile = File.createTempFile("temp", relativeConfigFilePath, file.getParentFile());
            tempFile.deleteOnExit();
            try (PrintWriter out = new PrintWriter(tempFile)) {
                out.println(content);
            }
            FileUtil.renameFile(tempFile, file);
            Files.setPosixFilePermissions(Paths.get(file.toURI()), PosixFilePermissions.fromString(posixFilePermissions));
        } catch (IOException ex) {
            throw new IllegalStateException(format("Error saving %s/%s to disk", parentDir, relativeConfigFilePath), ex);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save; deleting {} now.", tempFile.getAbsolutePath());
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }
        }
    }

    private static File absoluteConfigFile(String parentDir, String relativeConfigFilePath) {
        return new File(parentDir, relativeConfigFilePath);
    }
}
