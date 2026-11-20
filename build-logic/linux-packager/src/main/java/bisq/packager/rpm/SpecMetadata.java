package bisq.packager.rpm;

public record SpecMetadata(String sourceDirectoryName,
                           String appVersion,
                           String appSummary,
                           String sourceFileName) {
}
