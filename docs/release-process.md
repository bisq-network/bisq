# Release Process

* Update translations [translation-process.md](translation-process.md#synchronising-translations).
* Update data stores [data-stores.md](data-stores.md#update-stores).
* Update bitcoinj checkpoint [bitcoinj-checkpoint](bitcoinj-checkpoint.md#update-checkpoint).
* Write release notes (see below).
* Webpage (Prepare PR)
    * Update version number in:
        * [_config.yml](https://github.com/bisq-network/bisq-website/blob/master/_config.yml)
      
    * Update currency list in [market_currency_selector.html](https://github.com/bisq-network/bisq-website/blob/master/_includes/market_currency_selector.html) (use [MarketsPrintTool](https://github.com/bisq-network/bisq/blob/master/desktop/src/test/java/bisq/desktop/MarketsPrintTool.java)
    to create HTML content).
    * Update [roadmap](https://github.com/bisq-network/bisq-website/blob/master/roadmap.md) with new release notes.
      

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
For releasing a new Bisq version you'll need Linux, Windows and macOS.
You can use a virtualization solution like [VirtualBox](https://www.virtualbox.org/wiki/Downloads) for this purpose.

#### VirtualBox recommended configuration
Although performance of VMs might vary based on your hardware configuration following setup works pretty well on macOS.

Use VirtualBox < 5.2.22: Using a more recent version makes VMs hardly usable on a MacBook Pro (15-inch, 2016)
with following configuration:
  * System > Motherboard > Base Memory: 2048 MB
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
* Recommended virtual disk size: 15 GB

##### macOS VM
* macOS X 10.11 (El Capitan) 64bit
* Recommended virtual disk size: 40 GB

#### For every OS

* Install latest security updates

#### For Windows

* Update AntiVirus Software and virus definitions
* Install unicode version of [Inno Tools](http://www.jrsoftware.org/isdl.php)
* Run full AV system scan

### Build release

#### macOS

1. Make sure all version numbers are updated (update version variables and run [replace_version_number.sh](https://github.com/bisq-network/bisq/blob/master/bisq/desktop/package/macosx/replace_version_number.sh)).
2. Set environment variable e.g. export BISQ_GPG_USER=manfred@bitsquare.io to ~/.profile file or the like... (one time effort)
3. Update [vmPath variable](https://github.com/bisq-network/bisq/blob/b4b5d0bb12c36afbe1aa6611dd8451378df6db8c/desktop/package/macosx/create_app.sh#L42) if necessary
4. Run [create_app.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/create_app.sh)

Build output expected in deploy directory (opened after successful build process):
  
  1. `Bisq-${NEW-VERSION}.dmg` macOS signed installer 
  2. `Bisq-${NEW-VERSION}.jar` Deterministic fat jar 
  3. `Bisq-${NEW-VERSION}.jar.txt` sha256 sum of deterministic fat jar 
  
The build script also copies over the deterministic fat jar into the shared folders for the other VMs (Windows & Linux).
Before building the other binaries install the generated Bisq app on macOS and verify that everything works as expected.

#### Linux

* Run `desktop/package/linux/package.sh` from the shared VM folder

Build output expected:
  
  1. `Bisq-${NEW-VERSION}.deb` package for distributions that derive from Debian 
  2. `Bisq-${NEW-VERSION}.rpm` package for distributions that derive from Redhat based distros

* Install and run generated package

#### Windows

* Run `desktop/package/windows/package.bat` from the shared VM folder

Build output expected:
  
  1. `Bisq-${NEW-VERSION}.exe` Windows unsigned installer 
  2. `Bisq-${NEW-VERSION}.exe.txt` sha256 sum of installer

### Sign release on macOS

* Run [finalize.sh](https://github.com/bisq-network/bisq/blob/master/bisq/desktop/package/macosx/finalize.sh)

Build output expected:

  1. `F379A1C6.asc` Sig key of Manfred Karrer 
  2. `5BC5ED73.asc` Sig key of Chris Beams 
  3. `29CDFD3B.asc`Sig key of Christoph Atteneder 
  4. `signingkey.asc` Fingerprint of key that was used for these builds 
  5. `Bisq-${NEW-VERSION}.jar.txt` Sha256 sum of deterministic fat jar
  6. `Bisq-${NEW-VERSION}.dmg` macOS installer
  7. `Bisq-${NEW-VERSION}.dmg.asc` Signature for macOS installer
  8. `Bisq-${NEW-VERSION}.deb` Debian package
  9. `Bisq-${NEW-VERSION}.deb.asc` Signature for Debian package
  10. `Bisq-${NEW-VERSION}.rpm` Redhat based distro package
  11. `Bisq-${NEW-VERSION}.rpm.asc` Signature for Redhat based distro package
  12. `Bisq-${NEW-VERSION}.exe` Windows installer
  13. `Bisq-${NEW-VERSION}.exe.asc` Signature for Windows installer
  
 * Run a AV scan over all files on the Windows VM where the files got copied over.
 
### Final test
 
 * Make at least one mainnet test trade with some exotic currency to not interfere with real traders.
 
### Tag and push release to master

If all was successful: 

 * commit changes of new version number (update version number for release of e.g. v0.9.4)
 * create tag for the release
```
    git tag -s v(new version,  e.g. 0.9.4) -m"Release v(new version, e.g. 0.9.4)"
```
 * Revert back to SNAPSHOT where necessary (change version variable (e.g. 0.9.4) in shell script [insert_snapshot_version.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/insert_snapshot_version.sh) and run it) and commit these changes.
 * Push all commits to master including the new tag
```
    git push --tags origin master
```

### GitHub

#### Upload preparations

 * Check the fingerprint of the pgp key which was used for signing in signingkey.asc (e.g. 29CDFD3B for Christoph Atteneder)
 * Add all files including signingkey.asc and the gpg pub keys to GitHub release page
 * Check all uploaded files with [virustotal.com](https://www.virustotal.com)
 * Select the release tag as the source for the GitHub release.
 * Release on GitHub

#### Post GitHub release
 * Apply “A newer version is already available! Please don’t use this version anymore.” to old GitHub releases.
 * Merge the webpage PR and check if they got deployed properly.
 * Start the Alert sender app (CMD + M)  remove the old version and send the update message. 
 Check the checkbox for update, set the version number (e.g. 0.9.4) and add the short version of the release notes.
 * After sending the Update message leave it running for about 1 minute to give time for good propagation.
 * Make a backup of that alert sender app data directory

### Announce the release

  * Forum
  * Slack (#general channel)
  * Twitter
  * Optionally reddit /r/Bisq
  * Notify @freimair so that he can start updating [the Arch User Repository](https://aur.archlinux.org/cgit/aur.git/tree/PKGBUILD?h=bisq-git)
  * Celebrate
