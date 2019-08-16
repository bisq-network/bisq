package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PhoneNumberValidatorTest {
    private static PhoneNumberValidator validator;
    private static InputValidator.ValidationResult validationResult;

    @Before
    public void setup() {
        Res.setup(); // need i18n resources
    }

    @Test
    public void testInvalidCountry() {
        validator = new PhoneNumberValidator(null);
        validationResult = validator.validate("867 374-8299");
        assertFalse("A phone number should not be valid without an ISO country code",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.needCountryToValidatePhone", "867 374-8299"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testEmptyPhoneNumber() {
        validator = new PhoneNumberValidator("NL");
        validationResult = validator.validate("");
        assertFalse("Empty string should not be a valid phone number", validationResult.isValid);
        assertEquals(Res.get("validation.empty"), validationResult.errorMessage);

        validationResult = validator.validate(null);
        assertFalse("Null should not be a valid phone number", validationResult.isValid);
        assertEquals(Res.get("validation.empty"), validationResult.errorMessage);
    }

    @Test
    public void testValidateAndNormalizeAustriaPhoneNumbers() {
        validator = new PhoneNumberValidator("AT"); // AT country code is +43
        // 316 Graz
        validationResult = validator.validate("(0316) 214 4366");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+433162144366", validator.getNormalizedPhoneNumber());

        // 1 Vienna
        validationResult = validator.validate("+43 1 214-3512");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+4312143512", validator.getNormalizedPhoneNumber());

        // 1 Vienna cell
        validationResult = validator.validate("+43 1 650 454 0987");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+4316504540987", validator.getNormalizedPhoneNumber());

        // 676 T-Mobile
        validationResult = validator.validate("0676 2241647");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+436762241647", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidAustriaNumber() {
        validator = new PhoneNumberValidator("AT"); // AT country code is +43
        validationResult = validator.validate("+43 1 214");
        assertFalse("+43 1 214 should not be a valid number in AT", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+43 1 214", "AT"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeBrazilPhoneNumbers() {
        validator = new PhoneNumberValidator("BR"); // BR country code is +55
        // 11 Sao Paulo (cell # prefixed with 9)
        validationResult = validator.validate("11 9 8353 3654");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+5511983533654", validator.getNormalizedPhoneNumber());

        // 85 Fortaleza landline using long-distance carrier OI (31)
        validationResult = validator.validate("031 85 4433 8432");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+558544338432", validator.getNormalizedPhoneNumber());

        // 21 Rio de Janeiro
        validationResult = validator.validate("+55 21 55555555");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+552155555555", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidBrazilNumber() {
        validator = new PhoneNumberValidator("BR"); // BR country code is +55
        validationResult = validator.validate("+55 1 3467 2298");
        assertFalse("+55 1 3467 2298 should not be a valid number in BR", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+55 1 3467 2298", "BR"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeCanadaPhoneNumbers() {
        validator = new PhoneNumberValidator("CA"); // CA country code is +1
        validationResult = validator.validate("867 374-8299");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+18673748299", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("+1 306 3748299");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+13063748299", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("7093748299");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+17093748299", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidCanadaNumber() {
        validator = new PhoneNumberValidator("CA"); // CA country code is +1
        validationResult = validator.validate("+1 709 374-829");
        assertFalse("+1 709 374-829 should not be a valid number in CA", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+1 709 374-829", "CA"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeChinaPhoneNumbers() {
        validator = new PhoneNumberValidator("CN"); // CN country code is +86
        // In major cities, landline-numbers consist of a two-digit area code followed
        // by an eight-digit inner-number. In other places, landline-numbers consist of
        // a three-digit area code followed by a seven- or eight-digit inner-number.
        // The numbers of mobile phones consist of eleven digits.

        // 10 Beijing
        validationResult = validator.validate("10 4534 8214");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+861045348214", validator.getNormalizedPhoneNumber());

        //  21 Shanghai
        validationResult = validator.validate("+86 21 3422-5814");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+862134225814", validator.getNormalizedPhoneNumber());

        //  373 Xinxiang
        validationResult = validator.validate("86 373 1295367");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+863731295367", validator.getNormalizedPhoneNumber());

        // 18x  Mobile phone numbers have 11 digits in the format 1xx-xxxx-xxxx,
        // in which the first three digits (e.g. 13x, 14x,15x,17x and 18x) designate
        // the mobile phone service provider.
        validationResult = validator.validate("180-4353-7877"); // no country code prefix
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+8618043537877", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidChinaNumber() {
        validator = new PhoneNumberValidator("CN"); // CN country code is +86
        validationResult = validator.validate("+86 10 453 8214");
        assertFalse("+86 10 453 8214 should not be a valid number in CN", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+86 10 453 8214", "CN"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }


    @Test
    public void testValidateAndNormalizeFrancePhoneNumbers() {
        validator = new PhoneNumberValidator("FR"); // FR country code is +33
        validationResult = validator.validate("0612345678");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+33612345678", validator.getNormalizedPhoneNumber());

        // TODO more FR phone # tests
    }

    @Test
    public void testInvalidFranceNumber() {
        validator = new PhoneNumberValidator("FR"); // FR country code is +33
        validationResult = validator.validate("+3312345678");
        assertFalse("+3312345678 should not be a valid number in FR", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+3312345678", "FR"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeJapanPhoneNumbers() {
        validator = new PhoneNumberValidator("JP"); // JP country code is +81
        // 11 Sapporo  011-XXX-XXXX
        validationResult = validator.validate("11 367-2345");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+81113672345", validator.getNormalizedPhoneNumber());

        //  0476 Narita,  0476-XX-XXXX
        validationResult = validator.validate("0476 87 2055");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+81476872055", validator.getNormalizedPhoneNumber());

        //  03 Tokyo  03-XXXX-XXXX
        validationResult = validator.validate("03-3129-5367");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+81331295367", validator.getNormalizedPhoneNumber());

        // 090 Cell number (area code)
        validationResult = validator.validate("(090) 3129-5367");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+819031295367", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidJapanNumber() {
        validator = new PhoneNumberValidator("JP"); // JP country code is +81
        validationResult = validator.validate("3329-5167");
        assertFalse("3329-5167 should not be a valid number in JP", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "3329-5167", "JP"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidPortugalNumber() {
        validator = new PhoneNumberValidator("PT"); // PT country code is +351
        validationResult = validator.validate("1234");
        assertFalse("1234 should not be a valid number in PT",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "1234", "PT"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeRussiaPhoneNumbers() {
        validator = new PhoneNumberValidator("RU"); // RU country code is +7
        // Moscow has four area codes: 495, 496, 498 and 499
        validationResult = validator.validate("499 345-99-36");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+74993459936", validator.getNormalizedPhoneNumber());

        // 812 St. Peter
        validationResult = validator.validate("+7 8 812 567-22-11");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+78125672211", validator.getNormalizedPhoneNumber());

        // 395 Irkutsk Oblast Call from  outside  RU
        validationResult = validator.validate("+7 395 232-88-35");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+73952328835", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidRussiaNumber() {
        validator = new PhoneNumberValidator("RU"); // RU country code is +7
        validationResult = validator.validate("399 345-99-36"); // 399 bad area code
        assertFalse("399 345-99-36 should not be a valid number in RU", validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "399 345-99-36", "RU"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeUKPhoneNumbers() {
        validator = new PhoneNumberValidator("GB"); // GB country code is +44
        // (020) London
        validationResult = validator.validate("020 7946 0230");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+442079460230", validator.getNormalizedPhoneNumber());


        validationResult = validator.validate("+ 44 20 79 46 00 93");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+442079460093", validator.getNormalizedPhoneNumber());

        // (0114) Sheffield
        validationResult = validator.validate("(0114) 436 8888");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+441144368888", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("+44 11 44368888");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+441144368888", validator.getNormalizedPhoneNumber());

        // (0131) xxx xxxx Edinburgh
        validationResult = validator.validate("(0131) 267 1111");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+441312671111", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("131 267-1111");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+441312671111", validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidUKNumber() {
        validator = new PhoneNumberValidator("UK"); // UK country code is +44
        validationResult = validator.validate("+44 111"); // need's country dialing code
        assertFalse("+44 111 should not be a valid number in UK",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+44 111", "UK"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testValidateAndNormalizeUSPhoneNumbers() {
        validator = new PhoneNumberValidator("US"); // US country code is +1
        validationResult = validator.validate("(800) 253 0000");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+18002530000", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("+ 1 800 253-0000");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+18002530000", validator.getNormalizedPhoneNumber());

        validationResult = validator.validate("8002530000");
        assertTrue(validator.getValidationResult().isValid);
        assertEquals("+18002530000", validator.getNormalizedPhoneNumber());
    }


    @Test
    public void testInvalidUSNumbers() {
        validator = new PhoneNumberValidator("US"); // US country code is +1
        validationResult = validator.validate("+1 212 333"); // Not enough digits in #
        assertFalse("+1 212 333 should not be a valid number in US",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.invalidPhoneInCountry", "+1 212 333", "US"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testInvalidCountryCodeError() {
        // Causes INVALID_COUNTRY_CODE error during  parsing
        validator = new PhoneNumberValidator("UK"); // UK country code is +44
        validationResult = validator.validate("(0132) 267 1111"); // need's country dialing code
        assertFalse("(0132) 267 1111 should not be a valid number in UK",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.missingCountryDialingCode"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testNotANumberError() {
        // Causes NOT_A_NUMBER error during  parsing
        validator = new PhoneNumberValidator("BR");
        validationResult = validator.validate("11 GR8"); // Not enough digits in #
        assertFalse("11 GR8 should not be a valid number in BR",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.notANumber", "11 GR8"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Ignore // Cannot force a TOO_SHORT_AFTER_IDD error -- dead code or bug in library?
    @Test
    public void testTooShortAfterIddError() {
        // Causes TOO_SHORT_AFTER_IDD error during  parsing
        validator = new PhoneNumberValidator("RU");
        validationResult = validator.validate("+7 11"); // Not enough digits in #
        assertFalse("+7 11 should not be a valid number in RU",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.notANumber", "+7 11"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Ignore // Cannot force a TOO_SHORT_NSN error -- dead code or bug in library?
    @Test
    public void testTooShortNsnError() {
        // Causes TOO_SHORT_NSN error during  parsing
        validator = new PhoneNumberValidator("BR");
        validationResult = validator.validate("+55 "); // Not enough digits in #
        assertFalse("+55  should not be a valid number in BR",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.notANumber", "+55 "),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }

    @Test
    public void testTooLongError() {
        // Causes TOO_LONG error during  parsing
        validator = new PhoneNumberValidator("JP");
        validationResult = validator.validate("+81 90 31 44 29 53 61 11 99 33"); // too many digits
        assertFalse("+81 90 31 44 29 53 61 11 99 33  should not be a valid number in JP",
                validator.getValidationResult().isValid);
        assertEquals(Res.get("validation.phone.numberTooLong", "+81 90 31 44 29 53 61 11 99 33"),
                validator.getValidationResult().errorMessage);
        assertNull(validator.getNormalizedPhoneNumber());
    }
}
