package bisq.core.notifications;

import bisq.core.locale.Res;

import bisq.network.http.HttpClient;
import bisq.network.http.UrlSafetyChecker;

import com.google.gson.JsonObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;
import java.net.URI;

@Singleton
public class NtfyNotificationService {
    private final HttpClient httpClient;

    @Inject
    public NtfyNotificationService(HttpClient httpClient) {
        this.httpClient = httpClient;
        httpClient.setSensitive(true);
    }

    public static void validate(String serverUrl, String topic, String accessToken, boolean allowInsecureLan) {
        URI uri;
        try {
            uri = UrlSafetyChecker.parseAndValidate(serverUrl);
        } catch (UrlSafetyChecker.InvalidUrlException e) {
            throw new IllegalArgumentException("Invalid ntfy server URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !UrlSafetyChecker.isOnion(uri) &&
                !UrlSafetyChecker.isLocal(uri, allowInsecureLan))
            throw new IllegalArgumentException("Use HTTPS, an onion service, loopback, or explicitly allow LAN HTTP");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Server URL must not contain a query or fragment");
        if (!topic.matches("[A-Za-z0-9_-]{1,64}") || topic.equals("app") || topic.equals("file") ||
                topic.equals("docs") || topic.equals("static") || topic.equals("v1"))
            throw new IllegalArgumentException("Invalid ntfy topic");
        if (accessToken.chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new IllegalArgumentException("Invalid ntfy access token");
    }

    public synchronized String publish(String serverUrl, String topic, String accessToken,
                                       MobileMessage message, boolean includeDetails,
                                       boolean allowInsecureLan) throws IOException {
        validate(serverUrl, topic, accessToken, allowInsecureLan);
        httpClient.setBaseUrl(serverUrl.endsWith("/") ? serverUrl : serverUrl + "/");
        JsonObject payload = new JsonObject();
        payload.addProperty("topic", topic);
        payload.addProperty("title", includeDetails ? message.getTitle() : "Bisq");
        payload.addProperty("message", includeDetails ? message.getMessage() :
                Res.get("account.notifications.ntfy.genericMessage"));
        return httpClient.post(payload.toString(), "Authorization",
                accessToken.isEmpty() ? null : "Bearer " + accessToken);
    }
}
