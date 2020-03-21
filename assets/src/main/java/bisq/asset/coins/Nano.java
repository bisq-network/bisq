package bisq.asset.coins;

import bisq.asset.Coin;
import bisq.asset.RegexAddressValidator;

public class Nano extends Coin {
    private static final String NANO_VALIDATION_REGEX = "^(nano|xrb)_[13]{1}[13456789abcdefghijkmnopqrstuwxyz]{59}$";

    public Nano() {
        super("Nano", "NANO", new RegexAddressValidator(NANO_VALIDATION_REGEX));
    }
}
