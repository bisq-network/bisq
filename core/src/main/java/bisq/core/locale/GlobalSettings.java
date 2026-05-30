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

package bisq.core.locale;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalSettings {
    public interface LocaleListener {
        void onLocaleChanged(Locale oldLocale, Locale newLocale);
    }

    private static boolean useAnimations = true;
    private static Locale locale;
    private static final List<LocaleListener> localeListeners = new CopyOnWriteArrayList<>();
    private static TradeCurrency defaultTradeCurrency;
    private static String btcDenomination;

    static {
        locale = Locale.getDefault();
        log.info("Locale info: {}", locale);

        // On some systems there is no country defined, in that case we use en_US
        if (locale.getCountry() == null || locale.getCountry().isEmpty())
            locale = Locale.US;
    }

    public static void setLocale(Locale locale) {
        Locale oldLocale = GlobalSettings.locale;
        GlobalSettings.locale = locale;
        if (!Objects.equals(oldLocale, locale)) {
            localeListeners.forEach(listener -> listener.onLocaleChanged(oldLocale, locale));
        }
    }

    public static void setUseAnimations(boolean useAnimations) {
        GlobalSettings.useAnimations = useAnimations;
    }

    public static void setDefaultTradeCurrency(TradeCurrency fiatCurrency) {
        GlobalSettings.defaultTradeCurrency = fiatCurrency;
    }


    public static void setBtcDenomination(String btcDenomination) {
        GlobalSettings.btcDenomination = btcDenomination;
    }

    public static TradeCurrency getDefaultTradeCurrency() {
        return defaultTradeCurrency;
    }

    public static String getBtcDenomination() {
        return btcDenomination;
    }

    public static void addLocaleListener(LocaleListener listener) {
        localeListeners.add(listener);
    }

    public static void removeLocaleListener(LocaleListener listener) {
        localeListeners.remove(listener);
    }

    public static boolean getUseAnimations() {
        return useAnimations;
    }

    public static Locale getLocale() {
        return locale;
    }
}
