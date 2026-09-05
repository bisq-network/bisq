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

package bisq.core.util.validation;

import bisq.core.locale.Res;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlInputValidatorTest {
    private final UrlInputValidator validator = new UrlInputValidator();

    @BeforeEach
    void setUp() {
        Res.setup();
    }

    @Test
    void acceptsAbsoluteHttpUrls() {
        assertTrue(validator.validate("https://mempool.space/tx/").isValid);
        assertTrue(validator.validate("http://explorer.local:8080/api?tx=").isValid);
    }

    @Test
    void rejectsRelativeAndNonHttpUrls() {
        assertFalse(validator.validate("mempool.space/tx/").isValid);
        assertFalse(validator.validate("ftp://mempool.space/tx/").isValid);
    }

    @Test
    void rejectsCharactersThatUriParsingRejects() {
        assertFalse(validator.validate("https://mempool.space/tx|id").isValid);
        assertFalse(validator.validate("https://mempool.space/tx id").isValid);
    }
}
