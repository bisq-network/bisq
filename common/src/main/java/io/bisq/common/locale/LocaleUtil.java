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

package io.bisq.common.locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleUtil {
    private static final Logger log = LoggerFactory.getLogger(LocaleUtil.class);

    public static List<Locale> getAllLocales() {

        // derived form Locale.getAvailableLocales() and added some missing locales 
        List<Locale> allLocales = new ArrayList<>();

        allLocales.add(new Locale("bg", "", ""));
        allLocales.add(new Locale("it", "", ""));
        allLocales.add(new Locale("ko", "", ""));
        allLocales.add(new Locale("uk", "", ""));
        allLocales.add(new Locale("lv", "", ""));
        allLocales.add(new Locale("pt", "", ""));
        allLocales.add(new Locale("sk", "", ""));
        allLocales.add(new Locale("ga", "", ""));
        allLocales.add(new Locale("et", "", ""));
        allLocales.add(new Locale("sv", "", ""));
        allLocales.add(new Locale("cs", "", ""));
        allLocales.add(new Locale("el", "", ""));
        allLocales.add(new Locale("hu", "", ""));
        allLocales.add(new Locale("in", "", ""));
        allLocales.add(new Locale("be", "", ""));
        allLocales.add(new Locale("es", "", ""));
        allLocales.add(new Locale("tr", "", ""));
        allLocales.add(new Locale("hr", "", ""));
        allLocales.add(new Locale("lt", "", ""));
        allLocales.add(new Locale("sq", "", ""));
        allLocales.add(new Locale("fr", "", ""));
        allLocales.add(new Locale("ja", "", ""));
        allLocales.add(new Locale("is", "", ""));
        allLocales.add(new Locale("de", "", ""));
        allLocales.add(new Locale("en", "", ""));
        allLocales.add(new Locale("ca", "", ""));
        allLocales.add(new Locale("sl", "", ""));
        allLocales.add(new Locale("fi", "", ""));
        allLocales.add(new Locale("mk", "", ""));
        allLocales.add(new Locale("sr", "", ""));
        allLocales.add(new Locale("th", "", ""));
        allLocales.add(new Locale("ar", "", ""));
        allLocales.add(new Locale("ru", "", ""));
        allLocales.add(new Locale("ms", "", ""));
        allLocales.add(new Locale("hi", "", ""));
        allLocales.add(new Locale("nl", "", ""));
        allLocales.add(new Locale("vi", "", ""));
        allLocales.add(new Locale("sr", "", ""));
        allLocales.add(new Locale("mt", "", ""));
        allLocales.add(new Locale("da", "", ""));
        allLocales.add(new Locale("ro", "", ""));
        allLocales.add(new Locale("no", "", ""));
        allLocales.add(new Locale("pl", "", ""));
        allLocales.add(new Locale("iw", "", ""));
        allLocales.add(new Locale("zh", "", ""));
        allLocales.add(new Locale("ar", "AE", ""));
        allLocales.add(new Locale("sq", "AL", ""));
        allLocales.add(new Locale("es", "AR", ""));
        allLocales.add(new Locale("de", "AT", ""));
        allLocales.add(new Locale("en", "AU", ""));
        allLocales.add(new Locale("sr", "BA", ""));
        allLocales.add(new Locale("sr", "BA", ""));
        allLocales.add(new Locale("fr", "BE", ""));
        allLocales.add(new Locale("nl", "BE", ""));
        allLocales.add(new Locale("bg", "BG", ""));
        allLocales.add(new Locale("ar", "BH", ""));
        allLocales.add(new Locale("es", "BO", ""));
        allLocales.add(new Locale("pt", "BR", ""));
        allLocales.add(new Locale("be", "BY", ""));
        allLocales.add(new Locale("fr", "CA", ""));
        allLocales.add(new Locale("en", "CA", ""));
        allLocales.add(new Locale("fr", "CH", ""));
        allLocales.add(new Locale("de", "CH", ""));
        allLocales.add(new Locale("it", "CH", ""));
        allLocales.add(new Locale("es", "CL", ""));
        allLocales.add(new Locale("zh", "CN", ""));
        allLocales.add(new Locale("es", "CO", ""));
        allLocales.add(new Locale("es", "CR", ""));
        allLocales.add(new Locale("sr", "CS", ""));
        allLocales.add(new Locale("es", "CU", ""));
        allLocales.add(new Locale("el", "CY", ""));
        allLocales.add(new Locale("cs", "CZ", ""));
        allLocales.add(new Locale("de", "DE", ""));
        allLocales.add(new Locale("da", "DK", ""));
        allLocales.add(new Locale("es", "DO", ""));
        allLocales.add(new Locale("ar", "DZ", ""));
        allLocales.add(new Locale("es", "EC", ""));
        allLocales.add(new Locale("et", "EE", ""));
        allLocales.add(new Locale("ar", "EG", ""));
        allLocales.add(new Locale("ca", "ES", ""));
        allLocales.add(new Locale("es", "ES", ""));
        allLocales.add(new Locale("fi", "FI", ""));
        allLocales.add(new Locale("fr", "FR", ""));
        allLocales.add(new Locale("en", "GB", ""));
        allLocales.add(new Locale("el", "GR", ""));
        allLocales.add(new Locale("de", "GR", ""));
        allLocales.add(new Locale("es", "GT", ""));
        allLocales.add(new Locale("zh", "HK", ""));
        allLocales.add(new Locale("es", "HN", ""));
        allLocales.add(new Locale("hr", "HR", ""));
        allLocales.add(new Locale("hu", "HU", ""));
        allLocales.add(new Locale("in", "ID", ""));
        allLocales.add(new Locale("ga", "IE", ""));
        allLocales.add(new Locale("en", "IE", ""));
        allLocales.add(new Locale("iw", "IL", ""));
        allLocales.add(new Locale("hi", "IN", ""));
        allLocales.add(new Locale("en", "IN", ""));
        allLocales.add(new Locale("ar", "IQ", ""));
        allLocales.add(new Locale("is", "IS", ""));
        allLocales.add(new Locale("it", "IT", ""));
        allLocales.add(new Locale("ar", "JO", ""));
        allLocales.add(new Locale("ja", "JP", ""));
        allLocales.add(new Locale("ja", "JP", ""));
        allLocales.add(new Locale("sw", "KE", ""));
        allLocales.add(new Locale("km", "KH", ""));
        allLocales.add(new Locale("ko", "KR", ""));
        allLocales.add(new Locale("ar", "KW", ""));
        allLocales.add(new Locale("kk", "KZ", ""));
        allLocales.add(new Locale("ar", "LB", ""));
        allLocales.add(new Locale("lt", "LT", ""));
        allLocales.add(new Locale("fr", "LU", ""));
        allLocales.add(new Locale("de", "LU", ""));
        allLocales.add(new Locale("lv", "LV", ""));
        allLocales.add(new Locale("ar", "LY", ""));
        allLocales.add(new Locale("ar", "MA", ""));
        allLocales.add(new Locale("ro", "MD", ""));
        allLocales.add(new Locale("sr", "ME", ""));
        allLocales.add(new Locale("sr", "ME", ""));
        allLocales.add(new Locale("mk", "MK", ""));
        allLocales.add(new Locale("mt", "MT", ""));
        allLocales.add(new Locale("en", "MT", ""));
        allLocales.add(new Locale("es", "MX", ""));
        allLocales.add(new Locale("ms", "MY", ""));
        allLocales.add(new Locale("es", "NI", ""));
        allLocales.add(new Locale("nl", "NL", ""));
        allLocales.add(new Locale("no", "NO", ""));
        allLocales.add(new Locale("no", "NO", ""));
        allLocales.add(new Locale("en", "NZ", ""));
        allLocales.add(new Locale("ar", "OM", ""));
        allLocales.add(new Locale("es", "PA", ""));
        allLocales.add(new Locale("es", "PE", ""));
        allLocales.add(new Locale("en", "PH", ""));
        allLocales.add(new Locale("pl", "PL", ""));
        allLocales.add(new Locale("es", "PR", ""));
        allLocales.add(new Locale("pt", "PT", ""));
        allLocales.add(new Locale("es", "PY", ""));
        allLocales.add(new Locale("ar", "QA", ""));
        allLocales.add(new Locale("ro", "RO", ""));
        allLocales.add(new Locale("sr", "RS", ""));
        allLocales.add(new Locale("sr", "RS", ""));
        allLocales.add(new Locale("ru", "RU", ""));
        allLocales.add(new Locale("ar", "SA", ""));
        allLocales.add(new Locale("ar", "SD", ""));
        allLocales.add(new Locale("sv", "SE", ""));
        allLocales.add(new Locale("en", "SG", ""));
        allLocales.add(new Locale("zh", "SG", ""));
        allLocales.add(new Locale("sl", "SI", ""));
        allLocales.add(new Locale("sk", "SK", ""));
        allLocales.add(new Locale("es", "SV", ""));
        allLocales.add(new Locale("ar", "SY", ""));
        allLocales.add(new Locale("th", "TH", ""));
        allLocales.add(new Locale("th", "TH", ""));
        allLocales.add(new Locale("ar", "TN", ""));
        allLocales.add(new Locale("tr", "TR", ""));
        allLocales.add(new Locale("zh", "TW", ""));
        allLocales.add(new Locale("uk", "UA", ""));
        allLocales.add(new Locale("en", "US", ""));
        allLocales.add(new Locale("es", "US", ""));
        allLocales.add(new Locale("es", "UY", ""));
        allLocales.add(new Locale("es", "VE", ""));
        allLocales.add(new Locale("vi", "VN", ""));
        allLocales.add(new Locale("ar", "YE", ""));
        allLocales.add(new Locale("en", "ZA", ""));

        return allLocales;
    }
}
