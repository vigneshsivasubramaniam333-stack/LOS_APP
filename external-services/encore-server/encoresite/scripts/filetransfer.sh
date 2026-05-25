#!/bin/bash

[[ $# -ne 2 ]] && {
        echo "Usage: `basename $0` ENCORE_SITE_DIR SITE"
        exit 1
}
ENCORE_SITE_DIR=$1
site=$2
reportsdir=$ENCORE_SITE_DIR/reports
logdir=$ENCORE_SITE_DIR/logs
logfile=$logdir/FileTransfer-`date +%d-%m-%Y`.log
trfile=$ENCORE_SITE_DIR/file.txt

newerThan=
[ -f "$trfile" ] && lastReadFile=`cat $trfile`
[ -f "$lastReadFile" ] && newerThan=" -newer $lastReadFile"
[ -d "$reportsdir" -a -d "$logdir" ] || exit 1

echo `date "+%Y-%m-%d %T"` "Started file transfer of files newer than" $lastReadFile >> $logfile
for file in `find "$reportsdir" -type f $newerThan -exec ls -1rt "{}" + | grep -E "AllDataModified-$site-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].csv|BulkRepaymentSchedule-$site-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].csv|Disbursement-$site-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].csv|Repayment-$site-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].csv|BucketWiseDueDemand-$site-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].csv"`
do
        echo `date "+%Y-%m-%d %T"` Transferring $file >> $logfile
        azcopy --source $file --destination https://samfinblob.blob.core.windows.net/all-data-container/`basename $file` --dest-key=Bz/cI5JVToUkJghxZwv7dIS4mrLhgeuqK0l+AyhAD2yjYuRdMxXtjXp8wuLX4f8Jopsxjvuz99rCCMT9cEW0UQ==
        echo $file > $trfile
done
echo `date "+%Y-%m-%d %T"` "Completed file transfer " >> $logfile
exit 0