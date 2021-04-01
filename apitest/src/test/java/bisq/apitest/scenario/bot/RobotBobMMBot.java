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

package bisq.apitest.scenario.bot;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.util.Utilities;

import protobuf.PaymentAccount;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.apitest.botsupport.shutdown.ManualShutdown.isShutdownCalled;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.setShutdownCalled;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.core.offer.OfferPayload.Direction.BUY;
import static bisq.core.offer.OfferPayload.Direction.SELL;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.script.BashScriptGenerator;
import bisq.apitest.botsupport.shutdown.ManualBotShutdownException;
import bisq.apitest.method.BitcoinCliHelper;
import bisq.apitest.scenario.bot.protocol.ApiTestBotProtocol;
import bisq.apitest.scenario.bot.protocol.ApiTestMarketMakerBotProtocol;
import bisq.apitest.scenario.bot.protocol.MarketMakerTakeOnlyBotProtocol;
import bisq.apitest.scenario.bot.script.BotScript;


@SuppressWarnings("NullableProblems")
@Slf4j
public class RobotBobMMBot extends Bot {

    private static final int MAX_BUY_OFFERS = 500;
    private static final int MAX_SELL_OFFERS = 500;

    // Show wallet balances after every N trades.
    private static final int SHOW_WALLET_BALANCE_MARKER = 20;

    private static final String BUYER_BOT_NAME = "Maker/Buyer Bot";
    private static final String SELLER_BOT_NAME = "Maker/Seller Bot";
    private static final String TAKER_BOT_NAME = "Taker Bot";

    @Nullable
    @Setter
    @Getter
    private Exception buyerBotException;
    @Nullable
    @Setter
    @Getter
    private Exception sellerBotException;
    @Nullable
    @Setter
    @Getter
    private Exception takerBotException;

    private final AtomicBoolean isBuyBotShutdown = new AtomicBoolean(false);
    private final AtomicBoolean isSellBotShutdown = new AtomicBoolean(false);
    private final AtomicBoolean isTakerBotShutdown = new AtomicBoolean(false);

    private int numMakerSideBuys = 0;
    private int numMakerSideSells = 0;
    private int numTakerSideTrades = 0;

    private Timer btcBlockGenerator;

    @Getter
    private final AtomicLong bobsBankBalance = new AtomicLong(100_000);
    @Getter
    private final AtomicLong takersBankBalance = new AtomicLong(100_000);

    private final BotClient takerBotClient;
    @Getter
    private final String takerBotPaymentAccountId;

    private final ListeningExecutorService executor =
            Utilities.getListeningExecutorService("Market Maker",
                    3,
                    3,
                    24 * 60 * 60);

    public RobotBobMMBot(BotClient makerBotClient,
                         BotClient takerBotClient,
                         BotScript botScript,
                         BitcoinCliHelper bitcoinCli,
                         BashScriptGenerator bashScriptGenerator) {
        super(makerBotClient, botScript, bitcoinCli, bashScriptGenerator);
        this.takerBotClient = takerBotClient;
        this.takerBotPaymentAccountId = botScript.getPaymentAccountIdForCliScripts();
    }

    public void run() {
        btcBlockGenerator = UserThread.runPeriodically(() -> {
            String btcCoreAddress = bitcoinCli.getNewBtcAddress();
            log.info("Generating BTC block to address {}.", btcCoreAddress);
            bitcoinCli.generateToAddress(1, btcCoreAddress);
        }, 20, SECONDS);

        startBot(buyMakerBot,
                makerBotClient,
                BUYER_BOT_NAME);
        rest(15);

        // Do not start another bot if the 1st one is already shutting down.
        if (!isShutdownCalled()) {
            startBot(takerBot,
                    takerBotClient,
                    TAKER_BOT_NAME);
            rest(5);
        }

        if (!isShutdownCalled()) {
            startBot(sellMakerBot,
                    makerBotClient,
                    SELLER_BOT_NAME);
        }

        if (stayAlive)
            waitForManualShutdown();
        else
            warnCLIUserBeforeShutdown();
    }

    public void shutdownAllBots() {
        isBuyBotShutdown.set(true);
        isSellBotShutdown.set(true);
        isTakerBotShutdown.set(true);
        setShutdownCalled(true);
        btcBlockGenerator.stop();
        executor.shutdownNow();
    }

    public void startBot(Consumer<BotClient> bot,
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

    public final Predicate<Integer> shouldLogWalletBalance = (i) -> i % SHOW_WALLET_BALANCE_MARKER == 0;

    public final Consumer<BotClient> buyMakerBot = (botClient) -> {
        try {
            while (numMakerSideBuys < MAX_BUY_OFFERS) {
                var offersExist = botClient.iHaveCurrentOffersWithDirection.test(BUY.name(),
                        requireNonNull(paymentAccount.getSelectedTradeCurrency()).getCode());
                if (offersExist) {
                    logOfferAlreadyExistsWarning(BUYER_BOT_NAME, BUY.name());
                } else {
                    ApiTestBotProtocol botProtocol = new ApiTestMarketMakerBotProtocol(botClient,
                            paymentAccount,
                            protocolStepTimeLimitInMs,
                            bitcoinCli,
                            bashScriptGenerator,
                            BUY.name(),
                            bobsBankBalance);
                    botProtocol.run();
                    numMakerSideBuys++;
                    logTradingProgress(BUYER_BOT_NAME, BUY.name(), bobsBankBalance);
                    if (shouldLogWalletBalance.test(numMakerSideBuys))
                        logWalletBalance(BUYER_BOT_NAME, botClient);
                }
                rest(20);
            }
        } catch (ManualBotShutdownException ex) {
            logManualShutdownWarning(BUYER_BOT_NAME);
            shutdownAllBots();
            // Exit the function, do not try to get balances below because the
            // server may be shutting down.
            return;
        } catch (Exception ex) {
            logFailedTradeError(BUYER_BOT_NAME, ex);
            shutdownAllBots();
            // Fatal error, do not try to get balances below because server is shutting down.
            this.setBuyerBotException(ex);
            return;
        }
        logBotCompletion(BUYER_BOT_NAME, botClient, bobsBankBalance);
        isBuyBotShutdown.set(true);
    };

    public final Consumer<BotClient> sellMakerBot = (botClient) -> {
        try {
            while (numMakerSideSells < MAX_SELL_OFFERS) {
                var offersExist = botClient.iHaveCurrentOffersWithDirection.test(SELL.name(),
                        requireNonNull(paymentAccount.getSelectedTradeCurrency()).getCode());
                if (offersExist) {
                    logOfferAlreadyExistsWarning(SELLER_BOT_NAME, SELL.name());
                } else {
                    ApiTestBotProtocol botProtocol = new ApiTestMarketMakerBotProtocol(botClient,
                            paymentAccount,
                            protocolStepTimeLimitInMs,
                            bitcoinCli,
                            bashScriptGenerator,
                            SELL.name(),
                            bobsBankBalance);
                    botProtocol.run();
                    numMakerSideSells++;
                    logTradingProgress(SELLER_BOT_NAME, SELL.name(), bobsBankBalance);
                    if (shouldLogWalletBalance.test(numMakerSideSells))
                        logWalletBalance(SELLER_BOT_NAME, botClient);
                }
                rest(20);
            }
        } catch (ManualBotShutdownException ex) {
            logManualShutdownWarning(SELLER_BOT_NAME);
            shutdownAllBots();
            // Exit the function, do not try to get balances below because the
            // server may be shutting down.
            return;
        } catch (Exception ex) {
            logFailedTradeError(SELLER_BOT_NAME, ex);
            shutdownAllBots();
            // Fatal error, do not try to get balances below because server is shutting down.
            this.setSellerBotException(ex);
            return;
        }
        logBotCompletion(SELLER_BOT_NAME, botClient, bobsBankBalance);
        isSellBotShutdown.set(true);
    };

    public final Consumer<BotClient> takerBot = (botClient) -> {
        PaymentAccount takerPaymentAccount = botClient.getPaymentAccount(this.getTakerBotPaymentAccountId());
        // Keep taking offers until max offers is reached, or if any maker bot is running.
        while (takerShouldStayAlive()) {
            try {
                ApiTestBotProtocol botProtocol = new MarketMakerTakeOnlyBotProtocol(botClient,
                        takerPaymentAccount,
                        protocolStepTimeLimitInMs,
                        bitcoinCli,
                        bashScriptGenerator,
                        takersBankBalance);
                botProtocol.run();
                numTakerSideTrades++;
            } catch (ManualBotShutdownException ex) {
                logManualShutdownWarning(TAKER_BOT_NAME);
                shutdownAllBots();
                // Exit the function, do not try to get balances below because the
                // server may be shutting down.
                return;
            } catch (Exception ex) {
                logFailedTradeError(TAKER_BOT_NAME, ex);
                shutdownAllBots();
                // Fatal error, do not try to get balances below because server is shutting down.
                this.setTakerBotException(ex);
                return;
            }
            logTradingProgress(TAKER_BOT_NAME, null, takersBankBalance);
            if (shouldLogWalletBalance.test(numTakerSideTrades))
                logWalletBalance(TAKER_BOT_NAME, botClient);

            rest(20);
        }
        logBotCompletion(TAKER_BOT_NAME, botClient, takersBankBalance);
        isTakerBotShutdown.set(true);
    };

    public boolean takerShouldStayAlive() {
        if (numTakerSideTrades >= (MAX_BUY_OFFERS + MAX_SELL_OFFERS))
            return false;

        if (isTakerBotShutdown.get())
            return false;

        return !isBuyBotShutdown.get() || !isSellBotShutdown.get();
    }

    public boolean botDidFail() {
        return buyerBotException != null || sellerBotException != null || takerBotException != null;
    }

    public String getBotFailureReason() {
        StringBuilder reasonBuilder = new StringBuilder();

        if (buyerBotException != null)
            reasonBuilder.append(BUYER_BOT_NAME).append(" failed: ")
                    .append(buyerBotException.getMessage()).append("\n");

        if (sellerBotException != null)
            reasonBuilder.append(SELLER_BOT_NAME).append(" failed: ")
                    .append(sellerBotException.getMessage()).append("\n");

        if (takerBotException != null)
            reasonBuilder.append(TAKER_BOT_NAME).append(" failed: ")
                    .append(takerBotException.getMessage()).append("\n");

        return reasonBuilder.toString();
    }

    protected void waitForManualShutdown() {
        String harnessOrCase = isUsingTestHarness ? "harness" : "case";
        log.info("The test {} will stay alive until a /tmp/bottest-shutdown file is detected.",
                harnessOrCase);
        log.info("When ready to shutdown the test {}, run '$ touch /tmp/bottest-shutdown'.",
                harnessOrCase);
        if (!isUsingTestHarness) {
            log.warn("You will have to manually shutdown the bitcoind and Bisq nodes"
                    + " running outside of the test harness.");
        }
        try {
            while (!isShutdownCalled()) {
                rest(10);
            }
            log.warn("Manual shutdown signal received.");
        } catch (ManualBotShutdownException ex) {
            log.warn(ex.getMessage());
        } finally {
            btcBlockGenerator.stop();
        }
    }

    protected void warnCLIUserBeforeShutdown() {
        if (isUsingTestHarness) {
            while (!isBuyBotShutdown.get() || !isSellBotShutdown.get() || !isTakerBotShutdown.get()) {
                try {
                    SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                    // empty
                }
            }
            long delayInSeconds = 5;
            log.warn("You have {} seconds to complete any remaining tasks before the test harness shuts down.",
                    delayInSeconds);
            rest(delayInSeconds);
        } else {
            log.info("Shutting down test case");
        }
        btcBlockGenerator.stop();
    }

    protected void rest(long delayInSeconds) {
        try {
            SECONDS.sleep(delayInSeconds);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    protected void logOfferAlreadyExistsWarning(String botName, String direction) {
        log.warn("{} will not create a new {} while existing offer is waiting to be taken.",
                botName,
                direction);
    }

    protected void logTradingProgress(String botName, String direction, AtomicLong bankBalance) {
        log.info("===================================================================================================");
        if (direction == null || direction.isEmpty())
            log.info("{} completed {} trades.  Bank Balance: {}",
                    botName,
                    numTakerSideTrades,
                    bankBalance.get());
        else
            log.info("{} completed {} {} trades.  Bank Balance After {} BUY and {} SELL trades: {}",
                    botName,
                    direction.equals(BUY.name()) ? numMakerSideBuys : numMakerSideSells,
                    direction,
                    numMakerSideBuys,
                    numMakerSideSells,
                    bankBalance.get());
        log.info("===================================================================================================");
    }

    protected void logManualShutdownWarning(String botName) {
        log.warn("Manual shutdown called, stopping {}.", botName);
    }

    protected void logFailedTradeError(String botName, Exception exception) {
        log.error("{} could not complete trade # {}.",
                botName,
                numTakerSideTrades,
                exception);
    }

    protected void logBotCompletion(String botName, BotClient botClient, AtomicLong bankBalance) {
        log.info("{} is done.  Balances:\n{}\nBank Account Balance: {}",
                botName,
                formatBalancesTbls(botClient.getBalance()),
                bankBalance.get());
    }

    protected void logWalletBalance(String botName, BotClient botClient) {
        log.info("{} balances:\n{}", botName, formatBalancesTbls(botClient.getBalance()));
    }
}
