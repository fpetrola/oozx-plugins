# OOZX plugins

Everything [OOZX](https://github.com/fpetrola/oozx) can be given and does not come with: the
boards somebody cabled onto a Spectrum, the machines that are not a 48K, the windows that look
at what a machine is doing, and the readers for the files it did not ship with.

Each one is published as a release of this repository and is a jar. Dropping it in
`~/.oozx/plugins` is all there is to installing one; the emulator's own Plugins window brings
them from here, which is the usual way.

## What is here

| | |
|---|---|
| `devices/` | boards that answer ports, and the machines that are not the one the build carries |
| `tools/` | windows over what a machine already has: its keys, its stick, a recording |

## Building it

These are built against the emulator, which lives in its own repository and publishes no
artefacts anywhere. So its tree has to be built first, into the local repository:

```
git clone https://github.com/fpetrola/oozx
mvn -DskipTests install -f oozx/pom.xml
mvn install                                  # here
```

That is what the workflow does too, on every push.

## Writing one

A plugin is a jar that says which way in it answers to, and nothing else. Every way in there is,
with the interface and a working example of each, is in
[Extending OOZX](https://github.com/fpetrola/oozx/wiki/Extending-OOZX). A plugin of your own
does not belong here: yours is a jar of yours, and the emulator finds it the same way it finds
these.
