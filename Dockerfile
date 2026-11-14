FROM eclipse-temurin:21-jre
# data2의 NFS 폴더 소유자를 이 uid로 맞추므로 uid를 고정한다.
RUN useradd --uid 1001 --create-home app && mkdir /data && chown app:app /data
USER app
WORKDIR /home/app

COPY --chown=app:app build/libs/option.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
