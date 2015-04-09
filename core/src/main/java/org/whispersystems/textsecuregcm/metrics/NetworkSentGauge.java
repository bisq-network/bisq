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


import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.whispersystems.textsecuregcm.util.Pair;


public class NetworkSentGauge extends NetworkGauge {

    private final Logger logger = LoggerFactory.getLogger(NetworkSentGauge.class);

    private long lastTimestamp;
    private long lastSent;

    @Override
    public Long getValue() {
        try {
            long timestamp = System.currentTimeMillis();
            Pair<Long, Long> sentAndReceived = getSentReceived();
            long result = 0;

            if (lastTimestamp != 0) {
                result = sentAndReceived.first() - lastSent;
                lastSent = sentAndReceived.first();
            }

            lastTimestamp = timestamp;
            return result;
        } catch (IOException e) {
            logger.warn("NetworkSentGauge", e);
            return -1L;
        }
    }
}
