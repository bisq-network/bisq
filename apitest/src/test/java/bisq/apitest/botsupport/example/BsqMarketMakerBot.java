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

package bisq.apitest.botsupport.example;


import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.math.BigDecimal;

import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.SneakyThrows;

import javax.annotation.Nullable;

import static bisq.apitest.botsupport.protocol.BotProtocol.BSQ;
import static bisq.apitest.botsupport.protocol.BotProtocol.BTC;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.isShutdownCalled;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.startShutdownTimer;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.protocol.MarketMakerBotProtocol;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.botsupport.shutdown.ManualBotShutdownException;


@Getter
public class BsqMarketMakerBot extends BaseMarketMakerBot {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BsqMarketMakerBot.class);

    // TODO make this an option.
    static final long PROTOCOL_STEP_TIME_LIMIT = MINUTES.toMillis(180);

    public final Predicate<Integer> shouldLogWalletBalance = (i) -> i % 1 == 0;

    private final BigDecimal targetPrice;
    private final BigDecimal targetBtcAmount;
    private final BigDecimal targetSpread;
    private final int tradeCycleLimit;

    // Use a randomly chosen payment account for each trade.
    // This is preferred over using the same payment account over and over because
    // the BSQ receive address never changes, making BSQ payment confirmation riskier.
    // BSQ payment confirmation is done by matching a rcv address with a txout value,
    // making the check less certain.  Another way to improve the reliability of
    // bot BSQ payment confirmations is to vary the BSQ price by a few sats for
    // each trade.
    public BsqMarketMakerBot(String host,
                             int port,
                             String password,
                             int newBsqPaymentAccountsLimit,
                             BigDecimal targetPrice,
                             BigDecimal targetBtcAmount,
                             BigDecimal targetSpread,
                             int tradeCycleLimit) {
        super(host, port, password, newBsqPaymentAccountsLimit);
        this.targetPrice = targetPrice;
        this.targetBtcAmount = targetBtcAmount;
        this.targetSpread = targetSpread;
        this.tradeCycleLimit = tradeCycleLimit;
    }

    public void run() {
        try {

            if (botClient.iHaveCurrentOffers.test(BSQ)) {
                log.error("Bot shutting down because you already have BSQ offers in the book."
                                + "  Finish them with the CLI.\n{}",
                        formatOfferTable(botClient.getMyOffersSortedByDate(BTC), BSQ));
                return;
            }

            startShutdownTimer();

            // Do not start a bot if already shutting down.
            if (!isShutdownCalled()) {
                startBot(buyMakerBot,
                        botClient,
                        BUYER_BOT_NAME);
            }

            rest(15);

            // Do not start another bot if the 1st one is already shutting down.
            if (!isShutdownCalled()) {
                startBot(sellMakerBot,
                        botClient,
                        SELLER_BOT_NAME);
            }

            // TODO Optionally auto-shutdown after max cycles are complete.
            //  See boolean stayAlive usage.
            waitForManualShutdown();

        } catch (ManualBotShutdownException ex) {
            log.warn("{}  Shutting down bot before test completion", ex.getMessage());
        } catch (Throwable t) {
            log.error("Uncontrolled bot shutdown caused by uncaught bot exception", t);
        }
    }

    protected void startBot(Consumer<BotClient> bot,
                            BotClient botClient,
                            String botName) {
        try {
            log.info("Starting {}", botName);
            @SuppressWarnings({"unchecked"})
            ListenableFuture<Void> future =
                    (ListenableFuture<Void>) executor.submit(() -> bot.accept(botClient));
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void ignored) {
                    // 'Success' means a controlled shutdown that might be caused by an
                    // error.  The test case should only fail if the shutdown was caused
                    // by and exception.
                    log.info("{} shutdown.", botName);
                }

                @SneakyThrows
                @Override
                public void onFailure(Throwable t) {
                    if (t instanceof ManualBotShutdownException) {
                        log.warn("Manually shutting down {} thread.", botName);
                    } else {
                        log.error("Fatal error during {} run.", botName, t);
                    }
                    shutdownAllBots();
                }
            }, MoreExecutors.directExecutor());

        } catch (Exception ex) {
            log.error("", ex);
            throw new IllegalStateException(format("Error starting %s.", botName), ex);
        }
    }

    protected final Consumer<BotClient> buyMakerBot = (botClient) -> {
        try {
            while (numMakerSideBuys < this.getTradeCycleLimit()) {
                // Make sure the # of buy & sell offers never differ by more than 1.
                var canCreateNextOffer = numMakerSideBuys == 0 || numMakerSideBuys <= numMakerSideSells;
                if (canCreateNextOffer) {
                    var receiverPaymentAccount = getNextReceiverPaymentAccount();
                    MarketMakerBotProtocol botProtocol = new MarketMakerBotProtocol(BUYER_BOT_NAME,
                            botClient,
                            receiverPaymentAccount,
                            PROTOCOL_STEP_TIME_LIMIT,
                            new BashScriptGenerator(password, port, receiverPaymentAccount.getId(), false),
                            BUY.name(),
                            this.getTargetPrice(),
                            this.getTargetBtcAmount(),
                            this.getTargetSpread(),
                            0.15,
                            BSQ,
                            60);
                    botProtocol.run();
                    numMakerSideBuys++;
                    logTradingProgress();
                    /*
                    if (shouldLogWalletBalance.test(numMakerSideBuys))
                        logWalletBalance(log, BUYER_BOT_NAME, botClient);
                     */
                } else {
                    logOfferAlreadyExistsWarning(BUYER_BOT_NAME);
                }
                rest(20);
            }
        } catch (ManualBotShutdownException ex) {
            logManualShutdownWarning(log, BUYER_BOT_NAME);
            shutdownAllBots();
            // Exit the function, do not try to get balances below because the
            // server may be shutting down.
            return;
        } catch (Exception ex) {
            logFailedTradeError(log, BUYER_BOT_NAME, ex);
            shutdownAllBots();
            // Fatal error, do not try to get balances below because server is shutting down.
            this.setBuyerBotException(ex);
            return;
        }
        logBotCompletion(log, BUYER_BOT_NAME, botClient);
        isBuyBotShutdown.set(true);
    };

    public final Consumer<BotClient> sellMakerBot = (botClient) -> {
        try {
            while (numMakerSideSells < this.getTradeCycleLimit()) {
                // Make sure the # of buy & sell offers never differ by more than 1.
                var canCreateNextOffer = numMakerSideSells == 0 || numMakerSideSells <= numMakerSideBuys;
                if (canCreateNextOffer) {
                    var senderPaymentAccount = getSenderPaymentAccount();
                    MarketMakerBotProtocol botProtocol = new MarketMakerBotProtocol(SELLER_BOT_NAME,
                            botClient,
                            senderPaymentAccount,
                            PROTOCOL_STEP_TIME_LIMIT,
                            new BashScriptGenerator(password, port, senderPaymentAccount.getId(), false),
                            SELL.name(),
                            this.getTargetPrice(),
                            this.getTargetBtcAmount(),
                            this.getTargetSpread(),
                            0.15,
                            BSQ,
                            60);
                    botProtocol.run();
                    numMakerSideSells++;
                    logTradingProgress();
                    /*
                    if (shouldLogWalletBalance.test(numMakerSideSells))
                        logWalletBalance(log, SELLER_BOT_NAME, botClient);
                     */
                } else {
                    logOfferAlreadyExistsWarning(SELLER_BOT_NAME);
                }
                rest(20);
            }
        } catch (ManualBotShutdownException ex) {
            logManualShutdownWarning(log, SELLER_BOT_NAME);
            shutdownAllBots();
            // Exit the function, do not try to get balances below because the
            // server may be shutting down.
            return;
        } catch (Exception ex) {
            logFailedTradeError(log, SELLER_BOT_NAME, ex);
            shutdownAllBots();
            // Fatal error, do not try to get balances below because server is shutting down.
            this.setSellerBotException(ex);
            return;
        }
        logBotCompletion(log, SELLER_BOT_NAME, botClient);
        isSellBotShutdown.set(true);
    };

    protected void logOfferAlreadyExistsWarning(String botName, String direction) {
        log.warn("{} will not create a new {} while existing {} offer is waiting to be taken."
                        + "  Each trade cycle is 1 buy and 1 sell.",
                botName,
                direction,
                direction);
    }

    protected void logOfferAlreadyExistsWarning(String botName) {
        log.warn("{} will not create a new offer while an existing offer is waiting to be taken."
                        + "  Each trade cycle is 1 buy and 1 sell.",
                botName);
    }

    protected void logTradingProgress() {
        String completedTradeCycles;
        if (numMakerSideBuys == numMakerSideSells)
            completedTradeCycles = String.valueOf(numMakerSideBuys);
        else if (numMakerSideBuys > numMakerSideSells)
            completedTradeCycles = numMakerSideSells + ".5";
        else
            completedTradeCycles = numMakerSideBuys + ".5";

        log.info("===================================================================================================");
        log.info("Completed {} trades in {} trade cycles.  Balance After {} BUY and {} SELL trades:\n{}",
                numMakerSideBuys + numMakerSideSells,
                completedTradeCycles,
                numMakerSideBuys,
                numMakerSideSells,
                formatBalancesTbls(botClient.getBalance()));
        log.info("===================================================================================================");
    }
}
