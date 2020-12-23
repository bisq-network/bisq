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

package bisq.apitest.linux;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.linux.BashCommand.isAlive;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static joptsimple.internal.Strings.EMPTY;



import bisq.apitest.config.ApiTestConfig;

@Slf4j
public class BitcoinDaemon extends AbstractLinuxProcess implements LinuxProcess {

    public BitcoinDaemon(ApiTestConfig config) {
        super("bitcoind", config);
    }

    @Override
    public void start() throws InterruptedException, IOException {

        // If the bitcoind binary is dynamically linked to berkeley db libs, export the
        // configured berkeley-db lib path.  If statically linked, the berkeley db lib
        // path will not be exported.
        String berkeleyDbLibPathExport = config.berkeleyDbLibPath.equals(EMPTY) ? EMPTY
                : "export LD_LIBRARY_PATH=" + config.berkeleyDbLibPath + "; ";

        String bitcoindCmd = berkeleyDbLibPathExport
                + config.bitcoinPath + "/bitcoind"
                + " -datadir=" + config.bitcoinDatadir
                + " -daemon"
                + " -regtest=1"
                + " -server=1"
                + " -txindex=1"
                + " -peerbloomfilters=1"
                + " -debug=net"
                + " -fallbackfee=0.0002"
                + " -rpcport=" + config.bitcoinRpcPort
                + " -rpcuser=" + config.bitcoinRpcUser
                + " -rpcpassword=" + config.bitcoinRpcPassword
                + " -blocknotify=" + "\"" + config.bitcoinDatadir + "/blocknotify" + " %s\"";

        BashCommand cmd = new BashCommand(bitcoindCmd).run();
        log.info("Starting ...\n$ {}", cmd.getCommand());

        if (cmd.getExitStatus() != 0) {
            startupExceptions.add(new IllegalStateException(
                    format("Error starting bitcoind%nstatus: %d%nerror msg: %s",
                            cmd.getExitStatus(), cmd.getError())));
            return;
        }

        pid = BashCommand.getPid("bitcoind");
        if (!isAlive(pid))
            throw new IllegalStateException("Error starting regtest bitcoind daemon:\n" + cmd.getCommand());

        log.info("Running with pid {}", pid);
        log.info("Log {}", config.bitcoinDatadir + "/regtest/debug.log");
    }

    @Override
    public long getPid() {
        return this.pid;
    }

    @Override
    public void shutdown() {
        try {
            log.info("Shutting down bitcoind daemon...");

            if (!isAlive(pid)) {
                this.shutdownExceptions.add(new IllegalStateException("Bitcoind already shut down."));
                return;
            }

            if (new BashCommand("kill -15 " + pid).run().getExitStatus() != 0) {
                this.shutdownExceptions.add(new IllegalStateException("Could not shut down bitcoind; probably already stopped."));
                return;
            }

            MILLISECONDS.sleep(2500); // allow it time to shutdown

            if (isAlive(pid)) {
                this.shutdownExceptions.add(new IllegalStateException(
                        format("Could not kill bitcoind process with pid %d.", pid)));
                return;
            }

            log.info("Stopped");
        } catch (InterruptedException ignored) {
            // empty
        } catch (IOException e) {
            this.shutdownExceptions.add(new IllegalStateException("Error shutting down bitcoind.", e));
        }
    }
}
