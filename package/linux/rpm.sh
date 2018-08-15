#!/bin/bash

## From https://github.com/bisq-network/bisq-desktop/issues/401#issuecomment-372091261

version=0.7.1

alien -r -g /home/$USER/Desktop/Bisq-64bit-$version.deb
find bisq-$version -type f | while read LIB; do LDDOUT=$(ldd $LIB 2>&1); LDDRETVAL=$?;if [ \( -z "${LDDOUT%%*you do not have execution permission for*}" \) -a \( $LDDRETVAL -eq 0 \) ]; then chmod -v +x $LIB;fi;done
cat bisq-$version/bisq-$version-2.spec | while read LINE; do if echo "$LINE" | grep -q "_unpackaged_files_terminate_build" ;then break; else echo "$LINE";fi;done > bisq.spec
rm bisq-$version/bisq-$version-2.spec
echo "AutoReqProv: no" >> bisq.spec
find bisq-$version | /usr/lib/rpm/rpmdeps --requires 2>&1| while read LIB; do if [ -z "$(find bisq-$version -name  ${LIB%%(*}\*)" ]; then echo "Requires: $LIB"; fi; done | egrep -v '(libavcodec|libavformat|libavutil)' >> bisq.spec
cat >> bisq.spec << "EOF"
%description
Bisq is ....

%preun
if [ "$1" = 0 ]; then
   xdg-desktop-menu uninstall --novendor /opt/Bisq/Bisq.desktop
fi

%post
xdg-desktop-menu install --novendor /opt/Bisq/Bisq.desktop

%files
/opt/Bisq
EOF

pushd bisq-$version
rpmbuild --buildroot=$(pwd) -bb --target x86_64 ../bisq.spec
popd
