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
import io.bitsquare.btc.RegTestHost;
import io.bitsquare.network.OptionKeys;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.util.joptsimple.EnumValueConverter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.bitsquare.app.BitsquareEnvironment.*;

public class BitsquareAppMain extends BitsquareExecutable {
    private static final Logger log = LoggerFactory.getLogger(BitsquareAppMain.class);

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

        // need to call that before BitsquareAppMain().execute(args)
        initAppDir(bitsquareEnvironment.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY));

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
        parser.accepts(io.bitsquare.common.OptionKeys.LOG_LEVEL_KEY, description("Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]", LOG_LEVEL_DEFAULT))
                .withRequiredArg();

        parser.accepts(OptionKeys.SEED_NODES_KEY, description("Override hard coded seed nodes as comma separated list: E.g. rxdkppp3vicnbgqt.onion:8002, mfla72c4igh5ta2t.onion:8002", ""))
                .withRequiredArg();

        parser.accepts(io.bitsquare.common.OptionKeys.IGNORE_DEV_MSG_KEY, description("If set to true all signed messages from Bitsquare developers are ignored " +
                "(Global alert, Version update alert, Filters for offers, nodes or payment account data)", false))
                .withRequiredArg()
                .ofType(boolean.class);
        
        // use a fixed port as arbitrator use that for his ID
        parser.accepts(OptionKeys.PORT_KEY, description("Port to listen on", 9999))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(OptionKeys.USE_LOCALHOST, description("Use localhost network for development", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(OptionKeys.MAX_CONNECTIONS, description("Max. connections a peer will try to keep", P2PService.MAX_CONNECTIONS_DEFAULT))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(BitcoinNetwork.KEY, description("Bitcoin network", BitcoinNetwork.DEFAULT))
                .withRequiredArg()
                .ofType(BitcoinNetwork.class)
                .withValuesConvertedBy(new EnumValueConverter(BitcoinNetwork.class));

        parser.accepts(RegTestHost.KEY, description("", RegTestHost.DEFAULT))
                .withRequiredArg()
                .ofType(RegTestHost.class)
                .withValuesConvertedBy(new EnumValueConverter(RegTestHost.class));
    }

    @Override
    protected void doExecute(OptionSet options) {
        BitsquareApp.setEnvironment(new BitsquareEnvironment(options));
        javafx.application.Application.launch(BitsquareApp.class);
    }
}
