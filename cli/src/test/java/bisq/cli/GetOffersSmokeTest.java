package bisq.cli;

import static java.lang.System.out;

/**
 Smoke tests for getoffers method.  Useful for examining the format of the console output.

 Prerequisites:

 - Run `./bisq-daemon --apiPassword=xyz --appDataDir=$TESTDIR`

 This can be run on mainnet.
 */
public class GetOffersSmokeTest {

    public static void main(String[] args) {

        out.println(">>> getoffers buy usd");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "buy", "usd"});
        out.println(">>> getoffers sell usd");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "sell", "usd"});

        out.println(">>> getoffers buy eur");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "buy", "eur"});
        out.println(">>> getoffers sell eur");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "sell", "eur"});

        out.println(">>> getoffers buy gbp");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "buy", "gbp"});
        out.println(">>> getoffers sell gbp");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "sell", "gbp"});

        out.println(">>> getoffers buy brl");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "buy", "brl"});
        out.println(">>> getoffers sell brl");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "sell", "brl"});
    }

}
