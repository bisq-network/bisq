package bisq.core.offer;

import bisq.core.filter.FilterPolicyService;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.NetworkNotReadyException;
import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import java.nio.file.Files;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.offer.OfferMaker.btcUsdOffer;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OfferBookServiceTest {
    private P2PService p2PService;
    private OfferBookService service;

    @BeforeEach
    public void setUp() throws Exception {
        p2PService = mock(P2PService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        when(filterPolicyService.requireUpdateToNewVersionForTrading()).thenReturn(false);
        service = new OfferBookService(p2PService,
                priceFeedService,
                filterPolicyService,
                Files.createTempDirectory("storage").toFile(),
                false);
    }

    @Test
    public void testAddOfferReportsSynchronousNetworkNotReadyThroughErrorHandler() {
        when(p2PService.addProtectedStorageEntry(any())).thenThrow(new NetworkNotReadyException());

        AtomicBoolean resultHandled = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        service.addOffer(make(btcUsdOffer), () -> resultHandled.set(true), error::set);

        assertEquals("Add offer failed: the P2P network is not bootstrapped yet", error.get());
        assertFalse(resultHandled.get());
        verify(p2PService).addProtectedStorageEntry(any());
    }

    @Test
    public void testRemoveOfferReportsSynchronousNetworkNotReadyThroughErrorHandler() {
        when(p2PService.removeData(any())).thenThrow(new NetworkNotReadyException());

        AtomicBoolean resultHandled = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        service.removeOffer(make(btcUsdOffer).getOfferPayloadBase(), () -> resultHandled.set(true), error::set);

        assertEquals("Remove offer failed: the P2P network is not bootstrapped yet", error.get());
        assertFalse(resultHandled.get());
        verify(p2PService).removeData(any());
    }

    @Test
    public void testRemoveOfferToleratesMissingHandlersWhenNetworkNotReady() {
        when(p2PService.removeData(any())).thenThrow(new NetworkNotReadyException());

        assertDoesNotThrow(() ->
                service.removeOffer(make(btcUsdOffer).getOfferPayloadBase(),
                        (ResultHandler) null,
                        (ErrorMessageHandler) null));
        verify(p2PService).removeData(any());
    }

    @Test
    public void testRefreshTTLReportsSynchronousNetworkNotReadyThroughErrorHandler() {
        when(p2PService.refreshTTL(any())).thenThrow(new NetworkNotReadyException());

        AtomicBoolean resultHandled = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        service.refreshTTL(make(btcUsdOffer).getOfferPayloadBase(), () -> resultHandled.set(true), error::set);

        assertEquals("Refresh TTL failed: the P2P network is not bootstrapped yet.", error.get());
        assertFalse(resultHandled.get());
        verify(p2PService).refreshTTL(any());
    }
}
