#!/usr/bin/env bash
mkdir "${HOME}/.syncvr"
echo "${GOOG_CREDS_DEV}" > "${HOME}/.syncvr/dev.json"
echo "${GOOG_CREDS_PROD}" > "${HOME}/.syncvr/prod.json"
# make sure we don't corrupt the current last line by appending
echo "" >> gradle.properties
echo "${GRADLE_PROPS}" >> gradle.properties
# Some git-fu to hide from git the diff in gradle.properties
# If git sees that file has a diff, the release is "-dirty"
git update-index --assume-unchanged gradle.properties