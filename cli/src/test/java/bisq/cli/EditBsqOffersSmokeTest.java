package bisq.cli;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.Random;

import static java.lang.System.out;

/**
 Smoke tests for the editoffer method.

 Prerequisites:

 - Run `./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdesktop  --shutdownAfterTests=false --enableBisqDebugging=false`

 - Create some v1 protocol BSQ offers with Alice's UI or CLI.

 - Watch Alice's offers being edited in Bob's UI.

 Never run on mainnet.
 */
public class EditBsqOffersSmokeTest extends AbstractCliTest {

    public static void main(String[] args) {
        EditBsqOffersSmokeTest test = new EditBsqOffersSmokeTest();

        var myBsqOffers = test.getMyAltcoinOffers("bsq");
        test.doOfferPriceEdits(myBsqOffers);
        test.disableOffers(myBsqOffers);
    }

    private void doOfferPriceEdits(List<OfferInfo> offers) {
        out.println("Edit BSQ offers' fixed price");
        for (int i = 0; i < offers.size(); i++) {
            String randomFixedPrice = randomFixedAltcoinPrice.apply(0.000035, 0.00006);
            editOfferFixedPrice(offers.get(i), randomFixedPrice, new Random().nextBoolean());
            sleep(5);
        }
    }
}
