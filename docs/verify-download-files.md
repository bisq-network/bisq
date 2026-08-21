## Verification of Bisq updates

If you have Bisq already installed it is best to update from inside the Bisq application and let Bisq perform the
signature verification. The Bisq application contains the PGP keys used for signing and those will be compared to the
signing key from the Github download page and the Bisq webpage. This provides an additional layer of security compared
to downloading and verifying it via the Github page only.

For a detailed description on how to verify your Bisq installer please have a look at our
wiki: https://bisq.wiki/Downloading_and_installing#Verify_installer_file

### Signing key

Url of the signing key (Alejandro García): https://bisq.network/pubkey/E222AA02.asc
Full fingerprint:

`B493 3191 06CC 3D1F 252E  19CB F806 F422 E222 AA02`

### Import the key:

`curl https://bisq.network/pubkey/E222AA02.asc | gpg --import`

GPG prints a confusion warning: "This key is not certified with a trusted signature!". You can ignore that or read up
at https://serverfault.com/questions/569911/how-to-verify-an-imported-gpg-key for background information what it means.

### How to verify signatures?

`gpg --digest-algo SHA256 --verify BINARY{.asc*,}`

Replace BINARY with the file you downloaded (e.g. Bisq-1.9.11.dmg)

### Verify jar file inside binary:

You can verify on OSX the jar file with:

`shasum -a256 [PATH TO BISQ APP]/Bisq.app/Contents/app/desktop-1.9.11-all.jar`

The output need to match the value from the `Bisq-1.9.11.jar.txt` file.

There are three hashes within the Bisq-1.9.11.jar.txt file (macOS, Windows, Linux).
If you want to reproduce and verify the hash of the jar file locally, you need to do so on Windows or Linux using Java
15.0.9 and the v1.9.11 release tag. Because of the signing and notarization process that requires the developer
certificate used for the build on macOS it is not possible to create the same jar on macOS.
