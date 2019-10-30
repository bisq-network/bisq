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

public class KryptonTest extends AbstractAssetTest {

    public KryptonTest() {
        super(new Krypton());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("QQQ1LgQ1m8vX5tGrBZ2miS7A54Fmj5Qbij4UXT8nD4aqF75b1cpAauxVkjYaefcztV62UrDT1K9WHDeQWu4vpVXU2wezpshvex");
        assertValidAddress("QQQ1G56SKneSK1833tKjLH7E4ZgFwnqhqUb1HMHgYbnhaST56mukM1296jiYjTyTdMWnvH5FpWNAJWaQqwyPJHUR8qXRKBJy9o");
        assertValidAddress("QQQ1Bg61uUZhsNaTmUSZNcFgX2bk9wnAoYg9DSYZidDMJt7wVyccvMy8J7zRBoV5iT1pbraFUDWPQWWdXGPPws2P2ZGe8UzsaJ");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("QQQ1Bg61uUZhsNaTmUSZNcFgX2bk9wnAoYg9DSYZidDMJt7wVyccvMy8J7zRBoV5iT1pbraFUDWPQWWdXGPPws2P2ZGe8");
        assertInvalidAddress("11QQQ1Bg61uUZhsNaTmUSZNcFgX2bk9wnAoYg9DSYZidDMJt7wVyccvMy8J7zRBoV5iT1pbraFUDWPQWWdXGPPws2P2ZGe8UzsaJ");
        assertInvalidAddress("");
        assertInvalidAddress("#RoUKWRwpsx1F");
        assertInvalidAddress("YQQ1G56SKneSK1833tKjLH7E4ZgFwnqhqUb1HMHgYbnhaST56mukM1296jiYjTyTdMWnvH5FpWNAJWaQqwyPJHUR8qXRKBJy9o");
        assertInvalidAddress("3jyRo3rcp9fjdfjdSGpx");
        assertInvalidAddress("QQQ1G56SKneSK1833tKjLH7E4ZgFwnqhqUb1HMHgYbnhaST56mukM1296jiYjTyTdMWnvH5FpWNAJWaQqwyPJHUR8qXRKBJy9#");
        assertInvalidAddress("ZOD1Bg61uUZhsNaTmUSZNcFgX2bk9wnAoYg9DSYZidDMJt7wVyccvMy8J7zRBoV5iT1pbraFUDWPQWWdXGPPws2P2ZGe8UzsaJ");
    }
}