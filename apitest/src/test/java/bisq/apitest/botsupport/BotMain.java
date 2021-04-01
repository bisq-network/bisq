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

package bisq.apitest.botsupport;

import io.grpc.StatusRuntimeException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.PrintStream;

import java.math.BigDecimal;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.opts.OptLabel.OPT_HELP;
import static bisq.cli.opts.OptLabel.OPT_HOST;
import static bisq.cli.opts.OptLabel.OPT_PASSWORD;
import static bisq.cli.opts.OptLabel.OPT_PORT;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;



import bisq.apitest.botsupport.example.BsqMarketMakerBot;
import bisq.apitest.botsupport.example.CancelOffersBot;
import bisq.apitest.botsupport.opts.BsqMarketMakerBotOptionParser;
import bisq.cli.opts.ArgumentList;

// TODO Define BotMain in new gradle :bot subproject's generated bisq-bot script.

@Slf4j
public class BotMain {

    // Quick testing
    // --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13 --trade-cycle-limit=1 --new-payment-accts-limit=10
    // Longer test sessions
    // --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13 --trade-cycle-limit=20 --new-payment-accts-limit=20

    enum BotType {
        bsqmarketmaker,
        canceloffers
    }

    public static void main(String[] args) {
        try {
            run(args);
            exit(0);
        } catch (Throwable t) {
            err.println("Error: " + t.getMessage());
            exit(1);
        }
    }

    public static void run(String[] args) {
        var parser = new OptionParser();

        var helpOpt = parser.accepts(OPT_HELP, "Print this help text")
                .forHelp();

        var hostOpt = parser.accepts(OPT_HOST, "rpc server hostname or ip")
                .withRequiredArg()
                .defaultsTo("localhost");

        var portOpt = parser.accepts(OPT_PORT, "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);

        var passwordOpt = parser.accepts(OPT_PASSWORD, "rpc server password")
                .withRequiredArg();

        // Parse the CLI opts host, port, password, method name, and help.  The help opt
        // may indicate the user is asking for method level help, and will be excluded
        // from the parsed options if a method opt is present in String[] args.
        OptionSet options = parser.parse(new ArgumentList(args).getCLIArguments());
        @SuppressWarnings("unchecked")
        var nonOptionArgs = (List<String>) options.nonOptionArguments();

        // If neither the help opt nor a method name is present, print CLI level help
        // to stderr and throw an exception.
        if (!options.has(helpOpt) && nonOptionArgs.isEmpty()) {
            printHelp(parser, err);
            throw new IllegalArgumentException("no bot type specified");
        }

        // If the help opt is present, but not a method name, print CLI level help
        // to stdout.
        if (options.has(helpOpt) && nonOptionArgs.isEmpty()) {
            printHelp(parser, out);
            return;
        }

        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        var botOpt = nonOptionArgs.get(0);
        BotType botType;
        try {
            botType = getBotNameFromCmd(botOpt);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(format("'%s' does not exist", botOpt));
        }

        try {
            switch (botType) {
                case bsqmarketmaker: {
                    var opts = new BsqMarketMakerBotOptionParser(args).parse();
                    if (opts.isForHelp()) {
                        printHelp(parser, out);
                        return;
                    }
                    var targetBtcAmount = opts.getTargetBtcAmount();
                    var targetPrice = opts.getTargetPrice();
                    var targetSpread = opts.getTargetSpread();
                    var tradeCycleLimit = opts.getTradeCycleLimit(); // todo fix name
                    var newBsqPaymentAccountsLimit = opts.getNewBsqPaymentAccountsLimit();
                    var bsqMarketMakerBot = new BsqMarketMakerBot(host,
                            port,
                            password,
                            newBsqPaymentAccountsLimit,
                            new BigDecimal(targetPrice),
                            new BigDecimal(targetBtcAmount),
                            new BigDecimal(targetSpread),
                            tradeCycleLimit);
                    log.info("Starting {}.", bsqMarketMakerBot.getClass().getSimpleName());
                    bsqMarketMakerBot.run();
                    log.info("{} shutdown complete.", bsqMarketMakerBot.getClass().getSimpleName());
                    return;
                }
                case canceloffers: {
                    var cancelOffersBot = new CancelOffersBot(host, port, password);
                    cancelOffersBot.cancelAllBsqOffers();
                    return;
                }
                default: {
                    throw new RuntimeException(format("unhandled bot type '%s'", botType));
                }
            }
        } catch (StatusRuntimeException ex) {
            // Remove the leading gRPC status code (e.g. "UNKNOWN: ") from the message
            String message = ex.getMessage().replaceFirst("^[A-Z_]+: ", "");
            if (message.equals("io exception"))
                throw new RuntimeException(message + ", server may not be running", ex);
            else
                throw new RuntimeException(message, ex);
        }
    }

    private static void printHelp(OptionParser parser, @SuppressWarnings("SameParameterValue") PrintStream stream) {
        try {
            stream.println("TODO");
            stream.println();
            parser.printHelpOn(stream);
            stream.println();
        } catch (IOException ex) {
            ex.printStackTrace(stream);
        }
    }

    private static BotType getBotNameFromCmd(String botName) {
        return BotType.valueOf(botName.toLowerCase());
    }
}
