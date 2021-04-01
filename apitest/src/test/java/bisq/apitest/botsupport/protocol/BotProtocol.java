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

import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.security.SecureRandom;

import java.io.File;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.Getter;

import static bisq.apitest.botsupport.protocol.ProtocolStep.*;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.checkIfShutdownCalled;
import static bisq.cli.CurrencyFormat.formatBsqAmount;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.cli.TradeFormat;
import bisq.cli.TransactionFormat;

public abstract class BotProtocol {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotProtocol.class);

    // TODO move these to proper place.
    public static final String BSQ = "BSQ";
    public static final String BTC = "BTC";

    protected static final SecureRandom RANDOM = new SecureRandom();

    // Random random millis in range [5, 30) seconds.
    protected final Supplier<Long> shortRandomDelayInSeconds = () -> (long) (5000 + RANDOM.nextInt(30_000));
    // Returns random millis in range [1, 15) minutes.
    protected final Supplier<Long> longRandomDelayInMinutes = () -> (long) (60_000 + RANDOM.nextInt(15 * 60_000));

    protected final AtomicLong protocolStepStartTime = new AtomicLong(0);
    protected final Consumer<ProtocolStep> initProtocolStep = (step) -> {
        currentProtocolStep = step;
        printBotProtocolStep();
        protocolStepStartTime.set(currentTimeMillis());
    };

    // Functions declared in 'this' need getters.
    @Getter
    protected final String botDescription;
    @Getter
    protected final BotClient botClient;
    @Getter
    protected final PaymentAccount paymentAccount;
    protected final String currencyCode;
    protected final long protocolStepTimeLimitInMs;
    @Getter
    protected final BashScriptGenerator bashScriptGenerator;
    @Getter
    protected ProtocolStep currentProtocolStep;

    public BotProtocol(String botDescription,
                       BotClient botClient,
                       PaymentAccount paymentAccount,
                       long protocolStepTimeLimitInMs,
                       BashScriptGenerator bashScriptGenerator) {
        this.botDescription = botDescription;
        this.botClient = botClient;
        this.paymentAccount = paymentAccount;
        this.currencyCode = Objects.requireNonNull(paymentAccount.getSelectedTradeCurrency()).getCode();
        this.protocolStepTimeLimitInMs = protocolStepTimeLimitInMs;
        this.bashScriptGenerator = bashScriptGenerator;
        this.currentProtocolStep = START;
    }

    public abstract void run();

    protected final Function<TradeInfo, TradeInfo> waitForTakerFeeTxConfirm = (trade) -> {
        sleep(shortRandomDelayInSeconds.get());
        waitForTakerDepositFee(trade.getTradeId(), WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED);
        waitForTakerDepositFee(trade.getTradeId(), WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED);
        return trade;
    };

    protected void waitForTakerDepositFee(String tradeId, ProtocolStep depositTxProtocolStep) {
        initProtocolStep.accept(depositTxProtocolStep);
        validateCurrentProtocolStep(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED, WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED);
        log.info(waitingForDepositFeeTxMsg(tradeId));
        String warning = format("Interrupted before checking taker deposit fee tx is %s for trade %s.",
                depositTxProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed",
                tradeId);
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled(warning);
            try {
                var trade = this.getBotClient().getTrade(tradeId);
                if (isDepositFeeTxStepComplete.test(trade)) {
                    return;
                } else {
                    if (++numDelays % 5 == 0) {
                        var tx = this.getBotClient().getTransaction(trade.getDepositTxId());
                        log.warn("Still waiting for trade {} taker tx {} fee to be {}.\n{}",
                                trade.getTradeId(),
                                trade.getDepositTxId(),
                                depositTxProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed",
                                TransactionFormat.format(tx));
                    }
                    sleep(shortRandomDelayInSeconds.get());
                }
            } catch (Exception ex) {
                if (this.getBotClient().tradeContractIsNotReady.test(ex, tradeId))
                    continue;
                else
                    throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
        }  // end while

        // If the while loop is exhausted, a deposit fee tx was not published or confirmed within the protocol step time limit.
        throw new IllegalStateException(stoppedWaitingForDepositFeeTxMsg(tradeId));
    }

    protected String waitingForDepositFeeTxMsg(String tradeId) {
        return format("%s is waiting for taker deposit fee tx for trade %s to be %s.",
                botDescription,
                tradeId,
                currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed");
    }

    protected String stoppedWaitingForDepositFeeTxMsg(String tradeId) {
        return format("Taker deposit fee tx for trade %s took too long to be %s;  %s will stop waiting.",
                tradeId,
                currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) ? "published" : "confirmed",
                botDescription);
    }

    protected final Predicate<TradeInfo> isDepositFeeTxStepComplete = (trade) -> {
        if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_PUBLISHED) && trade.getIsDepositPublished()) {
            log.info("{} sees trade {} taker deposit fee tx {} has been published.",
                    this.getBotDescription(),
                    trade.getTradeId(),
                    trade.getDepositTxId());
            return true;
        } else if (currentProtocolStep.equals(WAIT_FOR_TAKER_DEPOSIT_TX_CONFIRMED) && trade.getIsDepositConfirmed()) {
            log.info("{} sees trade {} taker deposit fee tx {} has been confirmed.",
                    this.getBotDescription(),
                    trade.getTradeId(),
                    trade.getDepositTxId());
            return true;
        } else {
            return false;
        }
    };

    protected final Consumer<TradeInfo> waitForBsqPayment = (trade) -> {
        // TODO When atomic trades are implemented, a different payment acct rcv address
        //  may be used for each trade.  For now, the best we can do is match the amount
        //  with the address to verify the correct amount of BSQ was received.
        initProtocolStep.accept(WAIT_FOR_BSQ_PAYMENT_TO_RCV_ADDRESS);
        var contract = trade.getContract();
        var bsqSats = trade.getOffer().getVolume();
        var receiveAmountAsString = formatBsqAmount(bsqSats);
        var address = contract.getIsBuyerMakerAndSellerTaker()
                ? contract.getTakerPaymentAccountPayload().getAddress()
                : contract.getMakerPaymentAccountPayload().getAddress();
        log.info("{} verifying payment of {} BSQ was received to address {} for trade with id {}.",
                this.getBotDescription(),
                receiveAmountAsString,
                address,
                trade.getTradeId());
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking to see if BSQ payment has been received.");
            try {
                boolean isAmountReceived = this.getBotClient().verifyBsqSentToAddress(address, receiveAmountAsString);
                if (isAmountReceived) {
                    log.warn("{} has received payment of {} BSQ to address {} for trade with id {}.",
                            this.getBotDescription(),
                            receiveAmountAsString,
                            address,
                            trade.getTradeId());
                    return;
                } else {
                    log.warn("{} has still has not received payment of {} BSQ to address {} for trade with id {}.",
                            this.getBotDescription(),
                            receiveAmountAsString,
                            address,
                            trade.getTradeId());
                }
                sleep(shortRandomDelayInSeconds.get());
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
        }

        // If the while loop is exhausted, a payment started msg was not detected.
        throw new IllegalStateException("Payment started msg never sent; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> waitForPaymentStartedMessage = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYMENT_STARTED_MESSAGE);
        createPaymentStartedScript(trade);
        log.info("{} is waiting for a 'payment started' message from buyer for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if 'payment started' message has been sent.");
            int numDelays = 0;
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsFiatSent()) {
                    log.info("Buyer has started payment for trade: {}\n{}",
                            t.getTradeId(),
                            TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for 'payment started' message for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(shortRandomDelayInSeconds.get());
        } // end while

        // If the while loop is exhausted, a payment started msg was not detected.
        throw new IllegalStateException("Payment started msg never sent; we won't wait any longer.");
    };

    protected final Consumer<TradeInfo> sendBsqPayment = (trade) -> {
        // Be very careful when using this on mainnet.
        initProtocolStep.accept(SEND_PAYMENT_TO_RCV_ADDRESS);
        while (true) {
            // TODO FIX
            if (trade.hasContract()) {
                this.getBotClient().makeBsqPayment(trade);
                break;
            } else {
                log.warn("Trade contract for {} not ready.");
                sleep(shortRandomDelayInSeconds.get());
            }
        }
    };

    protected final Function<TradeInfo, TradeInfo> sendPaymentStartedMessage = (trade) -> {
        var isBsqOffer = this.getPaymentAccount().getSelectedTradeCurrency().getCode().equals(BSQ);
        if (isBsqOffer) {
            sendBsqPayment.accept(trade);
        }
        log.info("{} is sending 'payment started' msg for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        initProtocolStep.accept(SEND_PAYMENT_STARTED_MESSAGE);
        checkIfShutdownCalled("Interrupted before sending 'payment started' message.");
        this.getBotClient().sendConfirmPaymentStartedMessage(trade.getTradeId());
        return trade;
    };

    protected final Function<TradeInfo, TradeInfo> waitForPaymentReceivedConfirmation = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE);
        createPaymentReceivedScript(trade);
        log.info("{} is waiting for a 'payment received confirmation' message from seller for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        int numDelays = 0;
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if 'payment received confirmation' message has been sent.");
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsFiatReceived()) {
                    log.info("Seller has received payment for trade: {}\n{}",
                            t.getTradeId(),
                            TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for 'payment received confirmation' message for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(shortRandomDelayInSeconds.get());
        } // end while

        // If the while loop is exhausted, a payment rcvd confirmation msg was not detected within the protocol step time limit.
        throw new IllegalStateException("Payment was never received; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> sendPaymentReceivedMessage = (trade) -> {
        // TODO refactor this, move to top where functions are composed.
        var isBsqOffer = this.getPaymentAccount().getSelectedTradeCurrency().getCode().equals(BSQ);
        if (isBsqOffer) {
            waitForBsqPayment.accept(trade);
        }
        log.info("{} is sending 'payment received confirmation' msg for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        initProtocolStep.accept(SEND_PAYMENT_RECEIVED_CONFIRMATION_MESSAGE);
        checkIfShutdownCalled("Interrupted before sending 'payment received confirmation' message.");
        this.getBotClient().sendConfirmPaymentReceivedMessage(trade.getTradeId());
        return trade;
    };

    protected final Function<TradeInfo, TradeInfo> waitForPayoutTx = (trade) -> {
        initProtocolStep.accept(WAIT_FOR_PAYOUT_TX);
        log.info("{} is waiting on the 'payout tx published' confirmation for trade with id {}.",
                this.getBotDescription(),
                trade.getTradeId());
        while (isWithinProtocolStepTimeLimit()) {
            checkIfShutdownCalled("Interrupted before checking if payout tx has been published.");
            int numDelays = 0;
            try {
                var t = this.getBotClient().getTrade(trade.getTradeId());
                if (t.getIsPayoutPublished()) {
                    log.info("Payout tx {} has been published for trade {}:\n{}",
                            t.getPayoutTxId(),
                            t.getTradeId(),
                            TradeFormat.format(t));
                    return t;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(this.getBotClient().toCleanGrpcExceptionMessage(ex));
            }
            if (++numDelays % 5 == 0) {
                log.warn("{} is still waiting for payout tx for trade {}",
                        this.getBotDescription(),
                        trade.getShortId());
            }
            sleep(shortRandomDelayInSeconds.get());
        } // end while

        // If the while loop is exhausted, a payout tx was not detected within the protocol step time limit.
        throw new IllegalStateException("Payout tx was never published; we won't wait any longer.");
    };

    protected final Function<TradeInfo, TradeInfo> keepFundsFromTrade = (trade) -> {
        initProtocolStep.accept(KEEP_FUNDS);
        var isBuy = trade.getOffer().getDirection().equalsIgnoreCase(BUY.name());
        var isSell = trade.getOffer().getDirection().equalsIgnoreCase(SELL.name());

        var cliUserIsSeller = (this instanceof MakerBotProtocol && isBuy)
                || (this instanceof TakerBotProtocol && isSell);
        if (cliUserIsSeller) {
            createKeepFundsScript(trade);
        } else {
            createGetBalanceScript();
        }
        checkIfShutdownCalled("Interrupted before closing trade with 'keep funds' command.");
        this.getBotClient().sendKeepFundsMessage(trade.getTradeId());
        return trade;
    };

    protected void validateCurrentProtocolStep(Enum<?>... validBotSteps) {
        for (Enum<?> validBotStep : validBotSteps) {
            if (currentProtocolStep.equals(validBotStep))
                return;
        }
        throw new IllegalStateException("Unexpected bot step: " + currentProtocolStep.name() + ".\n"
                + "Must be one of "
                + stream(validBotSteps).map((Enum::name)).collect(Collectors.joining(","))
                + ".");
    }

    protected void checkIsStartStep() {
        if (currentProtocolStep != START) {
            throw new IllegalStateException("First bot protocol step must be " + START.name());
        }
    }

    protected void printBotProtocolStep() {
        log.info("{} is starting protocol step {}.  Time limit is {} minutes.",
                botDescription,
                currentProtocolStep.name(),
                MILLISECONDS.toMinutes(protocolStepTimeLimitInMs));
    }

    protected boolean isWithinProtocolStepTimeLimit() {
        return (currentTimeMillis() - protocolStepStartTime.get()) < protocolStepTimeLimitInMs;
    }

    protected void printCliHintAndOrScript(File script, String hint) {
        log.info("{} by running bash script '{}'.", hint, script.getAbsolutePath());
        if (this.getBashScriptGenerator().isPrintCliScripts())
            this.getBashScriptGenerator().printCliScript(script, log);
    }

    protected void createGetBalanceScript() {
        File script = bashScriptGenerator.createGetBalanceScript();
        printCliHintAndOrScript(script, "The manual CLI side can view current balances");
    }

    protected void createPaymentStartedScript(TradeInfo trade) {
        String scriptFilename = "confirmpaymentstarted-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createPaymentStartedScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can send a 'payment started' message");
    }

    protected void createPaymentReceivedScript(TradeInfo trade) {
        String scriptFilename = "confirmpaymentreceived-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createPaymentReceivedScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can send a 'payment received confirmation' message");
    }

    protected void createKeepFundsScript(TradeInfo trade) {
        String scriptFilename = "keepfunds-" + trade.getShortId() + ".sh";
        File script = bashScriptGenerator.createKeepFundsScript(trade, scriptFilename);
        printCliHintAndOrScript(script, "The manual CLI side can close the trade");
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            // empty
        }
    }
}
