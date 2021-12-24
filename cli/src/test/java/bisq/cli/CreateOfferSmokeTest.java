package bisq.cli;

import static java.lang.System.out;
import static java.util.Arrays.stream;

/**
 Smoke tests for createoffer method.  Useful for testing CLI command and examining the
 format of its console output.

 Prerequisites:

 - Run `./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon --shutdownAfterTests=false --enableBisqDebugging=false`

 Note:  Test harness will not automatically generate BTC blocks to confirm transactions.

 Never run on mainnet!
 */
@SuppressWarnings({"CommentedOutCode", "unused"})
public class CreateOfferSmokeTest extends AbstractCliTest {

    public static void main(String[] args) {
        createBsqSwapOffer("buy");
        createBsqSwapOffer("sell");
    }

    private static void createBsqSwapOffer(String direction) {
        String[] args = createBsqSwapOfferCommand(direction, "0.01", "0.005", "0.00005");
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        args = getMyOffersCommand(direction);
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        args = getAvailableOffersCommand(direction);
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    private static String[] createBsqSwapOfferCommand(String direction,
                                                      String amount,
                                                      String minAmount,
                                                      String fixedPrice) {
        return new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "createoffer",
                "--swap=true",
                "--direction=" + direction,
                "--currency-code=bsq",
                "--amount=" + amount,
                "--min-amount=" + minAmount,
                "--fixed-price=" + fixedPrice
        };
    }

    private static String[] getMyOffersCommand(String direction) {
        return new String[]{
                PASSWORD_OPT,
                ALICE_PORT_OPT,
                "getmyoffers",
                "--direction=" + direction,
                "--currency-code=bsq"
        };
    }

    private static String[] getAvailableOffersCommand(String direction) {
        return new String[]{
                PASSWORD_OPT,
                BOB_PORT_OPT,
                "getoffers",
                "--direction=" + direction,
                "--currency-code=bsq"
        };
    }
}
