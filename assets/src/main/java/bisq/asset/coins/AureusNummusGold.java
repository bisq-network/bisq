package bisq.asset.coins;

import bisq.asset.Coin;
import bisq.asset.RegexAddressValidator;

public class AureusNummusGold extends Coin {

    private static final String NAME = "Aureus Nummus Gold";
    private static final String TICKER_SYMBOL = "ANG";

    public AureusNummusGold() {
        super(NAME, TICKER_SYMBOL, new AureusNummusAddressValidator());
    }

    public static class AureusNummusAddressValidator extends RegexAddressValidator {

        private static final String REGEX = "^0x[0-9a-fA-F]{40}$";

        public AureusNummusAddressValidator() {
            super(REGEX);
        }

    }

}
