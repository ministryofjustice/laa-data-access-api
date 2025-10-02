FROM amazoncorretto:21-alpine

#update all packages
RUN apk update
RUN apk upgrade

#mount volume
WORKDIR /data-access-service
VOLUME /tmp
COPY data-access-service/build/libs/data-access-service-*.jar data-access-service.jar
EXPOSE 8080

#create custom user, give it uid 100001 which is same as deplyment security runAsUser attribute
#RUN addgroup --system --gid 100001 customgroup && adduser --system --uid 100001 --ingroup customgroup --shell /bin/sh customuser
#RUN chown customuser:customgroup providers-app.jar
#USER 100001

#run jar file
ENV TZ=Europe/London
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0"
CMD java -jar data-access-service.jar