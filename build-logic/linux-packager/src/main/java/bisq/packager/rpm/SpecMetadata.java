package bisq.packager.rpm;

public record SpecMetadata(String appName,
                           String appVersion,
                           String appSummary,
                           String sourceFileName) {
}
