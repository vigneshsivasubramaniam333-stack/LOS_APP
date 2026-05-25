FROM eclipse-temurin:21-jdk
ENV ENCORE_HOME=/home/encoresite
RUN mkdir -p $ENCORE_HOME
COPY encore-boot-server-2026.05.01-mysql-local-nokafka-native.jar $ENCORE_HOME
COPY encoresite $ENCORE_HOME
COPY dumpAccount.sh $ENCORE_HOME
COPY dumpAccount_latest.sh $ENCORE_HOME
RUN mkdir -p $ENCORE_HOME/reports
RUN chmod 777 $ENCORE_HOME/conf/* && chmod -R 777 $ENCORE_HOME/myreports && chmod -R 777 $ENCORE_HOME/reports
ENTRYPOINT ["java"]