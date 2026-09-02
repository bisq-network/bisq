package bisq.core.offer;

import bisq.core.api.CoreContext;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.offer.availability.messages.OfferAvailabilityRequest;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.model.TradableList;
import bisq.core.user.Preferences;

import bisq.network.p2p.NetworkNotReadyException;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;

import java.nio.file.Files;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.offer.OfferMaker.btcUsdOffer;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OpenOfferManagerTest {
    private PersistenceManager<TradableList<OpenOffer>> persistenceManager;
    private CoreContext coreContext;

    @BeforeEach
    public void setUp() throws Exception {
        var corruptedStorageFileHandler = mock(CorruptedStorageFileHandler.class);
        var storageDir = Files.createTempDirectory("storage").toFile();
        persistenceManager = new PersistenceManager<>(storageDir, null, corruptedStorageFileHandler);
        coreContext = new CoreContext();
    }

    @AfterEach
    public void tearDown() {
        persistenceManager.shutdown();
    }

    @Test
    public void testStartEditOfferForActiveOffer() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        AtomicBoolean startEditOfferSuccessful = new AtomicBoolean(false);


        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerBookService).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        final OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));

        ResultHandler resultHandler = () -> startEditOfferSuccessful.set(true);

        manager.editOpenOfferStart(openOffer, resultHandler, null);

        verify(offerBookService, times(1)).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        assertTrue(startEditOfferSuccessful.get());

    }

    @Test
    public void testStartEditOfferForDeactivatedOffer() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        AtomicBoolean startEditOfferSuccessful = new AtomicBoolean(false);

        ResultHandler resultHandler = () -> startEditOfferSuccessful.set(true);

        final OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));
        openOffer.setState(OpenOffer.State.DEACTIVATED);

        manager.editOpenOfferStart(openOffer, resultHandler, null);
        assertTrue(startEditOfferSuccessful.get());

    }

    @Test
    public void testStartEditOfferForOfferThatIsCurrentlyEdited() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        AtomicBoolean startEditOfferSuccessful = new AtomicBoolean(false);

        ResultHandler resultHandler = () -> startEditOfferSuccessful.set(true);

        final OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));
        openOffer.setState(OpenOffer.State.DEACTIVATED);

        manager.editOpenOfferStart(openOffer, resultHandler, null);
        assertTrue(startEditOfferSuccessful.get());

        startEditOfferSuccessful.set(false);

        manager.editOpenOfferStart(openOffer, resultHandler, null);
        assertTrue(startEditOfferSuccessful.get());
    }

    @Test
    public void testBsqSwapOfferAvailabilityDoesNotRequireBisqV1Payload() throws Exception {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        Preferences preferences = mock(Preferences.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        when(preferences.getIgnoreTradersList()).thenReturn(Collections.emptyList());
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                priceFeedService,
                preferences,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        Offer offer = mock(Offer.class);
        when(offer.isBsqSwapOffer()).thenReturn(true);
        when(offer.getOfferPayload()).thenReturn(Optional.empty());
        OpenOffer openOffer = new OpenOffer(offer);
        NodeAddress peer = new NodeAddress("taker.onion", 9999);
        OfferAvailabilityRequest request = new OfferAvailabilityRequest("swap-offer-id",
                mock(PubKeyRing.class),
                1,
                false,
                0);

        OpenOfferManager.AvailabilityCheckResult result = manager.checkAvailabilityForAvailableOpenOffer(openOffer,
                request,
                peer);

        assertEquals(AvailabilityResult.AVAILABLE, result.availabilityResult);
        verify(offer, never()).getOfferPayload();
    }

    @Test
    public void testStartEditOfferClearsEditStateOnDeactivateFailure() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        final OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));

        // The offer book service reports a synchronous NetworkNotReadyException from P2PService
        // through the error handler (converted at that boundary). Without clearing the edit
        // state this would leave the offer stuck in edit mode until the next restart.
        doAnswer(invocation -> {
            ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("not bootstrapped");
            return null;
        }).when(offerBookService).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        AtomicBoolean firstEditErrorHandled = new AtomicBoolean(false);
        manager.editOpenOfferStart(openOffer, () -> {
        }, errorMessage -> firstEditErrorHandled.set(true));
        assertTrue(firstEditErrorHandled.get());

        // Let the second edit's deactivation succeed.
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerBookService).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        AtomicBoolean secondEditSuccessful = new AtomicBoolean(false);
        manager.editOpenOfferStart(openOffer, () -> secondEditSuccessful.set(true), null);
        assertTrue(secondEditSuccessful.get());
        // This verify is the assertion that actually distinguishes cleared from leaked state: if
        // the entry had leaked, the second editOpenOfferStart would short-circuit on the
        // "already in edit mode" check and never reach deactivateOffer, so it would be called
        // only once. Two invocations prove the edit state was cleared.
        verify(offerBookService, times(2)).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));
    }

    @Test
    public void testRemoveAllOpenOffersReportsAggregatedFailure() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                mock(BtcWalletService.class),
                null,
                null,
                offerBookService,
                mock(ClosedTradableManager.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );
        manager.getObservableList().add(new OpenOffer(make(btcUsdOffer)));

        // Every removal fails, e.g. before the P2P network is bootstrapped. The aggregated
        // outcome must be the error path: a caller about to replace or empty the wallet must
        // not proceed while the offer is still persisted locally and alive on the network.
        doAnswer(invocation -> {
            ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("not bootstrapped");
            return null;
        }).when(offerBookService).removeOffer(any(OfferPayloadBase.class), any(), any(ErrorMessageHandler.class));

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<String> errorMessage = new AtomicReference<>();
            manager.removeAllOpenOffers(() -> completed.set(true), errorMessage::set);
            ManualTimer.firePendingTimers();

            assertFalse(completed.get());
            assertNotNull(errorMessage.get());
            assertEquals(1, manager.getObservableList().size());
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

    @Test
    public void testRemoveAllOpenOffersReportsFailureWhenAnySingleRemovalFailed() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                mock(BtcWalletService.class),
                null,
                null,
                offerBookService,
                mock(ClosedTradableManager.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );
        OpenOffer failingOffer = new OpenOffer(make(btcUsdOffer));
        OpenOffer succeedingOffer = new OpenOffer(make(btcUsdOffer.but(with(OfferMaker.id, "5678"))));
        manager.getObservableList().add(failingOffer);
        manager.getObservableList().add(succeedingOffer);

        // One removal succeeds, one fails: a single failure must already yield the error
        // outcome, as the destructive callers need every offer gone.
        doAnswer(invocation -> {
            OfferPayloadBase payload = invocation.getArgument(0);
            if (payload.getId().equals(failingOffer.getId())) {
                ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("not bootstrapped");
            } else {
                ((ResultHandler) invocation.getArgument(1)).handleResult();
            }
            return null;
        }).when(offerBookService).removeOffer(any(OfferPayloadBase.class), any(), any(ErrorMessageHandler.class));

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<String> errorMessage = new AtomicReference<>();
            manager.removeAllOpenOffers(() -> completed.set(true), errorMessage::set);
            ManualTimer.firePendingTimers();

            assertFalse(completed.get());
            assertNotNull(errorMessage.get());
            assertTrue(errorMessage.get().contains(failingOffer.getId()));
            assertEquals(1, manager.getObservableList().size());
            assertEquals(failingOffer.getId(), manager.getObservableList().get(0).getId());
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

    @Test
    public void testRemoveAllOpenOffersCompletesOnceAllRemovalsSucceeded() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                mock(BtcWalletService.class),
                null,
                null,
                offerBookService,
                mock(ClosedTradableManager.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );
        manager.getObservableList().add(new OpenOffer(make(btcUsdOffer)));

        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerBookService).removeOffer(any(OfferPayloadBase.class), any(), any(ErrorMessageHandler.class));

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<String> errorMessage = new AtomicReference<>();
            manager.removeAllOpenOffers(() -> completed.set(true), errorMessage::set);
            ManualTimer.firePendingTimers();

            assertTrue(completed.get());
            assertNull(errorMessage.get());
            assertTrue(manager.getObservableList().isEmpty());
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

    @Test
    public void testRemoveAllOpenOffersWithoutOffersCompletes() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                mock(BtcWalletService.class),
                null,
                null,
                offerBookService,
                mock(ClosedTradableManager.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<String> errorMessage = new AtomicReference<>();
            manager.removeAllOpenOffers(() -> completed.set(true), errorMessage::set);
            ManualTimer.firePendingTimers();

            assertTrue(completed.get());
            assertNull(errorMessage.get());
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

    @Test
    public void testStartEditOfferDoesNotTreatResultHandlerExceptionAsDeactivationFailure() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        final OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));

        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerBookService).deactivateOffer(any(OfferPayload.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        // The success continuation may itself fail synchronously (e.g. the API edit flow
        // publishing the edited offer while the network is not ready). That failure must reach
        // the caller unchanged and must not be reported as a deactivation failure.
        AtomicBoolean errorHandled = new AtomicBoolean(false);
        assertThrows(NetworkNotReadyException.class, () ->
                manager.editOpenOfferStart(openOffer,
                        () -> {
                            throw new NetworkNotReadyException();
                        },
                        errorMessage -> errorHandled.set(true)));
        assertFalse(errorHandled.get());
    }

    @Test
    public void testMaybeRepublishOfferSkipsRetryWhileNotBootstrapped() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));
        manager.getObservableList().add(openOffer);

        when(offerBookService.isBootstrapped()).thenReturn(false);
        doAnswer(invocation -> {
            ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage(
                    "Add offer failed: the P2P network is not bootstrapped yet");
            return null;
        }).when(offerBookService).addOffer(any(Offer.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            manager.maybeRepublishOffer(openOffer);
            ManualTimer.firePendingTimers();

            // No retry timer must be armed while the network is not bootstrapped: a retry
            // cannot change the outcome, and firing it would republish every open offer and
            // log another warning, every 10 seconds until the bootstrap completes.
            verify(offerBookService, times(1)).addOffer(any(Offer.class),
                    any(ResultHandler.class),
                    any(ErrorMessageHandler.class));
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

    @Test
    public void testMaybeRepublishOfferRetriesWhenBootstrapped() {
        P2PService p2PService = mock(P2PService.class);
        OfferBookService offerBookService = mock(OfferBookService.class);
        when(p2PService.getPeerManager()).thenReturn(mock(PeerManager.class));
        OpenOfferManager manager = new OpenOfferManager(coreContext,
                null,
                null,
                null,
                p2PService,
                null,
                null,
                null,
                offerBookService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                persistenceManager,
                null
        );

        OpenOffer openOffer = new OpenOffer(make(btcUsdOffer));
        manager.getObservableList().add(openOffer);

        when(offerBookService.isBootstrapped()).thenReturn(true);
        doAnswer(invocation -> {
            ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("Add offer failed");
            return null;
        }).when(offerBookService).addOffer(any(Offer.class), any(ResultHandler.class), any(ErrorMessageHandler.class));

        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
        try {
            manager.maybeRepublishOffer(openOffer);
            ManualTimer.firePendingTimers();

            // A failure on a bootstrapped node can be transient, so the retry stays.
            verify(offerBookService, times(2)).addOffer(any(Offer.class),
                    any(ResultHandler.class),
                    any(ErrorMessageHandler.class));
        } finally {
            ManualTimer.clear();
            UserThread.setTimerClass(FrameRateTimer.class);
        }
    }

}
