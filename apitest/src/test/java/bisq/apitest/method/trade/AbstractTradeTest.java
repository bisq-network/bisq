package bisq.apitest.method.trade;

import bisq.proto.grpc.TradeInfo;

import org.slf4j.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;

import static bisq.cli.TradeFormat.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



import bisq.apitest.method.offer.AbstractOfferTest;

public class AbstractTradeTest extends AbstractOfferTest {

    public static final ExpectedProtocolStatus EXPECTED_PROTOCOL_STATUS = new ExpectedProtocolStatus();

    // A Trade ID cache for use in @Test sequences.
    protected static String tradeId;

    @BeforeAll
    public static void initStaticFixtures() {
        EXPECTED_PROTOCOL_STATUS.init();
    }

    protected final TradeInfo takeAlicesOffer(String offerId,
                                              String paymentAccountId,
                                              String takerFeeCurrencyCode) {
        return bobStubs.tradesService.takeOffer(
                createTakeOfferRequest(offerId,
                        paymentAccountId,
                        takerFeeCurrencyCode))
                .getTrade();
    }

    @SuppressWarnings("unused")
    protected final TradeInfo takeBobsOffer(String offerId,
                                            String paymentAccountId,
                                            String takerFeeCurrencyCode) {
        return aliceStubs.tradesService.takeOffer(
                createTakeOfferRequest(offerId,
                        paymentAccountId,
                        takerFeeCurrencyCode))
                .getTrade();
    }

    protected final void verifyExpectedProtocolStatus(TradeInfo trade) {
        assertNotNull(trade);
        assertEquals(EXPECTED_PROTOCOL_STATUS.state.name(), trade.getState());
        assertEquals(EXPECTED_PROTOCOL_STATUS.phase.name(), trade.getPhase());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isDepositPublished, trade.getIsDepositPublished());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isDepositConfirmed, trade.getIsDepositConfirmed());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isFiatSent, trade.getIsFiatSent());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isFiatReceived, trade.getIsFiatReceived());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isPayoutPublished, trade.getIsPayoutPublished());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isWithdrawn, trade.getIsWithdrawn());
    }

    protected final void logTrade(Logger log,
                                  TestInfo testInfo,
                                  String description,
                                  TradeInfo trade) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("%s %s%n%s",
                    testName(testInfo),
                    description.toUpperCase(),
                    format(trade)));
        }
    }
}
