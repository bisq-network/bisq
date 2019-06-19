package bisq.api.http.arquillian;

import org.jboss.arquillian.core.spi.LoadableExtension;

public class CubeLoggerExtension implements LoadableExtension {

    @Override
    public void register(ExtensionBuilder extensionBuilder) {
        extensionBuilder.observer(CubeLogger.class);
    }
}
