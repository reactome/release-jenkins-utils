#!/bin/bash
set -e
## Jenkins user builds the graph db during Release, but Neo4j user is needed to replace graph.db folder at /var/lib/neo4j/data/databases/.
## This script changes all necessary permissions from Jenkins to Neo4j beforehand.
tmpNeo4jDir="/tmp/graph.db"
finalNeo4jDir="/var/lib/neo4j/data/databases"
transactionDir="/var/lib/neo4j/data/transactions"
chmod 644 ${tmpNeo4jDir}/*
chmod a+x ${tmpNeo4jDir}/schema/
chown -R neo4j:adm ${tmpNeo4jDir}/
rm -r ${transactionDir}/graph.db
rm -r ${finalNeo4jDir}/graph.db
mv ${tmpNeo4jDir} ${finalNeo4jDir}/
