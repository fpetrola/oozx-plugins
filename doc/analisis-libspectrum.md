# Fuse y libspectrum como origen de los formatos: qué hay, qué nos falta y qué conviene

Análisis de los fuentes de libspectrum y de Fuse para decidir si conviene tomar sus
implementaciones de formatos de archivo, pasarlas a Java, y hacer sobre eso el rediseño OOP con
TDD — en vez de rediseñar los lectores que hay (que vienen de JSpeccy).

Medido el 24 de septiembre de 2026 sobre `oozx/libspectrum` (1.6.4, agosto de 2026) y
`oozx/fuse-emulator-fuse`.

**La respuesta corta: sí a libspectrum, no a Fuse.** libspectrum es exactamente la capa que nos
falta — formatos puros, sin emulación, con un modelo de datos único para snapshots, cintas y
grabaciones, 18 máquinas, 46 bloques SZX, 362 tests y un corpus — y ya está instalada acá y
enlazada por JNA, así que cada función portada se puede comparar con la original. De Fuse no
sirve el código (su emulador es otro) pero sí **un patrón**: cómo cada periférico se lleva su
estado al snapshot y lo trae de vuelta, que es justo lo que a nuestro `Snapshots` le falta a
medias.

## Cómo está repartido el trabajo entre los dos

- **libspectrum** convierte bytes en structs y structs en bytes. No sabe qué es un Z80 ni una
  ULA: `libspectrum_snap` es un registro de 240 campos, `libspectrum_tape` es una lista de
  bloques que se reproduce como una secuencia de flancos, `libspectrum_rzx` es una grabación.
  23.263 líneas de C.
- **Fuse** es la máquina. Entre los dos hay 182 líneas (`snapshot.c`) y un registro de módulos
  (`module.c`, 124 líneas): cada periférico registra hasta tres funciones —
  `snapshot_enabled` (el snapshot dice si el periférico está), `snapshot_from` (el periférico
  toma lo suyo del snapshot) y `snapshot_to` (lo escribe). 38 módulos registrados, 59 hooks
  `from`/`to`, 20 `enabled`.

Nuestro reparto es el mismo a medias: los lectores (`SnapshotFile` y sus cuatro) son la parte de
libspectrum; `Snapshots` es `snapshot.c`; `RestoredFromASnapshot` es `snapshot_from`. **No hay
`snapshot_to` ni `snapshot_enabled`**: por eso `SnapshotSaver` escribe siempre un 48K con las
tres páginas que la CPU ve, y un snapshot de un juego con DivIDE pierde el DivIDE.

## libspectrum, área por área

| área | libspectrum | Java hoy | la brecha |
|---|---|---|---|
| **snapshots** | `szx.c` 4239, `z80.c` 1870, `sna.c` 503, `sp.c` 127, `snapshot.c` 237, más `zxs.c` 519, `dsnap.c` 485, `plusd.c` 217, `snp.c` 77 ≈ **8.300**. DTO de 240 campos generado de `snap_accessors.txt`. **18 máquinas** con capacidades (`128_MEMORY`, `PLUS3_MEMORY`, `TIMEX_*`, `SCORP_MEMORY`, `PENT512/1024`, `SE_MEMORY`, `NTSC`…) | 2832 en cuatro lectores + 811 de DTO/loader/saver. `MachineTypes` tiene **6** máquinas | SZX: **37 bloques leídos** (y 9 salteados a sabiendas) contra **12**. Z80: acepta SamRam, MGT, Scorpion, TC2048/2068, TS2068, +3 "XZX"; Java los rechaza y lee Pentagon "como 128K". Escribe SNA, Z80 y SZX (72 escritores de bloque) |
| **cintas** | `tape.c` 1660, `tape_block.c` 824, `tzx_read.c` 1194, `tzx_write.c` 963, `tap.c` 314, `csw.c` 364, `pzx_read.c` 676, `wav.c` 126 (+ backends), `warajevo_read.c` 633, `z80em.c` 73 ≈ **6.800**. 16 tipos de bloque. **La reproducción es una API de flancos** (`libspectrum_tape_get_next_edge`): quien reproduce no sabe de formatos | `Tape.java` 1831 + `TapeBlock.java` 305: TAP, TZX (24 ids de bloque), CSW; formato y reproducción en la misma clase; 2 tests | PZX, WAV, Warajevo, Z80EM, SPC/STA/LTP, todos los bloques TZX, `guess_hardware`, escribir TZX. Y la separación formato / flancos |
| **RZX** | `rzx.c` 1771 + `crypto.c` 353: leer, **escribir, grabar**, rollback, snapshots embebidos, firmas DSA | 500 líneas, sólo reproducción | grabar, firmar |
| **identificar** | `libspectrum_identify_file_raw`: **46 firmas** por extensión *y* contenido (magia con offset), más gz, bz2 y zip con localización adentro (`libspectrum_zip_*`) | por extensión, rol por rol (`handles(File)`); `DownloadAndUnzip` | contenido, contenedores en un lugar |
| **timings** | `timings.c` 282: T-states por frame y por línea, bordes, para las 18 máquinas | `MachineTypes`, 6 | las otras 12 |
| **otros** | `ide.c` 909 (HDF), `mmc.c` 762 (MMC/SD para DivMMC/ZXMMC), `microdrive.c` 297 (MDR), `dck.c` 229 (dock Timex) | repartido en `devices/ide`, `disk`, `interface1`, `timex` | según cada plugin |
| **disco** | **no está en libspectrum**: los formatos de disco viven en Fuse, `peripherals/disk/disk.c`, 3114 líneas, 14 formatos (DSK, EDSK, UDI, FDI, TD0, SCL, TRD, OPD, MGT, IMG, D40, D80, SAD, LOG) | `device-disk` 5472 líneas, imagen y controladora mezcladas | es un caso aparte, de Fuse, para cuando `device-disk` lo pida |

## Cómo está escrito, y qué tan mecánico es portarlo

**Tiene ya la estructura que el plan pedía.** SZX es una tabla `read_chunks[]` de id → función
(46 entradas) y un `write_<bloque>_chunk` por bloque: literalmente "una clase por bloque". TZX es
un `switch` por tipo de bloque con un `tzx_read_*` por tipo (45). Los bytes se leen con
`libspectrum_read_word/dword` y se escriben con `libspectrum_buffer_*`: nuestro `Bytes`. Los
accessors del snap se generan de una tabla: un DTO.

**La plomería de C desaparece en Java.** Sólo en `szx.c` hay 94 `libspectrum_print_error` y 41
`new`/`free`; los códigos `libspectrum_error` (`CORRUPT`, `UNSUPPORTED`, `INVALID`, `SLT`…)
son una excepción con causa. Estimo que el Java queda en un 40–50 % de las líneas del C.

**Los casos de borde ya están resueltos, y con nombre.** `pointer_wraparound_in_szx_file`,
`sna_file_with_sp_0x4000`, `sna_file_with_sp_0xffff`, `invalid_compressed_file`; el cambio de A
y F de libspectrum < 0.5.0 (que nuestro `SnapshotSZX` ya copió de acá); y en `z80.c`,
`uncompress_block` desempaqueta a un buffer que crece y valida el largo después — nuestro Lazy
Jones es un caso de esa clase, resuelto por diseño y no por parche.

**Sus tests se portan uno a uno.** 362 funciones `test_return_t nombre(void)` en 21 archivos
(`szx.c` 23, `snap-read.c`, `snap-memory.c`, `snap-peripherals.c`, `snap-z80.c`, `tape.c`,
`tape-edges.c`, `tape-iterator.c`, `edges.c`, `identify.c`, `rzx.c`, `timings.c`…), escritas
sobre un helper `read_snap(archivo, errorEsperado)`. Con corpus: 15 TZX, 2 TAP, 2 PZX, 2 Z80,
3 SZX más los 36 de un bloque, 1 RZX, 1 MDR.

**La licencia es compatible.** Cada archivo dice GPL-2 "or (at your option) any later version"
(Philip Kendall y otros): entra en nuestra GPL-3 conservando los avisos de copyright y diciendo
que deriva.

**Las dependencias nativas tienen equivalente.** zlib → `java.util.zip`; bzip2 → commons-compress
o no soportarlo; libgcrypt (firmas RZX) → JCA; glib (`GSList`, `GHashTable`) → colecciones;
audiofile (WAV) → `javax.sound`.

**Y el oráculo es gratis.** La `.so` está instalada, `bridge` la enlaza por JNA, lee los cuatro
formatos y escribe tres. Cada función portada se compara con la original sobre el corpus, campo
por campo (faltan enlazar accessors: una línea cada uno). Es TDD con la respuesta correcta
conocida de antemano — más que "que siga funcionando tal cual", que es lo único que el plan
anterior podía prometer.

**Pero no es infalible, y no hay que portarla a ciegas.** Armando la red del paso 0 apareció un bug
en `libspectrum_sp_read`: copia la memoria a `&memory[start]` sin restarle 0x4000, sobre un buffer
de 48K. Un SP de 48K escribe 16K fuera del buffer y el proceso muere; uno de 16K queda una página
más arriba. Está en la 1.5.0 instalada y en la 1.6.4 del repo, y ningún test de libspectrum lee un
SP. Donde libspectrum no tiene tests, la referencia no vale más que el código que se está
reemplazando.

## Lo que se gana, en concreto

1. **Máquinas.** Un snapshot de Pentagon, Scorpion, Timex o SE va a su plugin, que ya existe,
   en vez de leerse como 128K o rechazarse. Hoy `SnapshotChoosesItsMachineTest` dice que las
   variantes "no reclaman ningún modelo".
2. **Periféricos adentro del snapshot.** Los bloques que Java saltea — B128, PLSD, OPUS, DIDE,
   DMMC, ZXAT, ZXCF, COVX, DRUM, AMXM, ZXPR, SCLD, DOCK, SNET, USPE — son exactamente plugins
   que este repo ya tiene: beta128, plusd, opus, divide, divmmc, zxatasp, zxcf, covox, specdrum,
   mouse, printer, timex, interface1. Con el patrón de Fuse, cada plugin trae su bloque.
3. **El camino de vuelta.** `snapshot_to` en 59 hooks. Un `WrittenToASnapshot` al lado de
   `RestoredFromASnapshot` hace que guardar desde un 128K guarde los ocho bancos, y que el DivIDE
   se guarde con el juego. Y `EnabledBySnapshot` (20 hooks en Fuse): el snapshot enchufa lo que
   trae.
4. **Cintas.** Formato y reproducción separados por la API de flancos; los formatos que faltan;
   `Tape.java` deja de ser 1831 líneas de las dos cosas.
5. **RZX.** Grabar, no sólo reproducir.
6. **Un solo lugar** que identifica por contenido y abre zip, gz y bz2.

## Lo que cuesta, y los riesgos

- **Tamaño.** ≈ 19.000 líneas de C relevantes (snapshots 8.300, cintas 6.800, RZX 2.100,
  identificar y contenedores 1.100, timings 300); en Java, estimo 8.000 a 10.000. **No hace
  falta todo de una**: se porta por formato, y cada uno reemplaza al Java existente cuando pasa
  su suite. Lo que no se porta sigue como está.
- **El DTO.** `libspectrum_snap` tiene 240 campos: `pages[16]`, `zxatasp_ram[32]`, `zxcf_ram[64]`,
  `divmmc_ram[64]`, `divide_ram[4]`, dock y exrom, spectranet, multiface, if1/if2, beta, plusd,
  opus, disciple, didaktik… `SpectrumState` tiene ~50. Los consumidores de `SpectrumState` son
  pocos (AY, `Snapshots`, los tests de paginado, ULAplus, +3), así que se puede crecer por
  familias, o hacer un `Snap` nuevo y dejar `SpectrumState` como vista de compatibilidad mientras
  dura la transición.
- **`MachineTypes`** tiene que pasar de 6 a las 18 de libspectrum (o mapear): toca
  `Machine.forSnapshotModel` y el test que hoy afirma "6 modelos".
- **C en Java.** El riesgo real es traducir `read_z80r_chunk` línea por línea, con sus
  `print_error` y sus punteros, y quedarse ahí. El molde es el del plan de snapshots: cursor de
  bytes, tabla de registros, una clase por bloque. **Se porta a ese molde**, y el test de cada
  pedazo se escribe antes, contra la C.
- **Dos repos, y oozx en obras** (la reescritura de historia). Igual que en el plan anterior: lo
  compartido nace donde se pueda y baja después; instalar en `~/.m2` se coordina.
- **Fuse no se porta.** Su Z80, su ULA y su memoria son otros; el nuestro ya existe. De Fuse se
  copia el patrón `enabled`/`from`/`to`, y se mira `disk.c` el día que `device-disk` quiera más
  formatos.

## Cómo se haría (esbozo, para reescribir el plan si se decide)

0. **La red**, igual que en el plan de snapshots: fixtures, goldens, oráculo. Más: los 362 tests
   y el corpus de cintas de libspectrum, y más accessors enlazados en `bridge` para comparar
   campo por campo.
1. **La base**: `Snap` (el DTO), las máquinas con sus capacidades, `Bytes`, identificación por
   contenido y contenedores. El contrato en oozx y el motor en oozx-plugins (ver
   `plan-snapshots.md`). `SpectrumState` queda como adaptador mientras tanto.
2. **Snapshots**, en el orden que ya estaba — SP → SNA → SZX → Z80 — pero cada uno portado de
   `sp.c`, `sna.c`, `szx.c`, `z80.c` con su suite. Cada bloque SZX es una clase
   (`read_ay_chunk` + `write_ay_chunk` → `AyBlock`) en el plugin de snapshots; los bloques de
   periféricos son "una forma de entrar" que cada plugin trae cuando la necesita (el DivIDE
   trae `DivideBlock`).
3. **Los hooks**: `EnabledBySnapshot` y `WrittenToASnapshot` junto a `RestoredFromASnapshot`;
   `Snapshots.save` deja de ser 48K; `SnapshotLoader` y `SnapshotSaver` se van.
4. **Cintas**: `tape.c` (flancos) y los lectores; `Tape.java` se parte en formato y
   reproducción; el oráculo es `libspectrum_tape_get_next_edge` sobre los 15 TZX, con
   `tape-edges.c` y `edges.c` portados.
5. **RZX** (grabación), contenedores, timings: según haga falta.

## La recomendación

Portar libspectrum, formato por formato, al diseño que ya está en `plan-snapshots.md`, con la
librería C de oráculo y sus tests como semilla de los nuestros. Empezar por snapshots, como
estaba planeado; el paso 0 es el mismo. No portar Fuse; sí copiar su patrón de módulos.

Lo que hay que decidir antes:

1. **DTO**: un `Snap` nuevo con `SpectrumState` como vista, o crecer `SpectrumState` por
   familias (recomendado: `Snap` nuevo; el otro camino deja el DTO de JSpeccy como centro).
2. **`MachineTypes`** a 18 máquinas con capacidades (recomendado: sí, es lo que deja que un
   snapshot llegue a su plugin).
3. **Dónde vive la capa**: decidido en `plan-snapshots.md`. En oozx va sólo el contrato (roles,
   documento, conceptos, máquinas, `Library`), y el motor y los formatos van en oozx-plugins.
4. **Alcance inicial de SZX**: los bloques de la máquina base y los de los plugins que ya
   existen; el resto salteado a sabiendas, como hace libspectrum.
5. **Atribución**: cabecera con el copyright de libspectrum en cada archivo derivado.
