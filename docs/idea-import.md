# Importing Bisq into IntelliJ IDEA

Most Bisq contributors use IDEA for development. The following instructions have been tested on IDEA 2018.2.

 1. Follow the instructions in [build.md](build.md) to clone and build Bisq at the command line.
 1. Open IDEA
 1. Go to `Help->Edit Custom Properties...`, add a line to the file that reads `idea.max.intellisense.filesize=12500` (to handle Bisq's very large generated `PB.java` Protobuf source file)
 1. Go to `Preferences->Plugins`. Search for and install the _Lombok_ plugin. When prompted, do not restart IDEA.
 1. Go to `Preferences->Build, Execution, Deployment->Compiler->Annotation Processors` and check the `Enable annotation processing` option (to enable processing of Lombok annotations)
 1. Restart IDEA
 1. Go to `Import Project`, select the `settings.gradle` file and click `Open`
 1. In the `Import Project from Gradle` screen, check the `Use auto-import` option and click `OK`
 1. When prompted whether to overwrite the existing `.idea` directory, click `Yes`
 1. In the `Project` tool window, right click on the root-level `.idea` folder, select `Git->Revert...` and click OK in the dialog that appears (to restore source-controlled `.idea` configuration files that get overwritten during project import)
 1. If you did not yet setup JDK10 in IntelliJ, Go to `File -> Project Structure -> Project` and under the `Project SDK` option locate your JAVA_HOME folder, then in `Project language level` beneath select `10 - ...`.
 1. Go to `Build->Build Project`. Everything should build cleanly. You should be able to run tests, run `main` methods in any component, etc.

> TIP: If you encounter compilation errors in IDEA related to the `io.bisq.generated.protobuffer.PB` class, it is probably because you didn't build Bisq at the command line as instructed above. You need to run the `generateProto` task in the `common` project. You can do this via the Gradle tool window in IDEA, or you can do it the command line with `./gradlew :common:generateProto`. Once you've done that, run `Build->Build Project` again and you should have no errors.
