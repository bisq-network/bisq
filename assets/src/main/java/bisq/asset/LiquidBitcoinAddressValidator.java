package bisq.asset;

public class LiquidBitcoinAddressValidator extends RegexAddressValidator {
    static private final String REGEX = "^([a-km-zA-HJ-NP-Z1-9]{26,35}|[a-km-zA-HJ-NP-Z1-9]{80}|[a-z]{2,5}1[ac-hj-np-z02-9]{8,87}|[A-Z]{2,5}1[AC-HJ-NP-Z02-9]{8,87})$";

    public LiquidBitcoinAddressValidator() {
        super(REGEX, "validation.altcoin.liquidBitcoin.invalidAddress");
    }
}
