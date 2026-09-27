package bisq.deb_packager;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TarXzArchiveCreator {
    private final long fileTimestampInEpochMillis;
    private final Path sourceDir;
    private final Path outputFile;

    public TarXzArchiveCreator(long fileTimestampInEpochSeconds, Path sourceDir, Path outputFile) {
        this.fileTimestampInEpochMillis = fileTimestampInEpochSeconds * 1000L;
        this.sourceDir = sourceDir;
        this.outputFile = outputFile;
    }

    public void create() throws IOException {
        try (OutputStream fileOutputStream = Files.newOutputStream(outputFile);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
             XZCompressorOutputStream xzCompressorOutputStream = new XZCompressorOutputStream(bufferedOutputStream);
             TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(xzCompressorOutputStream, "UTF-8")) {

            enablePosixPaxHeaders(tarArchiveOutputStream);

            List<Path> paths = getSortedPaths();
            for (Path path : paths) {
                if (path.equals(sourceDir)) {
                    continue;
                }

                boolean isDir = Files.isDirectory(path);
                String entryName = getNormalizedPath(path) + (isDir ? "/" : "");

                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(isDir ? 0 : Files.size(path));

                makeTarArchiveEntryReproducible(entry, isDir, path);
                tarArchiveOutputStream.putArchiveEntry(entry);

                if (!isDir && Files.isRegularFile(path)) {
                    Files.copy(path, tarArchiveOutputStream);
                }

                tarArchiveOutputStream.closeArchiveEntry();
            }

            tarArchiveOutputStream.finish();
        }
    }

    private void enablePosixPaxHeaders(TarArchiveOutputStream tarArchiveOutputStream) {
        tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
    }

    private List<Path> getSortedPaths() throws IOException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream.sorted(Comparator.comparing(this::getNormalizedPath))
                    .collect(Collectors.toList());
        }
    }

    private String getNormalizedPath(Path path) {
        return sourceDir.relativize(path)
                .toString()
                .replace('\\', '/');
    }

    private void makeTarArchiveEntryReproducible(TarArchiveEntry entry,
                                                 boolean isDir,
                                                 Path path) {
        entry.setModTime(fileTimestampInEpochMillis);

        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setUserName("root");
        entry.setGroupName("root");

        entry.setMode(isDir || Files.isExecutable(path) ? 0755 : 0644);
    }
}
