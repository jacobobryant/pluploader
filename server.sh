#!/bin/bash

function serve {
    nc -l -p 8080 <<< "$(printf "$(date)\nBig surprise for you!\n")"
    date
}
serve
while true; do
    echo
    serve
done
