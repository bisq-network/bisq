package bisq.packager.rpm;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

import java.util.stream.Stream;

public class SpecBuilder {
    private final SpecMetadata metadata;
    private final Path installDistDir;

    public SpecBuilder(SpecMetadata metadata, Path installDistDir) {
        this.metadata = metadata;
        this.installDistDir = installDistDir;
    }

    public String build() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Name: ").append(metadata.appName()).append('\n');
        stringBuilder.append("Version: ").append(metadata.appVersion()).append('\n');
        stringBuilder.append("Release: 1%{?dist}\n");
        stringBuilder.append("Vendor: Bisq\n");
        stringBuilder.append("Summary: ").append(metadata.appSummary()).append('\n');
        stringBuilder.append("License: AGPLv3\n");
        stringBuilder.append("URL: https://github.com/bisq-network/bisq\n");
        stringBuilder.append("Source0: ").append(metadata.sourceFileName()).append('\n');
        stringBuilder.append("\n");
        stringBuilder.append("Requires: java-21-openjdk\n");
        stringBuilder.append("\n");
        stringBuilder.append("%global debug_package %{nil}\n");
        stringBuilder.append("\n");
        stringBuilder.append("%description\n");
        stringBuilder.append("Bisq is an open-source, peer-to-peer desktop application that allows users\n");
        stringBuilder.append("to buy and sell Bitcoin in exchange for fiat currencies and other\n");
        stringBuilder.append("cryptocurrencies.\n");
        stringBuilder.append("\n");
        stringBuilder.append("Unlike centralized exchanges, Bisq is fully non-custodial and operates\n");
        stringBuilder.append("without a central authority. All trading operations run directly between\n");
        stringBuilder.append("peers over the Tor network, and trade security is enforced using\n");
        stringBuilder.append("multi-signature escrow addresses and a built-in dispute resolution system.\n");
        stringBuilder.append("No account registration or identity verification (KYC) is required.\n");
        stringBuilder.append("\n");
        stringBuilder.append("%prep\n");
        stringBuilder.append("%setup -q -n %{name}\n");
        stringBuilder.append("\n");
        stringBuilder.append("%build\n");
        stringBuilder.append("\n");
        stringBuilder.append("%install\n");
        stringBuilder.append("mkdir -p %{buildroot}/opt/bisq/bin\n");
        stringBuilder.append("mkdir -p %{buildroot}/opt/bisq/lib\n");
        stringBuilder.append("\n");

        Path binDir = installDistDir.resolve("bin");
        try (Stream<String> installCommands = buildInstallCmdsForDirChildren(binDir, "0755")) {
            installCommands.forEach(stringBuilder::append);
        }

        Path libDir = installDistDir.resolve("lib");
        try (Stream<String> installCommands = buildInstallCmdsForDirChildren(libDir, "0644")) {
            installCommands.forEach(stringBuilder::append);
        }

        stringBuilder.append("\n");
        stringBuilder.append("%files\n");
        stringBuilder.append("%defattr(-,root,root,-)\n");
        stringBuilder.append("/opt/bisq\n");
        stringBuilder.append("\n");
        stringBuilder.append("%changelog\n");
        stringBuilder.append("* Sat Aug 01 2026 Alejandro Garcia <alejandro.garcia@disroot.org> - 1.0-1\n");
        stringBuilder.append("- Initial RPM release\n");

        return stringBuilder.toString();
    }

    private Stream<String> buildInstallCmdsForDirChildren(Path dirPath, String permissionMode) throws IOException {
        @SuppressWarnings("resource") Stream<Path> childrenStream = Files.list(dirPath);
        return childrenStream.filter(Files::isRegularFile)
                .sorted()
                .map(installDistDir::relativize)
                .map(path -> {
                    // "install -m 0755 bin/desktop %{buildroot}/opt/bisq/bin/desktop\n"
                    return "install -m " + permissionMode + ' ' + path + " %{buildroot}/opt/bisq/" + path + '\n';
                });
    }
}
