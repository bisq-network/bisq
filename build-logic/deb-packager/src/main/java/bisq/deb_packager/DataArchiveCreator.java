package bisq.deb_packager;

import org.apache.commons.io.FileUtils;

import java.nio.file.Path;

import java.io.File;
import java.io.IOException;


public class DataArchiveCreator {
    private final Path sourceDirectory;
    private final Path workingDirectory;
    private final Path targetArchivePath;

    public DataArchiveCreator(Path sourceDirectory, Path workingDirectory, Path targetArchivePath) {
        this.sourceDirectory = sourceDirectory;
        this.workingDirectory = workingDirectory;
        this.targetArchivePath = targetArchivePath;
    }

    void create() throws IOException {
        Path bisqPath = workingDirectory.resolve("opt")
                .resolve("bisq");

        boolean isSuccess = bisqPath.toFile().mkdirs();
        if (!isSuccess) {
            throw new IllegalStateException("Couldn't create directory (" + bisqPath + ") for data tar content.");
        }

        File sourceDir = sourceDirectory.toFile();
        File targetDir = bisqPath.toFile();
        FileUtils.copyDirectory(sourceDir, targetDir);

        Path sourceDirPath = workingDirectory.resolve("opt");
        createTarXz(sourceDirPath, targetArchivePath);
    }

    private void createTarXz(Path sourceDirPath, Path targetPath) throws IOException {
        long epochSeconds = getSourceDateEpoch();
        new TarXzArchiveCreator(epochSeconds, sourceDirPath.getParent(), targetPath)
                .create();
    }

    static long getSourceDateEpoch() {
        String sourceDateEpoch = System.getenv("SOURCE_DATE_EPOCH");
        if (sourceDateEpoch == null || sourceDateEpoch.trim().isEmpty()) {
            throw new IllegalStateException("SOURCE_DATE_EPOCH environment variable is not set.");
        }
        return Long.parseLong(sourceDateEpoch.trim());
    }
}
