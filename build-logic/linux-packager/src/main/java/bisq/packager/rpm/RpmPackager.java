package bisq.packager.rpm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.concurrent.TimeUnit;



import bisq.packager.Packager;

public class RpmPackager implements Packager {
    private static final String SOURCES_DIR_NAME = "SOURCES";
    private static final String SPECS_DIR_NAME = "SPECS";

    private final Path workingDirectory;
    private final Path tarballPath;
    private final SpecMetadata specMetadata;
    private final Path installDistDir;

    public RpmPackager(Path workingDirectory, Path tarballPath, SpecMetadata specMetadata, Path installDistDir) {
        this.workingDirectory = workingDirectory;
        this.tarballPath = tarballPath;
        this.specMetadata = specMetadata;
        this.installDistDir = installDistDir;
    }

    @Override
    public void createPackage() {
        createWorkingDirectorySubfolder();
        copyTarballToSourcesDir();
        File specFile = createSpecFile();
        buildRpmPackage(specFile);
    }

    private void createWorkingDirectorySubfolder() {
        try {
            var requiredDirNames = List.of("BUILD", "RPMS", SOURCES_DIR_NAME, SPECS_DIR_NAME, "SRPMS");
            for (String directoryName : requiredDirNames) {
                Path path = workingDirectory.resolve(directoryName);
                Files.createDirectory(path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create working directory subfolders.", e);
        }
    }

    private void copyTarballToSourcesDir() {
        try {
            Path targetPath = workingDirectory.resolve(SOURCES_DIR_NAME)
                    .resolve("desktop.tar");
            Files.copy(tarballPath, targetPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy tarball file.", e);
        }
    }

    private File createSpecFile() {
        try {
            Path bisqSpec = workingDirectory.resolve(SPECS_DIR_NAME)
                    .resolve("bisq.spec");
            String spec = new SpecBuilder(specMetadata, installDistDir).build();
            Files.writeString(bisqSpec, spec, StandardCharsets.UTF_8);

            return bisqSpec.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create spec file.", e);
        }
    }

    private void buildRpmPackage(File specFile) {
        try {
            List<String> command = List.of("rpmbuild",
                    "--define", "_topdir " + workingDirectory.toAbsolutePath(),
                    "-ba", specFile.getAbsolutePath());
            Path logFile = workingDirectory.resolve("log.txt");

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            boolean exited = process.waitFor(15, TimeUnit.MINUTES);
            if (!exited) {
                throw new IllegalStateException("rpmbuild process still running after 15 minutes.");
            } else {
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new IllegalStateException("rpmbuild process exited with exit code " + exitCode);
                }
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("rpmbuild thread got interrupted.", e);
        } catch (IOException e) {
            throw new IllegalStateException("rpmbuild process raised IOException.", e);
        }
    }

}
