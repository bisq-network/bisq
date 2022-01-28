# Release Process

* Update translations [translation-process.md](translation-process.md#synchronising-translations).
* Update data stores [data-stores.md](data-stores.md#update-stores).
* Update bitcoinj checkpoint [bitcoinj-checkpoint](bitcoinj-checkpoint.md#update-checkpoint).
* Write release notes (see below).
* Webpage (Prepare PR)
    * Update version number in:
        * [_config.yml](https://github.com/bisq-network/bisq-website/blob/master/_config.yml)

    * Update currency list
      in [market_currency_selector.html](https://github.com/bisq-network/bisq-website/blob/master/_includes/market_currency_selector.html) (
      use [MarketsPrintTool](https://github.com/bisq-network/bisq/blob/master/desktop/src/test/java/bisq/desktop/MarketsPrintTool.java)
      to create HTML content).

### Bisq maintainers, suggestion for writing release notes

To be able to create release notes before you make the final release tag, you can temporarily create a local tag and
remove it afterwards again.

    git tag v(new version, e.g. 0.9.4) #create tag
    git tag -d v(new version, e.g. 0.9.4) #delete tag

Write release notes. git shortlog helps a lot, for example:

    git shortlog --no-merges v(current version, e.g. 0.9.3)..v(new version, e.g. 0.9.4)

Generate list of authors:

    git log --format='- %aN' v(current version, e.g. 0.9.3)..v(new version, e.g. 0.9.4) | sort -fiu

1. Prepare the release notes with major changes from user perspective.
2. Prepare a short version of the release notes for the in-app update popup

### Basic preparations

For releasing a new Bisq version you'll need Linux, Windows and macOS. You can use a virtualization solution
like [VirtualBox](https://www.virtualbox.org/wiki/Downloads) for this purpose.

#### VirtualBox recommended configuration

Although performance of VMs might vary based on your hardware configuration following setup works pretty well on macOS.

Use VirtualBox > 6.1 with following configuration:

* System > Motherboard > Base Memory: 4096 MB
* System > Processor > Processor(s): 2 CPUs
* System > Processor > Execution Cap: 90%
* Display > Screen > Video Memory: 128 MB
* Display > Screen > Scale Factor: 200%
* Display > Screen > HiDPI Support: Use unscaled HiDPI Output (checked)
* Display > Screen > Acceleration: Enable 3D acceleration (checked)

##### Windows VM

* Windows 10 64bit
* Recommended virtual disk size: 55 GB

##### Linux VM

* Ubuntu 16.04.4 64bit
* Recommended virtual disk size: 25 GB

##### macOS VM

* macOS X 10.11 (El Capitan) 64bit
* Recommended virtual disk size: 40 GB

#### For every OS

* Install the latest security updates
* Install/Upgrade to the latest Java 15 SDK
    * macOS (brew option): `brew upgrade zulu15`
    * Ubuntu (brew option): `sudo apt-get install zulu15-jdk`
    * Windows: Download the latest version
      from https://www.azul.com/downloads/?version=java-15-mts&os=windows&architecture=x86-64-bit&package=jdk

#### For Windows

* Update AntiVirus Software and virus definitions
* Install [WiX toolset](https://wixtoolset.org/releases/)
* Run full AV system scan

### Build release

#### macOS

To be able to generate a signed and notarized binary you have to have an Apple developer account and create the required
certificate and provisioning file before running the build.

1. Make sure all version numbers are updated (update version variables and
   run [replace_version_number.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/replace_version_number.sh))
   .

2. Set environment variables to ~/.profile file or the like... (one time effort)

* `BISQ_GPG_USER`: e.g. export BISQ_GPG_USER=manfred@bitsquare.io
* `BISQ_SHARED_FOLDER`: shared folder that is used between your VM host and client system
* `BISQ_PACKAGE_SIGNING_IDENTITY`: e.g. "Developer ID Application: Christoph Atteneder (WQT93T6D6C)"
* `BISQ_PRIMARY_BUNDLE_ID`: e.g. "network.bisq.CAT"
* `BISQ_PACKAGE_NOTARIZATION_AC_USERNAME`: your Apple developer email address
* `BISQ_PACKAGE_NOTARIZATION_ASC_PROVIDER`: Your developer ID (e.g. WQT93T6D6C)

3. Run `./gradlew packageInstallers`

Build output expected in shared folder:

1. `Bisq-${NEW_VERSION}.dmg` macOS notarized and signed installer
2. `desktop-${NEW_VERSION}-all-mac.jar.SHA-256` sha256 sum of fat jar
3. `jar-lib-for-raspberry-pi-${NEW_VERSION}.zip` Jar libraries for Raspberry Pi

* Before building the other binaries install the generated Bisq app on macOS and verify that everything works as
  expected.

#### Linux

1. Checkout the release tag in your VM

2. Set environment variables to ~/.profile file or the like... (one time effort)
    * `BISQ_SHARED_FOLDER`: shared folder that is used between your VM host and client system

3. Run `./gradlew packageInstallers`

Build output expected:

1. `bisq_${NEW_VERSION}-1_amd64.deb` package for distributions that derive from Debian
2. `bisq-${NEW_VERSION}-1.x86_64.rpm` package for distributions that derive from Redhat based distros
3. `desktop-${NEW_VERSION}-all-linux.jar.SHA-256` sha256 sum of fat jar

* Install and run generated package

#### Windows

To be able to generate a signed binary you have to apply and install a developer certificate before running the build.

1. Checkout the release tag in your VM

2. Set environment variables to ~/.profile file or the like... (one time effort)
    * `BISQ_SHARED_FOLDER`: shared folder that is used between your VM host and client system

3. Run `./gradlew packageInstallers`

Build output expected:

1. `Bisq-${NEW_VERSION}.exe` Windows signed installer
2. `desktop-${NEW_VERSION}-all-windows.jar.SHA-256` sha256 sum of fat jar

* Install and run generated package

### Sign release on macOS

* Run [finalize.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/finalize.sh)

Build output expected:

1. `F379A1C6.asc` Sig key of Manfred Karrer
2. `5BC5ED73.asc` Sig key of Chris Beams
3. `29CDFD3B.asc`Sig key of Christoph Atteneder
4. `signingkey.asc` Fingerprint of key that was used for these builds
5. `Bisq-${NEW_VERSION}.dmg` macOS installer
6. `Bisq-${NEW_VERSION}.dmg.asc` Signature for macOS installer
7. `Bisq-64bit-${NEW_VERSION}.deb` Debian package
8. `Bisq-64bit-${NEW_VERSION}.deb.asc` Signature for Debian package
9. `Bisq-64bit-${NEW_VERSION}.rpm` Redhat based distro package
10. `Bisq-64bit-${NEW_VERSION}.rpm.asc` Signature for Redhat based distro package
11. `Bisq-64bit-${NEW_VERSION}.exe` Windows installer
12. `Bisq-64bit-${NEW_VERSION}.exe.asc` Signature for Windows installer

* Run a AV scan over all files on the Windows VM where the files got copied over.

### Final test

* Make at least one mainnet test trade with some exotic currency to not interfere with real traders.

### Tag and push release to master

If all was successful:

* commit changes of new version number (update version number for release of e.g. v1.5.0)
* create tag for the release

```
    git tag -s v(new version,  e.g. 1.5.0) -m"Release v(new version, e.g. 1.5.0)"
```

* Revert back to SNAPSHOT where necessary (change version variable (e.g. 1.5.0) in shell
  script [insert_snapshot_version.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/insert_snapshot_version.sh)
  and run it) and commit these changes.

* Push all commits to master including the new tag

```
    git push --tags origin master
```

### GitHub

#### Upload preparations

* Check the fingerprint of the pgp key which was used for signing in signingkey.asc (e.g. 29CDFD3B for Christoph
  Atteneder)

* Add all files including signingkey.asc and the gpg pub keys to GitHub release page

* Check all uploaded files with [virustotal.com](https://www.virustotal.com)

* Select the release tag as the source for the GitHub release.

* Release on GitHub

#### Post GitHub release

* Apply “A newer version is already available! Please don’t use this version anymore.” to old GitHub releases.

* Merge the webpage PR and check if they got deployed properly.

* Start the Alert sender app (CMD + M)  remove the old version and send the update message. Check the checkbox for
  update, set the version number (e.g. 0.9.4) and add the short version of the release notes.

* After sending the Update message leave it running for about 1 minute to give time for good propagation.

* Make a backup of that alert sender app data directory

* To support source code signature verification for Arch Linux download `Source code (tar.gz)`, sign it and upload
  signature.

```
    # sign source code bundle
    gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output bisq-${NEW_VERSION}.tar.gz.asc --detach-sig --armor bisq-${NEW_VERSION}.tar.gz

    # verify signature of source code bundle
    gpg --digest-algo SHA256 --verify bisq-${NEW_VERSION}.tar.gz{.asc*,}
```

### Announce the release

* Forum

* Matrix space (_General_ room)

* Twitter

* Optionally reddit /r/Bisq

* Notify @freimair so that he can start
  updating [the Arch User Repository](https://aur.archlinux.org/cgit/aur.git/tree/PKGBUILD?h=bisq-git)

* Celebrate
