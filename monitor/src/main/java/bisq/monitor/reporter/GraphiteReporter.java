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

import org.berndpruenster.netlayer.tor.TorSocket;

import java.net.URL;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

/**
 * Reports our findings to a graphite service.
 *
 * @author Florian Reimair
 */
public class GraphiteReporter extends Reporter {

    @Override
    public void report(long value, String prefix) {
        HashMap<String, String> result = new HashMap<>();
        result.put("", String.valueOf(value));
        report(result, prefix);

    }

    @Override
    public void report(long value) {
        report(value, "bisq");
    }

    @Override
    public void report(Map<String, String> values, String prefix) {
        long timestamp = System.currentTimeMillis() / 1000;
        values.forEach((key, value) -> {
            String report = prefix + ("".equals(key) ? "" : (prefix.isEmpty() ? "" : ".") + key) + " " + value + " "
                    + timestamp + "\n";

            URL url;
            try {
                url = new URL(configuration.getProperty("serviceUrl"));
                TorSocket socket = new TorSocket(url.getHost(), url.getPort());

                socket.getOutputStream().write(report.getBytes());
                socket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        });
    }

    @Override
    public void report(Map<String, String> values) {
        report(values, "bisq");
    }
}
