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
import io.bitsquare.btc.RegTestHost;
import io.bitsquare.common.CommonOptionKeys;
import io.bitsquare.messages.btc.BitcoinNetwork;
import io.bitsquare.messages.btc.BtcOptionKeys;
import io.bitsquare.messages.dao.blockchain.RpcOptionKeys;
import io.bitsquare.network.NetworkOptionKeys;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.util.joptsimple.EnumValueConverter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.springframework.core.env.JOptCommandLinePropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkNotNull;
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
        parser.accepts(CommonOptionKeys.LOG_LEVEL_KEY, description("Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]", LOG_LEVEL_DEFAULT))
                .withRequiredArg();

        parser.accepts(NetworkOptionKeys.SEED_NODES_KEY, description("Override hard coded seed nodes as comma separated list: E.g. rxdkppp3vicnbgqt.onion:8002, mfla72c4igh5ta2t.onion:8002", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.MY_ADDRESS, description("My own onion address (used for botstrap nodes to exclude itself)", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.BAN_LIST, description("Nodes to exclude from network connections.", ""))
                .withRequiredArg();
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
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, description("A proxy address to be used for Bitcoin network. [host:port]", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, description("A proxy address to be used for Http requests (should be non-Tor). [host:port]", ""))
                .withRequiredArg();

        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.MAX_MEMORY, description("Max. permitted memory (used only at headless versions)", 600))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_DATA_DIR_KEY, description("Application data directory", DEFAULT_APP_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.IGNORE_DEV_MSG_KEY, description("If set to true all signed messages from Bitsquare developers are ignored " +
                "(Global alert, Version update alert, Filters for offers, nodes or trading account data)", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.DUMP_STATISTICS, description("If set to true the trade statistics are stored as json file in the data dir.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.PROVIDERS, description("Custom providers (comma separated)", false))
                .withRequiredArg();

        parser.accepts(RpcOptionKeys.RPC_USER, description("Bitcoind rpc username", ""))
                .withRequiredArg();
        parser.accepts(RpcOptionKeys.RPC_PASSWORD, description("Bitcoind rpc password", ""))
                .withRequiredArg();
        parser.accepts(RpcOptionKeys.RPC_PORT, description("Bitcoind rpc port", ""))
                .withRequiredArg();
        parser.accepts(RpcOptionKeys.RPC_BLOCK_PORT, description("Bitcoind rpc port for block notifications", ""))
                .withRequiredArg();
        parser.accepts(RpcOptionKeys.RPC_WALLET_PORT, description("Bitcoind rpc port for wallet notifications", ""))
                .withRequiredArg();

        parser.accepts(BtcOptionKeys.BTC_NETWORK, description("Bitcoin network", BitcoinNetwork.DEFAULT.name()))
                .withRequiredArg()
                .ofType(String.class);
                //.withValuesConvertedBy(new EnumValueConverter(String.class));
        parser.accepts(BtcOptionKeys.REG_TEST_HOST, description("", RegTestHost.DEFAULT))
                .withRequiredArg()
                .ofType(RegTestHost.class)
                .withValuesConvertedBy(new EnumValueConverter(RegTestHost.class));
        parser.accepts(AppOptionKeys.BTC_NODES, description("Custom nodes used for BitcoinJ as comma separated IP addresses.", ""))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.USE_TOR_FOR_BTC, description("If set to true BitcoinJ is routed over tor (socks 5 proxy).", ""))
                .withRequiredArg();
    }

    public static BitsquareEnvironment getBitsquareEnvironment(OptionSet options) {
        return new BitsquareEnvironment(new JOptCommandLinePropertySource(BitsquareEnvironment.BITSQUARE_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
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
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new BitsquareException(ex, "Application data directory '%s' could not be created", dir);
        }
    }
}
