# Importing Bisq into IntelliJ IDEA

Most Bisq contributors use IDEA for development. The following instructions have been tested on IDEA 2019.2.

 1. Follow the instructions in [build.md](build.md) to clone and build Bisq at the command line.
 1. Open IDEA
 1. Go to `Preferences->Plugins` (`File->Settings, Plugins for Windows`). Search for and install the _Lombok_ plugin. When prompted, do not restart IDEA.
 1. Go to `Preferences->Build, Execution, Deployment->Compiler->Annotation Processors` and check the `Enable annotation processing` option (to enable processing of Lombok annotations)
 1. Restart IDEA
 1. Go to `Import Project`, select the `settings.gradle` file and click `Open`
 1. In the `Import Project from Gradle` screen, check the `Use auto-import` option and click `OK`
 1. When prompted whether to overwrite the existing `.idea` directory, click `Yes` (This step was not required with 2019.2 but is kept here in case you are running an older version)
 1. In the `Project` tool window, right click on the root-level `.idea` folder, select `Git->Revert...` and click OK in the dialog that appears (to restore source-controlled `.idea` configuration files that get overwritten during project import)
 1. If you did not yet setup JDK10 in IntelliJ, Go to `File->Project Structure->Project` and under the `Project SDK` option locate your JAVA_HOME folder, then in `Project language level` beneath select `10 - ...`. (JDK10 is no longer supported but you can still download it from the [archive](https://jdk.java.net/archive/))
 1. Select JDK 10 for gradle as well. Go to `Preferences->Build, Execution, Deployment->Build Tools->Gradle` and select the JDK10 location for Gradle JVM
 1. Go to `Build->Build Project`. Everything should build cleanly. You should be able to run tests, run `main` methods in any component, etc.

> TIP: If you encounter compilation errors in IDEA related to the `protobuf.*` classes, it is probably because you didn't build Bisq at the command line as instructed above. You need to run the `generateProto` task in the `common` project. You can do this via the Gradle tool window in IDEA, or you can do it the command line with `./gradlew :common:generateProto`. Once you've done that, run `Build->Build Project` again and you should have no errors.
