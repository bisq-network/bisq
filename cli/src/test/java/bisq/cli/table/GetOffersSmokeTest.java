package bisq.cli.table;

import bisq.proto.grpc.OfferInfo;

import java.util.List;

import static bisq.cli.table.builder.TableType.OFFER_TBL;
import static java.lang.String.format;
import static java.lang.System.out;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.cli.OfferFormat;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class GetOffersSmokeTest extends AbstractSmokeTest {

    public static void main(String[] args) {
        GetOffersSmokeTest test = new GetOffersSmokeTest();

        test.getMyBuyUsdOffers();
        test.getMySellUsdOffers();
        test.getAvailableBuyUsdOffers();
        test.getAvailableSellUsdOffers();

        test.getMyBuyXmrOffers();
        test.getMySellXmrOffers();
        test.getAvailableBuyXmrOffers();
        test.getAvailableSellXmrOffers();

        test.getMyBuyBsqOffers();
        test.getMySellBsqOffers();
        test.getAvailableBuyBsqOffers();
        test.getAvailableSellBsqOffers();
    }

    public GetOffersSmokeTest() {
        super();
    }

    private void getMyBuyUsdOffers() {
        var myOffers = aliceClient.getMyOffers(BUY.name(), "USD");
        printAndCheckDiffs(myOffers, BUY.name(), "USD");
    }

    private void getMySellUsdOffers() {
        var myOffers = aliceClient.getMyOffers(SELL.name(), "USD");
        printAndCheckDiffs(myOffers, SELL.name(), "USD");
    }

    private void getAvailableBuyUsdOffers() {
        var offers = bobClient.getOffers(BUY.name(), "USD");
        printAndCheckDiffs(offers, BUY.name(), "USD");
    }

    private void getAvailableSellUsdOffers() {
        var offers = bobClient.getOffers(SELL.name(), "USD");
        printAndCheckDiffs(offers, SELL.name(), "USD");
    }

    private void getMyBuyXmrOffers() {
        var myOffers = aliceClient.getMyOffers(BUY.name(), "XMR");
        printAndCheckDiffs(myOffers, BUY.name(), "XMR");
    }

    private void getMySellXmrOffers() {
        var myOffers = aliceClient.getMyOffers(SELL.name(), "XMR");
        printAndCheckDiffs(myOffers, SELL.name(), "XMR");
    }

    private void getAvailableBuyXmrOffers() {
        var offers = bobClient.getOffers(BUY.name(), "XMR");
        printAndCheckDiffs(offers, BUY.name(), "XMR");
    }

    private void getAvailableSellXmrOffers() {
        var offers = bobClient.getOffers(SELL.name(), "XMR");
        printAndCheckDiffs(offers, SELL.name(), "XMR");
    }

    private void getMyBuyBsqOffers() {
        var myOffers = aliceClient.getMyOffers(BUY.name(), "BSQ");
        printAndCheckDiffs(myOffers, BUY.name(), "BSQ");
    }

    private void getMySellBsqOffers() {
        var myOffers = aliceClient.getMyOffers(SELL.name(), "BSQ");
        printAndCheckDiffs(myOffers, SELL.name(), "BSQ");
    }

    private void getAvailableBuyBsqOffers() {
        var offers = bobClient.getOffers(BUY.name(), "BSQ");
        printAndCheckDiffs(offers, BUY.name(), "BSQ");
    }

    private void getAvailableSellBsqOffers() {
        var offers = bobClient.getOffers(SELL.name(), "BSQ");
        printAndCheckDiffs(offers, SELL.name(), "BSQ");
    }

    private void printAndCheckDiffs(List<OfferInfo> offers,
                                    String direction,
                                    String currencyCode) {
        if (offers.isEmpty()) {
            out.println(format("No %s %s offers to print.", direction, currencyCode));
        } else {
            out.println(format("Checking for diffs in %s %s offers.", direction, currencyCode));
            var oldTbl = OfferFormat.formatOfferTable(offers, currencyCode);
            var newTbl = new TableBuilder(OFFER_TBL, offers).build().toString();
            printOldTbl(oldTbl);
            printNewTbl(newTbl);
            out.flush();
            showDiffsIgnoreWhitespace(oldTbl, newTbl);
        }
    }
}
