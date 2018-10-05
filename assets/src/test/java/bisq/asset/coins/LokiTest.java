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

public class LokiTest extends AbstractAssetTest {

    public LokiTest() {
        super(new Loki());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("LVjRU9UVQ9SXDm3shuHVj5hmavGfbQEVMJwgH8Kh9AyPRyXtzZ64Rpjb15L3sWK2TT6caWVYSBATECKb9pc2qf5tCbsfb6Q");
        assertValidAddress("LTmZwKAZcwhhdRMRWLgGvzY4n5fT3n9Mh33H2xnXiCNVcwmtNToP4iVSu59MNc2YNkGGPVwE5B9ra2nRQ5nYmf3kE1kzXKx");
        assertValidAddress("LQmsf1ktNYQGWD2xbY1MStfYWFi1Sm4Cd7cHVryxepwcPcaa5N6KbpHdFXYDvswNYaRS1W5JLGY52dkRDV6hCVrtBhUCAe5");
        assertValidAddress("LX3F8zHNvth1JvU5qdETsfQFF33PPKw1sHdusAaaGLhi1J1dv6rKZbN92PxpV1uW9o8T5WPwYvYwKDteTiZN2gEE3y5wMqZ");
        assertValidAddress("LWv1YaK5jJaGPCFd1wxJbZ4hHa7yGKNJeGCPC9fJCXqz8NqittAtS1xYkr5gYxnjtYKDWPB6hNQBE93bz7ZVJFtXQJrT1SZ");
        assertValidAddress("LK6DQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9u");
        assertValidAddress("LPDCQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9u");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("LWv1YaK5jJaGPCFd1wxJbZ4hHa7yGKNJeGCPC9fJCXqz8NqittAtS1xYkr5gYxnjtYKDWPB6hNQBE93bz7ZVJFtXQJrT1SZz");
        assertInvalidAddress("lWv1YaK5jJaGPCFd1wxJbZ4hHa7yGKNJeGCPC9fJCXqz8NqittAtS1xYkr5gYxnjtYKDWPB6hNQBE93bz7ZVJFtXQJrT1SZ");
        assertInvalidAddress("Wv1YaK5jJaGPCFd1wxJbZ4hHa7yGKNJeGCPC9fJCXqz8NqittAtS1xYkr5gYxnjtYKDWPB6hNQBE93bz7ZVJFtXQJrT1SZz");
        assertInvalidAddress("LWv1YaK5jJaGPCFd1wxJbZ4hHa7yGKNJeGCPC9fJCXqz8NqittAtS1xYkr5gYxnjtYKDWPB6hNQBE93bz7ZVJFtXQJrT1S");
        assertInvalidAddress("LZ6DQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9u");
        assertInvalidAddress("lK6DQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9u");
        assertInvalidAddress("LK6DQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9uu");
        assertInvalidAddress("LK6DQ17G8R3zs3Xf33wCeViD2B95jgdpjAhcRsjuheJ784dumXn7g3RPAzedWpFq364jJKYL9dkQ8mY66sZG9BiCx3dmHUuhwuSMcRwr9");
    }
}
