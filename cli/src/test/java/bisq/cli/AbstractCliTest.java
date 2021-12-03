package bisq.cli;

import bisq.proto.grpc.OfferInfo;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.math.BigDecimal;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.opts.OptLabel.OPT_HOST;
import static bisq.cli.opts.OptLabel.OPT_PASSWORD;
import static bisq.cli.opts.OptLabel.OPT_PORT;
import static java.lang.System.out;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Arrays.stream;
import static org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Operation.DELETE;
import static org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Operation.INSERT;



import bisq.cli.opts.ArgumentList;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

/**
 * Parent class for CLI smoke tests.  Useful for examining the format of the console
 * output, and checking for diffs while making changes to console output formatters.
 *
 * Tests that create offers or trades should not be run on mainnet.
 */
@Slf4j
public abstract class AbstractCliTest {

    static final String PASSWORD_OPT = "--password=xyz";      // Both daemons' password.
    static final String ALICE_PORT_OPT = "--port=" + 9998;    // Alice's daemon port.
    static final String BOB_PORT_OPT = "--port=" + 9999;      // Bob's daemon port.
    static final String[] BASE_ALICE_CLIENT_OPTS = new String[]{PASSWORD_OPT, ALICE_PORT_OPT};
    static final String[] BASE_BOB_CLIENT_OPTS = new String[]{PASSWORD_OPT, BOB_PORT_OPT};

    protected final BiFunction<Integer, Integer, List<String>> randomMarginBasedPrices = (min, max) ->
            IntStream.range(min, max).asDoubleStream()
                    .boxed()
                    .map(d -> d / 100)
                    .map(Object::toString)
                    .collect(Collectors.toList());

    protected final BiFunction<Double, Double, String> randomFixedAltcoinPrice = (min, max) -> {
        String random = Double.valueOf(ThreadLocalRandom.current().nextDouble(min, max)).toString();
        BigDecimal bd = new BigDecimal(random).setScale(8, HALF_UP);
        return bd.toPlainString();
    };

    protected final GrpcClient aliceClient;
    protected final GrpcClient bobClient;

    public AbstractCliTest() {
        this.aliceClient = getGrpcClient(BASE_ALICE_CLIENT_OPTS);
        this.bobClient = getGrpcClient(BASE_BOB_CLIENT_OPTS);
    }

    protected GrpcClient getGrpcClient(String[] args) {
        var parser = new OptionParser();
        var hostOpt = parser.accepts(OPT_HOST, "rpc server hostname or ip")
                .withRequiredArg()
                .defaultsTo("localhost");
        var portOpt = parser.accepts(OPT_PORT, "rpc server port")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(9998);
        var passwordOpt = parser.accepts(OPT_PASSWORD, "rpc server password")
                .withRequiredArg();

        OptionSet options = parser.parse(new ArgumentList(args).getCLIArguments());
        var host = options.valueOf(hostOpt);
        var port = options.valueOf(portOpt);
        var password = options.valueOf(passwordOpt);
        if (password == null)
            throw new IllegalArgumentException("missing required 'password' option");

        return new GrpcClient(host, port, password);
    }

    protected void checkDiffsIgnoreWhitespace(String oldOutput, String newOutput) {
        Predicate<DiffMatchPatch.Operation> isInsertOrDelete = (operation) ->
                operation.equals(INSERT) || operation.equals(DELETE);
        Predicate<String> isWhitespace = (text) -> text.trim().isEmpty();
        boolean hasNonWhitespaceDiffs = false;
        if (!oldOutput.equals(newOutput)) {
            DiffMatchPatch dmp = new DiffMatchPatch();
            LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(oldOutput, newOutput, true);
            for (DiffMatchPatch.Diff d : diff) {
                if (isInsertOrDelete.test(d.operation) && !isWhitespace.test(d.text)) {
                    hasNonWhitespaceDiffs = true;
                    log.error(">>> DIFF {}", d);
                }
            }
        }

        if (hasNonWhitespaceDiffs)
            log.error("FAIL: There were diffs");
        else
            log.info("PASS:  No diffs");
    }

    protected void printOldTbl(String tbl) {
        log.info("OLD Console OUT:\n{}", tbl);
    }

    protected void printNewTbl(String tbl) {
        log.info("NEW Console OUT:\n{}", tbl);
    }

    protected List<OfferInfo> getMyAltcoinOffers(String currencyCode) {
        String[] args = getMyOffersCommand("buy", currencyCode);
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        args = getMyOffersCommand("sell", currencyCode);
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        return aliceClient.getMyCryptoCurrencyOffersSortedByDate(currencyCode);
    }

    protected String[] getMyOffersCommand(String direction, String currencyCode) {
        return new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "getmyoffers",
                "--direction=" + direction,
                "--currency-code=" + currencyCode
        };
    }

    protected String[] getAvailableOffersCommand(String direction, String currencyCode) {
        return new String[]{
                PASSWORD_OPT,
                BOB_PORT_OPT,
                "getoffers",
                "--direction=" + direction,
                "--currency-code=" + currencyCode
        };
    }


    protected void editOfferPriceMargin(OfferInfo offer, String priceMargin, boolean enable) {
        String[] args = new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "editoffer",
                "--offer-id=" + offer.getId(),
                "--market-price-margin=" + priceMargin,
                "--enable=" + enable
        };
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    protected void editOfferTriggerPrice(OfferInfo offer, String triggerPrice, boolean enable) {
        String[] args = new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "editoffer",
                "--offer-id=" + offer.getId(),
                "--trigger-price=" + triggerPrice,
                "--enable=" + enable
        };
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    protected void editOfferPriceMarginAndTriggerPrice(OfferInfo offer,
                                                       String priceMargin,
                                                       String triggerPrice,
                                                       boolean enable) {
        String[] args = new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "editoffer",
                "--offer-id=" + offer.getId(),
                "--market-price-margin=" + priceMargin,
                "--trigger-price=" + triggerPrice,
                "--enable=" + enable
        };
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    protected void editOfferFixedPrice(OfferInfo offer, String fixedPrice, boolean enable) {
        String[] args = new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "editoffer",
                "--offer-id=" + offer.getId(),
                "--fixed-price=" + fixedPrice,
                "--enable=" + enable
        };
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    protected void disableOffers(List<OfferInfo> offers) {
        out.println("Disable Offers");
        for (OfferInfo offer : offers) {
            editOfferEnable(offer, false);
            sleep(5);
        }
    }

    protected void enableOffers(List<OfferInfo> offers) {
        out.println("Enable Offers");
        for (OfferInfo offer : offers) {
            editOfferEnable(offer, true);
            sleep(5);
        }
    }

    protected void editOfferEnable(OfferInfo offer, boolean enable) {
        String[] args = new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "editoffer",
                "--offer-id=" + offer.getId(),
                "--enable=" + enable
        };
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    protected void sleep(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
