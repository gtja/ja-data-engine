FROM registry.cn-hangzhou.aliyuncs.com/jingansi/ubuntu:v1.0-jdk11

ENV LANG en_US.utf8
ENV TZ=PRC

RUN bash -c "mkdir -p /home/jingansi/apps/{logs,config}"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ARG JAR_FILE=./engine-api/target/*.jar
COPY ${JAR_FILE} /home/jingansi/apps/app.jar

WORKDIR /home/jingansi/apps

COPY ./build/run.sh /home/jingansi/apps/run.sh

ENTRYPOINT ["sh", "run.sh"]
