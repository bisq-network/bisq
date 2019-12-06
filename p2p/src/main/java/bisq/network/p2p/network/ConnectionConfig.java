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

package bisq.network.p2p.network;

import bisq.network.NetworkOptionKeys;

import javax.inject.Named;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionConfig {
    public static final int MSG_THROTTLE_PER_SEC = 200;        // With MAX_MSG_SIZE of 200kb results in bandwidth of 40MB/sec or 5 mbit/sec
    public static final int MSG_THROTTLE_PER_10_SEC = 1000;    // With MAX_MSG_SIZE of 200kb results in bandwidth of 20MB/sec or 2.5 mbit/sec
    public static final int SEND_MSG_THROTTLE_TRIGGER = 20;    // Time in ms when we trigger a sleep if 2 messages are sent
    public static final int SEND_MSG_THROTTLE_SLEEP = 50;      // Pause in ms to sleep if we get too many messages to send

    @Getter
    private int msgThrottlePerSec;
    @Getter
    private int msgThrottlePer10Sec;
    @Getter
    private int sendMsgThrottleTrigger;
    @Getter
    private int sendMsgThrottleSleep;

    @Inject
    public ConnectionConfig(@Named(NetworkOptionKeys.MSG_THROTTLE_PER_SEC) int msgThrottlePerSec,
                            @Named(NetworkOptionKeys.MSG_THROTTLE_PER_10_SEC) int msgThrottlePer10Sec,
                            @Named(NetworkOptionKeys.SEND_MSG_THROTTLE_TRIGGER) int sendMsgThrottleTrigger,
                            @Named(NetworkOptionKeys.SEND_MSG_THROTTLE_SLEEP) int sendMsgThrottleSleep) {
        this.msgThrottlePerSec = msgThrottlePerSec;
        this.msgThrottlePer10Sec = msgThrottlePer10Sec;
        this.sendMsgThrottleTrigger = sendMsgThrottleTrigger;
        this.sendMsgThrottleSleep = sendMsgThrottleSleep;

        log.info(this.toString());
    }


    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "\n     msgThrottlePerSec=" + msgThrottlePerSec +
                ",\n     msgThrottlePer10Sec=" + msgThrottlePer10Sec +
                ",\n     sendMsgThrottleTrigger=" + sendMsgThrottleTrigger +
                ",\n     sendMsgThrottleSleep=" + sendMsgThrottleSleep +
                "\n}";
    }
}
