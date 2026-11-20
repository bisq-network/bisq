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

package bisq.core.dao.node;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseSizeLimitTest {
    @Test
    void keepsAllElementsWithinTheLimit() {
        assertEquals(List.of("aa", "bb", "cc"),
                ResponseSizeLimit.leadingElementsWithin(List.of("aa", "bb", "cc"), String::length, 6));
    }

    @Test
    void dropsTheElementsAfterTheLimit() {
        assertEquals(List.of("aa", "bb"),
                ResponseSizeLimit.leadingElementsWithin(List.of("aa", "bb", "cc", "d"), String::length, 5));
    }

    @Test
    void keepsTheFirstElementAboveTheLimit() {
        assertEquals(List.of("aaaaaa"),
                ResponseSizeLimit.leadingElementsWithin(List.of("aaaaaa", "b"), String::length, 5));
    }

    @Test
    void keepsAnEmptyList() {
        assertEquals(List.of(), ResponseSizeLimit.leadingElementsWithin(List.<String>of(), String::length, 5));
    }
}
