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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util.validation;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmailValidatorTest {

    @BeforeEach
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @Test
    public void validate() {
        EmailValidator validator = new EmailValidator();

        // Valid email addresses
        assertTrue(validator.validate("user@example.com").isValid);
        assertTrue(validator.validate("john.doe@example.com").isValid);
        assertTrue(validator.validate("user123@test-domain.org").isValid);
        assertTrue(validator.validate("user_name+tag@example.co.uk").isValid);
        assertTrue(validator.validate("a@b.cd").isValid); // shortest practical valid form
        assertTrue(validator.validate(" user@example.com ").isValid); // trims whitespace
        assertTrue(validator.validate("test_email%123@example-domain.com").isValid);
        assertTrue(validator.validate("user@example.xn--p1ai").isValid); // punycode TLD

        // Invalid null / empty / length
        assertFalse(validator.validate(null).isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("a@b.c").isValid); // TLD too short
        assertFalse(validator.validate("a@b").isValid); // no dot in domain

        // Invalid local-part
        assertFalse(validator.validate("@example.com").isValid); // missing local-part
        assertFalse(validator.validate(".user@example.com").isValid); // leading dot
        assertFalse(validator.validate("user.@example.com").isValid); // trailing dot
        assertFalse(validator.validate("user..name@example.com").isValid); // consecutive dots
        assertFalse(validator.validate("user name@example.com").isValid); // space
        assertFalse(validator.validate("user()@example.com").isValid); // invalid chars

        // Invalid domain
        assertFalse(validator.validate("user@example").isValid); // missing TLD
        assertFalse(validator.validate("user@-example.com").isValid); // label starts with hyphen
        assertFalse(validator.validate("user@example-.com").isValid); // label ends with hyphen
        assertFalse(validator.validate("user@example..com").isValid); // consecutive dots
        assertFalse(validator.validate("user@.example.com").isValid); // leading dot in domain

        // Invalid TLD
        assertFalse(validator.validate("user@example.c1").isValid); // non-letter TLD
        assertFalse(validator.validate("user@example.123").isValid); // numeric TLD
        assertFalse(validator.validate("user@example.xn--").isValid); // invalid punycode TLD

        // Local-part too long (>64 chars before @)
        String longLocalPart =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@example.com";
        assertFalse(validator.validate(longLocalPart).isValid);

        // Total length too long (>254 chars)
        String longEmail =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@" +
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb." +
                "ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc." +
                "ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd." +
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.com";
        assertFalse(validator.validate(longEmail).isValid);

        assertTrue(validator.validate("x.y+z_1@test.io").isValid);

        assertFalse(validator.validate("user@localhost").isValid); // no dot
        assertFalse(validator.validate("user@sub_domain.com").isValid); // underscore in domain
        assertFalse(validator.validate("user@@example.com").isValid);
        assertFalse(validator.validate("user@example.c").isValid);
        assertFalse(validator.validate("user@example.toolongtld123").isValid);
        assertFalse(validator.validate("user@exa_mple.com").isValid);
    }
}