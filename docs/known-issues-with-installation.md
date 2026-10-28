## Known issues with installation

### macOS:

We removed notarization from our build pipeline because of the risk of Apple certification revocation (
see https://github.com/bisq-network/bisq/discussions/6341). Unfortunately this will require extra steps when installing
Bisq on macOS.

Please follow the guide at https://support.apple.com/en-us/HT202491 in the section If you want to open an app that
hasn't been notarized or is from an unidentified developer

If you are running already macOS Ventura (13.0+) you need to do following to be able to start Bisq:

Enter following command in Apple Terminal `sudo xattr -rd com.apple.quarantine /Applications/Bisq.app`
hit enter, and you will be prompted to enter your password to be able to execute the command as super user

### Windows:

We removed the developer code signing for the same reason mentioned above.
For Windows you just have to ignore the warning after you have verified the installation file yourself and proceed with
the installation.

There is a known issue with Anti Virus software. We got several reports from users running into different problems.
Either the AV software blocks Bisq or Tor, delete files in the data directory [2] or app directory [1] or cause such a
long delay at startup that Tor gets terminated and a file remains locked which can cause that Bisq cannot be started
afterwards. To resolve that you need to restart Windows then the lock get released. We are working on solutions to fix
those issues.

If you use Crypto currencies on your Windows system be aware that Windows is much more vulnerable to malware than Linux
or OSX. Consider to use a dedicated non-Windows system when dealing with cryptocurrencies.

### Linux:

Hint for Debian users:
If you have problems starting Bisq on Debian use: `/opt/bisq/bin/Bisq`

Bisq brings its own Tor on Linux, as on macOS and Windows, and does not need the tor package of your distribution. Its
Tor needs glibc 2.17 or newer (2.27 on ARM64) and the zlib library of the system (`libz.so.1`, the package `zlib1g` on
Debian and Ubuntu).

Some development builds before the release of 1.10.11 depended on the tor package; no release did. After an upgrade
from such a build, the tor package stays installed, and on Debian and Ubuntu its Tor service keeps connecting to the Tor
network at every boot, also when Bisq does not run. Bisq does not use that service, unless you configured it in
`external_tor.config`. To turn the service off, run `sudo systemctl disable --now tor`, or remove the package with
`sudo apt remove tor` if nothing else uses it.

If your Linux distro does not support `.deb` files please follow this instruction:

```
cd ~/Downloads
mkdir tmp
cd tmp
ar x ../Bisq-64bit-1.9.11.deb
sudo tar Jxvf data.tar.xz
sudo cp -rp opt/Bisq /opt/
```

That instruction is not tested on many different distros. If you encounter problems please report it in a Github issue
so we can improve it.

[1] Application directory (contains application installation files):
C:\Users<username>\AppData\Local\Bisq

[2] Data directory (contains all Bisq data including wallet):
C:\Users<username>\AppData\Roaming\Bisq\btc_mainnet\tor (you can delete everything except the hiddenservice directory
and the external_tor.config file, which holds your settings for a running Tor; without it, Bisq starts its own Tor)
