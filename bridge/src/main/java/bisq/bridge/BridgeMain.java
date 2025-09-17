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

package bisq.bridge;

import bisq.core.app.misc.ExecutableForAppWithP2p;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BridgeMain extends ExecutableForAppWithP2p {
    public static void main(String[] args) {
        new BridgeMain().execute(args);
    }

    private Bridge bridge;

    public BridgeMain() {
        super("Bisq Bridge", "bisq-bridge", "bisq_bridge", Version.VERSION);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        try {
            // Prevent from terminating
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Thread interrupted while running BridgeMain", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();
        bridge = new Bridge(injector);
    }

    @Override
    protected void onHiddenServicePublished() {
        UserThread.runAfter(this::setupConnectionLossCheck, 60);
    }

    @Override
    protected void startApplication() {
        super.startApplication();
        bridge.startApplication();
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        if (bridge != null) {
            bridge.shutDown();
        }
        super.gracefulShutDown(resultHandler);
    }
}
