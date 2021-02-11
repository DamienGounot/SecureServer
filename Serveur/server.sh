#/bin/sh
clear
echo "Compilating Server java files..."
javac ServiceChat.java ChatServer.java
echo "Start Server..."
java ChatServer
