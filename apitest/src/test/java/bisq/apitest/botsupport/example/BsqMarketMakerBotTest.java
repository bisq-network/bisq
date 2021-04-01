package bisq.apitest.botsupport.example;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.botsupport.protocol.BotProtocol.BTC;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.cli.CurrencyFormat.formatBsqAmount;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.botsupport.BotClient;
import bisq.cli.GrpcClient;
import bisq.cli.TradeFormat;

// Requirements:  test harness, registered agents, BsqMarketMakerBot.
//
// $ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon  --shutdownAfterTests=false --enableBisqDebugging=false
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=5 --new-payment-accts-limit=10
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=20 --new-payment-accts-limit=20
// Only use the one below without the the bloom filter hack.
// $ ./bisq-bot --password=xyz --port=9998 bsqmarketmaker --target-btc-amount=0.05 --target-price=0.00004 --target-spread=13.00 --trade-cycle-limit=100 --new-payment-accts-limit=30
//
@Slf4j
public class BsqMarketMakerBotTest {

    // TODO Install shutdown hook, check state.

    private static final int MAX_TRADE_CYCLES = 5;

    private final BotClient botClient;
    private final PaymentAccount paymentAccount;
    private final List<OfferInfo> offers;

    public BsqMarketMakerBotTest() {
        this.botClient = new BotClient(new GrpcClient("localhost", 9999, "xyz"));
        this.paymentAccount = botClient.createCryptoCurrencyPaymentAccount("Bob's Instant Acct", true);
        this.offers = new ArrayList<>();
    }

    public void runTradeCycles() {
        try {
            verifyHavePaymentAccount();

            for (int tradeCycle = 1; tradeCycle <= MAX_TRADE_CYCLES; tradeCycle++) {
                offers.addAll(botClient.getOffersSortedByDate(BTC));
                verifyHaveOffers(tradeCycle);  // Should 1 BUY and 1 SELL for each trade cycle

                takeBuyBsqOffer();
                SECONDS.sleep(30);
                takeSellBsqOffer();
                SECONDS.sleep(10);

                verifyHaveTrades();
                SECONDS.sleep(5);

                sendAliceBsqPaymentForHerBtc();
                SECONDS.sleep(60);

                verifyBsqReceivedFromAlice();
                SECONDS.sleep(60);

                SECONDS.sleep(5);
                closeTrades();

                SECONDS.sleep(5);
                printBalance();

                offers.clear();
                if (tradeCycle < MAX_TRADE_CYCLES) {
                    log.info("Completed {} trade cycle(s).  Starting the next in 1 minute.", tradeCycle);
                    SECONDS.sleep(60);
                }
            }

            log.info("Shutting down taker bot.");

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    protected void takeBuyBsqOffer() {
        var offer = offers.stream().filter(o -> o.getDirection().equals(SELL.name())).findFirst().get();
        log.info("Bob is taking Buy BSQ (Sell BTC) offer {} using payment account {}.",
                offer.getId(),
                paymentAccount.getId());
        botClient.takeOffer(offer.getId(), paymentAccount, BSQ);
    }

    protected void takeSellBsqOffer() {
        var offer = offers.stream().filter(o -> o.getDirection().equals(BUY.name())).findFirst().get();
        log.info("Bob is taking Sell BSQ (Buy BTC) offer {} using payment account {}.",
                offer.getId(),
                paymentAccount.getId());
        botClient.takeOffer(offer.getId(), paymentAccount, BTC);
    }

    protected void verifyHavePaymentAccount() {
        if (paymentAccount == null)
            throw new IllegalStateException("No payment account for taking offers.");

        log.info("Bob is using '{}' with id = {}", paymentAccount.getAccountName(), paymentAccount.getId());
    }

    protected void verifyHaveOffers(int currentTradeCycle) {
        if (currentTradeCycle == MAX_TRADE_CYCLES) {
            log.warn("We're done, shutting down");
            System.exit(0);
        }

        if (offers.isEmpty()) {
            if (currentTradeCycle < MAX_TRADE_CYCLES) {
                throw new IllegalStateException(
                        format("No offers to take in trade cycle %d.", currentTradeCycle));
            } else {
                log.warn("No offers, might be finished after {} cycle(s).", currentTradeCycle);
                return;
            }
        } else if (offers.size() == 1) {
            // TODO Wait for the next offer to sync up again.
            for (int i = 0; i < 10; i++) {
                try {
                    log.warn("There is only 1 available offer {} at start of cycle, will check again in 10 seconds. "
                            , offers.get(0).getId());
                    SECONDS.sleep(10);
                    offers.clear();
                    offers.addAll(botClient.getOffersSortedByDate(BTC));
                    if (offers.size() == 2) {
                        log.info("Now Bob can take offers:\n{}", formatOfferTable(offers, BSQ));
                        break;
                    }
                } catch (InterruptedException ignored) {
                    // empty
                }
            }
            throw new IllegalStateException(
                    format("No offers to take in trade cycle %d.", currentTradeCycle));
        } else {
            log.info("Bob can take offers:\n{}", formatOfferTable(offers, BSQ));
        }
    }

    protected void verifyHaveTrades() {
        var alicesBuyOfferId = getAlicesBuyBsqOfferId();
        var bobsBuyBsqTrade = botClient.getTrade(alicesBuyOfferId);
        if (bobsBuyBsqTrade != null) {
            log.info("Bob's Buy BSQ (Sell BTC) Trade: \n{}", TradeFormat.format(bobsBuyBsqTrade));
        } else {
            throw new IllegalStateException(format("Take BUY offer %s failed.", alicesBuyOfferId));
        }

        var alicesSellOfferId = getAlicesSellBsqOfferId();
        var bobsSellBsqTrade = botClient.getTrade(alicesSellOfferId);
        if (bobsSellBsqTrade != null) {
            log.info("Bob's Sell BSQ (Buy BTC) Trade: \n{}", TradeFormat.format(bobsSellBsqTrade));
        } else {
            throw new IllegalStateException(format("Take SELL offer %s failed.", alicesSellOfferId));
        }
    }

    protected void sendAliceBsqPaymentForHerBtc() {
        try {
            var tradeId = getAlicesBuyBsqOfferId();
            var trade = botClient.getTrade(tradeId);
            botClient.makeBsqPayment(trade);
            log.info("Payment sent, generate a btc block now.");
            SECONDS.sleep(15);
            botClient.sendConfirmPaymentStartedMessage(tradeId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected void verifyBsqReceivedFromAlice() {
        try {
            var tradeId = getAlicesSellBsqOfferId();
            var trade = botClient.getTrade(tradeId);

            // TODO refactor into BotClient
            var contract = trade.getContract();
            var bsqSats = trade.getOffer().getVolume();
            var receiveAmountAsString = formatBsqAmount(bsqSats);
            var address = contract.getIsBuyerMakerAndSellerTaker()
                    ? contract.getTakerPaymentAccountPayload().getAddress()
                    : contract.getMakerPaymentAccountPayload().getAddress();
            log.info("Bob verifying payment of {} BSQ was received to address {} for trade with id {}.",
                    receiveAmountAsString,
                    address,
                    tradeId);
            log.info("Give bot time to send payment. Generate a block while you wait");
            for (int i = 0; i < 5; i++) {
                SECONDS.sleep(30);
                boolean receivedPayment = botClient.verifyBsqSentToAddress(address, receiveAmountAsString);
                if (receivedPayment) {
                    log.warn("Payment received, sending payment rcvd confirmation.");
                    botClient.sendConfirmPaymentReceivedMessage(tradeId);
                    break;
                } else {
                    log.warn("Payment NOT received.");
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    protected void closeTrades() {
        for (OfferInfo offer : offers) {
            var tradeId = offer.getId();
            log.info("Sending keepfunds request for trade {}.", tradeId);
            botClient.sendKeepFundsMessage(tradeId);
        }
    }

    protected void printBalance() {
        log.info("Finished Trade Cycle\n{}", formatBalancesTbls(botClient.getBalance()));
    }

    protected String getAlicesBuyBsqOfferId() {
        // Buy BSQ = SELL BTC
        return offers.stream().filter(o -> o.getDirection().equals(SELL.name())).findFirst().get().getId();
    }

    protected String getAlicesSellBsqOfferId() {
        // Sell BSQ = BUY BTC
        return offers.stream().filter(o -> o.getDirection().equals(BUY.name())).findFirst().get().getId();
    }

    public static void main(String[] args) {
        BsqMarketMakerBotTest bob = new BsqMarketMakerBotTest();
        bob.runTradeCycles();
    }
}
