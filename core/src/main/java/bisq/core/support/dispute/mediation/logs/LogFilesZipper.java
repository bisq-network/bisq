package bisq.core.support.dispute.mediation.logs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogFilesZipper {
    private final Path destinationPath;

    public LogFilesZipper(Path destinationPath) {
        this.destinationPath = destinationPath;
    }

    public void zip(List<File> filesToZip) throws IOException {
        zip(filesToZip, Map.of());
    }

    /**
     * @param redactions the redaction of the text of each file of filesToZip which goes through one before it is
     *                   zipped
     */
    public void zip(List<File> filesToZip, Map<File, UnaryOperator<String>> redactions) throws IOException {
        try (ZipOutputStream zipOutputStream = openZipOutputStream()) {
            zipOutputStream.setLevel(ZipOutputStream.DEFLATED);

            for (File fileToZip : filesToZip) {
                ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                zipOutputStream.putNextEntry(zipEntry);
                UnaryOperator<String> redaction = redactions.get(fileToZip);
                if (redaction != null) {
                    zipRedactedFile(fileToZip, redaction, zipOutputStream);
                } else {
                    zipFile(fileToZip, zipOutputStream);
                }
            }
        }
    }

    private ZipOutputStream openZipOutputStream() throws FileNotFoundException {
        File destinationFile = destinationPath.toFile();
        return new ZipOutputStream(new FileOutputStream(destinationFile));
    }

    // ISO-8859-1 maps every byte to one character and back, so the bytes which the redaction does not replace stay as
    // they are, also where the file is no valid UTF-8
    private static void zipRedactedFile(File fileToZip,
                                        UnaryOperator<String> redaction,
                                        ZipOutputStream zipOutputStream) throws IOException {
        String text = new String(Files.readAllBytes(fileToZip.toPath()), StandardCharsets.ISO_8859_1);
        zipOutputStream.write(redaction.apply(text).getBytes(StandardCharsets.ISO_8859_1));
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
