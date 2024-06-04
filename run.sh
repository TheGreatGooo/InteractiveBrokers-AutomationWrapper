#!/bin/sh

chmod u+x Scripts/IBController.sh

tail -F /tws/**/ibgateway.log &

/usr/bin/xvfb-run -e /tmp/xvbf.log --listen-tcp --server-num 99 -s "-auth /tmp/xvfb.auth -ac -screen 0 1920x1080x24 -listen tcp" Scripts/IBController.sh 1019 -g --tws-path=/tws --tws-settings-path=/tws --ibc-path=$PWD/target --ibc-ini=/ibc-config/IBController.ini
