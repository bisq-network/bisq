FROM azul/zulu-openjdk:11.0.20-11.66.15 as build

COPY '{{ ARCHIVE_PATH }}' /
RUN tar xvf /seednode.tar

FROM gcr.io/distroless/java11-debian11
COPY --from=build /seednode /seednode

ENTRYPOINT ["java", "-classpath", "'{{ CLASS_PATH }}'", "'{{ MAIN_CLASS }}'"]
