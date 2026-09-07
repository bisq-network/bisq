package bisq.deb_packager;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

public class ControlArchiveCreator {
    private final String bisqVersion;
    private final Path workingDirectory;
    private final Path targetArchivePath;

    public ControlArchiveCreator(String bisqVersion, Path workingDirectory, Path targetArchivePath) {
        this.bisqVersion = bisqVersion;
        this.workingDirectory = workingDirectory;
        this.targetArchivePath = targetArchivePath;
    }

    void createControlDirectory() throws IOException {
        Path controlFilePath = workingDirectory.resolve("control");

        String maintainer = "Alejandro Garcia <alejandro.garcia@disroot.org>";
        String jrePackage = "openjdk-21-jre";

        StringBuilder stringBuilder = new StringBuilder("Package: bisq\n");

        // Structure: "Version: 1.10.3-1\n"
        stringBuilder.append("Version: ")
                .append(bisqVersion)
                .append("-1\n");

        stringBuilder.append("Section: misc\n");

        // Structure (RFC 822): "Maintainer: Alejandro Garcia <alejandro.garcia@disroot.org>\n"
        stringBuilder.append("Maintainer: ")
                .append(maintainer)
                .append('\n');

        stringBuilder.append("Priority: optional\n")
                .append("Architecture: amd64\n")
                .append("Provides: bisq\n")
                .append("Description: A decentralized bitcoin exchange network.\n");

        // Structure: "Depends: libasound2, libbsd0, libc6\n"
        stringBuilder.append("Depends: ")
                .append(jrePackage)
                .append('\n');

        stringBuilder.append("\n\n");
        Files.writeString(controlFilePath, stringBuilder.toString());
        createTarXz(controlFilePath, targetArchivePath);
    }


    private void createTarXz(Path sourceDirPath, Path targetPath) throws IOException {
        long epochSeconds = DataArchiveCreator.getSourceDateEpoch();
        new TarXzArchiveCreator(epochSeconds, sourceDirPath.getParent(), targetPath)
                .create();
    }
}
