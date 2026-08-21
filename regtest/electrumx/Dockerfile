FROM python:3

WORKDIR /root

RUN git clone https://github.com/spesmilo/electrumx.git && \
    cd electrumx && \
    pip3 install . && \
    mkdir db_directory

WORKDIR /root/electrumx
