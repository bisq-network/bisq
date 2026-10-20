FROM azul/zulu-openjdk:21.0.6-21.40 as build

COPY '{{ ARCHIVE_PATH }}' /
RUN tar xvf /seednode.tar

FROM gcr.io/distroless/java21-debian12
COPY --from=build /seednode /seednode

ENTRYPOINT ["java", "-classpath", "'{{ CLASS_PATH }}'", "'{{ MAIN_CLASS }}'"]
