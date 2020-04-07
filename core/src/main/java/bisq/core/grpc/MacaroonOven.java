package bisq.core.grpc;

import bisq.common.storage.FileUtil;

import java.io.File;
import java.io.PrintWriter;

import lombok.extern.slf4j.Slf4j;



import com.github.nitram509.jmacaroons.Macaroon;
import com.github.nitram509.jmacaroons.MacaroonsBuilder;

@Slf4j
public class MacaroonOven {

    private final String location; // TODO what is correct bisqd hostname?
    private final String secretKey;
    private final String identifier;
    private final File targetFile;

    public MacaroonOven(String location, String secretKey, String identifier, File targetFile) {
        this.location = location;
        this.secretKey = secretKey;
        this.identifier = identifier;
        this.targetFile = targetFile;
    }

    public Macaroon bake() {
        Macaroon macaroon = MacaroonsBuilder.create(location, secretKey, identifier);
        persist(macaroon);
        return macaroon;
    }

    private void persist(Macaroon macaroon) {
        File tempFile = null;
        PrintWriter printWriter = null;
        try {
            tempFile = File.createTempFile("temp", null, targetFile.getParentFile());
            printWriter = new PrintWriter(tempFile);
            printWriter.write(macaroon.serialize());
            FileUtil.renameFile(tempFile, targetFile);
        } catch (Throwable t) {
            log.error("could not create macaroon file " + targetFile.toString(), t);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("temp file still exists after failed save, deleting it now");
                if (!tempFile.delete())
                    log.error("cannot delete temp file");
            }
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }
}
