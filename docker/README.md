# Running Bisq using Docker

Running Bisq using Docker has currently only been tested using Linux.

## Build the image

```sh
docker build -t bisq .
```

## Running Bisq

To ensure that your Bisq configuration is persisted to the host
to survive between `docker run` invocations,
we create a directory and then bind-mount it into the container.

```sh
if [ ! -d "${HOME}/.local/share/Bisq" ]; then
  mkdir -p ~/.local/share/Bisq
fi

docker run -ti --rm \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  -e DISPLAY=$DISPLAY \
  -v $HOME/.local/share/Bisq:/root/.local/share/Bisq:rw \
  bisq ./bisq-desktop
```

## Troubleshooting

#### I see "Exception in thread "main" java.lang.UnsupportedOperationException: Unable to open DISPLAY" when I start the Docker container.

You may need to modify your X11 access; try running 
```sh
xhost +
```

and then retrying the `docker run` command.
