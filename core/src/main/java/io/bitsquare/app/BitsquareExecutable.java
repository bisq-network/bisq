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
import io.bitsquare.common.CommonOptionKeys;
import io.bitsquare.network.NetworkOptionKeys;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.util.joptsimple.EnumValueConverter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.bitsquare.app.BitsquareEnvironment.*;
import static java.lang.String.format;
import static java.lang.String.join;

public abstract class BitsquareExecutable {
    private static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;
    private static final String HELP_KEY = "help";

    public void execute(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
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
            System.out.println("error: " + ex.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(EXIT_FAILURE);
            return;
        }

        this.doExecute(options);
    }

    protected void customizeOptionParsing(OptionParser parser) {
        parser.accepts(USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();
        parser.accepts(APP_DATA_DIR_KEY, description("Application data directory", DEFAULT_APP_DATA_DIR))
                .withRequiredArg();
        parser.accepts(CommonOptionKeys.LOG_LEVEL_KEY, description("Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]", LOG_LEVEL_DEFAULT))
                .withRequiredArg();

        parser.accepts(NetworkOptionKeys.SEED_NODES_KEY, description("Override hard coded seed nodes as comma separated list: E.g. rxdkppp3vicnbgqt.onion:8002, mfla72c4igh5ta2t.onion:8002", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.MY_ADDRESS, description("My own onion address (used for botstrap nodes to exclude itself)", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.BAN_LIST, description("Nodes to exclude from network connections.", ""))
                .withRequiredArg();

        parser.accepts(CommonOptionKeys.IGNORE_DEV_MSG_KEY, description("If set to true all signed messages from Bitsquare developers are ignored " +
                "(Global alert, Version update alert, Filters for offers, nodes or payment account data)", false))
                .withRequiredArg()
                .ofType(boolean.class);

        // use a fixed port as arbitrator use that for his ID
        parser.accepts(NetworkOptionKeys.PORT_KEY, description("Port to listen on", 9999))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.USE_LOCALHOST, description("Use localhost network for development", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(NetworkOptionKeys.MAX_CONNECTIONS, description("Max. connections a peer will try to keep", P2PService.MAX_CONNECTIONS_DEFAULT))
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

    protected static String description(String descText, Object defaultValue) {
        String description = "";
        if (StringUtils.hasText(descText))
            description = description.concat(descText);
        if (defaultValue != null)
            description = join(" ", description, format("(default: %s)", defaultValue));
        return description;
    }

    protected abstract void doExecute(OptionSet options);


    public static void initAppDir(String appDir) {
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
}
