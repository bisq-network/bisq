package bisq.core.notifications;

import bisq.core.locale.Res;

import bisq.network.http.HttpClient;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NtfyNotificationServiceTest {
    @BeforeEach
    void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    void publishesTitleAndMessageWithBearerToken() throws Exception {
        HttpClient client = mock(HttpClient.class);
        NtfyNotificationService service = new NtfyNotificationService(client);
        service.publish("http://localhost:8080", "bisq-trades", "tk_test",
                new MobileMessage("Trade update", "Payment received", MobileMessageType.TRADE), true, false);
        verify(client).setBaseUrl("http://localhost:8080/");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client).post(body.capture(), eq("Authorization"), eq("Bearer tk_test"));
        var json = JsonParser.parseString(body.getValue()).getAsJsonObject();
        assertEquals("bisq-trades", json.get("topic").getAsString());
        assertEquals("Trade update", json.get("title").getAsString());
        assertEquals("Payment received", json.get("message").getAsString());
        assertFalse(body.getValue().contains("tk_test"));
    }

    @Test
    void supportsAnonymousPublishingAndServerPath() throws Exception {
        HttpClient client = mock(HttpClient.class);
        new NtfyNotificationService(client).publish("http://localhost:8080/ntfy/", "bisq", "",
                new MobileMessage("Bisq", "Test", MobileMessageType.TRADE), false, false);
        verify(client).setBaseUrl("http://localhost:8080/ntfy/");
        verify(client).post(anyString(), eq("Authorization"), isNull());
    }

    @Test
    void genericPayloadDoesNotIncludeTradeDetails() throws Exception {
        HttpClient client = mock(HttpClient.class);
        new NtfyNotificationService(client).publish("http://localhost:8080", "bisq", "tk_secret",
                new MobileMessage("Payment received", "Trade private-id-123", MobileMessageType.TRADE), false, false);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client).post(body.capture(), eq("Authorization"), eq("Bearer tk_secret"));
        var json = JsonParser.parseString(body.getValue()).getAsJsonObject();
        assertEquals("Bisq", json.get("title").getAsString());
        assertFalse(body.getValue().contains("Payment received"));
        assertFalse(body.getValue().contains("private-id-123"));
        assertFalse(body.getValue().contains("tk_secret"));
        verify(client).setSensitive(true);
    }

    @Test
    void requiresSecureRemoteTransportAndExplicitLanConsent() {
        for (String url : new String[]{"http://example.com", "http://8.8.8.8", "http://192.168.1.2", "http://[fd00::1]"})
            assertThrows(IllegalArgumentException.class, () ->
                    NtfyNotificationService.validate(url, "bisq", "tk_secret", false));
        assertThrows(IllegalArgumentException.class, () ->
                NtfyNotificationService.validate("http://example.com", "bisq", "", true));
        for (String url : new String[]{"https://example.com", "http://localhost", "http://127.0.0.1", "http://[::1]"})
            assertDoesNotThrow(() -> NtfyNotificationService.validate(url, "bisq", "", false));
        assertDoesNotThrow(() -> NtfyNotificationService.validate("http://192.168.1.2", "bisq", "", true));
        assertDoesNotThrow(() -> NtfyNotificationService.validate("http://[fd00::1]", "bisq", "", true));
        assertDoesNotThrow(() -> NtfyNotificationService.validate(
                "http://bisqpushv56wo32w2dv7xacvvcrnow6gud5vkvftwsgevczmspzvqgad.onion", "bisq", "", false));
    }

    @Test
    void rejectsInvalidConfigurationBeforeMakingRequests() {
        HttpClient client = mock(HttpClient.class);
        var service = new NtfyNotificationService(client);
        for (String url : new String[]{"", "file:///tmp/ntfy", "http://user:pass@localhost", "http://localhost/?a=b"})
            assertThrows(IllegalArgumentException.class, () ->
                    service.publish(url, "bisq", "", new MobileMessage("", "", MobileMessageType.TRADE), false, false));
        for (String topic : new String[]{"", "../other", "a/b", "app", "topic?query", "topic\nheader"})
            assertThrows(IllegalArgumentException.class, () ->
                    NtfyNotificationService.validate("http://localhost", topic, "", false));
        assertThrows(IllegalArgumentException.class, () ->
                NtfyNotificationService.validate("http://localhost", "bisq", "token\r\nHeader: value", false));
        verify(client).setSensitive(true);
        verifyNoMoreInteractions(client);
    }
}
