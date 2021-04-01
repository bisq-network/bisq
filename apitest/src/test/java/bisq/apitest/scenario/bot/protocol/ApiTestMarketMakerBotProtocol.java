package bisq.apitest.scenario.bot.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.io.File;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static bisq.apitest.botsupport.protocol.ProtocolStep.DONE;
import static bisq.apitest.botsupport.protocol.ProtocolStep.WAIT_FOR_OFFER_TAKER;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.CurrencyFormat.formatPrice;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.core.offer.OfferPayload.Direction.BUY;
import static bisq.core.offer.OfferPayload.Direction.SELL;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.protocol.MakerBotProtocol;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.method.BitcoinCliHelper;
import bisq.cli.TradeFormat;

// TODO Create a MarketMakerBotProtocol in CLI, stripped of all the test harness references.
// This not for the CLI, regtest/apitest only.

public class ApiTestMarketMakerBotProtocol extends ApiTestBotProtocol implements MakerBotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiTestMarketMakerBotProtocol.class);

    private static final int MAX_CREATE_OFFER_FAILURES = 3;

    static final double PRICE_MARGIN = 6.50; // Target spread is 13%.

    private final String direction;
    private final AtomicLong bobsBankBalance;

    public ApiTestMarketMakerBotProtocol(BotClient botClient,
                                         PaymentAccount paymentAccount,
                                         long protocolStepTimeLimitInMs,
                                         BitcoinCliHelper bitcoinCli,
                                         BashScriptGenerator bashScriptGenerator,
                                         String direction,
                                         AtomicLong bobsBankBalance) {
        super("Maker",
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bitcoinCli,
                bashScriptGenerator);
        this.direction = direction;
        this.bobsBankBalance = bobsBankBalance;
    }

    @Override
    public void run() {
        checkIsStartStep();

        var isBuy = direction.equalsIgnoreCase(BUY.name());

        Function<Supplier<OfferInfo>, TradeInfo> makeTrade = waitForNewTrade.andThen(waitForTakerFeeTxConfirm);
        var trade = isBuy
                ? makeTrade.apply(createBuyOffer)
                : makeTrade.apply(createSellOffer);

        var makerIsBuyer = trade.getOffer().getDirection().equalsIgnoreCase(BUY.name());
        Function<TradeInfo, TradeInfo> completeFiatTransaction = makerIsBuyer
                ? sendPaymentStartedMessage.andThen(waitForPaymentReceivedConfirmation)
                : waitForPaymentStartedMessage.andThen(sendPaymentReceivedMessage);
        completeFiatTransaction.apply(trade);

        Function<TradeInfo, TradeInfo> closeTrade = waitForPayoutTx.andThen(keepFundsFromTrade);
        closeTrade.apply(trade);

        long bankBalanceDelta = isBuy
                ? -1 * toDollars(trade.getOffer().getVolume())
                : toDollars(trade.getOffer().getVolume());
        bobsBankBalance.addAndGet(bankBalanceDelta);

        currentProtocolStep = DONE;
    }

    private final Supplier<OfferInfo> createBuyOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random BUY offer.");
        for (int i = 0; i < MAX_CREATE_OFFER_FAILURES; i++) {
            try {
                var offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                        BUY.name(),
                        currencyCode,
                        2_500_000,
                        2_500_000,
                        PRICE_MARGIN,
                        0.15,
                        BSQ);
                log.info("Created BUY / {} offer at {}% below current market price of {}:\n{}",
                        currencyCode,
                        PRICE_MARGIN,
                        botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                        formatOfferTable(singletonList(offer), currencyCode));
                log.warn(">>>>> NEW BUY  OFFER {} PRICE: {} =~ {}",
                        offer.getId(),
                        offer.getPrice(),
                        formatPrice(offer.getPrice()));
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create offer at attempt #{}.", i, ex);
                try {
                    SECONDS.sleep(5);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new IllegalStateException(format("%s could not create offer after 3 attempts.", botDescription));
    };

    private final Supplier<OfferInfo> createSellOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random SELL offer.");
        for (int i = 0; i < MAX_CREATE_OFFER_FAILURES; i++) {
            try {
                var offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                        SELL.name(),
                        currencyCode,
                        2_500_000,
                        2_500_000,
                        PRICE_MARGIN,
                        0.15,
                        BSQ);
                log.info("Created SELL / {} offer at {}% above current market price of {}:\n{}",
                        currencyCode,
                        PRICE_MARGIN,
                        botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                        formatOfferTable(singletonList(offer), currencyCode));
                log.warn(">>>>> NEW SELL OFFER {} PRICE: {} =~ {}",
                        offer.getId(),
                        offer.getPrice(),
                        formatPrice(offer.getPrice()));
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create offer at attempt #{}.", i, ex);
                try {
                    SECONDS.sleep(5);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
        throw new IllegalStateException(format("%s could not create offer after 3 attempts.", botDescription));
    };

    private final Function<Supplier<OfferInfo>, TradeInfo> waitForNewTrade = (latestOffer) -> {
        initProtocolStep.accept(WAIT_FOR_OFFER_TAKER);
        OfferInfo offer = latestOffer.get();
        createTakeOfferCliScript(offer);
        log.info("Waiting for offer {} to be taken.", offer.getId());
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted while waiting for offer to be taken.");
            try {
                var trade = getNewTrade(offer.getId());
                if (trade.isPresent()) {
                    return trade.get();
                } else {
                    if (++numDelays % 5 == 0) {
                        log.warn("Offer {} still waiting to be taken, current state = {}",
                                offer.getId(), offer.getState());
                        String offerCounterCurrencyCode = offer.getCounterCurrencyCode();
                        List<OfferInfo> myCurrentOffers = botClient.getMyOffersSortedByDate(offerCounterCurrencyCode);
                        if (myCurrentOffers.isEmpty()) {
                            log.warn("{} has no current offers at this time, but offer {} should exist.",
                                    botDescription,
                                    offer.getId());
                        } else {
                            log.info("{}'s current offers {} is in the list, or fail):\n{}",
                                    botDescription,
                                    offer.getId(),
                                    formatOfferTable(myCurrentOffers, offerCounterCurrencyCode));
                        }
                    }
                    sleep(shortRandomDelayInSeconds.get());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(botClient.toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while

        // If the while loop is exhausted, the offer was not taken within the protocol step time limit.
        throw new IllegalStateException("Offer was never taken; we won't wait any longer.");
    };

    private Optional<TradeInfo> getNewTrade(String offerId) {
        try {
            var trade = botClient.getTrade(offerId);
            log.info("Offer {} was taken, new trade:\n{}", offerId, TradeFormat.format(trade));
            return Optional.of(trade);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void createTakeOfferCliScript(OfferInfo offer) {
        String scriptFilename = "takeoffer-" + offer.getId() + ".sh";
        File script = bashScriptGenerator.createTakeOfferScript(offer, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can take the offer");
    }
}
