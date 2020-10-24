package bisq.common.crypto;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class PubKeyRingProvider implements Provider<PubKeyRing> {

    private final PubKeyRing pubKeyRing;

    @Inject
    public PubKeyRingProvider(KeyRing keyRing) {
        pubKeyRing = keyRing.getPubKeyRing();
    }

    @Override
    public PubKeyRing get() {
        return pubKeyRing;
    }
}
