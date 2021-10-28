# Java 18 - Project Loom Experiments

This repo is used to test project loom code and maven multi release builds for Java 8 + Java 18.


## Building

```
JAVA_HOME=/opt/jvm/java8
JAVA_HOME_18=/opt/jvm/java18
mvn clean verify
```

This will compile the main project code using Java 8. The other code in `src/main/java18` will be compiled using JDK 18 and added to the jar via the mutli-release jar mechanism.

Calling ```$JAVA_HOME/bin/java -jar target/loom-experiments-0.0.1-SNAPSHOT.jar``` will utilize the default Java 8 version of `JavaCompatUtil`.

Output:
```
Supports Virtual Threads: false
```

Using ```$JAVA_HOME_18/bin/java -jar target/loom-experiments-0.0.1-SNAPSHOT.jar``` uses the Java 18 version of that class.

Output:
```
Supports Virtual Threads: true
```
