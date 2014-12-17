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

package io.bitsquare.app.gui;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

public class MockUpdateProcess extends UpdateProcess {
    private static final Logger log = LoggerFactory.getLogger(MockUpdateProcess.class);

    @Inject
    public MockUpdateProcess(Environment environment) {
        super(environment);
    }

    @Override
    protected void init(Environment environment) {

       /* timeoutTimer.stop();
        state.set(State.UPDATE_AVAILABLE);*/

        state.set(State.UP_TO_DATE);
        timeoutTimer.stop();
        process.onCompleted();

      /*  state.set(State.FAILURE);
        errorMessage = "dummy exc.";
         timeoutTimer.stop();
        process.onCompleted();*/

    }

    @Override
    public void restart() {
        log.debug("restart requested");
    }
}
