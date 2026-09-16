package bisq.deb_packager;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class DebPackager {
    private final String versionNumber;
    private final Path sourceDirectory;
    private final Path workingDirectory;
    private final Path finalArchiveWorkingDirPath;

    public DebPackager(String versionNumber, Path sourceDirectory, Path workingDirectory) {
        this.versionNumber = versionNumber;
        this.sourceDirectory = sourceDirectory;
        this.workingDirectory = workingDirectory;
        this.finalArchiveWorkingDirPath = workingDirectory.resolve("final");
    }

    public void createPackage() throws IOException {
        Files.createDirectories(workingDirectory);
        Files.createDirectories(finalArchiveWorkingDirPath);

        createDebianBinaryFile();
        createControlArchive();
        createDataArchive();

        Path debPackagePath = workingDirectory.resolve("bisq_" + versionNumber + "_amd64.deb");
        createArArchive(finalArchiveWorkingDirPath, debPackagePath);
    }

    private void createDebianBinaryFile() throws IOException {
        Path debianBinaryFilePath = finalArchiveWorkingDirPath.resolve("debian-binary");
        Files.writeString(debianBinaryFilePath, "2.0\n");
    }

    private void createControlArchive() throws IOException {
        Path controlWorkDir = workingDirectory.resolve("control");
        Files.createDirectories(controlWorkDir);

        Path finalArchivePath = finalArchiveWorkingDirPath.resolve("control.tar.xz");
        var controlArchiveCreator = new ControlArchiveCreator(versionNumber, controlWorkDir, finalArchivePath);
        controlArchiveCreator.createControlDirectory();
    }

    private void createDataArchive() throws IOException {
        Path dataWorkDir = workingDirectory.resolve("data");
        Files.createDirectories(dataWorkDir);

        Path finalArchivePath = finalArchiveWorkingDirPath.resolve("data.tar.xz");
        var dataArchiveCreator = new DataArchiveCreator(sourceDirectory, dataWorkDir, finalArchivePath);
        dataArchiveCreator.create();
    }

    private void createArArchive(Path sourceFilesDirPath, Path targetArchivePath) {
        try {
            List<String> command = List.of("ar", "rcD",
                    targetArchivePath.toAbsolutePath().toString(),
                    "debian-binary",
                    "control.tar.xz",
                    "data.tar.xz");
            Path logFilePath = workingDirectory.resolve("ar_deb_archive.log");

            Process process = new ProcessBuilder(command)
                    .directory(sourceFilesDirPath.toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFilePath.toFile())
                    .start();

            boolean exited = process.waitFor(15, TimeUnit.MINUTES);
            if (!exited) {
                throw new IllegalStateException("Compress process still running after 15 minutes.");
            } else {
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new IllegalStateException("Compress process exited with exit code " + exitCode);
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Compress thread got interrupted.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Compress process raised IOException.", e);
        }
    }
}
