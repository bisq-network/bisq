package bisq.cli;

import static java.lang.System.out;

/**
 Smoke tests for getoffers method.  Useful for examining the format of the console output.

 Prerequisites:

 - Run `./bisq-daemon --apiPassword=xyz --appDataDir=$TESTDIR`

 This can be run on mainnet.
 */
@SuppressWarnings("unused")
public class GetOffersSmokeTest {

    private static final String PASSWORD_OPT = "--password=xyz";            // Both daemons
    private static final String GETMYOFFERS_PORT_OPT = "--port=" + 9998;    // Alice's daemon
    private static final String GETOFFERS_PORT_OPT = "--port=" + 9999;      // Bob's daemon

    public static void main(String[] args) {
        getMyBtcOffers();
        getBtcOffers();

        /*
        getMyBsqOffers();
        getBsqOffers();

        getMyXmrOffers();
        getXmrOffers();
         */
    }

    private static void getMyBtcOffers() {
        out.println(">>> getmyoffers buy btc");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=buy", "--currency-code=btc"});
        out.println(">>> getmyoffers sell btc");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=sell", "--currency-code=btc"});
    }

    private static void getBtcOffers() {
        out.println(">>> getoffers buy btc");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=btc"});
        out.println(">>> getoffers sell btc");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=btc"});
    }

    private static void getMyBsqOffers() {
        out.println(">>> getmyoffers buy bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=buy", "--currency-code=bsq"});
        out.println(">>> getmyoffers sell bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=sell", "--currency-code=bsq"});
    }

    private static void getBsqOffers() {
        out.println(">>> getoffers buy bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=bsq"});
        out.println(">>> getoffers sell bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=bsq"});
    }

    private static void getMyXmrOffers() {
        out.println(">>> getmyoffers buy xmr");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=buy", "--currency-code=xmr"});
        out.println(">>> getmyoffers sell xmr");
        CliMain.main(new String[]{PASSWORD_OPT, GETMYOFFERS_PORT_OPT, "getmyoffers", "--direction=sell", "--currency-code=xmr"});
    }

    private static void getXmrOffers() {
        out.println(">>> getoffers buy bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=xmr"});
        out.println(">>> getoffers sell bsq");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=xmr"});
    }

    private static void getUSDOffers() {
        out.println(">>> getoffers buy usd");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=usd"});
        out.println(">>> getoffers sell usd");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=usd"});
    }

    private static void getEurOffers() {
        out.println(">>> getoffers buy eur");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=eur"});
        out.println(">>> getoffers sell eur");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=eur"});
    }

    private static void getGbpOffers() {
        out.println(">>> getoffers buy gbp");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=gbp"});
        out.println(">>> getoffers sell gbp");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=gbp"});
    }

    private static void getBrlOffers() {
        out.println(">>> getoffers buy brl");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=buy", "--currency-code=brl"});
        out.println(">>> getoffers sell brl");
        CliMain.main(new String[]{PASSWORD_OPT, GETOFFERS_PORT_OPT, "getoffers", "--direction=sell", "--currency-code=brl"});
    }
}
