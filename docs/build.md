## Building Bisq

1. **Clone Bisq**

   ```sh
   git clone --recurse-submodules https://github.com/bisq-network/bisq
   cd bisq
   ```

   If the project was already cloned and you need to update the submodule, run:

   ```sh
   git submodule sync --recursive
   git submodule update --init --recursive
   ```

2. **Build Bisq**

   On macOS and Linux, execute:
   ```sh
   ./gradlew build
   ```

   On Windows:
   ```cmd
   gradlew.bat build
   ```

   If you prefer to skip tests to speed up the building process, just append _-x test_ to the previous commands.

### Tor submodule

The directory `tor/` is the Git submodule [tor](https://github.com/bisq-network/tor), the Tor library which Bisq
shares with Bisq 2. Bisq records one commit of it, and `tor/` must contain this commit. The build stops with a message
if `tor/` is empty.

**Check out the recorded commit.** A clone without `--recurse-submodules`, a `git pull` without it, and a branch switch
leave `tor/` empty or at another commit. Then run:

```sh
git submodule update --init
```

This command does not take new commits of the tor repository. It checks out the commit which Bisq records, also when
`tor/` has a newer commit checked out. To let `git pull` and `git switch` update `tor/` automatically, run once:
`git config submodule.recurse true`.

**Update Bisq to a newer commit of the tor repository:**

1. Fetch the new commits into `tor/` and check out the new commit there, for example `origin/main` for the newest
   commit of the `main` branch:

   ```sh
   git -C tor fetch origin
   git -C tor checkout <commit>
   ```

2. Review the changes which the update brings:

   ```sh
   git diff --submodule=log tor
   ```

3. Build and test, then commit the new commit of `tor/` in Bisq:

   ```sh
   git add tor
   git commit
   ```

**Change the Tor code.** `tor/` is a repository of its own. Commit the change in `tor/` on a branch and push it to the
tor repository. Then commit the new commit of `tor/` in Bisq as in step 3. Push the tor commit first, otherwise other
clones of Bisq cannot check it out.

**Use a local Tor repository.** To fetch committed changes from a local repository `tor` next to the Bisq checkout,
run the following commands from the Bisq checkout's root directory. Keep the published URL in `.gitmodules`; these
overrides apply only to your local checkout.

Set the local repository path:

```sh
git config --local submodule.tor.url ../tor
```

If `tor/` is empty, initialize it from the local repository first:

```sh
git -c protocol.file.allow=always submodule update --init tor
```

Once initialization has succeeded, configure the submodule's remote and select the local repository's `main` branch:

```sh
git -C tor remote set-url origin ../../tor
git config --local submodule.tor.branch main
```

Run `remote set-url` only after `tor/` is initialized: in an empty `tor/`, `git -C tor` changes the remote of the Bisq
checkout. The two relative paths above resolve from the Bisq checkout and its nested `tor/` checkout, respectively.
Relative URLs in `.gitmodules` resolve against Bisq's default remote repository, so they do not refer to the adjacent
local repository when Bisq's remote is on GitHub.

Fetch and check out the latest committed changes from the local repository's `main` branch:

```sh
git -c protocol.file.allow=always submodule update --remote tor
git diff --submodule=log tor
```

Git restricts local file transport for submodules by default; `-c protocol.file.allow=always` allows it for that
command. Without `--remote`, `submodule update` checks out the commit recorded by Bisq. Uncommitted changes in the
local repository are not included. Build and test, then record the new Tor commit in Bisq with `git add tor` and
`git commit`, as described above. Other developers need access to that Tor commit before they can use the update.

Do not run `git submodule sync` for Tor after configuring these overrides, including the recursive command in step 1
above: it overwrites both local URLs with the URL from `.gitmodules`.

### Important notes

1. You do _not_ need to install Gradle to build Bisq. The `gradlew` shell script will install it for you, if necessary.

2. Bisq currently works with JDK 21. You can find out which version you have with:

   ```sh
   javac -version
   ```

   If you have multiple JDK versions installed, check which one Gradle will use, with:

   ```sh
   ./gradlew --version
   ```

   and if the version number on the JVM line is not a supported one, you can pick the correct JDK at runtime with this syntax (verify your system path):

   ```sh
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH ./gradlew clean build
   ```

If you do not have JDK 21 installed, check out scripts in the [scripts](../scripts) directory or download it manually
from https://jdk.java.net/archive/.

3. On Linux, Bisq runs the `tor` executable of the system, which must be on the `PATH`. Install the tor package of your
   distribution, for example with `sudo apt install tor`. On macOS and Windows, Bisq brings its own Tor.
   On Debian and Ubuntu, the tor package also enables and starts the Tor service of the system, which then runs at every
   boot, also when Bisq does not run. Bisq starts its own Tor and uses that service only if `external_tor.config` tells
   it to, as the seed node does. Otherwise you can turn it off with `sudo systemctl disable --now tor`.

## Running Bisq

Once Bisq is installed, its executables will be available in the `scripts` folder under the root project directory. Run **Bisq Desktop** as follows:

On macOS and Linux:
```sh
./scripts/desktop
```
or, to select a specific version of Java:
```sh
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./scripts/desktop
```

On Windows:
```cmd
scripts\desktop.bat
```

## See also

 - [Importing Bisq into IntelliJ IDEA](./idea-import.md)
 - [Bisq development environment setup guide](./dev-setup.md)
