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
        CreateOfferSmokeTest test = new CreateOfferSmokeTest();
        test.createBsqSwapOffer("buy");
        test.createBsqSwapOffer("sell");
    }

    private void createBsqSwapOffer(String direction) {
        String[] args = createBsqSwapOfferCommand(direction, "0.01", "0.005", "0.00005");
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        args = getMyOffersCommand(direction, "bsq");
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");

        args = getAvailableOffersCommand(direction, "bsq");
        out.print(">>>>> bisq-cli ");
        stream(args).forEach(a -> out.print(a + " "));
        out.println();
        CliMain.main(args);
        out.println("<<<<<");
    }

    private String[] createBsqSwapOfferCommand(String direction,
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
}
