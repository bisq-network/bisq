package bisq.desktop.util.validation;

import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;

import java.util.List;
import java.util.Optional;

public class SepaIBANValidator extends IBANValidator {

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = super.validate(input);

        if (result.isValid) {
            List<Country> sepaCountries = CountryUtil.getAllSepaCountries();
            String ibanCountryCode = input.substring(0, 2).toUpperCase();
            Optional<Country> ibanCountry = sepaCountries
                    .stream()
                    .filter(c -> c.code.equals(ibanCountryCode))
                    .findFirst();

            if (!ibanCountry.isPresent()) {
                return new ValidationResult(false, Res.get("validation.iban.sepaNotSupported"));
            }
        }

        return result;
    }
}
