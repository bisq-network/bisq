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

import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.security.SecureRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.apitest.botsupport.protocol.BotProtocol.BTC;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.isShutdownCalled;
import static bisq.apitest.botsupport.shutdown.ManualShutdown.setShutdownCalled;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.cli.TableFormat.formatPaymentAcctTbl;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.botsupport.BotClient;
import bisq.apitest.botsupport.shutdown.ManualBotShutdownException;
import bisq.apitest.botsupport.util.BotUtilities;
import bisq.cli.GrpcClient;

@Getter
@Slf4j
abstract class BaseMarketMakerBot {

    protected static final SecureRandom RANDOM = new SecureRandom();

    protected static final String BUYER_BOT_NAME = "Maker/Buyer Bot";
    protected static final String SELLER_BOT_NAME = "Maker/Seller Bot";

    protected final ListeningExecutorService executor =
            BotUtilities.getListeningExecutorService("Bisq Bot",
                    2,
                    2,
                    DAYS.toSeconds(1));

    @Nullable
    @Setter
    @Getter
    protected Exception buyerBotException;
    @Nullable
    @Setter
    @Getter
    protected Exception sellerBotException;

    protected final AtomicBoolean isBuyBotShutdown = new AtomicBoolean(false);
    protected final AtomicBoolean isSellBotShutdown = new AtomicBoolean(false);
    protected int numMakerSideBuys = 0;
    protected int numMakerSideSells = 0;

    protected final String host;
    protected final int port;
    protected final String password;
    protected final BotClient botClient;
    protected final int newBsqPaymentAccountsLimit;
    protected final List<PaymentAccount> receiverPaymentAccounts = new ArrayList<>();
    protected final PaymentAccount senderPaymentAccount;

    protected final List<TradeInfo> botTradeHistory = new ArrayList<>(); // TODO persist?  Json?

    public BaseMarketMakerBot(String host,
                              int port,
                              String password,
                              int newBsqPaymentAccountsLimit) {

        this.host = host;
        this.port = port;
        this.password = password;
        this.newBsqPaymentAccountsLimit = newBsqPaymentAccountsLimit;
        this.botClient = new BotClient(new GrpcClient(host, port, password));
        this.receiverPaymentAccounts.addAll(botClient.getReceiverBsqPaymentAccounts());
        this.senderPaymentAccount = getOrCreateSenderPaymentAccount();
    }

    abstract void run();

    protected final PaymentAccount getOrCreateSenderPaymentAccount() {
        var senderAccounts = botClient.getSenderBsqPaymentAccounts();
        return senderAccounts.isEmpty()
                ? botClient.createSenderBsqPaymentAccount()
                : senderAccounts.get(0);
    }

    protected final PaymentAccount getNextReceiverPaymentAccount() {
        if (receiverPaymentAccounts.isEmpty()) {
            log.warn("You have not set up any BSQ payment accounts."
                            + "  The bot may create up to {} new accounts as needed, with unique receiving BSQ addresses.",
                    newBsqPaymentAccountsLimit);
            var newAccount = botClient.createReceiverBsqPaymentAccount();
            receiverPaymentAccounts.add(newAccount);
            log.info("The new receiving payment account id is {}.", newAccount.getId());
            return newAccount;
        } else if (receiverPaymentAccounts.size() < newBsqPaymentAccountsLimit) {
            log.warn("You have {} BSQ payment accounts."
                            + "  The bot may create up to {} new accounts as needed, with unique receiving BSQ addresses.",
                    receiverPaymentAccounts.size(),
                    newBsqPaymentAccountsLimit - receiverPaymentAccounts.size());
            var newAccount = botClient.createReceiverBsqPaymentAccount();
            receiverPaymentAccounts.add(newAccount);
            log.info("The new receiving payment account id is {}.", newAccount.getId());
            return newAccount;
        } else {
            var next = RANDOM.nextInt(receiverPaymentAccounts.size());
            var nextAccount = receiverPaymentAccounts.get(next);
            log.info("The next receiving payment account id is {}.", nextAccount.getId());
            return nextAccount;
        }
    }

    protected void waitForManualShutdown() {
        log.info("When ready to shutdown bot, run '$ touch /tmp/bot-shutdown'.");
        try {
            while (!isShutdownCalled()) {
                rest(10);
            }
            log.warn("Manual shutdown signal received.");
        } catch (ManualBotShutdownException ex) {
            log.warn(ex.getMessage());
        }
    }

    protected void rest(long delayInSeconds) {
        try {
            SECONDS.sleep(delayInSeconds);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    protected void logStatus(Logger log, PaymentAccount paymentAccount) {
        log.info("Payment Account:\n{}", formatPaymentAcctTbl(singletonList(paymentAccount)));

        log.info("Balances:\n{}", formatBalancesTbls(botClient.getBalance()));

        var currentOffers = botClient.getMyOffersSortedByDate(BTC);
        if (currentOffers.isEmpty())
            log.info("No current offers.");
        else
            log.info("Current offers:\n{}", formatOfferTable(botClient.getMyOffersSortedByDate(BTC), BSQ));

        if (botTradeHistory.isEmpty()) {
            log.info("No trades during this bot run.");
        } else {
            log.info("TODO print trades");
        }
    }


    protected void logWalletBalance(Logger log, String botName, BotClient botClient) {
        log.info("{} balances:\n{}", botName, formatBalancesTbls(botClient.getBalance()));
    }

    protected void logManualShutdownWarning(Logger log, String botName) {
        log.warn("Manual shutdown called, stopping {}.", botName);
    }

    protected void logFailedTradeError(Logger log, String botName, Exception exception) {
        log.error("{} could not complete trade.", botName, exception);
    }

    protected void logBotCompletion(Logger log, String botName, BotClient botClient) {
        log.info("{} is done.  Balances:\n{}",
                botName,
                formatBalancesTbls(botClient.getBalance()));
    }

    protected boolean botDidFail() {
        return buyerBotException != null || sellerBotException != null;
    }

    protected String getBotFailureReason() {
        StringBuilder reasonBuilder = new StringBuilder();

        if (buyerBotException != null)
            reasonBuilder.append(BUYER_BOT_NAME).append(" failed: ")
                    .append(buyerBotException.getMessage()).append("\n");

        if (sellerBotException != null)
            reasonBuilder.append(SELLER_BOT_NAME).append(" failed: ")
                    .append(sellerBotException.getMessage()).append("\n");

        return reasonBuilder.toString();
    }

    protected void shutdownAllBots() {
        isBuyBotShutdown.set(true);
        isSellBotShutdown.set(true);
        setShutdownCalled(true);
        executor.shutdownNow();
    }
}
