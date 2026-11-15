package bisq.core.notifications;

import bisq.core.user.Preferences;

import bisq.network.http.HttpClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileNotificationServiceTest {
    private Preferences preferences;
    private NtfyNotificationService ntfy;
    private HttpClient relay;
    private MobileNotificationService service;
    private CountDownLatch published;

    @BeforeEach
    void setUp() throws Exception {
        preferences = mock(Preferences.class);
        ntfy = mock(NtfyNotificationService.class);
        published = new CountDownLatch(1);
        doAnswer(invocation -> {
            published.countDown();
            return "success";
        }).when(ntfy).publish(anyString(), anyString(), anyString(), any(), anyBoolean(), anyBoolean());
        relay = mock(HttpClient.class);
        service = new MobileNotificationService(preferences, mock(MobileMessageEncryption.class),
                mock(MobileNotificationValidator.class), new MobileModel(), relay, ntfy, false);
        when(preferences.isUseNtfyNotifications()).thenReturn(true);
        when(preferences.getNtfyServerUrl()).thenReturn("http://localhost:8080");
        when(preferences.getNtfyTopic()).thenReturn("bisq");
        when(preferences.getNtfyAccessToken()).thenReturn("");
        service.getUseTradeNotificationsProperty().set(true);
    }

    @Test
    void sendsWithoutPhonePairing() throws Exception {
        MobileMessage message = new MobileMessage("Trade", "Payment received", MobileMessageType.TRADE);
        assertTrue(service.sendMessage(message));
        assertTrue(published.await(5, TimeUnit.SECONDS));
        verify(ntfy).publish("http://localhost:8080", "bisq", "", message, false, false);
        verify(relay, never()).get(anyString(), anyString(), anyString());
        assertTrue(service.isNotificationSetupComplete());
    }

    @Test
    void forwardsExplicitPrivacySettingsToTransport() throws Exception {
        when(preferences.isNtfyIncludeDetails()).thenReturn(true);
        when(preferences.isNtfyAllowInsecureLan()).thenReturn(true);
        when(preferences.getNtfyServerUrl()).thenReturn("http://192.168.1.2:8080");
        MobileMessage message = new MobileMessage("Trade", "Test", MobileMessageType.TRADE);
        assertTrue(service.sendMessage(message));
        assertTrue(published.await(5, TimeUnit.SECONDS));
        verify(ntfy).publish("http://192.168.1.2:8080", "bisq", "", message, true, true);
    }

    @Test
    void respectsSharedEventToggles() throws Exception {
        service.getUseTradeNotificationsProperty().set(false);
        for (MobileMessageType type : new MobileMessageType[]{MobileMessageType.TRADE, MobileMessageType.DISPUTE,
                MobileMessageType.OFFER, MobileMessageType.MARKET, MobileMessageType.PRICE})
            assertFalse(service.sendMessage(new MobileMessage("", "", type)));
        verifyNoInteractions(ntfy);
    }

    @Test
    void doesNotForwardMobileControlMessages() throws Exception {
        assertFalse(service.sendMessage(new MobileMessage("", "", MobileMessageType.SETUP_CONFIRMATION)));
        assertFalse(service.sendMessage(new MobileMessage("", "", MobileMessageType.ERASE)));
        verifyNoInteractions(ntfy);
    }

    @Test
    void unpairedMobileBackendDoesNotUseNtfy() throws Exception {
        when(preferences.isUseNtfyNotifications()).thenReturn(false);
        assertFalse(service.sendMessage(new MobileMessage("Trade", "Test", MobileMessageType.TRADE)));
        verifyNoInteractions(ntfy);
        assertFalse(service.isNotificationSetupComplete());
    }

    @Test
    void invalidNtfyConfigurationIsNotReady() {
        when(preferences.getNtfyTopic()).thenReturn("");
        assertFalse(service.isNotificationSetupComplete());
    }
}
