if [ ! -e "server/target/server-2.10-SNAPSHOT.jar" ]
then
  mvn clean package
fi
java -cp server/target/server-2.10-SNAPSHOT.jar simpledb.server.Startup $1
