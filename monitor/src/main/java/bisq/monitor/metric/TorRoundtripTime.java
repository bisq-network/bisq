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

package bisq.monitor.metric;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksException;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

/**
 * A Metric to measure the round-trip time to the Bisq seednodes via plain tor.
 * 
 * @author Florian Reimair
 */
public class TorRoundtripTime extends Metric {

    private static final String HOSTS = "run.hosts";

    public TorRoundtripTime() {
        super();
    }

    @Override
    protected void execute() {
        SocksSocket socket = null;
        try {
            // fetch proxy
            Socks5Proxy proxy = Tor.getDefault().getProxy();

            // for each configured host
            for (String current : configuration.getProperty(HOSTS, "").split(",")) {
                // parse Url
                URL tmp = new URL(current);

                // start timer - we do not need System.nanoTime as we expect our result to be in
                // seconds time.
                long start = System.currentTimeMillis();

                // connect
                socket = new SocksSocket(proxy, tmp.getHost(), tmp.getPort());

                // by the time we get here, we are connected
                report(System.currentTimeMillis() - start);
            }
        } catch (TorCtlException | SocksException | UnknownHostException | MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            // close the connection
            if (socket != null)
                try {
                    socket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
    }
}
