FROM eclipse-temurin:8-jre-alpine

RUN apk add --no-cache ca-certificates tzdata && \
    update-ca-certificates && \
    rm -f /opt/java/openjdk/lib/security/cacerts && \
    keytool -importkeystore \
        -srckeystore /etc/ssl/certs/java/cacerts \
        -srcstorepass changeit \
        -destkeystore /opt/java/openjdk/lib/security/cacerts \
        -deststorepass changeit \
        -noprompt 2>/dev/null || \
    cp /etc/ssl/cert.pem /tmp/ca-bundle.crt && \
    keytool -import -trustcacerts -noprompt \
        -keystore /opt/java/openjdk/lib/security/cacerts \
        -storepass changeit \
        -file /tmp/ca-bundle.crt 2>/dev/null || \
    true

WORKDIR /app
COPY target/lzb-indexer-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/app.jar"]