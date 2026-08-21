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

package bisq.apitest;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;



import bisq.apitest.linux.BashCommand;

@Slf4j
class SmokeTestBashCommand {

    public SmokeTestBashCommand() {
    }

    public void runSmokeTest() {
        try {
            BashCommand cmd = new BashCommand("ls -l").run();
            log.info("$ {}\n{}", cmd.getCommand(), cmd.getOutput());

            cmd = new BashCommand("free -g").run();
            log.info("$ {}\n{}", cmd.getCommand(), cmd.getOutput());

            cmd = new BashCommand("date").run();
            log.info("$ {}\n{}", cmd.getCommand(), cmd.getOutput());

            cmd = new BashCommand("netstat -a | grep localhost").run();
            log.info("$ {}\n{}", cmd.getCommand(), cmd.getOutput());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
