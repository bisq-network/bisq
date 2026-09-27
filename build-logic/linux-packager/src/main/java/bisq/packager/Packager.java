package bisq.packager;

import java.io.IOException;

public interface Packager {
    void createPackage() throws IOException;
}
