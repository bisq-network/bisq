/**
 * Copyright (C) 2014 Open WhisperSystems
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.whispersystems.textsecuregcm.metrics;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.codahale.metrics.Gauge;
import org.whispersystems.textsecuregcm.util.Pair;

public abstract class NetworkGauge implements Gauge<Long> {

    protected Pair<Long, Long> getSentReceived() throws IOException {
        File proc = new File("/proc/net/dev");
        BufferedReader reader = new BufferedReader(new FileReader(proc));
        String header = reader.readLine();
        String header2 = reader.readLine();

        long bytesSent = 0;
        long bytesReceived = 0;

        String interfaceStats;

        while ((interfaceStats = reader.readLine()) != null) {
            String[] stats = interfaceStats.split("\\s+");

            if (!stats[1].equals("lo:")) {
                bytesReceived += Long.parseLong(stats[2]);
                bytesSent += Long.parseLong(stats[10]);
            }
        }

        return new Pair<>(bytesSent, bytesReceived);
    }
}
