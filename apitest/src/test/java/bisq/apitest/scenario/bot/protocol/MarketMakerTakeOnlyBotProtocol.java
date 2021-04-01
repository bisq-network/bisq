package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static bisq.apitest.botsupport.protocol.ProtocolStep.DONE;
import static bisq.apitest.botsupport.protocol.ProtocolStep.FIND_OFFER;
import static bisq.apitest.botsupport.protocol.ProtocolStep.TAKE_OFFER;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.core.offer.OfferPayload.Direction.BUY;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.protocol.TakeOfferHelper;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.method.BitcoinCliHelper;

public class MarketMakerTakeOnlyBotProtocol extends ApiTestBotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketMakerTakeOnlyBotProtocol.class);

    private final AtomicLong takersBankBalance;

    public MarketMakerTakeOnlyBotProtocol(BotClient botClient,
                                          PaymentAccount paymentAccount,
                                          long protocolStepTimeLimitInMs,
                                          BitcoinCliHelper bitcoinCli,
                                          BashScriptGenerator bashScriptGenerator,
                                          AtomicLong takersBankBalance) {
        super("Taker",
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bitcoinCli,
                bashScriptGenerator);
        this.takersBankBalance = takersBankBalance;
    }

    @Override
    public void run() {
        checkIsStartStep();

        Function<OfferInfo, TradeInfo> takeTrade = takeOffer.andThen(waitForTakerFeeTxConfirm);
        var trade = takeTrade.apply(findOffer.get());

        var takerIsSeller = trade.getOffer().getDirection().equalsIgnoreCase(BUY.name());
        Function<TradeInfo, TradeInfo> completeFiatTransaction = takerIsSeller
                ? waitForPaymentStartedMessage.andThen(sendPaymentReceivedMessage)
                : sendPaymentStartedMessage.andThen(waitForPaymentReceivedConfirmation);
        completeFiatTransaction.apply(trade);

        Function<TradeInfo, TradeInfo> closeTrade = waitForPayoutTx.andThen(keepFundsFromTrade);
        closeTrade.apply(trade);

        var iAmSeller = trade.getOffer().getDirection().equalsIgnoreCase(BUY.name());
        long bankBalanceDelta = iAmSeller
                ? toDollars(trade.getOffer().getVolume())
                : -1 * toDollars(trade.getOffer().getVolume());
        takersBankBalance.addAndGet(bankBalanceDelta);

        currentProtocolStep = DONE;
    }

    private final Supplier<Optional<OfferInfo>> firstOffer = () -> {
        var offers = botClient.getOffersSortedByDate(currencyCode);
        if (offers.size() > 0) {
            log.info("Offers found:\n{}", formatOfferTable(offers, currencyCode));
            OfferInfo offer = offers.get(0);
            log.info("Will take first offer {}", offer.getId());
            return Optional.of(offer);
        } else {
            log.info("No buy or sell {} offers found.", currencyCode);
            return Optional.empty();
        }
    };

    private final Supplier<OfferInfo> findOffer = () -> {
        initProtocolStep.accept(FIND_OFFER);
        log.info("Looking for a {} offer.", currencyCode);
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted while checking offers.");
            try {
                Optional<OfferInfo> offer = firstOffer.get();
                if (offer.isPresent()) {
                    return offer.get();
                } else {
                    if (++numDelays % 5 == 0) {
                        List<OfferInfo> currentOffers = botClient.getOffersSortedByDate(currencyCode);
                        if (currentOffers.isEmpty()) {
                            log.info("Still no available {} offers for {}.", currencyCode, botDescription);
                        } else {
                            log.warn("{} should be taking one of these available {} offers:\n{}",
                                    botDescription,
                                    currencyCode,
                                    formatOfferTable(currentOffers, currencyCode));
                        }
                    }
                    sleep(shortRandomDelayInSeconds.get());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while

        // If the while loop is exhausted, the offer was not created within the protocol step time limit.
        throw new IllegalStateException("Offer was never created; we won't wait any longer.");
    };


    private final Function<OfferInfo, TradeInfo> takeOffer = (offer) -> {
        initProtocolStep.accept(TAKE_OFFER);
        checkIfShutdownCalled("Interrupted before taking offer.");
        String feeCurrency = RANDOM.nextBoolean() ? "BSQ" : "BTC";
        TakeOfferHelper takeOfferHelper = new TakeOfferHelper(botClient,
                botDescription,
                offer,
                paymentAccount,
                feeCurrency,
                60,
                60,
                30);

        takeOfferHelper.run();

        if (takeOfferHelper.hasNewTrade.get()) {
            try {
                log.info("{} waiting 5s for trade prep before allowing any gettrade calls.", botDescription);
                SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
                // empty
            }
            return takeOfferHelper.getNewTrade();
        } else if (takeOfferHelper.hasTakeOfferError.get()) {
            throw new IllegalStateException(format("%s's takeoffer %s attempt failed.",
                    botDescription,
                    offer.getId()),
                    takeOfferHelper.getFatalThrowable());
        } else {
            throw new IllegalStateException(format("%s's takeoffer %s attempt failed for unknown reason.",
                    botDescription,
                    offer.getId()));
        }
    };
}


