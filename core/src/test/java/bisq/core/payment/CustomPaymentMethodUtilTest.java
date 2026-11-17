/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment;

import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;

import java.text.Normalizer;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static bisq.core.payment.CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH;
import static bisq.core.payment.CustomPaymentMethodUtil.MAX_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomPaymentMethodUtilTest {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Name
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void nameIsValidUpToTheMaxLength() {
        assertEquals(20, MAX_NAME_LENGTH);
        assertTrue(CustomPaymentMethodUtil.isValidName("M-Pesa"));
        assertTrue(CustomPaymentMethodUtil.isValidName("a"));
        assertTrue(CustomPaymentMethodUtil.isValidName("a".repeat(MAX_NAME_LENGTH)));
        assertFalse(CustomPaymentMethodUtil.isValidName("a".repeat(MAX_NAME_LENGTH + 1)));
    }

    @Test
    public void nameNeedsALetterOrDigit() {
        assertFalse(CustomPaymentMethodUtil.isValidName(null));
        assertFalse(CustomPaymentMethodUtil.isValidName(""));
        assertFalse(CustomPaymentMethodUtil.isValidName("   "));
        assertFalse(CustomPaymentMethodUtil.isValidName("-+()"));
        // Marks alone are no name. They are compared, so they would not give an empty name to compare.
        assertFalse(CustomPaymentMethodUtil.isValidName("\u0301\u0302"));
        assertTrue(CustomPaymentMethodUtil.isValidName("7"));
        // A circled digit is a digit once it is normalized
        assertTrue(CustomPaymentMethodUtil.isValidName("\u2460"));
        // Symbols are not compared, also those which the normalization turns into letters
        assertFalse(CustomPaymentMethodUtil.isValidName("\u2122"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u338F"));
    }

    @Test
    public void nameInAnotherScriptIsValid() {
        assertTrue(CustomPaymentMethodUtil.isValidName("支付宝香港"));
        assertTrue(CustomPaymentMethodUtil.isValidName("बैंक ट्रांसफर"));
        assertTrue(CustomPaymentMethodUtil.isValidName("Überweisung Österr."));
        // Persian with a zero width non-joiner
        assertTrue(CustomPaymentMethodUtil.isValidName("کارت\u200Cبه\u200Cکارت"));
    }

    // Such a name would be shown as an empty name, or it would differ from a name which looks the same
    @Test
    public void nameWithCharacterWhichIsNotShownIsNotValid() {
        // Hangul fillers are letters which are not shown
        assertFalse(CustomPaymentMethodUtil.isValidName("\u3164"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u3164Pal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u115FPal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u1160Pal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\uFFA0Pal"));
        // The inherent vowels of Khmer and the variation selectors of Mongolian, of emoji and of ideographs
        assertFalse(CustomPaymentMethodUtil.isValidName("\u1780\u17B4"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u1780\u17B5"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u1820\u180B"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u1820\u180F"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\uFE00"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\uFE0E"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u6F22\uDB40\uDD00"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u6F22\uDB40\uDDEF"));
        // A variation selector, as it follows an emoji, and the combining grapheme joiner are marks
        assertFalse(CustomPaymentMethodUtil.isValidName("M-Pesa \u2714\uFE0F"));
        assertFalse(CustomPaymentMethodUtil.isValidName("SEPA\uFE0F"));
        assertFalse(CustomPaymentMethodUtil.isValidName("M\u034FPesa"));
        assertTrue(CustomPaymentMethodUtil.isValidName("M-Pesa \u2714"));
    }

    // A later Java runtime can know such a character as a letter and would then compare the name differently
    @Test
    public void nameWithCharacterWhichTheRuntimeDoesNotKnowIsNotValid() {
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\uFFFF"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\uFDD0"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\uFFFF"));
        // An ideograph of Unicode 15.1, which Java 21 does not know
        assertFalse(CustomPaymentMethodUtil.isValidName("\uD87A\uDFF0"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\uD87A\uDFF0"));
    }

    // Many marks above or below one character are drawn over the lines next to it
    @Test
    public void nameWithManyMarksOnOneCharacterIsNotValid() {
        assertEquals(4, CustomPaymentMethodUtil.MAX_MARKS_IN_A_ROW);
        assertTrue(CustomPaymentMethodUtil.isValidName("x" + "\u0301".repeat(4)));
        assertFalse(CustomPaymentMethodUtil.isValidName("x" + "\u0301".repeat(5)));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pax" + "\u0301\u0302\u0303\u0304\u0305" + "Pal"));
        // A letter with an accent counts like the letter and the accent
        assertTrue(CustomPaymentMethodUtil.isValidName("\u00FD" + "\u0302\u0303\u0304"));
        assertFalse(CustomPaymentMethodUtil.isValidName("\u00FD" + "\u0302\u0303\u0304\u0305"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay" + "\u0301\u0302\u0303\u0304\u0305" + "Pal"));
        // A joiner between the marks is not shown, and the marks after it are drawn on the same character
        assertFalse(CustomPaymentMethodUtil.isValidName("x" + "\u0301".repeat(4) + "\u200D" + "\u0301"));
        assertFalse(CustomPaymentMethodUtil.isValidName("x" + "\u0301".repeat(3) + "\u200C" + "\u0301".repeat(2)));
        // Enclosing marks are drawn around the character, one around the other
        assertTrue(CustomPaymentMethodUtil.isValidName("a" + "\u20DD".repeat(4)));
        assertFalse(CustomPaymentMethodUtil.isValidName("a" + "\u20DD".repeat(5)));
    }

    // Words of some scripts have several marks on one character. A mark with a width of its own is not counted.
    @Test
    public void wordsWithSeveralMarksOnOneCharacterAreValid() {
        // Thai, Tibetan, and Hebrew with vowel points and an accent
        assertTrue(CustomPaymentMethodUtil.isValidName("\u0E17\u0E35\u0E48"));
        assertTrue(CustomPaymentMethodUtil.isValidName("\u0F56\u0F66\u0F92\u0FB2\u0F74\u0F56\u0F66"));
        assertTrue(CustomPaymentMethodUtil.isValidName("\u05E9\u05C1\u05BC\u05B8\u0591"));
        // A word of Burmese with five marks in a row, three of them with a width of their own
        assertTrue(CustomPaymentMethodUtil.isValidName("\u101C\u103B\u103E\u1031\u102C\u1037"));
        // Vietnamese has five marks in this name, at most two on one character
        assertTrue(CustomPaymentMethodUtil.isValidName("Thanh to\u00E1n \u0111i\u1EC7n t\u1EED"));
    }

    // A name is shown in one line of the offer book and in labels
    @Test
    public void nameWithLineBreakOrControlCharacterIsNotValid() {
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\nPal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\rPal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\tPal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u0000Pal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u001BPal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u0085Pal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u2028Pal"));
        assertFalse(CustomPaymentMethodUtil.isValidName("Pay\u2029Pal"));
    }

    // Such characters change the order in which a text is shown, or are not shown at all
    @Test
    public void nameWithFormatCharacterIsNotValid() {
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\u202Edef"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\u2066def"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\u200Edef"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\u200Bdef"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\uFEFFdef"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\uE000def"));
        assertFalse(CustomPaymentMethodUtil.isValidName("abc\uD800def"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Account data
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void accountDataIsValidUpToTheMaxLength() {
        assertEquals(1000, MAX_ACCOUNT_DATA_LENGTH);
        assertTrue(CustomPaymentMethodUtil.isValidAccountData("Name: Jane Doe\nPhone: +254 700 000000\n\tor by email"));
        assertTrue(CustomPaymentMethodUtil.isValidAccountData("a".repeat(MAX_ACCOUNT_DATA_LENGTH)));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("a".repeat(MAX_ACCOUNT_DATA_LENGTH + 1)));
    }

    @Test
    public void accountDataNeedsALetterOrNumber() {
        assertFalse(CustomPaymentMethodUtil.isValidAccountData(null));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData(""));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData(" \n\t "));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("---"));
        // A no-break space and a blank Braille pattern are no white space for Java, and a filler is not shown
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("\u00A0"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("\u2800\u2800"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("\u3164"));
        assertTrue(CustomPaymentMethodUtil.isValidAccountData("7"));
    }

    @Test
    public void accountDataWithControlOrFormatCharacterIsNotValid() {
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\u0000def"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\u001Bdef"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\u202Edef"));
        assertFalse(CustomPaymentMethodUtil.isValidAccountData("abc\u2028def"));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Clean
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void cleanNameRemovesCharactersWhichAreNotAllowedAndSpacesAround() {
        assertEquals("M-Pesa", CustomPaymentMethodUtil.cleanName("M-Pesa"));
        assertEquals("M-Pesa", CustomPaymentMethodUtil.cleanName("  M-Pesa \t"));
        // A zero width space, a soft hyphen and a line break
        assertEquals("M-Pesa", CustomPaymentMethodUtil.cleanName("\u200BM-\u00ADPe\nsa"));
        assertEquals("abcdef", CustomPaymentMethodUtil.cleanName("abc\u202Edef"));
        assertEquals("Orange Money", CustomPaymentMethodUtil.cleanName("Orange Money"));
        assertEquals("", CustomPaymentMethodUtil.cleanName(" \n "));
        assertTrue(CustomPaymentMethodUtil.isValidName(CustomPaymentMethodUtil.cleanName("\uFEFFM-Pesa\r\n")));
    }

    @Test
    public void cleanAccountDataKeepsLineBreaks() {
        assertEquals("Jane Doe\n+254 700 000000",
                CustomPaymentMethodUtil.cleanAccountData("\uFEFF Jane Doe\n+254 700\u200B 000000\n\n"));
        assertEquals("a\tb\r\nc", CustomPaymentMethodUtil.cleanAccountData("a\tb\r\nc"));
        assertEquals("ab", CustomPaymentMethodUtil.cleanAccountData("a\u0000b"));
        assertTrue(CustomPaymentMethodUtil.isValidAccountData(
                CustomPaymentMethodUtil.cleanAccountData("abc\u202Edef\u0007")));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Same name
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void caseSpacesAndPunctuationMakeNoDifference() {
        assertTrue(CustomPaymentMethodUtil.isSameName("M-Pesa", "M-Pesa"));
        assertTrue(CustomPaymentMethodUtil.isSameName("M-Pesa", "m pesa"));
        assertTrue(CustomPaymentMethodUtil.isSameName("M-Pesa", " MPESA "));
        assertTrue(CustomPaymentMethodUtil.isSameName("Orange Money (CI)", "orange money ci"));
        // Full width letters are the same letters
        assertTrue(CustomPaymentMethodUtil.isSameName("ＬＩＮＥ Pay", "Line Pay"));
        // An apostrophe, a typographic apostrophe and the spacing acute accent which some keyboards give for it
        assertTrue(CustomPaymentMethodUtil.isSameName("Joe's Pay", "Joe\u2019s Pay"));
        assertTrue(CustomPaymentMethodUtil.isSameName("Joe's Pay", "Joe\u00B4s Pay"));
        // The trade mark sign is a symbol, as the registered sign is
        assertTrue(CustomPaymentMethodUtil.isSameName("M-Pesa\u2122", "M-Pesa"));
        assertTrue(CustomPaymentMethodUtil.isSameName("M-Pesa\u00AE", "M-Pesa"));
    }

    // Two traders type the name of one payment method in different case
    @Test
    public void caseOfOtherScriptsMakesNoDifference() {
        assertTrue(CustomPaymentMethodUtil.isSameName("Straße Pay", "STRASSE PAY"));
        assertTrue(CustomPaymentMethodUtil.isSameName("İninal", "ininal"));
        assertTrue(CustomPaymentMethodUtil.isSameName("İNİNAL", "ininal"));
        // The lower case of the last letter of a Greek word is another letter than inside a word
        assertTrue(CustomPaymentMethodUtil.isSameName("ΟΔΟΣ PAY", "ΟΔΟΣPAY"));
        assertTrue(CustomPaymentMethodUtil.isSameName("ΟΔΟΣ PAY", "οδος pay"));
        assertTrue(CustomPaymentMethodUtil.isSameName("ΟΔΟΣ PAY", "οδοσ pay"));
        // The capital sharp s is its own upper case
        assertTrue(CustomPaymentMethodUtil.isSameName("STRA\u1E9EE PAY", "strasse pay"));
        // The upper case and the lower case of an iota with dialytika and tonos take it apart in different ways
        assertTrue(CustomPaymentMethodUtil.isSameName("\u0390", "\u03AA\u0301"));
    }

    // A check mark with and without the selector for its emoji form is the same once the name is cleaned
    @Test
    public void cleanedNameIsTheSameNameAsTheNameWithoutCharactersWhichAreNotShown() {
        assertEquals("M-Pesa \u2714", CustomPaymentMethodUtil.cleanName("M-Pesa \u2714\uFE0F"));
        assertTrue(CustomPaymentMethodUtil.isSameName(CustomPaymentMethodUtil.cleanName("M-Pesa \u2714\uFE0F"),
                "M-Pesa"));
        assertEquals("PayPal", CustomPaymentMethodUtil.cleanName("Pay\u3164\u034FPal\uFFFF"));
    }

    // The comparison is a rule between peers, so it is checked for every character which the Java runtime knows.
    // unicodeTablesOfTheRuntimeAreThoseOfJava21 fails on a runtime with other Unicode tables.
    @Test
    public void caseOfNoCharacterMakesADifference() {
        List<String> differences = new ArrayList<>();
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            int type = Character.getType(codePoint);
            if (type == Character.UNASSIGNED || type == Character.SURROGATE ||
                    type == Character.PRIVATE_USE || type == Character.CONTROL) {
                continue;
            }
            String character = Character.toString(codePoint);
            String comparable = CustomPaymentMethodUtil.toComparableName(character);
            if (!comparable.equals(CustomPaymentMethodUtil.toComparableName(comparable))) {
                differences.add(String.format("U+%04X compared again", codePoint));
            }
            for (String variant : List.of(character.toUpperCase(Locale.ROOT),
                    character.toLowerCase(Locale.ROOT),
                    Character.toString(Character.toUpperCase(codePoint)),
                    Character.toString(Character.toLowerCase(codePoint)),
                    Character.toString(Character.toTitleCase(codePoint)))) {
                if (!comparable.equals(CustomPaymentMethodUtil.toComparableName(variant))) {
                    differences.add(String.format("U+%04X and %s", codePoint, toCodePoints(variant)));
                }
            }
        }

        // The lower case of the text for a capital I with dot is an i with a combining dot above, which is
        // another name than the i of the lower case of the character
        assertEquals(List.of("U+0130 and U+0069 U+0307"), differences);
    }

    // The rules for the texts are rules between peers, and they follow the Unicode tables of the Java runtime.
    // Java 21 has the tables of Unicode 15.0. On a runtime with other tables this test fails: peers on both
    // runtimes can then disagree about a name or about account data. Update the expected value only together
    // with a plan for such peers.
    @Test
    public void unicodeTablesOfTheRuntimeAreThoseOfJava21() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            String character = Character.toString(codePoint);
            String tables = Character.getType(codePoint) + " " +
                    Character.isWhitespace(codePoint) + " " +
                    Character.toLowerCase(codePoint) + " " +
                    Character.toUpperCase(codePoint) + " " +
                    Character.toTitleCase(codePoint) + " " +
                    Character.digit(codePoint, 10) + " " +
                    character.toLowerCase(Locale.ROOT) + " " +
                    character.toUpperCase(Locale.ROOT) + " " +
                    Normalizer.normalize(character, Normalizer.Form.NFKC) + " " +
                    Normalizer.normalize(character, Normalizer.Form.NFKD) + "\n";
            digest.update(tables.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("48159dd38ad633ab80321c63ccf06fd975e2001856050d9977dc47718aa5da6c",
                HexFormat.of().formatHex(digest.digest()));
    }

    private static String toCodePoints(String text) {
        return text.codePoints()
                .mapToObj(codePoint -> String.format("U+%04X", codePoint))
                .collect(Collectors.joining(" "));
    }

    // A trader types the digits of the own script, another trader those of the Latin script
    @Test
    public void digitsOfAllScriptsAreTheDigitsOfTheirValue() {
        assertTrue(CustomPaymentMethodUtil.isSameName("Bank 1", "Bank \u06F1"));
        assertTrue(CustomPaymentMethodUtil.isSameName("Bank 1", "Bank \u0661"));
        assertTrue(CustomPaymentMethodUtil.isSameName("Bank 1", "Bank \u0967"));
        assertFalse(CustomPaymentMethodUtil.isSameName("Bank 1", "Bank \u06F2"));
    }

    @Test
    public void otherLettersOrDigitsAreAnotherName() {
        assertFalse(CustomPaymentMethodUtil.isSameName("M-Pesa", "M-Pesa 2"));
        assertFalse(CustomPaymentMethodUtil.isSameName("M-Pesa", "Airtel Money"));
        assertFalse(CustomPaymentMethodUtil.isSameName("Uberweisung", "Überweisung"));
        // The vowel signs of Devanagari are marks, not letters
        assertFalse(CustomPaymentMethodUtil.isSameName("कि", "की"));
        // The numerals of Ethiopic are numbers, but no decimal digits
        assertFalse(CustomPaymentMethodUtil.isSameName("Bank \u1369", "Bank \u136A"));
        // The trade mark sign is not compared, the letters T and M are
        assertFalse(CustomPaymentMethodUtil.isSameName("Pay\u2122", "Paytm"));
    }

    @Test
    public void invalidNameIsNeverTheSameName() {
        assertFalse(CustomPaymentMethodUtil.isSameName(null, null));
        assertFalse(CustomPaymentMethodUtil.isSameName("", ""));
        assertFalse(CustomPaymentMethodUtil.isSameName("--", "++"));
        assertFalse(CustomPaymentMethodUtil.isSameName("M-Pesa", null));
        assertFalse(CustomPaymentMethodUtil.isSameName("M-Pesa\n", "M-Pesa"));
        String tooLong = "a".repeat(MAX_NAME_LENGTH + 1);
        assertFalse(CustomPaymentMethodUtil.isSameName(tooLong, tooLong));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Name of a payment method of Bisq
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void idNameAndShortNameOfAPaymentMethodAreRefused() {
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("SEPA"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("sepa instant"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("SEPA_INSTANT"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Zelle"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("clear x change"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("National bank transfer"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("national banks"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Face to face (in person)"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("f2f"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Wise-USD"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Altcoins"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("BSQ Swap"));
    }

    // Venmo and Cash App were removed for their chargeback risk
    @Test
    public void nameOfARemovedPaymentMethodIsRefused() {
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Venmo"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Cash App"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("cashapp"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("OKPay"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Chase QuickPay"));
        assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Verse"));
    }

    @Test
    public void nameWhichBisqDoesNotKnowIsNotRefused() {
        assertFalse(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("M-Pesa"));
        assertFalse(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Orange Money"));
        assertFalse(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("SEPA transfer"));
        assertFalse(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod(""));
    }

    // The check reads the names from the display strings. A payment method without them would not be found by
    // its name.
    @Test
    public void everyPaymentMethodHasItsNamesInTheDisplayStrings() {
        Stream.concat(PaymentMethod.getPaymentMethods().stream().map(PaymentMethod::getId),
                        PaymentMethod.REMOVED_PAYMENT_METHOD_IDS.stream())
                .forEach(id -> {
                    assertTrue(Res.getDefaultResourceBundle().containsKey(id), id);
                    assertTrue(Res.getDefaultResourceBundle().containsKey(id + "_SHORT"), id + "_SHORT");
                    assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod(
                            Res.getDefaultResourceBundle().getString(id)), id);
                    assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod(
                            Res.getDefaultResourceBundle().getString(id + "_SHORT")), id + "_SHORT");
                });
    }

    @Test
    public void nameInTheLanguageOfTheUserAndInEnglishIsRefused() {
        Locale previous = GlobalSettings.getLocale();
        try {
            GlobalSettings.setLocale(new Locale("de", "DE"));
            assertEquals("Inlandsüberweisung", Res.getResourceBundle().getString("NATIONAL_BANK"));

            assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("Inlandsüberweisung"));
            assertTrue(CustomPaymentMethodUtil.isNameOfBisqPaymentMethod("National bank transfer"));
        } finally {
            GlobalSettings.setLocale(previous);
        }
    }
}
