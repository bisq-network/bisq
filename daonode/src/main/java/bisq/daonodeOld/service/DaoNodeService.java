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

package bisq.daonodeOld.service;

import bisq.core.dao.state.DaoStateService;

import lombok.extern.slf4j.Slf4j;



import bisq.daonodeOld.web.WebServer;
import bisq.daonodeOld.web.jdk.JdkServer;

// Todo We should limit usage to localhost as its not intended at that stage to be used
// as a public API, but rather be used by Bisq 2 bridge clients or BSQ explorer nodes,
// both running in a localhost environment. As long that holds, we do not require a high
// level of protection against malicious usage.

// TODO This JDK http server is a super simple implementation. We might use some other
// lightweight REST server framework.
@Slf4j
public class DaoNodeService {
    private WebServer webServer;
    private DaoStateService daoStateService;

    public DaoNodeService(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }

    public void start(int port) {
        try {
            webServer = new JdkServer(port, daoStateService);
            webServer.start();
        } catch (Throwable t) {
            log.error(t.toString());
        }
    }

    public void shutDown() {
        if (webServer != null) {
            webServer.stop(0);
        }
    }
}
