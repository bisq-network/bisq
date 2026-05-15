# In-App Update Download

Bisq can show an in-app update prompt when an update alert is received. The
prompt does not update the running application in place. It downloads the
installer for the current platform, verifies the detached PGP signature, opens
the user's download directory, and then shuts down so the user can install the
new version manually.

## Runtime Flow

1. `BisqSetup` receives an update alert and delegates display to
   `DisplayUpdateDownloadWindow`.
2. `DisplayUpdateDownloadWindow` renders the alert text and a download button.
3. On download, the window asks `BisqInstaller` whether the current OS is
   supported.
4. `BisqInstaller` builds the file list for the update version:
   - the platform installer
   - `signingkey.asc`, which identifies the signer key fingerprint
   - signer public keys from `https://bisq.network/pubkey/`
   - bundled local signer public keys from application resources
   - detached installer signatures
5. `DownloadTask` downloads those files into the user's download directory.
6. `VerifyTask` reads `signingkey.asc`, selects the matching downloaded or local
   public key, and verifies the installer with its detached signature.
7. After successful verification the window offers to open the download
   directory and shut down Bisq.

The update dialog is intentionally a downloader and verifier, not an automatic
installer. Installation stays outside the running Bisq process.

## Artifact Naming Contract

Release artifacts must use the filenames that `BisqInstaller` derives at
runtime:

| Platform | Installer filename |
| --- | --- |
| macOS Intel | `Bisq-x86_64-<version>.dmg` |
| macOS Apple Silicon | `Bisq-aarch64-<version>.dmg` |
| Windows | `Bisq-<bits>bit-<version>.exe` |
| Debian Linux | `Bisq-<bits>bit-<version>.deb` |
| Red Hat Linux | `Bisq-<bits>bit-<version>.rpm` |

For macOS, the architecture in the filename is part of the public download
contract. Intel and Apple Silicon users must receive different DMG files because
the packaged runtime and native dependencies differ.

## macOS Architecture Selection

`BisqInstaller` owns installer filename selection. On macOS it maps JVM
`os.arch` values to the release artifact classifier:

| `os.arch` values | Release classifier |
| --- | --- |
| `x86_64`, `amd64`, `x64`, `x86`, `i386`, `i686` | `x86_64` |
| `aarch64`, `arm64` | `aarch64` |

`DisplayUpdateDownloadWindow` should not hardcode macOS artifact names. It
should present the filename selected by `BisqInstaller` and surface a clear
manual-download error if the installer cannot be selected.

## Signature Model

Each installer has a detached `.asc` signature next to it in the release
download directory. The app downloads the signature for the selected installer
only. The signature is checked against the signer fingerprint from
`signingkey.asc` using both remote and bundled local copies of known signer
public keys.

This means dual macOS support does not require downloading both DMGs. It
requires selecting the correct DMG for the current machine and verifying the
matching detached signature.

## UI Responsibilities

`DisplayUpdateDownloadWindow` is responsible for user feedback and task
lifecycle:

- start download only on supported operating systems
- show the selected installer and metadata files as they are downloaded
- show progress while `DownloadTask` runs
- switch to verification state after all downloads succeeded
- show signer key filenames as `VerifyTask` verifies signatures
- re-enable the download button and show a manual-download fallback if
  selection, download, or verification fails
- cancel running tasks when the window closes

The window should treat installer-selection failures the same way as unsupported
OS failures: no background task should start, and the user should be told to
download and verify manually.

## Release Checklist

For a release that supports both macOS architectures, the release download
directory for version `<version>` must contain:

- `Bisq-x86_64-<version>.dmg`
- `Bisq-x86_64-<version>.dmg.asc`
- `Bisq-aarch64-<version>.dmg`
- `Bisq-aarch64-<version>.dmg.asc`
- `signingkey.asc`
- signer public keys under `https://bisq.network/pubkey/`

If any macOS artifact is missing, users on that architecture will see a download
failure and must fall back to manual download and verification.
