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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalSettings {
    private static boolean useAnimations = true;
    private static Locale locale;
    private static final ObjectProperty<Locale> localeProperty = new SimpleObjectProperty<>(locale);
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
        GlobalSettings.locale = locale;
        localeProperty.set(locale);
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

    public static ReadOnlyObjectProperty<Locale> localeProperty() {
        return localeProperty;
    }

    public static boolean getUseAnimations() {
        return useAnimations;
    }

    public static Locale getLocale() {
        return locale;
    }
}
