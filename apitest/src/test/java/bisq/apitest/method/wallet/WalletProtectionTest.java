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

package bisq.apitest.method.wallet;

import bisq.apitest.method.DockerMethodTest;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * Fresh-stack test: mutates Alice's wallet encryption state. The runner resets
 * the docker stack before this class runs, so encrypted-state leak between tests
 * is bounded to this class. {@link #removePasswordIfSet()} is the belt-and-braces
 * cleanup that tries to leave Alice's wallet unlocked even if a test mid-flow fails.
 */
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
@Tag("freshstack")
public class WalletProtectionTest extends DockerMethodTest {

    private static final String PW1 = "first-password";
    private static final String PW2 = "second-password";

    @Test
    @Order(1)
    public void testSetWalletPassword() {
        aliceClient.setWalletPassword(PW1);
    }

    @Test
    @Order(2)
    public void testGetBalanceOnEncryptedWalletShouldThrowException() {
        Throwable ex = assertThrows(StatusRuntimeException.class, aliceClient::getBtcBalances);
        assertEquals("FAILED_PRECONDITION: wallet is locked", ex.getMessage());
    }

    @Test
    @Order(3)
    public void testUnlockWalletForShortPeriod() {
        aliceClient.unlockWallet(PW1, 1);
        aliceClient.getBtcBalances(); // works while unlocked
        // Gate on the wallet re-locking — poll instead of sleeping past a fixed window.
        // Safety net inside awaitCond is 60s, but real wait is ~1s.
        awaitWalletLocked();
    }

    @Test
    @Order(5)
    public void testLockWalletBeforeUnlockTimeoutExpiry() {
        aliceClient.unlockWallet(PW1, 60);
        aliceClient.lockWallet();
        Throwable ex = assertThrows(StatusRuntimeException.class, aliceClient::getBtcBalances);
        assertEquals("FAILED_PRECONDITION: wallet is locked", ex.getMessage());
    }

    @Test
    @Order(6)
    public void testLockAlreadyLockedWalletShouldThrowException() {
        Throwable ex = assertThrows(StatusRuntimeException.class, aliceClient::lockWallet);
        assertEquals("ALREADY_EXISTS: wallet is already locked", ex.getMessage());
    }

    @Test
    @Order(7)
    public void testUnlockWalletTimeoutOverride() {
        // Override a short unlock window with a longer one before the first expires;
        // wallet must remain unlocked past the original timeout. The 1.5s sleep sits
        // past the original 1s window — proving the override took effect — while the
        // new window is set far wider (60s) so a CI stall between the override and the
        // balance read cannot cross the re-lock deadline and flake the test.
        aliceClient.unlockWallet(PW1, 1);
        aliceClient.unlockWallet(PW1, 60); // override before 1s expires
        sleep(1_500);                      // past original (1s), far inside new (60s)
        aliceClient.getBtcBalances();      // must succeed
    }

    @Test
    @Order(8)
    public void testSetNewWalletPassword() {
        aliceClient.setWalletPassword(PW1, PW2);
        // Setting a new password persists the wallet asynchronously. Gate on the new
        // password actually being usable instead of guessing a fixed save delay.
        awaitCond(() -> {
            try {
                aliceClient.unlockWallet(PW2, 30);
                return true;
            } catch (StatusRuntimeException ex) {
                return false;
            }
        }, "new wallet password becomes effective");
        aliceClient.getBtcBalances();
    }

    @Test
    @Order(9)
    public void testSetNewWalletPasswordWithWrongOldShouldThrowException() {
        Throwable ex = assertThrows(StatusRuntimeException.class,
                () -> aliceClient.setWalletPassword("bad old password", "irrelevant"));
        assertEquals("INVALID_ARGUMENT: incorrect old password", ex.getMessage());
    }

    @Test
    @Order(10)
    public void testRemoveWalletPassword() {
        aliceClient.removeWalletPassword(PW2);
        aliceClient.getBtcBalances();
    }

    /** Block until getBtcBalances throws "wallet is locked" — the deterministic signal
     *  that the unlock timeout has fired daemon-side. */
    private static void awaitWalletLocked() {
        awaitCond(() -> {
            try {
                aliceClient.getBtcBalances();
                return false;
            } catch (StatusRuntimeException ex) {
                return ex.getMessage().contains("wallet is locked");
            }
        }, "wallet relocks after unlock timeout");
    }

    @AfterAll
    public static void removePasswordIfSet() {
        for (String pw : new String[]{PW2, PW1}) {
            try {
                aliceClient.removeWalletPassword(pw);
                return;
            } catch (RuntimeException ignored) { }
        }
    }
}
