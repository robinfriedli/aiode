#!/bin/sh
git fetch

UPSTREAM=${1:-'@{u}'}
LOCAL=$(git rev-parse @)
REMOTE=$(git rev-parse "$UPSTREAM")
BASE=$(git merge-base @ "$UPSTREAM")

if [ $LOCAL = $REMOTE ]; then
    echo "Already Up-to-date"
elif [ $LOCAL = $BASE ]; then
    git pull
    ./gradlew build
    echo "Pulled updates and executed gradle build. You might need to restart the bot."
elif [ $REMOTE = $BASE ]; then
    echo "Need to push"
else
    echo "Diverged"
fi