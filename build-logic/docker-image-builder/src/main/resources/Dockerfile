FROM ubuntu:24.04@sha256:73b999393d07f2bf65a40373778a44dc9a6d54ad7718d29247d8ff9413c4d6a8 AS build

COPY '{{ ARCHIVE_PATH }}' /
RUN tar xvf /seednode.tar

# Corretto 21.0.13.1.2 includes the fix for CVE-2026-114938.
FROM amazoncorretto:21-al2023-headless@sha256:71b005271e4df1a2fb44f6d261bad4318e66aff82ea7541b4d2968a9a31357d7
COPY --from=build /seednode /seednode

ENTRYPOINT ["java", "-classpath", "'{{ CLASS_PATH }}'", "'{{ MAIN_CLASS }}'"]
