package bisq.apitest.method.trade;

import bisq.proto.grpc.TradeInfo;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.cli.CurrencyFormat.formatBsqAmount;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TradeFormat.format;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG;
import static bisq.core.trade.Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN;
import static bisq.core.trade.Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG;
import static org.junit.jupiter.api.Assertions.*;



import bisq.apitest.method.offer.AbstractOfferTest;
import bisq.cli.GrpcClient;

public class AbstractTradeTest extends AbstractOfferTest {

    public static final ExpectedProtocolStatus EXPECTED_PROTOCOL_STATUS = new ExpectedProtocolStatus();

    // A Trade ID cache for use in @Test sequences.
    protected static String tradeId;

    protected final Supplier<Integer> maxTradeStateAndPhaseChecks = () -> isLongRunningTest ? 10 : 2;

    private final Function<GrpcClient, String> toUserName = (client) -> client.equals(aliceClient) ? "Alice" : "Bob";

    @BeforeAll
    public static void initStaticFixtures() {
        EXPECTED_PROTOCOL_STATUS.init();
    }

    protected final TradeInfo takeAlicesOffer(String offerId,
                                              String paymentAccountId,
                                              String takerFeeCurrencyCode) {
        return takeAlicesOffer(offerId,
                paymentAccountId,
                takerFeeCurrencyCode,
                true);
    }

    protected final TradeInfo takeAlicesOffer(String offerId,
                                              String paymentAccountId,
                                              String takerFeeCurrencyCode,
                                              boolean generateBtcBlock) {
        @SuppressWarnings("ConstantConditions")
        var trade = bobClient.takeOffer(offerId,
                paymentAccountId,
                takerFeeCurrencyCode);
        assertNotNull(trade);
        assertEquals(offerId, trade.getTradeId());

        if (takerFeeCurrencyCode.equals(BTC))
            assertTrue(trade.getIsCurrencyForTakerFeeBtc());
        else
            assertFalse(trade.getIsCurrencyForTakerFeeBtc());

        // Cache the trade id for the other tests.
        tradeId = trade.getTradeId();

        if (generateBtcBlock)
            genBtcBlocksThenWait(1, 6_000);

        return trade;
    }


    protected final void waitForDepositConfirmation(Logger log,
                                                    TestInfo testInfo,
                                                    GrpcClient grpcClient,
                                                    String tradeId) {
        Predicate<TradeInfo> isTradeInDepositConfirmedStateAndPhase = (t) ->
                t.getState().equals(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.name())
                        && t.getPhase().equals(DEPOSIT_CONFIRMED.name());

        String userName = toUserName.apply(grpcClient);
        for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
            TradeInfo trade = grpcClient.getTrade(tradeId);
            if (!isTradeInDepositConfirmedStateAndPhase.test(trade)) {
                log.warn("{} still waiting on trade {} tx {}: DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN, attempt # {}",
                        userName,
                        trade.getShortId(),
                        trade.getDepositTxId(),
                        i);
                genBtcBlocksThenWait(1, 4_000);
            } else {
                EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                        .setPhase(DEPOSIT_CONFIRMED)
                        .setDepositPublished(true)
                        .setDepositConfirmed(true);
                verifyExpectedProtocolStatus(trade);
                logTrade(log,
                        testInfo,
                        userName + "'s view after deposit is confirmed",
                        trade,
                        true);
                break;
            }
        }
    }

    protected final void verifyTakerDepositConfirmed(TradeInfo trade) {
        if (!trade.getIsDepositConfirmed()) {
            fail(String.format("INVALID_PHASE for trade %s in STATE=%s PHASE=%s, deposit tx never confirmed.",
                    trade.getShortId(),
                    trade.getState(),
                    trade.getPhase()));
        }
    }

    protected final void waitForBuyerSeesPaymentInitiatedMessage(Logger log,
                                                                 TestInfo testInfo,
                                                                 GrpcClient grpcClient,
                                                                 String tradeId) {
        String userName = toUserName.apply(grpcClient);
        for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
            TradeInfo trade = grpcClient.getTrade(tradeId);
            if (!trade.getIsFiatSent()) {
                log.warn("{} still waiting for trade {} {}, attempt # {}",
                        userName,
                        trade.getShortId(),
                        BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG,
                        i);
                sleep(5_000);
            } else {
                // Do not check trade.getOffer().getState() here because
                // it might be AVAILABLE, not OFFER_FEE_PAID.
                EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                        .setPhase(FIAT_SENT)
                        .setFiatSent(true);
                verifyExpectedProtocolStatus(trade);
                logTrade(log, testInfo, userName + "'s view after confirming trade payment sent", trade);
                break;
            }
        }
    }

    protected final void waitForSellerSeesPaymentInitiatedMessage(Logger log,
                                                                  TestInfo testInfo,
                                                                  GrpcClient grpcClient,
                                                                  String tradeId) {
        Predicate<TradeInfo> isTradeInPaymentReceiptConfirmedStateAndPhase = (t) ->
                t.getState().equals(SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.name()) &&
                        (t.getPhase().equals(PAYOUT_PUBLISHED.name()) || t.getPhase().equals(FIAT_SENT.name()));
        String userName = toUserName.apply(grpcClient);
        for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
            TradeInfo trade = grpcClient.getTrade(tradeId);
            if (!isTradeInPaymentReceiptConfirmedStateAndPhase.test(trade)) {
                log.warn("INVALID_PHASE for {}'s trade {} in STATE={} PHASE={}, cannot confirm payment received yet.",
                        userName,
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase());
                sleep(10_000);
            } else {
                break;
            }
        }

        TradeInfo trade = grpcClient.getTrade(tradeId);
        if (!isTradeInPaymentReceiptConfirmedStateAndPhase.test(trade)) {
            fail(String.format("INVALID_PHASE for {}'s trade %s in STATE=%s PHASE=%s, cannot confirm payment received.",
                    userName,
                    trade.getShortId(),
                    trade.getState(),
                    trade.getPhase()));
        }
    }

    protected final void verifyExpectedProtocolStatus(TradeInfo trade) {
        assertNotNull(trade);
        assertEquals(EXPECTED_PROTOCOL_STATUS.state.name(), trade.getState());
        assertEquals(EXPECTED_PROTOCOL_STATUS.phase.name(), trade.getPhase());

        if (!isLongRunningTest)
            assertEquals(EXPECTED_PROTOCOL_STATUS.isDepositPublished, trade.getIsDepositPublished());

        assertEquals(EXPECTED_PROTOCOL_STATUS.isDepositConfirmed, trade.getIsDepositConfirmed());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isFiatSent, trade.getIsFiatSent());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isFiatReceived, trade.getIsFiatReceived());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isPayoutPublished, trade.getIsPayoutPublished());
        assertEquals(EXPECTED_PROTOCOL_STATUS.isWithdrawn, trade.getIsWithdrawn());
    }

    protected final void sendBsqPayment(Logger log,
                                        GrpcClient grpcClient,
                                        TradeInfo trade) {
        var contract = trade.getContract();
        String receiverAddress = contract.getIsBuyerMakerAndSellerTaker()
                ? contract.getTakerPaymentAccountPayload().getAddress()
                : contract.getMakerPaymentAccountPayload().getAddress();
        String sendBsqAmount = formatBsqAmount(trade.getOffer().getVolume());
        log.info("Sending {} BSQ to address {}", sendBsqAmount, receiverAddress);
        grpcClient.sendBsq(receiverAddress, sendBsqAmount, "");
    }

    protected final void verifyBsqPaymentHasBeenReceived(Logger log,
                                                         GrpcClient grpcClient,
                                                         TradeInfo trade) {
        var contract = trade.getContract();
        var bsqSats = trade.getOffer().getVolume();
        var receiveAmountAsString = formatBsqAmount(bsqSats);
        var address = contract.getIsBuyerMakerAndSellerTaker()
                ? contract.getTakerPaymentAccountPayload().getAddress()
                : contract.getMakerPaymentAccountPayload().getAddress();
        boolean receivedBsqSatoshis = grpcClient.verifyBsqSentToAddress(address, receiveAmountAsString);
        if (receivedBsqSatoshis)
            log.info("Payment of {} BSQ was received to address {} for trade with id {}.",
                    receiveAmountAsString,
                    address,
                    trade.getTradeId());
        else
            fail(String.format("Payment of %s BSQ was was not sent to address %s for trade with id %s.",
                    receiveAmountAsString,
                    address,
                    trade.getTradeId()));
    }

    protected final void logBalances(Logger log, TestInfo testInfo) {
        var alicesBalances = aliceClient.getBalances();
        log.info("{} Alice's Current Balance:\n{}",
                testName(testInfo),
                formatBalancesTbls(alicesBalances));
        var bobsBalances = bobClient.getBalances();
        log.info("{} Bob's Current Balance:\n{}",
                testName(testInfo),
                formatBalancesTbls(bobsBalances));
    }

    protected final void logTrade(Logger log,
                                  TestInfo testInfo,
                                  String description,
                                  TradeInfo trade) {
        logTrade(log, testInfo, description, trade, false);
    }

    protected final void logTrade(Logger log,
                                  TestInfo testInfo,
                                  String description,
                                  TradeInfo trade,
                                  boolean force) {
        if (force)
            log.info(String.format("%s %s%n%s",
                    testName(testInfo),
                    description.toUpperCase(),
                    format(trade)));
        else if (log.isDebugEnabled()) {
            log.debug(String.format("%s %s%n%s",
                    testName(testInfo),
                    description.toUpperCase(),
                    format(trade)));
        }
    }
}
