package bisq.apitest.scenario;


import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;



import bisq.apitest.method.offer.CancelOfferTest;
import bisq.apitest.method.offer.CreateOfferUsingFixedPriceTest;
import bisq.apitest.method.offer.CreateOfferUsingMarketPriceMarginTest;
import bisq.apitest.method.offer.ValidateCreateOfferTest;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OfferTest extends ScenarioTest {

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                true,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon);
    }

    @Test
    @Order(1)
    public void testAmtTooLargeShouldThrowException() {
        ValidateCreateOfferTest test = new ValidateCreateOfferTest();
        test.initDummyPaymentAccount();
        test.testAmtTooLargeShouldThrowException();
    }

    @Test
    @Order(2)
    public void testCancelOffer() {
        CancelOfferTest test = new CancelOfferTest();
        test.initDummyPaymentAccount();
        test.testCancelOffer();
    }

    @Test
    @Order(3)
    public void testCreateOfferUsingFixedPrice() {
        CreateOfferUsingFixedPriceTest test = new CreateOfferUsingFixedPriceTest();
        test.initDummyPaymentAccount();
        test.testCreateAUDBTCBuyOfferUsingFixedPrice16000();
        test.testCreateUSDBTCBuyOfferUsingFixedPrice100001234();
        test.testCreateEURBTCSellOfferUsingFixedPrice95001234();
    }

    @Test
    @Order(4)
    public void testCreateOfferUsingMarketPriceMargin() {
        CreateOfferUsingMarketPriceMarginTest test = new CreateOfferUsingMarketPriceMarginTest();
        test.initDummyPaymentAccount();
        test.testCreateUSDBTCBuyOffer5PctPriceMargin();
        test.testCreateNZDBTCBuyOfferMinus2PctPriceMargin();
        test.testCreateGBPBTCSellOfferMinus1Point5PctPriceMargin();
        test.testCreateBRLBTCSellOffer6Point55PctPriceMargin();
    }
}
