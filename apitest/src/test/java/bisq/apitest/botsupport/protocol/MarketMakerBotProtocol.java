/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest.botsupport.protocol;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.math.BigDecimal;
import java.math.MathContext;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.Getter;

import static bisq.apitest.botsupport.protocol.ProtocolStep.DONE;
import static bisq.apitest.botsupport.protocol.ProtocolStep.WAIT_FOR_OFFER_TAKER;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static protobuf.OfferPayload.Direction;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.cli.TradeFormat;

public class MarketMakerBotProtocol extends BotProtocol implements MakerBotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketMakerBotProtocol.class);

    private static final BigDecimal MAX_BTC_AMOUNT_DEVIATION_SIZE = new BigDecimal("0.000001");
    private static final BigDecimal MAX_BSQ_PRICE_DEVIATION_SIZE = new BigDecimal("0.0000001");
    private static final BigDecimal PERCENT_MULTIPLICAND = new BigDecimal("0.01");

    protected final String direction;
    @Getter
    protected final BigDecimal priceMargin;
    @Getter
    protected final BigDecimal targetBsqPrice;
    @Getter
    protected final BigDecimal targetBtcAmount;
    @Getter
    protected final double securityDepositAsPercent;
    @Getter
    protected final String tradingFeeCurrencyCode;
    @Getter
    protected final int maxCreateOfferFailureLimit;

    protected final Supplier<BigDecimal> randomPercent = () ->
            new BigDecimal(Double.toString(RANDOM.nextDouble())).round(new MathContext(2, HALF_UP));

    protected final Function<BigDecimal, BigDecimal> calculatedMargin = (targetSpread) ->
            targetSpread.divide(new BigDecimal(2)).round(new MathContext(3, HALF_UP));

    protected final Supplier<Long> nextTradeBtcAmount = () -> {
        BigDecimal nextBtcAmountDeviation = randomPercent.get().multiply(MAX_BTC_AMOUNT_DEVIATION_SIZE);
        BigDecimal nextTradeBtcAmount = RANDOM.nextBoolean()
                ? this.getTargetBtcAmount().subtract(nextBtcAmountDeviation)
                : this.getTargetBtcAmount().add(nextBtcAmountDeviation);
        long amountInSatoshis = nextTradeBtcAmount.scaleByPowerOfTen(8).longValue();
        log.info("Calculated next trade's amount: {} BTC ({} sats) using target amount {} and max deviation {}.",
                nextTradeBtcAmount,
                amountInSatoshis,
                this.getTargetBtcAmount(),
                MAX_BTC_AMOUNT_DEVIATION_SIZE);
        return amountInSatoshis;
    };

    // We use the same random BSQ price in a trade cycle (1 buy and 1 sell).
    // This price queue has a max of 2 prices.  When it's empty, we add two identical
    // prices, then remove & use them until queue is exhausted, rinse and repeat for
    // each trade cycle.
    protected static final Queue<BigDecimal> PSEUDO_FIXED_TRADE_PRICE_QUEUE = new ConcurrentLinkedQueue<>();

    protected final Supplier<BigDecimal> nextPseudoFixedTradeBsqPrice = () -> {
        if (PSEUDO_FIXED_TRADE_PRICE_QUEUE.isEmpty()) {
            BigDecimal nextBsqPriceDeviation = randomPercent.get().multiply(MAX_BSQ_PRICE_DEVIATION_SIZE);
            BigDecimal fixedTradePrice = RANDOM.nextBoolean()
                    ? this.getTargetBsqPrice().add(nextBsqPriceDeviation)
                    : this.getTargetBsqPrice().subtract(nextBsqPriceDeviation);
            log.info("Calculated next trade's pseudo fixed BSQ price: {} BTC using target price {} and max deviation {}.",
                    fixedTradePrice.toPlainString(),
                    this.getTargetBsqPrice(),
                    MAX_BSQ_PRICE_DEVIATION_SIZE.toPlainString());
            PSEUDO_FIXED_TRADE_PRICE_QUEUE.add(fixedTradePrice);
            PSEUDO_FIXED_TRADE_PRICE_QUEUE.add(fixedTradePrice);
        }
        return PSEUDO_FIXED_TRADE_PRICE_QUEUE.remove();
    };

    protected final Function<Direction, BigDecimal> calculateNextTradeMarginBasedPrice = (direction) -> {
        BigDecimal marginAsDecimal = this.getPriceMargin().multiply(PERCENT_MULTIPLICAND);
        BigDecimal basePrice = nextPseudoFixedTradeBsqPrice.get();
        BigDecimal marginDifference = basePrice.multiply(marginAsDecimal);
        BigDecimal nextMarginPrice = direction.equals(BUY)
                ? basePrice.subtract(marginDifference).round(new MathContext(4, HALF_UP))
                : basePrice.add(marginDifference).round(new MathContext(4, HALF_UP));
        log.info("Calculated next {} trade's BSQ margin based price: {} BTC based on pseudo base price {} {} {}% margin.",
                direction.name(),
                nextMarginPrice.toPlainString(),
                basePrice.toPlainString(),
                direction.equals(BUY) ? "-" : "+",
                this.getPriceMargin());
        return nextMarginPrice;
    };

    protected final Function<Double, Double> roundedSecurityDeposit = (securityDepositAsPercent) ->
            new BigDecimal(securityDepositAsPercent).round(new MathContext(3, HALF_UP)).doubleValue();

    public static void main(String[] args) {
        BigDecimal targetBtcAmount = new BigDecimal("0.1");
        BigDecimal targetPrice = new BigDecimal("0.00005");
        BigDecimal targetSpread = new BigDecimal("10.00");

        Supplier<BigDecimal> randomPercent = () ->
                new BigDecimal(Double.toString(RANDOM.nextDouble())).round(new MathContext(2, HALF_UP));
        BigDecimal nextBtcAmountDeviation = randomPercent.get().multiply(MAX_BTC_AMOUNT_DEVIATION_SIZE);
        BigDecimal nextTradeBtcAmount = RANDOM.nextBoolean()
                ? targetBtcAmount.subtract(nextBtcAmountDeviation)
                : targetBtcAmount.add(nextBtcAmountDeviation);
        long amountInSatoshis = nextTradeBtcAmount.scaleByPowerOfTen(8).longValue();
        log.info("Calculated next trade's amount: {} BTC ({} sats) using target amount {} and max deviation {}.",
                nextTradeBtcAmount,
                amountInSatoshis,
                targetBtcAmount,
                MAX_BTC_AMOUNT_DEVIATION_SIZE);

        BigDecimal nextBsqPriceDeviation = randomPercent.get().multiply(MAX_BSQ_PRICE_DEVIATION_SIZE);
        BigDecimal nextTradeBsqPrice = RANDOM.nextBoolean()
                ? targetPrice.add(nextBsqPriceDeviation)
                : targetPrice.subtract(nextBsqPriceDeviation);
        log.info("Calculated next trade's BSQ price: {} BTC using target price {} and max deviation {}.",
                nextTradeBsqPrice.toPlainString(),
                targetPrice,
                MAX_BSQ_PRICE_DEVIATION_SIZE.toPlainString());

        BigDecimal marginAsPercent = targetSpread.divide(new BigDecimal(2), HALF_UP);
        BigDecimal marginAsDecimal = marginAsPercent.multiply(PERCENT_MULTIPLICAND);
        BigDecimal delta = nextTradeBsqPrice.multiply(marginAsDecimal).round(new MathContext(3, HALF_UP));
        BigDecimal nextBuyMarginPrice = nextTradeBsqPrice.subtract(delta);
        BigDecimal nextSellMarginPrice = nextTradeBsqPrice.add(delta);
        log.info("Calculated next BUY  BSQ margin based price: {} BTC based on pseudo target price {} and {}% margin.",
                nextBuyMarginPrice.toPlainString(),
                nextTradeBsqPrice,
                marginAsPercent);
        log.info("Calculated next SELL BSQ margin based price: {} BTC based on pseudo target price {} and {}% margin.",
                nextSellMarginPrice.toPlainString(),
                nextTradeBsqPrice,
                marginAsPercent);
    }


    public MarketMakerBotProtocol(String botDescription,
                                  BotClient botClient,
                                  PaymentAccount paymentAccount,
                                  long protocolStepTimeLimitInMs,
                                  BashScriptGenerator bashScriptGenerator,
                                  String direction,
                                  BigDecimal targetBsqPrice,
                                  BigDecimal targetBtcAmount,
                                  BigDecimal targetSpread,
                                  double securityDepositAsPercent,
                                  String tradingFeeCurrencyCode,
                                  int maxCreateOfferFailureLimit) {
        super(botDescription,
                botClient,
                paymentAccount,
                protocolStepTimeLimitInMs,
                bashScriptGenerator);
        this.direction = direction;
        this.priceMargin = calculatedMargin.apply(targetSpread);
        this.targetBsqPrice = targetBsqPrice;
        this.targetBtcAmount = targetBtcAmount;
        this.securityDepositAsPercent = roundedSecurityDeposit.apply(securityDepositAsPercent);
        this.tradingFeeCurrencyCode = tradingFeeCurrencyCode;
        this.maxCreateOfferFailureLimit = maxCreateOfferFailureLimit;
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

        // TODO track changes in balances and print here.

        currentProtocolStep = DONE;
    }

    protected final Supplier<OfferInfo> createBuyOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random BUY offer.");
        int attempts;
        for (attempts = 0; attempts < this.getMaxCreateOfferFailureLimit(); attempts++) {
            try {
                var amount = nextTradeBtcAmount.get();
                var isBsqOffer = paymentAccount.getSelectedTradeCurrency().getCode().equals(BSQ);
                OfferInfo offer;
                if (isBsqOffer) {
                    var priceAsString = calculateNextTradeMarginBasedPrice.apply(BUY).toPlainString();
                    offer = botClient.createOfferAtFixedPrice(paymentAccount,
                            SELL.name(),  // This is the Buy BSQ (Sell BTC) bot.
                            currencyCode,
                            amount,
                            amount,
                            priceAsString,
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                } else {
                    offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                            BUY.name(),
                            currencyCode,
                            amount,
                            amount,
                            this.getPriceMargin().doubleValue(),
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                    log.info("Created BUY / {} offer at {}% below current market price of {}:\n{}",
                            currencyCode,
                            this.getPriceMargin(),
                            botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                            formatOfferTable(singletonList(offer), currencyCode));
                    log.info("Payment account used to create offer: {}", paymentAccount.getId());
                }
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create BUY offer after attempt #{}.", attempts, ex);
                try {
                    SECONDS.sleep(30);
                } catch (InterruptedException ignored) {
                    // empty
                }
            }
        }
        throw new CreateOfferException(format("%s could not create offer after %s attempts.",
                botDescription,
                attempts));
    };

    protected final Supplier<OfferInfo> createSellOffer = () -> {
        checkIfShutdownCalled("Interrupted before creating random SELL offer.");
        int attempts;
        for (attempts = 0; attempts < this.getMaxCreateOfferFailureLimit(); attempts++) {
            try {
                var amount = nextTradeBtcAmount.get();
                var isBsqOffer = paymentAccount.getSelectedTradeCurrency().getCode().equals(BSQ);
                OfferInfo offer;
                if (isBsqOffer) {
                    var priceAsString = calculateNextTradeMarginBasedPrice.apply(SELL).toPlainString();
                    offer = botClient.createOfferAtFixedPrice(paymentAccount,
                            BUY.name(),  // This is the Sell BSQ (Buy BTC) bot.
                            currencyCode,
                            amount,
                            amount,
                            priceAsString,
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                } else {
                    offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                            SELL.name(),
                            currencyCode,
                            amount,
                            amount,
                            this.getPriceMargin().doubleValue(),
                            this.getSecurityDepositAsPercent(),
                            this.getTradingFeeCurrencyCode());
                    log.info("Created SELL / {} offer at {}% above current market price of {}:\n{}",
                            currencyCode,
                            this.getPriceMargin(),
                            botClient.getCurrentBTCMarketPriceAsString(currencyCode),
                            formatOfferTable(singletonList(offer), currencyCode));
                    log.info("Payment account used to create offer: {}", paymentAccount.getId());
                }
                return offer;
            } catch (Exception ex) {
                log.error("Failed to create SELL offer after attempt #{}.", attempts, ex);
                try {
                    SECONDS.sleep(30);
                } catch (InterruptedException ignored) {
                    // empty
                }
            }
        }
        throw new CreateOfferException(format("%s could not create offer after %s attempts.",
                botDescription,
                attempts));
    };

    protected final Function<Supplier<OfferInfo>, TradeInfo> waitForNewTrade = (latestOffer) -> {
        initProtocolStep.accept(WAIT_FOR_OFFER_TAKER);
        OfferInfo offer = latestOffer.get();
        // TODO ?  ->  createTakeOfferCliScript(offer);
        log.info("Waiting for offer {} to be taken.", offer.getId());
        int numDelays = 0;
        // An offer may never be taken.  There is no protocol step time limit on takers.
        // This loop can keep the bot alive for an indefinite time, but it can
        // still be manually shutdown.
        while (true) {
            checkIfShutdownCalled("Interrupted while waiting for offer to be taken.");
            try {
                var trade = getNewTrade(offer.getId());
                if (trade.isPresent()) {
                    return trade.get();
                } else {
                    if (++numDelays % 15 == 0) {
                        log.warn("Offer {} still waiting to be taken.", offer.getId());
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
                    sleep(MINUTES.toMillis(1));
                }
            } catch (Exception ex) {
                throw new IllegalStateException(botClient.toCleanGrpcExceptionMessage(ex), ex);
            }
        } // end while
    };

    protected Optional<TradeInfo> getNewTrade(String offerId) {
        try {
            var trade = botClient.getTrade(offerId);
            log.info("Offer {} created with payment account {} has been taken. New trade:\n{}",
                    offerId,
                    paymentAccount.getId(),
                    TradeFormat.format(trade));
            return Optional.of(trade);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
