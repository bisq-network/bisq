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

package bisq.core.api;

import bisq.core.api.exception.FailedPreconditionException;
import bisq.core.app.AppStartupState;
import bisq.core.btc.Balances;
import bisq.core.btc.wallet.BsqTransferService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoFacade;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreWalletsServiceUnlockTest {
    private static final String PASSWORD = "password";

    // A low scrypt cost keeps the key derivation fast
    private final KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(2);
    private final WalletsManager walletsManager = mock(WalletsManager.class);
    private CoreWalletsService coreWalletsService;

    @BeforeEach
    void setUp() {
        AppStartupState appStartupState = mock(AppStartupState.class);
        when(appStartupState.isWalletAndNetworkReady()).thenReturn(true);
        when(walletsManager.areWalletsAvailable()).thenReturn(true);
        when(walletsManager.areWalletsEncrypted()).thenReturn(true);
        when(walletsManager.getKeyCrypterScrypt()).thenReturn(keyCrypterScrypt);
        byte[] correctKey = keyCrypterScrypt.deriveKey(PASSWORD).getKey();
        when(walletsManager.checkAESKey(any())).thenReturn(false);
        when(walletsManager.checkAESKey(argThat(key -> key != null && Arrays.equals(key.getKey(), correctKey))))
                .thenReturn(true);

        coreWalletsService = new CoreWalletsService(appStartupState,
                new CoreContext(),
                mock(Balances.class),
                walletsManager,
                mock(BsqWalletService.class),
                mock(BsqTransferService.class),
                mock(BsqFormatter.class),
                mock(BtcWalletService.class),
                mock(CoinFormatter.class),
                mock(FeeService.class),
                mock(DaoFacade.class),
                mock(Preferences.class));
    }

    @Test
    void failedUnlockLeavesTheWalletLocked() {
        assertThrows(IllegalArgumentException.class, () -> coreWalletsService.unlockWallet("wrong", 60));

        assertThrows(FailedPreconditionException.class, coreWalletsService::verifyEncryptedWalletIsUnlocked);
        assertThrows(FailedPreconditionException.class, coreWalletsService::getKey);
    }

    @Test
    void failedUnlockKeepsTheKeyOfAnEarlierUnlock() {
        coreWalletsService.unlockWallet(PASSWORD, 60);
        KeyParameter key = coreWalletsService.getKey();

        assertThrows(IllegalArgumentException.class, () -> coreWalletsService.unlockWallet("wrong", 60));

        assertArrayEquals(key.getKey(), coreWalletsService.getKey().getKey());
    }
}
