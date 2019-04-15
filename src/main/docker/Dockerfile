FROM openjdk:8-jre-alpine

RUN apk update && apk add bash && rm -rf /var/lib/apk/* /var/cache/apk/*

VOLUME /etc/gridepo
VOLUME /var/gridepo
EXPOSE 9009

ENV JAVA_OPTS=""
ENV CONF_FILE_PATH="/etc/gridepo/gridepo.yaml"
ENV DATA_DIR_PATH="/var/gridepo"
ENV DATABASE_TYPE="postgresql"
ENV DATABASE_CONNECTION="//db/gridepo?user=gridepo&password=gridepo"

CMD [ "/app/start.sh" ]

ADD start.sh /app/start.sh
ADD gridepo /app/gridepo
ADD gridepo.jar /app/gridepo.jar
