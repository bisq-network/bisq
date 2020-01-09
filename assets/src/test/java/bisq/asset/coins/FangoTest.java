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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class FangoTest extends AbstractAssetTest {

    public FangoTest() {
        super(new Fango());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("fango7cNPfNMd7bPXtnVcxeVutJ8eV5CHPADdSmkURA9HyhWWDjz61qXUAjsRisdeDMrCQw1EPG4RSZi9BkVgg5iBRgJRaLogir");
        assertValidAddress("fango4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("fango4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwD");
        assertInvalidAddress("fango4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYDd");
        assertInvalidAddress("fangO4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
        assertInvalidAddress("fanGo4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
        assertInvalidAddress("faNgo4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
        assertInvalidAddress("fAngo4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
        assertInvalidAddress("Fango4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
        assertInvalidAddress("FANGO4Uxurg6s7mTd7r7aZeMxkdrsPNYQM4yPjmX6rTRh4VZx4QGqe3K29vKB9sEBxMztybnbj3ZvNgGS7ztzLZ88x83hM3GwYD");
    }
}
