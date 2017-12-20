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

package io.bisq.core.app;

import io.bisq.common.CommonOptionKeys;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.RegTestHost;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.exceptions.BisqException;
import io.bisq.core.util.joptsimple.EnumValueConverter;
import io.bisq.network.NetworkOptionKeys;
import io.bisq.network.p2p.P2PService;
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
import static java.lang.String.format;
import static java.lang.String.join;

public abstract class BisqExecutable {
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
        //CommonOptionKeys
        parser.accepts(CommonOptionKeys.LOG_LEVEL_KEY,
                description("Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]", BisqEnvironment.LOG_LEVEL_DEFAULT))
                .withRequiredArg();

        //NetworkOptionKeys
        parser.accepts(NetworkOptionKeys.SEED_NODES_KEY,
                description("Override hard coded seed nodes as comma separated list: E.g. rxdkppp3vicnbgqt.onion:8002, mfla72c4igh5ta2t.onion:8002", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.MY_ADDRESS,
                description("My own onion address (used for botstrap nodes to exclude itself)", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.BAN_LIST,
                description("Nodes to exclude from network connections.", ""))
                .withRequiredArg();
        // use a fixed port as arbitrator use that for his ID
        parser.accepts(NetworkOptionKeys.PORT_KEY,
                description("Port to listen on", 9999))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P,
                description("Use localhost P2P network for development", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(NetworkOptionKeys.MAX_CONNECTIONS,
                description("Max. connections a peer will try to keep", P2PService.MAX_CONNECTIONS_DEFAULT))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS,
                description("A proxy address to be used for Bitcoin network. [host:port]", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS,
                description("A proxy address to be used for Http requests (should be non-Tor). [host:port]", ""))
                .withRequiredArg();

        //AppOptionKeys
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY,
                description("User data directory", BisqEnvironment.DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY,
                description("Application name", BisqEnvironment.DEFAULT_APP_NAME))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.MAX_MEMORY,
                description("Max. permitted memory (used only at headless versions)", 600))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_DATA_DIR_KEY,
                description("Application data directory", BisqEnvironment.DEFAULT_APP_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.IGNORE_DEV_MSG_KEY,
                description("If set to true all signed network_messages from bisq developers are ignored " +
                        "(Global alert, Version update alert, Filters for offers, nodes or trading account data)", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.DUMP_STATISTICS,
                description("If set to true the trade statistics are stored as json file in the data dir.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.PROVIDERS,
                description("Custom providers (comma separated)", false))
                .withRequiredArg();

        //BtcOptionKeys
        parser.accepts(BtcOptionKeys.BASE_CURRENCY_NETWORK,
                description("Base currency network", BisqEnvironment.getDefaultBaseCurrencyNetwork().name()))
                .withRequiredArg()
                .ofType(String.class);
        //.withValuesConvertedBy(new EnumValueConverter(String.class));
        parser.accepts(BtcOptionKeys.REG_TEST_HOST,
                description("", RegTestHost.DEFAULT))
                .withRequiredArg()
                .ofType(RegTestHost.class)
                .withValuesConvertedBy(new EnumValueConverter(RegTestHost.class));
        parser.accepts(BtcOptionKeys.BTC_NODES,
                description("Custom nodes used for BitcoinJ as comma separated IP addresses.", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USE_TOR_FOR_BTC,
                description("If set to true BitcoinJ is routed over tor (socks 5 proxy).", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.SOCKS5_DISCOVER_MODE,
                description("Specify discovery mode for Bitcoin nodes. One or more of: [ADDR, DNS, ONION, ALL]" +
                        " (comma separated, they get OR'd together). Default value is ALL", "ALL"))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USE_ALL_PROVIDED_NODES,
                description("Set to true if connection of bitcoin nodes should include clear net nodes", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USER_AGENT,
                description("User agent at btc node connections", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC,
                description("Number of connections to the Bitcoin network", "9"))
                .withRequiredArg();


        //RpcOptionKeys
        parser.accepts(DaoOptionKeys.RPC_USER,
                description("Bitcoind rpc username", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_PASSWORD,
                description("Bitcoind rpc password", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_PORT,
                description("Bitcoind rpc port", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT,
                description("Bitcoind rpc port for block notifications", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA,
                description("If set to true the blockchain data from RPC requests to Bitcoin Core are stored " +
                        "as json file in the data dir.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(DaoOptionKeys.FULL_DAO_NODE,
                description("If set to true the node requests the blockchain data via RPC requests from Bitcoin Core and " +
                        "provide the validated BSQ txs to the network. It requires that the other RPC properties are " +
                        "set as well.", false))
                .withRequiredArg()
                .ofType(boolean.class);

    }

    public static BisqEnvironment getBisqEnvironment(OptionSet options) {
        return new BisqEnvironment(new JOptCommandLinePropertySource(BisqEnvironment.BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
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
