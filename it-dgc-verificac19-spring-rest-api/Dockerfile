FROM adoptopenjdk/openjdk11:latest
RUN mkdir -p /software/app
COPY target/it-dgc-verificac19-spring-rest-api-0.2.4.jar /software/app/c19.jar
CMD java -jar /software/app/c19.jar