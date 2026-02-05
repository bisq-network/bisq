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

package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Email addresses consist of localPart @ domainPart
 *
 * Local part:
 * - Practical subset allowed: A-Za-z0-9._%+- (max 64 characters)
 * - Leading, trailing, and consecutive dots are not allowed
 * - Quoted strings and exotic RFC constructs are intentionally excluded
 *
 * Domain part:
 * - Labels separated by dots, each 1..63 characters
 * - Each label starts and ends with alphanumeric character
 * - Hyphens allowed internally
 * - At least one dot required
 * - Last label (TLD) must be at least 2 characters, letters-only (or punycode starting with "xn--")
 *
 * Conservative validator:
 * - Accepts common, interoperable email addresses
 * - Rejects edge-case RFC addresses that break UIs, JSON, or payment rails
 */
public final class EmailValidator extends InputValidator {
    /**
     * Precompiled pattern matching a conservative subset of email addresses.
     * - prevents leading/trailing/consecutive dots in the local part
     * - ensures domain labels start/end with alnum and allows internal hyphens
     */
    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_%+\\-]+(?:\\.[A-Za-z0-9_%+\\-]+)*@" +          // local-part
            "[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?" +         // first domain label
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)+$"  // additional labels
    );

    private static final ValidationResult INVALID_ADDRESS = new ValidationResult(false, Res.get("validation.email.invalidAddress"));

    /**
     * Validate the supplied email address.
     *
     * @param input the email address to validate (may be null)
     * @return ValidationResult true if the address is acceptable; otherwise a ValidationResult
     *         containing {@code validation.email.invalidAddress}
     */
    @Override
    public ValidationResult validate(String input) {
        if (input == null) return INVALID_ADDRESS;

        String email = input.trim();

        // Practical length limits: shortest plausible a@b.cc (6), longest allowed by spec 254
        if (email.length() < 6 || email.length() > 254) return INVALID_ADDRESS;

        // local-part must be 1..64 chars
        int at = email.indexOf('@');
        if (at <= 0 || at > 64) return INVALID_ADDRESS; 

        // Apply regex pattern
        if (!SIMPLE_EMAIL_PATTERN.matcher(email).matches()) return INVALID_ADDRESS;

        // Check TLD (last label after final dot)
        int lastDot = email.lastIndexOf('.');
        if (lastDot < 0 || lastDot == email.length() - 1) return INVALID_ADDRESS;

        String tld = email.substring(lastDot + 1);

        // TLD length must be at least 2 characters
        if (tld.length() < 2) return INVALID_ADDRESS;

        // allow punycode TLDs for IDN support
        if (tld.toLowerCase(Locale.ROOT).startsWith("xn--")) {
            // basic sanity check for punycode TLD
            if (tld.length() > 63) return INVALID_ADDRESS;
            for (int i = 4; i < tld.length(); i++) {
                char c = tld.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '-')) return INVALID_ADDRESS;
            }
        } else {
            // ASCII letters only
            for (int i = 0; i < tld.length(); i++) {
                if (!Character.isLetter(tld.charAt(i))) return INVALID_ADDRESS;
            }
        }

        return new ValidationResult(true);
    }
}
