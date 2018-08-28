package network.bisq.api;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;

public final class BtcAddressValidator extends InputValidator {

    @Override
    public ValidationResult validate(String input) {

        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid)
            return validateBtcAddress(input);
        else
            return result;
    }

    private ValidationResult validateBtcAddress(String input) {
        try {
            Address.fromBase58(BisqEnvironment.getParameters(), input);
            return new ValidationResult(true);
        } catch (AddressFormatException e) {
            return new InputValidator.ValidationResult(false, Res.get("validation.btc.invalidFormat"));
        }
    }
}
