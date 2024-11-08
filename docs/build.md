## Building Bisq

1. **Clone Bisq**

   ```sh
   git clone https://github.com/bisq-network/bisq
   # if you intend to do testing on the latest release, you can clone the respective branch selectively, without downloading the whole repository
   # for the 1.9.3 release, you would do it like this:
   git clone --recurse-submodules --branch release/v1.9.3 https://github.com/bisq-network/bisq
   cd bisq
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

### Important notes

1. You do _not_ need to install Gradle to build Bisq. The `gradlew` shell script will install it for you, if necessary.

2. Bisq currently works with JDK 11 and JDK 15. You can find out which
   version you have with:

   ```sh
   javac -version
   ```

If you do not have JDK 11 installed, check out scripts in the [scripts](../scripts) directory or download it manually from https://jdk.java.net/archive/.

## Running Bisq

Once Bisq is installed, its executables will be available in the root project directory. Run **Bisq Desktop** as follows:

On macOS and Linux:
```sh
./bisq-desktop
```

On Windows:
```cmd
bisq-desktop.bat
```

## See also

 - [Importing Bisq into IntelliJ IDEA](./idea-import.md)
 - [Bisq development environment setup guide](./dev-setup.md)
