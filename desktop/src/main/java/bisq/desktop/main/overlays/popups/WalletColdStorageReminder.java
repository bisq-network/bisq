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

package bisq.desktop.main.overlays.popups;

import bisq.desktop.Navigation;

import bisq.core.btc.Balances;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Named;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Reminds users with significant on-wallet BTC to treat the Bisq wallet as a hot
 * wallet and move idle funds to cold storage. Triggers on app launch and on every
 * navigation event with a cooldown so it stays visible (and annoying) until the
 * user reduces their wallet balance below the threshold.
 */
@Singleton
@Slf4j
public class WalletColdStorageReminder {

    // Threshold above which the reminder fires. Tune by editing this constant.
    private static final Coin THRESHOLD = Coin.parseCoin("0.5");

    // Re-show interval for the navigation trigger.
    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(6);

    private final Balances balances;
    private final Navigation navigation;
    private final CoinFormatter btcFormatter;

    private long lastShownMs = 0L;
    private boolean navListenerRegistered = false;

    @Inject
    public WalletColdStorageReminder(Balances balances,
                                     Navigation navigation,
                                     @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        this.balances = balances;
        this.navigation = navigation;
        this.btcFormatter = btcFormatter;
    }

    /** Call once after splash screen removal. Always shows on launch when above threshold. */
    public void onAppStarted() {
        registerNavListener();
        maybeShow(true);
    }

    private void registerNavListener() {
        if (navListenerRegistered) return;
        navListenerRegistered = true;
        navigation.addListener((path, data) -> maybeShow(false));
    }

    private void maybeShow(boolean ignoreCooldown) {
        Coin total = totalBalance();
        if (!total.isGreaterThan(THRESHOLD)) return;

        long now = System.currentTimeMillis();
        if (!ignoreCooldown && now - lastShownMs < COOLDOWN_MS) return;
        lastShownMs = now;

        new Popup()
                .headLine(Res.get("popup.warning.coldStorage.headline"))
                .warning(Res.get("popup.warning.coldStorage.msg",
                        btcFormatter.formatCoinWithCode(total),
                        btcFormatter.formatCoinWithCode(THRESHOLD)))
                .show();
    }

    private Coin totalBalance() {
        return orZero(balances.getAvailableBalance().get())
                .add(orZero(balances.getReservedBalance().get()))
                .add(orZero(balances.getLockedBalance().get()));
    }

    private static Coin orZero(Coin c) {
        return c == null ? Coin.ZERO : c;
    }
}
