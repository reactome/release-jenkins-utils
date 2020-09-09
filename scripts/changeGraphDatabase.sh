#!/bin/bash
set -e
## Jenkins user builds the graph db during Release, but Neo4j user is needed to replace graph.db folder at /var/lib/neo4j/data/databases/.
## This script changes all necessary permissions from Jenkins to Neo4j beforehand.
tmpNeo4jDir="/tmp/graph.db"
finalNeo4jDir="/var/lib/neo4j/data/databases"
chmod 644 ${tmpNeo4jDir}/*
chmod a+x ${tmpNeo4jDir}/schema/
chown -R neo4j:adm ${tmpNeo4jDir}/
## Existing graph database should either be archived already on S3 (final), or can otherwise be deleted (post-orthoinference, pre-biomodels).
## Either way, safe to delete the graph database here.
rm -r ${finalNeo4jDir}/graph.db
mv ${tmpNeo4jDir} ${finalNeo4jDir}/
