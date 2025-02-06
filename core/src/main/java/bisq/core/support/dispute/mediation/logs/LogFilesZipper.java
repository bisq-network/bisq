package bisq.core.support.dispute.mediation.logs;

import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogFilesZipper {
    private final Path destinationPath;

    public LogFilesZipper(Path destinationPath) {
        this.destinationPath = destinationPath;
    }

    public void zip(List<Path> filesToZip) throws IOException {
        try (ZipOutputStream zipOutputStream = openZipOutputStream()) {
            zipOutputStream.setLevel(ZipOutputStream.DEFLATED);

            for (Path path : filesToZip) {
                ZipEntry zipEntry = new ZipEntry(path.getFileName().toString());
                zipOutputStream.putNextEntry(zipEntry);

                File fileToZip = path.toFile();
                zipFile(fileToZip, zipOutputStream);
            }
        }
    }

    private ZipOutputStream openZipOutputStream() throws FileNotFoundException {
        File destinationFile = destinationPath.toFile();
        return new ZipOutputStream(new FileOutputStream(destinationFile));
    }

    private static void zipFile(File fileToZip, ZipOutputStream zipOutputStream) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(fileToZip)) {
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fileInputStream.read(bytes)) >= 0) {
                zipOutputStream.write(bytes, 0, length);
            }
        }
    }
}
