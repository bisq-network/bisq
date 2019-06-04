#!/usr/bin/env bash
# Requirements:
#   - OracleJDK 10 installed
#     Note: OpenJDK 10 does not have the javapackager util, so must use OracleJDK
# Prior to running this script:
#   - Update version below
#   - Ensure JAVA_HOME below is pointing to OracleJDK 10 directory

version=1.1.2-SNAPSHOT
if [ ! -f "$JAVA_HOME/bin/javapackager" ]; then
	if [ -d "/usr/lib/jvm/jdk-10.0.2" ]; then
    	JAVA_HOME=/usr/lib/jvm/jdk-10.0.2
	else
	    echo Javapackager not found. Update JAVA_HOME variable to point to OracleJDK.
	    exit 1
	fi
fi

base_dir=$( cd "$(dirname "$0")" ; pwd -P )/../../..
src_dir=$base_dir/desktop/package

cd $base_dir

set -eu

echo Installing required packages
if [[ -f "/etc/debian_version" ]]; then
    sudo apt install -y fakeroot rpm
elif [[ -f "/etc/redhat-release" ]]; then
    sudo yum install -y fakeroot rpm-build dpkg perl-Digest-SHA
fi

if [ ! -f "$base_dir/desktop/package/desktop-$version-all.jar" ]; then
    echo Building application
    ./gradlew :desktop:clean :desktop:build -x test shadowJar
    jar_file=$base_dir/desktop/build/libs/desktop-$version-all.jar
    if [ ! -f "$jar_file" ]; then
        echo No jar file available at $jar_file
        exit 2
    fi

    tmp=$base_dir/desktop/build/libs/tmp
    echo Extracting jar file to $tmp
    if [ -d "$tmp" ]; then
        rm -rf $tmp
    fi
    mkdir -p $tmp
    unzip -o -q $jar_file -d $tmp

    echo Deleting problematic module config from extracted jar
    # Strip out Java 9 module configuration used in the fontawesomefx library as it causes javapackager to stop
    # because of this existing module information, since it is not used as a module.
    # Sometimes module-info.class does not exist - TODO check why and if still needed
    if [ -f "$tmp/module-info.class" ]; then
        rm -f $tmp/module-info.class
    fi

    jar_file=$base_dir/desktop/package/desktop-$version-all.jar
    echo Zipping jar again without module config to $jar_file
    cd $tmp; zip -r -q -X $jar_file *
    cd $base_dir; rm -rf $tmp

    echo SHA256 before stripping jar file:
    shasum -a256 $jar_file | awk '{print $1}'

    echo Making deterministic jar by stripping out parameters and comments that contain dates
    # Jar file created from https://github.com/ManfredKarrer/tools
    # TODO Is this step still necessary? Since we are using preserveFileTimestamps and reproducibleFileOrder in build.gradle
    java -jar $base_dir/desktop/package/tools-1.0.jar $jar_file

    echo SHA256 after stripping jar file:
    shasum -a256 $jar_file | awk '{print $1}' | tee $base_dir/desktop/package/desktop-$version-all.jar.txt
else
    local_src_dir="/home/$USER/Desktop/build"
    mkdir -p $local_src_dir
    cp $base_dir/desktop/package/desktop-$version-all.jar $local_src_dir/desktop-$version-all.jar
    src_dir=$local_src_dir
fi

chmod o+rx "$src_dir/desktop-$version-all.jar"

# Remove previously generated packages so we can later determine if they are actually generated successfully
if [ -f "$base_dir/desktop/package/linux/bisq-$version.deb" ]; then
    rm "$base_dir/desktop/package/linux/bisq-$version.deb"
fi
if [ -f "$base_dir/desktop/package/linux/bisq-$version.rpm" ]; then
    rm "$base_dir/desktop/package/linux/bisq-$version.rpm"
fi

# TODO: add the license as soon as it is working with our build setup
#-BlicenseFile=LICENSE \
#-srcfiles package/linux/LICENSE \

echo Generating deb package
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=contact@bisq.network \
    -BlicenseType=GPLv3 \
    -Bicon=$base_dir/desktop/package/linux/icon.png \
    -native deb \
    -name Bisq \
    -title "The decentralized exchange network." \
    -vendor Bisq \
    -outdir $base_dir/desktop/package/linux \
    -srcdir $src_dir \
    -srcfiles desktop-$version-all.jar \
    -appclass bisq.desktop.app.BisqAppMain \
    -BjvmOptions=-Xss1280k \
    -BjvmOptions=-Djava.net.preferIPv4Stack=true \
    -outfile Bisq-$version \
    -v

if [ ! -f "$base_dir/desktop/package/linux/bisq-$version.deb" ]; then
    echo No deb file found at $base_dir/desktop/package/linux/bisq-$version.deb
    exit 3
fi

echo Generating rpm package
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=contact@bisq.network \
    -BlicenseType=GPLv3 \
    -Bicon=$base_dir/desktop/package/linux/icon.png \
    -native rpm \
    -name Bisq \
    -title "The decentralized exchange network." \
    -vendor Bisq \
    -outdir $base_dir/desktop/package/linux \
    -srcdir $src_dir \
    -srcfiles desktop-$version-all.jar \
    -appclass bisq.desktop.app.BisqAppMain \
    -BjvmOptions=-Xss1280k \
    -BjvmOptions=-Djava.net.preferIPv4Stack=true \
    -outfile Bisq-$version \
    -v

if [ ! -f "$base_dir/desktop/package/linux/bisq-$version-1.x86_64.rpm" ]; then
    echo No rpm file found at $base_dir/desktop/package/linux/bisq-$version-1.x86_64.rpm
    exit 3
fi

# FIXME: My Ubuntu somehow also deletes the lower case file
# if [ -f "$base_dir/desktop/package/linux/Bisq-$version.deb" ]; then
#     rm "$base_dir/desktop/package/linux/Bisq-$version.deb"
# fi
mv $base_dir/desktop/package/linux/bisq-$version.deb $base_dir/desktop/package/linux/Bisq-$version.deb

echo SHA256 of $base_dir/desktop/package/linux/Bisq-$version.deb:
shasum -a256 $base_dir/desktop/package/linux/Bisq-$version.deb | awk '{print $1}' | tee $base_dir/desktop/package/linux/Bisq-$version.deb.txt

# FIXME: My Ubuntu somehow also deletes the lower case file
# if [ -f "$base_dir/desktop/package/linux/Bisq-$version-1.x86_64.rpm" ]; then
#     rm "$base_dir/desktop/package/linux/Bisq-$version-1.x86_64.rpm"
# fi
mv $base_dir/desktop/package/linux/bisq-$version-1.x86_64.rpm $base_dir/desktop/package/linux/Bisq-$version.rpm

echo SHA256 of $base_dir/desktop/package/linux/Bisq-$version.rpm:
shasum -a256 $base_dir/desktop/package/linux/Bisq-$version.rpm | awk '{print $1}' | tee $base_dir/desktop/package/linux/Bisq-$version.rpm.txt

echo Done!
