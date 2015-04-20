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

package io.bitsquare.p2p;

import java.util.concurrent.Executor;

import net.tomp2p.dht.PeerDHT;

public class BaseP2PService implements P2PService {

    private static Executor userThread;

    public static void setUserThread(Executor userThread) {
        BaseP2PService.userThread = userThread;
    }

    public static Executor getUserThread() {
        return userThread;
    }

    protected Executor executor;
    protected PeerDHT peerDHT;
    private int openRequests = 0;

    @Override
    public void bootstrapCompleted() {
        this.executor = BaseP2PService.userThread;
    }

    @Override
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }


    protected void openRequestsUp() {
        executor.execute(() -> openRequests++);
    }

    protected void openRequestsDown() {
        executor.execute(() -> openRequests--);
    }

    @Override
    public void shutDown() {
        long ts = System.currentTimeMillis();
        // wait max. 10 sec. for open calls to complete
        while (openRequests > 0 && (System.currentTimeMillis() - ts) < 10000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
