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

package bisq.api.http.arquillian;

import org.arquillian.cube.CubeController;
import org.arquillian.cube.spi.event.lifecycle.BeforeStop;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;

public class CubeLogger {

    private static boolean isExtensionEnabled(ArquillianDescriptor arquillianDescriptor) {
        String dumpContainerLogs = arquillianDescriptor.extension("cubeLogger").getExtensionProperty("enable");
        return Boolean.parseBoolean(dumpContainerLogs);
    }

    @SuppressWarnings({"unused", "UnusedParameters"})
    public void beforeContainerStop(@Observes BeforeStop event, CubeController cubeController, ArquillianDescriptor arquillianDescriptor, TestClass testClass) {
        if (isExtensionEnabled(arquillianDescriptor)) {
            String cubeId = event.getCubeId();
            System.out.println("=====================================================================================");
            System.out.println("Start of container logs: " + cubeId + " from " + testClass.getName());
            System.out.println("=====================================================================================");
            cubeController.copyLog(cubeId, false, true, true, true, -1, System.out);
            System.out.println("=====================================================================================");
            System.out.println("End of container logs: " + cubeId + " from " + testClass.getName());
            System.out.println("=====================================================================================");
        }
    }

}
