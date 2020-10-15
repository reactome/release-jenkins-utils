package org.reactome.release.jenkins.utilities

def getReleaseVersion()
{
    return (pwd() =~ /Releases\/(\d+)\//)[0][1];
}