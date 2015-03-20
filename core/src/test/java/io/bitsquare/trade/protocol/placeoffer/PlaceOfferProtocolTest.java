/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.placeoffer;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.UserAgent;
import io.bitsquare.btc.WalletService;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.p2p.BootstrapState;
import io.bitsquare.p2p.Node;
import io.bitsquare.p2p.tomp2p.BootstrappedPeerBuilder;
import io.bitsquare.p2p.tomp2p.TomP2PNode;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferBookService;
import io.bitsquare.offer.tomp2p.TomP2POfferBookService;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.tomp2p.TomP2PMessageService;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Threading;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.Currency;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

/**
 * That test is ignored for automated testing as it needs custom setup.
 * <p/>
 * It uses RegTest mode of Bitcoin network and localhost TomP2P network.
 * <p/>
 * 1. Need a first run to get the wallet receiving address.
 * 2. Fund that from regtest Bitcoin Core client.
 * 3. Create a block on regtest Bitcoin Core (setgenerate true) to get the balance.
 * 4. Start BootstrapNodeMain at localhost with program args: --node.name localhost
 */
@Ignore
public class PlaceOfferProtocolTest {
    private static final Logger log = LoggerFactory.getLogger(PlaceOfferProtocolTest.class);

    private WalletService walletService;
    private MessageService messageService;
    private OfferBookService offerBookService;
    private final File dir = new File("./temp");
    private final static String OFFER_ID = "offerID";
    private Address address;
    private TomP2PNode tomP2PNode;
    private BootstrappedPeerBuilder bootstrappedPeerBuilder;

    @Before
    public void setup() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        dir.mkdirs();

        Persistence persistence = new Persistence(dir, "prefs");
        persistence.init();

        // messageService
        Node bootstrapNode = Node.at("localhost", "127.0.0.1");
        User user = new User();
        user.applyPersistedUser(null);
        bootstrappedPeerBuilder = new BootstrappedPeerBuilder(Node.DEFAULT_PORT, false, bootstrapNode, "<unspecified>");
        tomP2PNode = new TomP2PNode(bootstrappedPeerBuilder);
        messageService = new TomP2PMessageService(tomP2PNode);

        Observable<BootstrapState> messageObservable = tomP2PNode.bootstrap(user.getP2pSigKeyPair());
        messageObservable.publish();
        messageObservable.subscribe(
                state -> log.trace("state changed: " + state),
                error -> {
                    log.error(error.toString());
                },
                () -> {
                    log.trace("message completed");

                    offerBookService = new TomP2POfferBookService(tomP2PNode, user);
                    offerBookService.setExecutor(Threading.SAME_THREAD);
                }
        );
        bootstrappedPeerBuilder.start();

        // WalletService
        walletService = new WalletService(BitcoinNetwork.REGTEST,
                new FeePolicy(BitcoinNetwork.REGTEST),
                null,
                persistence,
                new UserAgent("", ""),
                dir,
                "Tests"
        );

        Observable<Object> walletServiceObservable = walletService.initialize(Threading.SAME_THREAD);
        walletServiceObservable.subscribe(
                next -> {
                    // log.trace("wallet next");
                },
                error -> {
                    log.trace("wallet error");
                },
                () -> {
                    log.trace("wallet complete");
                });

        Observable<?> allTasks = Observable.merge(messageObservable, walletServiceObservable);
        allTasks.subscribe(
                next -> {
                    //log.trace("next");
                },
                error -> log.error(error.toString()),
                () -> {
                    log.trace("wallet completed");
                    // 1. Use that address for funding the trading wallet
                    address = walletService.getAddressEntry(OFFER_ID).getAddress();
                    log.info("address for funding wallet = " + address.toString());//muoTvFHJmQwPKYoA8Fr7t87UCSfZM4fciG
                    log.info("Balance = " + walletService.getBalanceForAddress(address));
                    countDownLatch.countDown();
                });

        countDownLatch.await();
    }

    @After
    public void shutDown() throws IOException, InterruptedException {
        walletService.shutDown();
        bootstrappedPeerBuilder.shutDown();
    }

/*    @Test
    public void validateOfferTest() throws InterruptedException {
        try {
            Offer offer = getOffer();
            getCreateOfferCoordinator(offer).validateOffer();
            assertTrue(true);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void createOfferFeeTxTest() throws InterruptedException {
        try {
            Offer offer = getOffer();
            Transaction transaction = getCreateOfferCoordinator(offer).createOfferFeeTx();
            assertNotNull(transaction);
        } catch (Exception e) {
            log.info("address for funding wallet = " + address.toString());
            log.info("Balance = " + walletService.getBalanceForAddress(address));
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void broadcastCreateOfferFeeTxTest() throws InterruptedException {
        try {
            log.info("Balance pre = " + walletService.getBalanceForAddress(address));
            Offer offer = getOffer();
            TransactionResultHandler resultHandler = transaction -> assertNotNull(transaction);
            FaultHandler faultHandler = (message, throwable) -> {
                log.error(message);
                throwable.printStackTrace();
                fail(throwable.getMessage());
            };
            PlaceOfferProtocol placeOfferProtocol = getCreateOfferCoordinator(offer);
            Transaction transaction = placeOfferProtocol.createOfferFeeTx();
            placeOfferProtocol.broadcastCreateOfferFeeTx(transaction, resultHandler, faultHandler);
            log.info("Balance post = " + walletService.getBalanceForAddress(address));

        } catch (Exception e) {
            log.info("address for funding wallet = " + address.toString());
            log.info("Balance = " + walletService.getBalanceForAddress(address));
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void addOfferTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        try {
            Offer offer = getOffer();
            offerBookService.addListener(new OfferBookService.Listener() {
                @Override
                public void onOfferAdded(Offer offer1) {
                    assertEquals("Offer matching", offer.getId(), offer1.getId());
                    countDownLatch.countDown();
                }

                @Override
                public void onOffersReceived(List<Offer> offers) {
                }

                @Override
                public void onOfferRemoved(Offer offer) {
                }
            });

            TransactionResultHandler resultHandler = transaction -> {
                assertNotNull(transaction);
                countDownLatch.countDown();
            };
            FaultHandler faultHandler = (message, throwable) -> {
                log.error(message);
                throwable.printStackTrace();
                fail(throwable.getMessage());
                countDownLatch.countDown();
                countDownLatch.countDown();
            };
            PlaceOfferProtocol placeOfferProtocol = getPlaceOfferProtocol(offer, resultHandler, faultHandler);
            Transaction transaction = placeOfferProtocol.createOfferFeeTx();
            placeOfferProtocol.addOffer(transaction);
            countDownLatch.await();
            log.info("Finished");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
            countDownLatch.countDown();
            countDownLatch.countDown();
        }
    }
    
    @Test
    public void placeOfferTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        TransactionResultHandler resultHandler = transaction -> {
            assertNotNull(transaction);
            countDownLatch.countDown();
        };
        FaultHandler faultHandler = (message, throwable) -> {
            log.error(message);
            throwable.printStackTrace();
            fail(throwable.getMessage());
            countDownLatch.countDown();
            countDownLatch.countDown();
        };
        PlaceOfferProtocol placeOfferProtocol = getPlaceOfferProtocol(getOffer(), resultHandler, faultHandler);
        placeOfferProtocol.placeOffer();
        countDownLatch.await();
    }


    private PlaceOfferProtocol getCreateOfferCoordinator(Offer offer) throws InterruptedException {
        TransactionResultHandler resultHandler = transaction -> log.debug("result transaction=" + transaction.toString());
        FaultHandler faultHandler = (message, throwable) -> {
            log.error(message);
            throwable.printStackTrace();
            log.info("Balance = " + walletService.getBalanceForAddress(walletService.getAddressEntryByTradeID(OFFER_ID).getAddress()));
        };
        return getPlaceOfferProtocol(offer, resultHandler, faultHandler);
    }

    private PlaceOfferProtocol getPlaceOfferProtocol(Offer offer, TransactionResultHandler resultHandler, FaultHandler faultHandler) throws
            InterruptedException {
        return new PlaceOfferProtocol(offer,
                walletService,
                offerBookService,
                resultHandler,
                faultHandler);
    }*/

    private Offer getOffer() {
        return new Offer(OFFER_ID,
                DSAKeyUtil.generateKeyPair().getPublic(),
                Direction.BUY,
                100L,
                Coin.CENT,
                Coin.CENT,
                FiatAccountType.INTERNATIONAL,
                Currency.getInstance("EUR"),
                CountryUtil.getDefaultCountry(),
                "bankAccountUID",
                Arrays.asList(new Arbitrator()),
                Coin.CENT,
                Arrays.asList(CountryUtil.getDefaultCountry()),
                Arrays.asList(LanguageUtil.getDefaultLanguageLocale())
        );
    }
}
