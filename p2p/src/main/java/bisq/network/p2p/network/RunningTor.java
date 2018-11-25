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

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.berndpruenster.netlayer.tor.ExternalTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class creates a brand new instance of the Tor onion router.
 * 
 * When asked, the class checks for the authentication method selected and
 * connects to the given control port. Finally, a {@link Tor} instance is
 * returned for further use.
 * 
 * @author Florian Reimair
 *
 */
public class RunningTor extends TorMode {

    private static final Logger log = LoggerFactory.getLogger(RunningTor.class);
    private final int controlPort;
    private final String password;
    private final String torDir;
    private final File cookieFile;
    private final boolean useSafeCookieAuthentication;


    public RunningTor(final File torDir, final int controlPort, final String password, final String cookieFile,
            final boolean useSafeCookieAuthentication) {
        this.torDir = torDir.getAbsolutePath();
        this.controlPort = controlPort;
        this.password = password;
        this.cookieFile = new File(cookieFile);
        this.useSafeCookieAuthentication = useSafeCookieAuthentication;
    }

    @Override
    public Tor getTor() throws IOException, TorCtlException {
        long ts1 = new Date().getTime();

        log.info("Connecting to running tor");

        Tor result;
        if (!password.isEmpty())
            result = new ExternalTor(controlPort, password);
        else if (cookieFile.exists())
            result = new ExternalTor(controlPort, cookieFile, useSafeCookieAuthentication);
        else
            result = new ExternalTor(controlPort);

        log.info(
                "\n################################################################\n"
                        + "Connecting to Tor successful after {} ms. Start publishing hidden service.\n"
                        + "################################################################",
                (new Date().getTime() - ts1)); // takes usually a few seconds

        return result;
    }

    @Override
    public String getHiddenServiceDirectory() {
        return torDir + File.separator + "externalTorHiddenService";
    }

}
