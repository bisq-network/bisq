package network.bisq.api.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TokenRegistry {

    private static final long TTL = 30 * 60 * 1000;

    private Map<String, Long> tokens = new HashMap<>();

    public String generateToken() {
        String uuid;
        do {
            uuid = UUID.randomUUID().toString();
        } while (tokens.containsKey(uuid));
        tokens.put(uuid, System.currentTimeMillis());
        removeTimeoutTokens();
        return uuid;
    }

    public boolean isValidToken(String token) {
        final Long createDate = tokens.get(token);
        if (null == createDate || isTimeout(createDate)) {
            tokens.remove(token);
            return false;
        } else {
            return true;
        }
    }

    private boolean isTimeout(Long createDate) {
        return System.currentTimeMillis() - createDate > TTL;
    }

    private void removeTimeoutTokens() {
        final Iterator<Long> iterator = tokens.values().iterator();
        while (iterator.hasNext()) {
            if (isTimeout(iterator.next()))
                iterator.remove();
        }
    }

    public void clear() {
        tokens.clear();
    }
}
