FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY zscaler-root.pem /usr/local/share/ca-certificates/zscaler-root.crt
RUN apk add --no-cache ca-certificates && update-ca-certificates && \
    keytool -importcert -trustcacerts -alias zscaler-root -storepass changeit -cacerts \
    -file /usr/local/share/ca-certificates/zscaler-root.crt -noprompt
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven

FROM build AS test
RUN MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true" \
    mvn test \
      -Dmaven.wagon.http.ssl.insecure=true \
      -Dmaven.wagon.http.ssl.allowall=true \
      -Dmaven.wagon.http.ssl.ignore.validity.dates=true \
      -Dmaven.resolver.transport=wagon

FROM build AS package
RUN MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true" \
    mvn package -DskipTests \
      -Dmaven.wagon.http.ssl.insecure=true \
      -Dmaven.wagon.http.ssl.allowall=true \
      -Dmaven.wagon.http.ssl.ignore.validity.dates=true \
      -Dmaven.resolver.transport=wagon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY zscaler-root.pem /usr/local/share/ca-certificates/zscaler-root.crt
RUN apk add --no-cache ca-certificates && update-ca-certificates && \
    keytool -importcert -trustcacerts -alias zscaler-root -storepass changeit -cacerts \
    -file /usr/local/share/ca-certificates/zscaler-root.crt -noprompt
COPY --from=package /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
