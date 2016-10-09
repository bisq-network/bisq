/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocaleUtil {
    private static final Logger log = LoggerFactory.getLogger(LocaleUtil.class);

    public static List<Locale> getAllLocales() {
        List<Locale> allLocales = new ArrayList<>();

        allLocales.add(new Locale("", "AE", ""));
        allLocales.add(new Locale("", "AL", ""));
        allLocales.add(new Locale("", "AR", ""));
        allLocales.add(new Locale("", "AT", ""));
        allLocales.add(new Locale("", "AU", ""));
        allLocales.add(new Locale("", "BA", ""));
        allLocales.add(new Locale("", "BE", ""));
        allLocales.add(new Locale("", "BG", ""));
        allLocales.add(new Locale("", "BH", ""));
        allLocales.add(new Locale("", "BO", ""));
        allLocales.add(new Locale("", "BR", ""));
        allLocales.add(new Locale("", "BY", ""));
        allLocales.add(new Locale("", "CA", ""));
        allLocales.add(new Locale("", "CH", ""));
        allLocales.add(new Locale("", "CL", ""));
        allLocales.add(new Locale("", "CN", ""));
        allLocales.add(new Locale("", "CO", ""));
        allLocales.add(new Locale("", "CR", ""));
        allLocales.add(new Locale("", "CS", ""));
        allLocales.add(new Locale("", "CU", ""));
        allLocales.add(new Locale("", "CY", ""));
        allLocales.add(new Locale("", "CZ", ""));
        allLocales.add(new Locale("", "DE", ""));
        allLocales.add(new Locale("", "DK", ""));
        allLocales.add(new Locale("", "DO", ""));
        allLocales.add(new Locale("", "DZ", ""));
        allLocales.add(new Locale("", "EC", ""));
        allLocales.add(new Locale("", "EE", ""));
        allLocales.add(new Locale("", "EG", ""));
        allLocales.add(new Locale("", "ES", ""));
        allLocales.add(new Locale("", "FI", ""));
        allLocales.add(new Locale("", "FR", ""));
        allLocales.add(new Locale("", "GB", ""));
        allLocales.add(new Locale("", "GR", ""));
        allLocales.add(new Locale("", "GT", ""));
        allLocales.add(new Locale("", "HK", ""));
        allLocales.add(new Locale("", "HN", ""));
        allLocales.add(new Locale("", "HR", ""));
        allLocales.add(new Locale("", "HU", ""));
        allLocales.add(new Locale("", "ID", ""));
        allLocales.add(new Locale("", "IE", ""));
        allLocales.add(new Locale("", "IL", ""));
        allLocales.add(new Locale("", "IN", ""));
        allLocales.add(new Locale("", "IQ", ""));
        allLocales.add(new Locale("", "IS", ""));
        allLocales.add(new Locale("", "IT", ""));
        allLocales.add(new Locale("", "JO", ""));
        allLocales.add(new Locale("", "JP", ""));
        allLocales.add(new Locale("", "KH", ""));
        allLocales.add(new Locale("", "KR", ""));
        allLocales.add(new Locale("", "KW", ""));
        allLocales.add(new Locale("", "KZ", ""));
        allLocales.add(new Locale("", "LB", ""));
        allLocales.add(new Locale("", "LT", ""));
        allLocales.add(new Locale("", "LU", ""));
        allLocales.add(new Locale("", "LV", ""));
        allLocales.add(new Locale("", "LY", ""));
        allLocales.add(new Locale("", "MA", ""));
        allLocales.add(new Locale("", "MD", ""));
        allLocales.add(new Locale("", "ME", ""));
        allLocales.add(new Locale("", "MK", ""));
        allLocales.add(new Locale("", "MT", ""));
        allLocales.add(new Locale("", "MX", ""));
        allLocales.add(new Locale("", "MY", ""));
        allLocales.add(new Locale("", "NI", ""));
        allLocales.add(new Locale("", "NL", ""));
        allLocales.add(new Locale("", "NO", ""));
        allLocales.add(new Locale("", "NZ", ""));
        allLocales.add(new Locale("", "OM", ""));
        allLocales.add(new Locale("", "PA", ""));
        allLocales.add(new Locale("", "PE", ""));
        allLocales.add(new Locale("", "PH", ""));
        allLocales.add(new Locale("", "PL", ""));
        allLocales.add(new Locale("", "PR", ""));
        allLocales.add(new Locale("", "PT", ""));
        allLocales.add(new Locale("", "PY", ""));
        allLocales.add(new Locale("", "QA", ""));
        allLocales.add(new Locale("", "RO", ""));
        allLocales.add(new Locale("", "RS", ""));
        allLocales.add(new Locale("", "RU", ""));
        allLocales.add(new Locale("", "SA", ""));
        allLocales.add(new Locale("", "SD", ""));
        allLocales.add(new Locale("", "SE", ""));
        allLocales.add(new Locale("", "SG", ""));
        allLocales.add(new Locale("", "SI", ""));
        allLocales.add(new Locale("", "SK", ""));
        allLocales.add(new Locale("", "SV", ""));
        allLocales.add(new Locale("", "SY", ""));
        allLocales.add(new Locale("", "TH", ""));
        allLocales.add(new Locale("", "TN", ""));
        allLocales.add(new Locale("", "TR", ""));
        allLocales.add(new Locale("", "TW", ""));
        allLocales.add(new Locale("", "UA", ""));
        allLocales.add(new Locale("", "US", ""));
        allLocales.add(new Locale("", "UY", ""));
        allLocales.add(new Locale("", "VE", ""));
        allLocales.add(new Locale("", "VN", ""));
        allLocales.add(new Locale("", "YE", ""));
        allLocales.add(new Locale("", "ZA", ""));

        return allLocales;
    }
}
