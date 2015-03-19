/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import io.bitsquare.BitsquareException;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.p2p.BootstrapNodes;
import io.bitsquare.p2p.Node;
import io.bitsquare.util.joptsimple.EnumValueConverter;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vinumeris.updatefx.UpdateFX;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import static io.bitsquare.app.BitsquareEnvironment.*;
import static io.bitsquare.p2p.Node.*;
import static io.bitsquare.p2p.tomp2p.TomP2PModule.*;
import static java.util.Arrays.asList;

public class BitsquareAppMain extends BitsquareExecutable {
    private static final Logger log = LoggerFactory.getLogger(BitsquareAppMain.class);

    private static final String VERSION = "0.1";

    public static String getVersion() {
        return VERSION + "." + UpdateProcess.getBuildVersion();
    }

    public static void main(String[] args) throws Exception {
        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts(USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println("error: " + ex.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(EXIT_FAILURE);
            return;
        }
        BitsquareEnvironment bitsquareEnvironment = new BitsquareEnvironment(options);
        String updatesDirectory = bitsquareEnvironment.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY);

        // app dir need to be setup before UpdateFX bootstrap
        initAppDir(updatesDirectory);

        UpdateFX.bootstrap(BitsquareAppMain.class, new File(updatesDirectory).toPath(), args);
    }

    // That will be called from UpdateFX after updates are checked
    public static void realMain(String[] args) throws Exception {
        log.trace("realMain");
        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
        Thread.currentThread().setContextClassLoader(BitsquareAppMain.class.getClassLoader());

        new BitsquareAppMain().execute(args);
    }

    private static void initAppDir(String appDir) {
        Path dir = Paths.get(appDir);
        if (Files.exists(dir)) {
            if (!Files.isWritable(dir))
                throw new BitsquareException("Application data directory '%s' is not writeable", dir);
            else
                return;
        }
        try {
            Files.createDirectory(dir);
        } catch (IOException ex) {
            throw new BitsquareException(ex, "Application data directory '%s' could not be created", dir);
        }
    }

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        parser.accepts(USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();
        parser.accepts(APP_DATA_DIR_KEY, description("Application data directory", DEFAULT_APP_DATA_DIR))
                .withRequiredArg();
        parser.acceptsAll(asList(APP_DATA_DIR_CLEAN_KEY, "clean"),
                description("Clean application data directory", DEFAULT_APP_DATA_DIR_CLEAN))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(NAME_KEY, description("Name of this node", null))
                .withRequiredArg();
        parser.accepts(PORT_KEY, description("Port to listen on", Node.DEFAULT_PORT))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(USE_MANUAL_PORT_FORWARDING_KEY, description("Use manual port forwarding", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(BitcoinNetwork.KEY, description("", BitcoinNetwork.DEFAULT))
                .withRequiredArg()
                .ofType(BitcoinNetwork.class)
                .withValuesConvertedBy(new EnumValueConverter(BitcoinNetwork.class));
        parser.accepts(BOOTSTRAP_NODE_NAME_KEY, description("", BootstrapNodes.DEFAULT.getName()))
                .withRequiredArg();
        parser.accepts(BOOTSTRAP_NODE_IP_KEY, description("", BootstrapNodes.DEFAULT.getIp()))
                .withRequiredArg();
        parser.accepts(BOOTSTRAP_NODE_PORT_KEY, description("", BootstrapNodes.DEFAULT.getPort()))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NETWORK_INTERFACE_KEY, description("Network interface", null))
                .withRequiredArg();
    }

    @Override
    protected void doExecute(OptionSet options) {
        BitsquareApp.setEnvironment(new BitsquareEnvironment(options));
        javafx.application.Application.launch(BitsquareApp.class);
    }
}
