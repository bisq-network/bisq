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

package bisq.monitor.reporter;

import bisq.monitor.Reporter;

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple console reporter.
 *
 * @author Florian Reimair
 */
public class ConsoleReporter extends Reporter {

    @Override
    public void report(long value, String prefix) {
        HashMap<String, String> result = new HashMap<>();
        result.put("", String.valueOf(value));
        report(result, prefix);

    }

    @Override
    public void report(long value) {
        HashMap<String, String> result = new HashMap<>();
        result.put("", String.valueOf(value));
        report(result);
    }

    @Override
    public void report(Map<String, String> values, String prefix) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        values.forEach((key, value) -> {
            report(key, value, timestamp, prefix);
        });
    }

    @Override
    public void report(String key, String value, String timestamp, String prefix) {
        System.err.println("Report: bisq" + (Version.getBaseCurrencyNetwork() != 0 ? "-" + BaseCurrencyNetwork.values()[Version.getBaseCurrencyNetwork()].getNetwork() : "")
                + (prefix.isEmpty() ? "" : "." + prefix)
                + (key.isEmpty() ? "" : "." + key)
                + " " + value + " " + timestamp);
    }

    @Override
    public void report(Map<String, String> values) {
        report(values, "");
    }
}
