# El corpus de libspectrum

Copiado de `libspectrum/test` (libspectrum 1.6.4), el conjunto de archivos con que libspectrum
prueba sus propios lectores: dos `.z80`, tres `.szx` (uno inválido, uno al azar), dos `.sna` con el
SP fuera de la RAM, y un `.szx` por cada bloque del formato en `szx-chunks/`. Los que venían
comprimidos con gzip están descomprimidos: están acá para leerse, no para probar gzip.

libspectrum es de Philip Kendall y otros, bajo la GPL versión 2 o posterior; estos archivos vienen
con esa licencia, compatible con la GPL 3 de este repositorio.

Los hace `MakeFixtures`, que también arma el resto de los snapshots de la carpeta de arriba.
