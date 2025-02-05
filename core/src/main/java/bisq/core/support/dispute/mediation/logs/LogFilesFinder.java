package bisq.core.support.dispute.mediation.logs;

import java.nio.file.Path;

import java.io.File;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LogFilesFinder {
    private final Path dataDir;

    public LogFilesFinder(Path dataDir) {
        this.dataDir = dataDir;
    }

    public List<File> find() {
        File dataDirFile = dataDir.toFile();
        File[] files = dataDirFile.listFiles((dir, name) -> name.endsWith(".log"));
        return files == null ? Collections.emptyList() : Arrays.asList(files);
    }
}
