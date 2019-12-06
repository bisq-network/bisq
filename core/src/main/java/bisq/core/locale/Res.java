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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;

import java.net.URL;
import java.net.URLConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class Res {
    public static void setup() {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        setBaseCurrencyCode(baseCurrencyNetwork.getCurrencyCode());
        setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
    }

    @SuppressWarnings("CanBeFinal")
    private static ResourceBundle resourceBundle = ResourceBundle.getBundle("i18n.displayStrings", GlobalSettings.getLocale(), new UTF8Control());

    static {
        GlobalSettings.localeProperty().addListener((observable, oldValue, newValue) -> {
            if ("en".equalsIgnoreCase(newValue.getLanguage()))
                newValue = Locale.ROOT;
            resourceBundle = ResourceBundle.getBundle("i18n.displayStrings", newValue, new UTF8Control());
        });
    }

    public static String getWithCol(String key) {
        return get(key) + ":";
    }

    public static String getWithColAndCap(String key) {
        return StringUtils.capitalize(get(key)) + ":";
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    private static String baseCurrencyCode;
    private static String baseCurrencyName;
    private static String baseCurrencyNameLowerCase;

    public static void setBaseCurrencyCode(String baseCurrencyCode) {
        Res.baseCurrencyCode = baseCurrencyCode;
    }

    public static void setBaseCurrencyName(String baseCurrencyName) {
        Res.baseCurrencyName = baseCurrencyName;
        baseCurrencyNameLowerCase = baseCurrencyName.toLowerCase();
    }

    public static String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public static String getBaseCurrencyName() {
        return baseCurrencyName;
    }

    // Capitalize first character
    public static String getWithCap(String key) {
        return StringUtils.capitalize(get(key));
    }

    public static String getWithCol(String key, Object... arguments) {
        return get(key, arguments) + ":";
    }

    public static String get(String key, Object... arguments) {
        return MessageFormat.format(Res.get(key), arguments);
    }

    public static String get(String key) {
        try {
            return resourceBundle.getString(key)
                    .replace("BTC", baseCurrencyCode)
                    .replace("Bitcoin", baseCurrencyName)
                    .replace("bitcoin", baseCurrencyNameLowerCase);
        } catch (MissingResourceException e) {
            log.warn("Missing resource for key: {}", key);
            e.printStackTrace();
            if (DevEnv.isDevMode())
                UserThread.runAfter(() -> {
                    // We delay a bit to not throw while UI is not ready
                    throw new RuntimeException("Missing resource for key: " + key);
                }, 1);

            return key;
        }
    }
}

// Adds UTF8 support for property files
class UTF8Control extends ResourceBundle.Control {

    public ResourceBundle newBundle(String baseName, @NotNull Locale locale, @NotNull String format, ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        // Below is a copy of the default implementation.
        final String bundleName = toBundleName(baseName, locale);
        final String resourceName = toResourceName(bundleName, "properties");
        ResourceBundle bundle = null;
        InputStream stream = null;
        if (reload) {
            final URL url = loader.getResource(resourceName);
            if (url != null) {
                final URLConnection connection = url.openConnection();
                if (connection != null) {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        } else {
            stream = loader.getResourceAsStream(resourceName);
        }
        if (stream != null) {
            try {
                // Only this line is changed to make it read properties files as UTF-8.
                bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
            } finally {
                stream.close();
            }
        }
        return bundle;
    }
}
