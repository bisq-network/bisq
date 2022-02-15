package bisq.cli;

import static java.lang.System.out;

/**
 Smoke tests for getoffers method.  Useful for examining the format of the console output.

 Prerequisites:

 - Run `./bisq-daemon --apiPassword=xyz --appDataDir=$TESTDIR`

 This can be run on mainnet.
 */
@SuppressWarnings({"CommentedOutCode", "unused"})
public class GetOffersSmokeTest extends AbstractCliTest {

    // TODO use the static password and port opt definitions in superclass

    public static void main(String[] args) {
        getMyBsqOffers();
        // getAvailableBsqOffers();
        // getMyUsdOffers();
        // getAvailableUsdOffers();
    }

    private static void getMyBsqOffers() {
        out.println(">>> getmyoffers buy bsq");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffers", "--direction=buy", "--currency-code=bsq"});
        out.println(">>> getmyoffers sell bsq");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffers", "--direction=sell", "--currency-code=bsq"});
        out.println(">>> getmyoffer --offer-id=KRONTTMO-11cef1a9-c636-4dc7-b3f2-1616e4960c28-175");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffer", "--offer-id=KRONTTMO-11cef1a9-c636-4dc7-b3f2-1616e4960c28-175"});
    }

    private static void getAvailableBsqOffers() {
        out.println(">>> getoffers buy bsq");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getoffers", "--direction=buy", "--currency-code=bsq"});
        out.println(">>> getoffers sell bsq");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getoffers", "--direction=sell", "--currency-code=bsq"});
    }

    private static void getMyUsdOffers() {
        out.println(">>> getmyoffers buy usd");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffers", "--direction=buy", "--currency-code=usd"});
        out.println(">>> getmyoffers sell usd");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getmyoffers", "--direction=sell", "--currency-code=usd"});
    }

    private static void getAvailableUsdOffers() {
        out.println(">>> getoffers buy usd");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getoffers", "--direction=buy", "--currency-code=usd"});
        out.println(">>> getoffers sell usd");
        CliMain.main(new String[]{"--password=xyz", "--port=9998", "getoffers", "--direction=sell", "--currency-code=usd"});
    }

    private static void TODO() {
        out.println(">>> getoffers buy eur");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=buy", "--currency-code=eur"});
        out.println(">>> getoffers sell eur");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=sell", "--currency-code=eur"});

        out.println(">>> getoffers buy gbp");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=buy", "--currency-code=gbp"});
        out.println(">>> getoffers sell gbp");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=sell", "--currency-code=gbp"});

        out.println(">>> getoffers buy brl");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=buy", "--currency-code=brl"});
        out.println(">>> getoffers sell brl");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=sell", "--currency-code=brl"});
    }
}
