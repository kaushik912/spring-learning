# remote JMX for JMC

Flags to open the JVM up to a remote JMX client (JMC, VisualVM) instead of
localhost-only attach:

```bash
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=7091
-Dcom.sun.management.jmxremote.rmi.port=7091
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Djava.rmi.server.hostname=<WSL_IP>
```

`java.rmi.server.hostname` must be the WSL IP (not `localhost`) so the RMI
stub handed back to the remote client is actually reachable from outside
WSL. Get it with `hostname -I`. In JMC, connect to `<WSL_IP>:7091`.

## Full example (mysql profile)

```bash
SPRING_PROFILES_ACTIVE=mysql \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<pass> \
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=7091 \
     -Dcom.sun.management.jmxremote.rmi.port=7091 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -Djava.rmi.server.hostname=<WSL_IP> \
     -jar target/db-io-exhaustion-demo-0.0.1-SNAPSHOT.jar
```
---
Finally, 
Connect in JMC using the WSL_IP and JMX Port ( i.e 7091)