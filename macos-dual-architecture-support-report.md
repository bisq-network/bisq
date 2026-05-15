# macOS Dual Architecture Support Report

## Scope

Bisq now needs separate macOS installer artifacts for Intel and Apple Silicon.
The app update downloader, macOS packaging output, and release finalization flow
were still assuming a single macOS DMG named `Bisq-<version>.dmg`.

## Changes Applied

- Updated the in-app installer descriptor to select a macOS DMG by runtime
  architecture:
  - Intel: `Bisq-x86_64-<version>.dmg`
  - Apple Silicon: `Bisq-aarch64-<version>.dmg`
- Added focused tests for macOS updater filename selection using common JVM
  architecture strings: `x86_64`, `amd64`, `aarch64`, and `arm64`.
- Updated Gradle packaging so a macOS `generateInstallers` run renames the
  generated DMG to the same architecture-qualified filename that the app
  downloader expects.
- Updated the macOS library hash filename and hash content label to include the
  macOS architecture, preventing Intel and Apple Silicon builds from producing
  colliding `desktop-<version>-all-mac.jar.SHA-256` files.
- Updated `desktop/package/macosx/finalize.sh` to copy, sign, and verify both
  macOS DMGs, and to aggregate both macOS library hash files into
  `Bisq-<version>.jar.txt`.

## Release Inputs Expected By `finalize.sh`

- Intel macOS build directory:
  - Defaults to `$BISQ_VM_PATH/vm_shared_macosx`
  - Can be overridden with `BISQ_MACOS_X86_64_PATH`
  - Must contain `Bisq-x86_64-<version>.dmg`
  - Must contain `desktop-<version>-all-mac-x86_64.jar.SHA-256`
- Apple Silicon macOS build directory:
  - Defaults to `$BISQ_VM_PATH/vm_shared_macosx_aarch64`
  - Can be overridden with `BISQ_MACOS_AARCH64_PATH`
  - Must contain `Bisq-aarch64-<version>.dmg`
  - Must contain `desktop-<version>-all-mac-aarch64.jar.SHA-256`

## x86 Test Plan

Run this on the Intel macOS machine:

```sh
./gradlew :desktop:generateInstallers
```

Expected artifacts:

```text
desktop/build/packaging/jpackage/packages/Bisq-x86_64-1.10.0.dmg
desktop/build/packaging/jpackage/packages/desktop-1.10.0-all-mac-x86_64.jar.SHA-256
```

Then install and launch the DMG on the Intel machine. After the Apple Silicon
DMG is also available, run `desktop/package/macosx/finalize.sh` with both macOS
build directories available and verify that the release folder contains both
macOS DMGs, `.asc` signatures, and `Bisq-<version>.jar.txt`. The individual
`desktop-<version>-all-mac-*.jar.SHA-256` files are inputs to finalization, not
separate release artifacts.

## Notes

- No Rosetta-dependent test was added or run. The Intel build path should be
  validated on the separate x86 machine.
- Reproducible-build comparison logic was not expanded beyond accepting the
  generated installer filenames; the core reproducible-build workflow was left
  unchanged.

## Verification Performed

On the Apple Silicon machine:

```sh
bash -n desktop/package/macosx/finalize.sh
./gradlew :desktop:test --tests bisq.desktop.main.overlays.windows.downloadupdate.BisqInstallerTest
./gradlew :desktop:generateHashes
./gradlew :desktop:generateInstallers
```

The focused test passed, packaging build logic compiled, and
`:desktop:generateHashes` produced:

```text
desktop/build/packaging/jpackage/packages/desktop-1.10.0-all-mac-aarch64.jar.SHA-256
```

The generated hash file uses `macOS-aarch64` as its entry prefix.
`:desktop:generateInstallers` also completed successfully and produced:

```text
desktop/build/packaging/jpackage/packages/Bisq-aarch64-1.10.0.dmg
```
