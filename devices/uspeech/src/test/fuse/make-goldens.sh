#!/bin/sh
# Builds Fuse's own SP0256 (peripherals/sound/sp0256.c, unchanged) into harness.c and runs it over
# every ROM in ../resources/sp0256 with the script there, writing expected.txt: the digest of all
# it printed, and how many lines. FUSE is the root of a Fuse source tree (1.9.2 was used).
set -e
here=$(cd "$(dirname "$0")" && pwd)
data="$here/../resources/sp0256"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
gcc -O0 -std=gnu99 -I"$here/stubs" -I"$FUSE/peripherals/sound" -o "$work/harness" "$here/harness.c"
: > "$data/expected.txt"
for rom in $(cd "$data" && ls rom*.bin | sort -V); do
  "$work/harness" "$data/$rom" "$data/script.txt" > "$work/out.txt"
  echo "$rom $(sha256sum "$work/out.txt" | cut -d' ' -f1) $(wc -l < "$work/out.txt")" >> "$data/expected.txt"
done
