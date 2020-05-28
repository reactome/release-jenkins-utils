## Jenkins user builds the graph db during Release, but Neo4j user is needed to replace graph.db folder at /var/lib/neo4j/data/databases/.
## This script changes all necessary permissions from Jenkins to Neo4j beforehand.
chmod 644 /tmp/graph.db/*
chmod a+x /tmp/graph.db/schema/
chown -R neo4j:adm /tmp/graph.db/
mv /var/lib/neo4j/data/databases/graph.db /var/lib/neo4j/data/databases/extra/
mv /tmp/graph.db /var/lib/neo4j/data/databases/
