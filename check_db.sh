#!/bin/bash
adb pull /data/data/com.jacobobryant.playlistuploader/databases/recommendations.db
sqlite3 recommendations.db
