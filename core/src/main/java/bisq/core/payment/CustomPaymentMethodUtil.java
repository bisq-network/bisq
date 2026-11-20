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

import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;

import java.text.Normalizer;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * The rules for the texts of the custom payment method: the name of the payment method and the
 * account data. Both are free texts of a user and reach other users in offers and trades.
 */
public class CustomPaymentMethodUtil {
    // The lengths are those of Bisq 2 (the name of a custom payment method and the data of a user defined account)
    public static final int MAX_NAME_LENGTH = 20;
    public static final int MAX_ACCOUNT_DATA_LENGTH = 1000;

    // A character with more marks above or below it is drawn over the lines next to it
    public static final int MAX_MARKS_IN_A_ROW = 4;

    // Both are needed to write words in scripts such as Persian or Devanagari
    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    /**
     * @return true if the name can be shown and compared: it is one line of at most
     * {@link #MAX_NAME_LENGTH} characters with at least one letter or number which is compared, and no character of
     * it has more than {@link #MAX_MARKS_IN_A_ROW} marks above or below it.
     */
    public static boolean isValidName(@Nullable String name) {
        return name != null &&
                name.length() <= MAX_NAME_LENGTH &&
                hasOnlyAllowedCharacters(name, false) &&
                hasLetterOrNumber(toComparableName(name)) &&
                !hasTooManyMarksInARow(name);
    }

    /**
     * @return true if the account data can be shown: it is a text of at most
     * {@link #MAX_ACCOUNT_DATA_LENGTH} characters with at least one letter or number.
     */
    public static boolean isValidAccountData(@Nullable String accountData) {
        return accountData != null &&
                accountData.length() <= MAX_ACCOUNT_DATA_LENGTH &&
                hasOnlyAllowedCharacters(accountData, true) &&
                hasLetterOrNumber(Normalizer.normalize(accountData, Normalizer.Form.NFKC));
    }

    /**
     * @return the name without the characters which are not allowed in it and without the spaces around it. A text
     * copied from a web page or a document can contain such characters, and most of them are not visible.
     */
    public static String cleanName(String name) {
        return removeNotAllowedCharacters(name, false).strip();
    }

    public static String cleanAccountData(String accountData) {
        return removeNotAllowedCharacters(accountData, true).strip();
    }

    /**
     * @return true if both names are valid and name the same payment method. Upper and lower case, spaces,
     * punctuation and symbols make no difference, so "M-Pesa" and "m pesa" are the same name. A decimal digit of
     * any script is the digit of its value.
     */
    public static boolean isSameName(@Nullable String name, @Nullable String otherName) {
        return isValidName(name) &&
                isValidName(otherName) &&
                toComparableName(name).equals(toComparableName(otherName));
    }

    /**
     * @return true if the name is the ID, the name or the short name of a payment method of Bisq, in the
     * language of the user or in English. The removed payment methods are included.
     */
    public static boolean isNameOfBisqPaymentMethod(String name) {
        String comparableName = toComparableName(name);
        return Stream.concat(PaymentMethod.getPaymentMethods().stream().map(PaymentMethod::getId),
                        PaymentMethod.REMOVED_PAYMENT_METHOD_IDS.stream())
                .flatMap(CustomPaymentMethodUtil::getNamesOfPaymentMethod)
                .map(CustomPaymentMethodUtil::toComparableName)
                .anyMatch(comparableName::equals);
    }

    // The letters, numbers and marks of the name without the difference between upper and lower case. Marks are
    // kept, as they are parts of words in scripts such as Devanagari or Thai. Symbols are dropped before the
    // normalization, which would turn some of them into letters or marks: the trade mark sign into TM, the spacing
    // acute accent, which some keyboards give for an apostrophe, into a space and an accent.
    // Java has no case folding, so the case is removed in three steps. The lower case of each character comes
    // first, as the capital sharp s is its own upper case. The upper case of the text then writes a sharp s as two
    // letters. The lower case is then taken for each character on its own again, as the lower case of a whole
    // text depends on the place of a letter in its word (the final sigma of Greek). The case conversions take
    // some letters and their accents apart, so the result is normalized again.
    static String toComparableName(String name) {
        String normalized = Normalizer.normalize(keepLettersNumbersAndMarks(name), Normalizer.Form.NFKC);
        String withoutCase = toLowerCaseOfEachCharacter(
                toLowerCaseOfEachCharacter(normalized).toUpperCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder();
        keepLettersNumbersAndMarks(Normalizer.normalize(withoutCase, Normalizer.Form.NFKC)).codePoints()
                .map(CustomPaymentMethodUtil::toDigitOfItsValue)
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static String keepLettersNumbersAndMarks(String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints()
                .filter(codePoint -> isLetterOrNumber(codePoint) || isMark(codePoint))
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    // The decimal digits of all scripts with the same value are the same digit
    private static int toDigitOfItsValue(int codePoint) {
        return Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER ?
                '0' + Character.digit(codePoint, 10) :
                codePoint;
    }

    private static String toLowerCaseOfEachCharacter(String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints()
                .map(Character::toLowerCase)
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static Stream<String> getNamesOfPaymentMethod(String paymentMethodId) {
        return Stream.concat(Stream.of(paymentMethodId),
                Stream.of(Res.getResourceBundle(), Res.getDefaultResourceBundle())
                        .flatMap(resourceBundle -> Stream.of(paymentMethodId, paymentMethodId + "_SHORT")
                                .filter(resourceBundle::containsKey)
                                .map(resourceBundle::getString)));
    }

    private static boolean hasLetterOrNumber(String text) {
        return text.codePoints().anyMatch(CustomPaymentMethodUtil::isLetterOrNumber);
    }

    // Not only the decimal digits are numbers: the numerals of some scripts are of another kind
    private static boolean isLetterOrNumber(int codePoint) {
        if (Character.isLetter(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.DECIMAL_DIGIT_NUMBER ||
                type == Character.LETTER_NUMBER ||
                type == Character.OTHER_NUMBER;
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK ||
                type == Character.COMBINING_SPACING_MARK ||
                type == Character.ENCLOSING_MARK;
    }

    // Marks above or below a character are drawn on top of each other. A mark with a width of its own, as many
    // vowel signs of Burmese or Devanagari, is a new place for such marks. The marks are counted in the decomposed
    // form, in which a letter with an accent is the letter and a mark.
    private static boolean hasTooManyMarksInARow(String text) {
        int marksInARow = 0;
        for (int codePoint : Normalizer.normalize(text, Normalizer.Form.NFKD).codePoints().toArray()) {
            if (isMarkAboveOrBelow(codePoint)) {
                marksInARow++;
                if (marksInARow > MAX_MARKS_IN_A_ROW) {
                    return true;
                }
            } else if (codePoint != ZERO_WIDTH_NON_JOINER && codePoint != ZERO_WIDTH_JOINER) {
                // A joiner is not shown, and the marks after it are drawn on the same character
                marksInARow = 0;
            }
        }
        return false;
    }

    private static boolean isMarkAboveOrBelow(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    private static boolean hasOnlyAllowedCharacters(String text, boolean allowLineBreaks) {
        return text.codePoints().allMatch(codePoint -> isAllowedCharacter(codePoint, allowLineBreaks));
    }

    private static String removeNotAllowedCharacters(String text, boolean allowLineBreaks) {
        StringBuilder sb = new StringBuilder();
        text.codePoints()
                .filter(codePoint -> isAllowedCharacter(codePoint, allowLineBreaks))
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    // Control and format characters can break a line, hide text or change the order in which text is shown. A
    // character which the Java runtime does not know is not allowed either: a newer runtime can know it as a
    // letter, and the two runtimes would then compare a name with it differently.
    private static boolean isAllowedCharacter(int codePoint, boolean allowLineBreaks) {
        if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
            return allowLineBreaks;
        }
        if (codePoint == ZERO_WIDTH_NON_JOINER || codePoint == ZERO_WIDTH_JOINER) {
            return true;
        }
        if (isNotShown(codePoint)) {
            return false;
        }
        switch (Character.getType(codePoint)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.PRIVATE_USE:
            case Character.SURROGATE:
            case Character.UNASSIGNED:
                return false;
            default:
                return true;
        }
    }

    // The characters which are not shown although they are letters or marks (the default ignorable code points
    // of Unicode which are no format characters). As letters and marks they would be compared.
    private static boolean isNotShown(int codePoint) {
        return codePoint == 0x034F ||                               // combining grapheme joiner
                codePoint == 0x115F || codePoint == 0x1160 ||       // Hangul fillers
                codePoint == 0x17B4 || codePoint == 0x17B5 ||       // Khmer inherent vowels
                (codePoint >= 0x180B && codePoint <= 0x180F) ||     // Mongolian variation selectors
                codePoint == 0x3164 || codePoint == 0xFFA0 ||       // Hangul fillers
                (codePoint >= 0xFE00 && codePoint <= 0xFE0F) ||     // variation selectors
                (codePoint >= 0xE0100 && codePoint <= 0xE01EF);     // variation selectors supplement
    }
}
