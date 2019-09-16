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

package bisq.core.xmr;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import bisq.core.locale.GlobalSettings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmrFormatter {
	
	public static final BigInteger MINIMUM_SENDABLE_AMOUNT = new BigInteger("2000000000");
	
    private DecimalFormat amountFormat;

    @Inject
    public XmrFormatter() {
        super();

        setFormatter(GlobalSettings.getLocale());
        amountFormat.setMinimumFractionDigits(2);
    }

    private void setFormatter(Locale locale) {
        amountFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        amountFormat.setMinimumFractionDigits(12);
        amountFormat.setMaximumFractionDigits(12);
    }
    
    public static BigDecimal formatAsScaled(BigInteger amount) {
    	return new BigDecimal(amount != null ? amount : BigInteger.ZERO, 12);
    }

    public String formatBigInteger(BigInteger amount) {
    	BigDecimal scaledAmount = formatAsScaled(amount);
        return amountFormat.format(scaledAmount).concat(" XMR");
    }

    public String formatDateTime(Date date) {
        return formatDateTime(date, true);
    }

    public String formatDateTime(Date date, boolean useLocaleAndLocalTimezone) {
        Locale locale = useLocaleAndLocalTimezone ? getLocale() : Locale.US;
        DateFormat dateInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
        DateFormat timeInstance = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
        if (!useLocaleAndLocalTimezone) {
            dateInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return formatDateTime(date, dateInstance, timeInstance);
    }

    public String formatDateTime(Date date, DateFormat dateFormatter, DateFormat timeFormatter) {
        if (date != null) {
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public Locale getLocale() {
        return GlobalSettings.getLocale();
    }
}
