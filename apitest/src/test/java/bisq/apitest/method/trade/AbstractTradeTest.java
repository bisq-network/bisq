package bisq.apitest.method.trade;

import bisq.core.trade.Trade;

import bisq.proto.grpc.TradeInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



import bisq.apitest.method.offer.AbstractOfferTest;

public class AbstractTradeTest extends AbstractOfferTest {

    protected final TradeInfo takeAlicesOffer(String offerId, String paymentAccountId) {
        return bobStubs.tradesService.takeOffer(createTakeOfferRequest(offerId, paymentAccountId)).getTrade();
    }

    protected final TradeInfo takeBobsOffer(String offerId, String paymentAccountId) {
        return aliceStubs.tradesService.takeOffer(createTakeOfferRequest(offerId, paymentAccountId)).getTrade();
    }

    protected final void verifyExpectedTradeStateAndPhase(TradeInfo trade,
                                                          Trade.State expectedState,
                                                          Trade.Phase expectedPhase) {
        assertNotNull(trade);
        assertEquals(expectedState.name(), trade.getState());
        assertEquals(expectedPhase.name(), trade.getPhase());
    }

}
