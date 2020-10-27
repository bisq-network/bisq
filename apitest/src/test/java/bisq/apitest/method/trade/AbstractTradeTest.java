package bisq.apitest.method.trade;

import bisq.core.trade.Trade;

import bisq.proto.grpc.TradeInfo;

import org.slf4j.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import static bisq.cli.TradeFormat.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



import bisq.apitest.method.offer.AbstractOfferTest;

public class AbstractTradeTest extends AbstractOfferTest {

    // A test fixture encapsulating expected trade protocol status.
    // ExpectedProtocolStatus.init should be called before any @Test begins.
    protected static final ExpectedProtocolStatus EXPECTED_PROTOCOL_STATUS = new ExpectedProtocolStatus();

    // A Trade ID cache for use in @Test sequences.
    protected static String tradeId;

    @BeforeAll
    public static void clearExpectedPaymentStatusFlags() {
        EXPECTED_PROTOCOL_STATUS.init();
    }

    @BeforeEach
    public void initDummyPaymentAccounts() {
        super.initAlicesDummyPaymentAccount();
        super.initBobsDummyPaymentAccount();
    }

    protected final TradeInfo takeAlicesOffer(String offerId, String paymentAccountId) {
        return bobStubs.tradesService.takeOffer(createTakeOfferRequest(offerId, paymentAccountId)).getTrade();
    }

    @SuppressWarnings("unused")
    protected final TradeInfo takeBobsOffer(String offerId, String paymentAccountId) {
        return aliceStubs.tradesService.takeOffer(createTakeOfferRequest(offerId, paymentAccountId)).getTrade();
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
        log.info(String.format("%s %s%n%s",
                testName(testInfo),
                description.toUpperCase(),
                format(trade)));
    }

    @SuppressWarnings("UnusedReturnValue")
    static class ExpectedProtocolStatus {
        Trade.State state;
        Trade.Phase phase;
        boolean isDepositPublished;
        boolean isDepositConfirmed;
        boolean isFiatSent;
        boolean isFiatReceived;
        boolean isPayoutPublished;
        boolean isWithdrawn;

        ExpectedProtocolStatus setState(Trade.State state) {
            this.state = state;
            return this;
        }

        ExpectedProtocolStatus setPhase(Trade.Phase phase) {
            this.phase = phase;
            return this;
        }

        ExpectedProtocolStatus setDepositPublished(boolean depositPublished) {
            isDepositPublished = depositPublished;
            return this;
        }

        ExpectedProtocolStatus setDepositConfirmed(boolean depositConfirmed) {
            isDepositConfirmed = depositConfirmed;
            return this;
        }

        ExpectedProtocolStatus setFiatSent(boolean fiatSent) {
            isFiatSent = fiatSent;
            return this;
        }

        ExpectedProtocolStatus setFiatReceived(boolean fiatReceived) {
            isFiatReceived = fiatReceived;
            return this;
        }

        ExpectedProtocolStatus setPayoutPublished(boolean payoutPublished) {
            isPayoutPublished = payoutPublished;
            return this;
        }

        ExpectedProtocolStatus setWithdrawn(boolean withdrawn) {
            isWithdrawn = withdrawn;
            return this;
        }

        @SuppressWarnings("unused")
        void init() {
            state = null;
            phase = null;
            isDepositPublished = false;
            isDepositConfirmed = false;
            isFiatSent = false;
            isFiatReceived = false;
            isPayoutPublished = false;
            isWithdrawn = false;
        }
    }
}
