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
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=buy", "--currency-code=usd"});
        out.println(">>> getoffers sell usd");
        CliMain.main(new String[]{"--password=xyz", "getoffers", "--direction=sell", "--currency-code=usd"});

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
