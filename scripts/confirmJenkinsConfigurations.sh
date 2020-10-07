#!/bin/bash
set -e

DIR=$(dirname "$(readlink -f "$0")") # Directory of the script -- allows the script to invoked from anywhere
cd $DIR

configFile=
jenkinsURLReleaseNumber=
jenkinsURLPreviousReleaseNumber=
userInputReleaseNumber=

## Parse command-line arguments.
while (( "$#" )); do
	case "$1" in
		-c|--config-file)
			configFile=$2;
			shift 2;
			;;
		-r|--release-jenkins-url)
			jenkinsURLReleaseNumber=$2;
			shift 2;
			;;
		-p|--release-previous-jenkins-url)
			jenkinsURLPreviousReleaseNumber=$2;
			shift 2;
			;;
		-u|--user-input-release)
			userInputReleaseNumber=$2;
			shift 2;
			;;
		-*|--*=)
			echo "Error: Unsupported flag $1"
			exit 1
	esac
done

## If missing arguments, explain usage.
if [ -z "$configFile" ] || [ -z "$jenkinsURLReleaseNumber" ] || [ -z "$jenkinsURLPreviousReleaseNumber" ] || [ -z "$userInputReleaseNumber" ]
then
	echo "Confirm Jenkins configurations.";
	echo "Compares 'release number' values for config file, Jenkins URL and from user input. This script should be invoked by a Jenkins process during Reactome's Release."; 
	echo "Usage: bash confirmJenkinsConfigurations.sh --config configFilepath --release-jenkins-url jenkinsURLReleaseNumber --release-previous-jenkins-url jenkinsURLPreviousReleaseNumber --user-release userInputReleaseNumber ";
	exit 1
fi

configFileReleaseNumber=

## Iterate through lines of config file, which should be in .properties format, looking for 'releaseNumber' value.
while read line; do
  if [[ $line =~ releaseNumber= ]] ; then
    configFileReleaseNumber=${line#*=}
  fi
done < $configFile

## Compare user input release number value with release number value in Jenkins URL.
if [[ $userInputReleaseNumber != $jenkinsURLReleaseNumber ]] ; then 
  echo "User input release number ($userInputReleaseNumber) does not match release number in Jenkins URL: ($jenkinsURLReleaseNumber)."
  exit 1
fi

## Compare user input release number value with config file's release number value.
if  [[ $userInputReleaseNumber != $configFileReleaseNumber ]]; then
  echo "User input release number ($userInputReleaseNumber) does not match config file's releaseNumber variable ($configFileReleaseNumber)."
  echo "Please update config file at 'Jenkins -> Releases -> $userInputReleaseNumber -> Credentials -> Config'."
  exit 1
fi

## Compare previous release number, created by subtracting from user input release number, with jenkins environment's previous release number value.
previousReleaseNumber="$(($userInputReleaseNumber-1))"

if [[ $previousReleaseNumber != $jenkinsURLPreviousReleaseNumber ]]; then
  echo "Jenkins URL 'previous' release number ($jenkinsURLPreviousReleaseNumber) does not match expected previous release number ($previousReleaseNumber)."
  exit 1
fi

echo "All checked configurations look appropriate."
