package bisq.cli;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.Random;

import static java.lang.System.out;
import static protobuf.OfferDirection.BUY;

/**
 Smoke tests for the editoffer method.

 Prerequisites:

 - Run `./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdesktop  --shutdownAfterTests=false --enableBisqDebugging=false`

 - Create some XMR offers with Alice's UI or CLI.

 - Watch Alice's offers being edited in Bob's UI.

 Never run on mainnet.
 */
public class EditXmrOffersSmokeTest extends AbstractCliTest {

    public static void main(String[] args) {
        var test = new EditXmrOffersSmokeTest();

        test.doOfferPriceEdits();

        List<OfferInfo> offers = test.getMyAltcoinOffers("xmr");
        test.disableOffers(offers);

        test.sleep(6);

        offers = test.getMyAltcoinOffers("xmr");
        test.enableOffers(offers);

        // A final look after last edit.
        test.getMyAltcoinOffers("xmr");
    }

    private void doOfferPriceEdits() {
        editPriceMargin();
        editTriggerPrice();
        editPriceMarginAndTriggerPrice();
        editFixedPrice();
    }

    private void editPriceMargin() {
        var offers = getMyAltcoinOffers("xmr");
        out.println("Edit XMR offers' price margin");
        var margins = randomMarginBasedPrices.apply(-301, 300);
        for (int i = 0; i < offers.size(); i++) {
            String randomMargin = margins.get(new Random().nextInt(margins.size()));
            editOfferPriceMargin(offers.get(i), randomMargin, new Random().nextBoolean());
            sleep(5);
        }
    }

    private void editTriggerPrice() {
        var offers = getMyAltcoinOffers("xmr");
        out.println("Edit XMR offers' trigger price");
        for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            if (offer.getUseMarketBasedPrice()) {
                // Trigger price is hardcode to be a bit above or below xmr mkt price at runtime.
                // It could be looked up and calculated instead.
                var newTriggerPrice = offer.getDirection().equals(BUY.name()) ? "0.0039" : "0.005";
                editOfferTriggerPrice(offer, newTriggerPrice, true);
                sleep(5);
            }
        }
    }

    private void editPriceMarginAndTriggerPrice() {
        var offers = getMyAltcoinOffers("xmr");
        out.println("Edit XMR offers' price margin and trigger price");
        for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            if (offer.getUseMarketBasedPrice()) {
                // Trigger price is hardcode to be a bit above or below xmr mkt price at runtime.
                // It could be looked up and calculated instead.
                var newTriggerPrice = offer.getDirection().equals(BUY.name()) ? "0.0038" : "0.0051";
                editOfferPriceMarginAndTriggerPrice(offer, "0.05", newTriggerPrice, true);
                sleep(5);
            }
        }
    }

    private void editFixedPrice() {
        var offers = getMyAltcoinOffers("xmr");
        out.println("Edit XMR offers' fixed price");
        for (int i = 0; i < offers.size(); i++) {
            String randomFixedPrice = randomFixedAltcoinPrice.apply(0.004, 0.0075);
            editOfferFixedPrice(offers.get(i), randomFixedPrice, new Random().nextBoolean());
            sleep(5);
        }
    }
}
