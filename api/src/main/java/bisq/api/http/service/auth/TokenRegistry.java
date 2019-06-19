package bisq.api.http.service.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TokenRegistry {

    public static final long TTL = 30 * 60 * 1000;

    private final TimeProvider timeProvider;
    private final RandomStringGenerator randomStringGenerator;
    private Map<String, Long> tokens = new HashMap<>();

    public TokenRegistry() {
        this(() -> UUID.randomUUID().toString(), System::currentTimeMillis);
    }

    public TokenRegistry(RandomStringGenerator randomStringGenerator, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.randomStringGenerator = randomStringGenerator;
    }

    public String generateToken() {
        String uuid;
        do {
            uuid = randomStringGenerator.generateRandomString();
        } while (tokens.containsKey(uuid));
        tokens.put(uuid, timeProvider.getTime());
        removeTimeoutTokens();
        return uuid;
    }

    boolean isValidToken(String token) {
        Long createDate = tokens.get(token);
        if (createDate == null || isTimeout(createDate)) {
            tokens.remove(token);
            return false;
        } else {
            return true;
        }
    }

    private boolean isTimeout(Long createDate) {
        return timeProvider.getTime() - createDate > TTL;
    }

    private void removeTimeoutTokens() {
        tokens.values().removeIf(this::isTimeout);
    }

    public void clear() {
        tokens.clear();
    }

    public interface TimeProvider {
        Long getTime();
    }

    public interface RandomStringGenerator {
        String generateRandomString();
    }
}
