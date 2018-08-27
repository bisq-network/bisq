package network.bisq.api.arquillian;

import org.arquillian.cube.CubeController;
import org.arquillian.cube.spi.event.lifecycle.BeforeStop;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;

public class CubeLogger {

    @SuppressWarnings({"unused", "UnusedParameters"})
    public void beforeContainerStop(@Observes BeforeStop event, CubeController cubeController, ArquillianDescriptor arquillianDescriptor, TestClass testClass) {
        if (isExtensionEnabled(arquillianDescriptor)) {
            final String cubeId = event.getCubeId();
            System.out.println("=====================================================================================");
            System.out.println("Start of container logs: " + cubeId + " from " + testClass.getName());
            System.out.println("=====================================================================================");
            cubeController.copyLog(cubeId, false, true, true, true, -1, System.out);
            System.out.println("=====================================================================================");
            System.out.println("End of container logs: " + cubeId + " from " + testClass.getName());
            System.out.println("=====================================================================================");
        }
    }

    private static boolean isExtensionEnabled(ArquillianDescriptor arquillianDescriptor) {
        final String dumpContainerLogs = arquillianDescriptor.extension("cubeLogger").getExtensionProperty("enable");
        return Boolean.parseBoolean(dumpContainerLogs);
    }

}
