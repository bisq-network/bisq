FROM azul/zulu-openjdk:11.0.20-11.66.15

COPY '{{ ARCHIVE_PATH }}' /
RUN tar xvf /seednode.tar

ENTRYPOINT ["java", "-classpath",  "'{{ CLASS_PATH }}'", "'{{ MAIN_CLASS }}'"]
