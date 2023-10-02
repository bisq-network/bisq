FROM azul/zulu-openjdk:11.0.20-11.66.15

COPY '{{ ARCHIVE_PATH }}' /
RUN tar xvf /seednode.tar

ENTRYPOINT ["bash",  "/seednode/bin/seednode"]
