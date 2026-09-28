# Los formatos de snapshot, sobre conceptos y colocaciones, con libspectrum de oráculo

Plan para reemplazar los cuatro lectores de snapshot —Z80, SNA, SP y SZX, heredados de JSpeccy—
por formatos escritos sobre el diseño de `diseno-desde-cero.md`: los conceptos del Spectrum
modelados una vez con sus propiedades, y cada formato reducido a una tabla de colocaciones. Con
libspectrum (`analisis-libspectrum.md`) como oráculo de cada paso y como fuente del conocimiento
que a los conceptos les falte.

Medido el 24 de septiembre de 2026, con `devices/all` en 305 tests verdes (30 salteados).

**Estado: el paso 0 está hecho** (27 de septiembre de 2026): 117 tests en `devices/snapshots`,
verdes con el código de hoy. Lo que encontró está al final del paso 0.

Es la tercera versión del plan. La primera refactorizaba los lectores que hay; la segunda los
reescribía en un módulo nuevo de oozx; esta los reescribe **acá, en oozx-plugins**, y deja en
oozx sólo el contrato que el emulador necesita para entregar y recibir una máquina.

## El principio

**No es un refactor: es una reescritura guiada por tests.** Los lectores de hoy no se tocan. Se
construye la base (conceptos, colocaciones, motor), después cada formato se escribe test-first
sobre ella, y cuando pasa **la misma suite** que el viejo —los goldens y el oráculo del paso 0—
lo reemplaza en un commit y el viejo se borra. Nunca hay dos formatos a medias, y nunca se
mejora código que va a desaparecer.

**Los conceptos van antes que cualquier formato.** `Processor`, `MemoryMap`, `Paging`, `Ula`,
`AySound` y los demás se escriben primero, con su catálogo de propiedades y sus reglas, y con
libspectrum de lista de control (los 240 campos de su `snap`, por familia). Recién entonces un
formato es una tabla: SP en treinta líneas, SNA en cuarenta, cada bloque de SZX en diez.

**El rojo es del pedazo nuevo; los goldens nunca se ponen rojos.** Cada concepto, cada tabla,
cada codificación nace de un test que falla porque la clase no existe. Cuando un formato nuevo
reemplaza al viejo, los goldens del paso 0 tienen que seguir verdes tal cual, salvo en los casos
decididos y anotados abajo. Si un golden cambia sin decisión, el commit no entra.

## Dónde vive cada cosa

La regla es la de todo el proyecto: el emulador no depende de ningún plugin. Así que en oozx va
lo que el emulador usa o expone, y todo lo demás va en un plugin.

**En oozx, el contrato:**
- los roles: `SnapshotFormat`, `TapeFormat`, `Arrival`, `Captures`, `Restores` y `PluggedBy`;
- el documento `Snapshot` y los conceptos que tocan al menos dos formatos, cada uno con su
  catálogo de propiedades: `Processor`, `Memory`, `Paging`, `Ula`, `AySound`, `Joysticks`,
  `UlaPlus`, `Multiface`, `InterfaceOne`, `InterfaceTwo` y `Unread`;
- `MachineModel`, `Capability`, `Timings` y `MemoryMap`;
- lo que un formato devuelve y lanza: `Format`, `Image`, `Read`, `Identity`, `Notes` y los `Refusal`;
- la `Library`, que dice qué formato lee cada archivo;
- las partes del núcleo que entregan y reciben su pieza (paso 6), y `Snapshots`;
- el formato propio con que el emulador guarda sus sesiones (paso 6).

Va en `machine/spectrum`, donde hoy están `SnapshotFile` y `SpectrumState`, en un paquete propio:
todo depende de ese módulo y no hay que tocar ningún pom. Son records, interfaces y tablas.

**En oozx-plugins, todo lo demás:**
- `device-formats`, módulo nuevo: el motor (`Cursor`, `Sink`, `Codec`, `Layout`, `Codes`,
  `Encoding`, `Pages`, `Tagged`, `Framing`, `Context`) y los contratos de tests que heredan los
  formatos. Es un plugin sin ventanas del que dependen los demás, como hoy `device-snapshots`
  depende de `device-spectrum128`.
- `device-snapshots`: los cuatro formatos, **Z80 incluido**.
- `device-tape`: la cinta entera (el documento, `Signal`, los formatos y el deck). El núcleo hoy
  no usa la cinta para nada, y `Tape.java` ya vive ahí.
- Los conceptos de un solo periférico (`DivIde`, `Beta128`, `PlusD`…), con su bloque de SZX y su
  parte, en el plugin de ese periférico.

Un concepto de periférico que tocan varios formatos (el AY lo tocan Z80 y SZX) va en el
contrato aunque la parte que lo emula sea un plugin: el contrato es de datos, y un plugin puede
depender de él.

## Lo que hay hoy, en dos líneas

Cuatro lectores, 2832 líneas, con el conocimiento del Spectrum repetido en cada uno: el bucle de
"leer N bytes" dieciséis veces, la tabla de registros ocho veces (leer y escribir por formato),
el orden 5, 2, 0 en ocho lugares, la tabla de hardware del `.z80` dos veces, el AY tres. Los
números están en `analisis-libspectrum.md`.

Y quién usa el `.z80` desde oozx, que es lo que decide qué hay que resolver al sacarlo de ahí:
- **las sesiones del escritorio**, que se guardan como un `.z80` empaquetado en la configuración
  (`ZXSpectrumDesktopApp`, líneas 1150 y 2157);
- **`Snapshots.save` y `load`**, para los que el único formato del núcleo es el Z80;
- **el traductor** (`translation/translator`), que lee juegos `.z80` con
  `SnapshotLoader.setupStateWithSnapshot` en `EmulatedMiniZX`, en `RemoteZ80Translator` y en
  `JSWBytecodeCreationTests`. **oozx-lift depende del artefacto `translator`.** Los dos
  cargadores propios de `MiniZXWithEmulationBase` están muertos: uno tiene una sola llamada,
  comentada, y el otro ninguna;
- **cinco tests** que guardan o leen `.z80`: `SavingBringsTheMachineBackTest`, `TestGameExecution`,
  `LoadingASnapshotTest`, `AFormatThatArrivedTest` y `Z80SnapshotTstatesAsTheReferenceTest`.

## Paso 0: la red de seguridad — hecho

En `devices/snapshots/src/test`, paquete `model.tests.formats`, sin tocar código de producción ni oozx:
- **Fixtures:** 62. Son 43 del corpus de libspectrum (en `snapshots/libspectrum/`, con su `README.md`)
  y 19 hechas por `MakeFixtures`: Manic Miner en Z80 v1 crudo, v1 comprimido, v2 y v3, más SNA, SZX y SP;
  `banks` y `banks-5-on-top` (128K con un patrón por banco); `plus3-1ffd`; `sixteen` (16K); y
  `lone-ed-at-the-end.sna`.
- **Goldens:** en `snapshots/goldens/`, escritos por `WriteTheGoldens`. Usan los nombres del catálogo
  que viene (`a`, `bc`, `iff1`, `border`, `port7ffd`, `page.5`), así que siguen sirviendo cuando se
  reemplacen los lectores.
- **Tests:** `EveryFixtureReadsAsItDidTest`, `WhatIsWrittenIsWhatItWasTest`,
  `EveryFixtureReadsAsTheReferenceReadsItTest`, `WhatIsWrittenIsWhatTheReferenceReadsTest`,
  `ACutFileIsRefusedTest` y `AFormatAnswersForWhatItReadsTest`.
- **Diferencias conocidas:** en `snapshots/known/`, cada grupo con su porqué. Un test falla si
  aparece una diferencia que no está escrita, y también si una escrita deja de pasar.
- **El enlace con libspectrum es propio** (JNA en scope de test), no el de `bridge`.
- **libspectrum no es el oráculo del SP.** Su `libspectrum_sp_read` copia la memoria a
  `&memory[start]` sin restarle 0x4000, sobre un buffer de 48K. Un SP de 48K escribe 16K fuera del
  buffer y el proceso muere ("malloc(): corrupted top size"); uno de 16K queda una página más
  arriba. Pasa igual en la 1.5.0 instalada y en la 1.6.4 del repo, y libspectrum no tiene ningún
  test de SP. El lector SP queda sostenido sólo por sus goldens.

Lo que encontró en el código de hoy:
- **Escribir un `.z80` desde una memoria cuya página termina en un ED suelto revienta** con
  `ArrayIndexOutOfBoundsException` (queda en `lone-ed-at-the-end.sna.written.txt`).
- **Un SZX con ULAplus guardado por Fuse no abre:** libspectrum escribe el bloque PLTT con 67
  bytes (el último es el registro 0xff) y lee 66 o más; el lector de hoy exige 66 exactos.
- **Un SZX con un bloque que el lector no conoce se rechaza entero:** DIDE, DIRP, DMMC, DMRP,
  SIDE, SNEF, SNER, ZMMC. Son periféricos que este repositorio emula.
- **Un SZX sin Z80R o sin RAMP se lee sin procesador o sin memoria**, y la máquina fallaría al
  cargarlo; libspectrum completa con valores de fábrica.
- **Un `.z80` escrito sin joystick** vuelve como Cursor: el formato no tiene "ninguno".
- **Ningún archivo cortado hace reventar a un lector:** todos se rechazan con `SnapshotException`.

## Paso 1: el contrato, en oozx

Un commit aditivo en `machine/spectrum`: clases nuevas en un paquete nuevo, sin tocar nada de lo
que hay. Entra cuando la reescritura de historia de oozx esté empujada y esa sesión esté quieta;
mientras tanto se escribe en un worktree y se rebasea.

**Los conceptos, con su catálogo.** Cada uno nace de sus tests, y la lista de control es el `snap`
de libspectrum:
- `Processor`, `Registers`, `Interrupts`, `Reg` (23 registros, y los pares como propiedades
  derivadas) y las seis propiedades más. `ProcessorTest`: el catálogo cubre el record; `of` e
  `into` son inversas para cada propiedad; el valor de fábrica de cada una.
- `Memory` y `MemoryMap`. `MemoryMapTest`: las páginas de las dieciocho máquinas (16K: la 5;
  48K: 5, 2, 0; 128K: ocho; Pentagon 1024: sesenta y cuatro); `asMapped48k()`; `screen()` con y
  sin el bit 3 de `7ffd`.
- `Paging`. `PagingTest`: `bankAtTop()`, `locked()`, `romSelected()`, el `1ffd` de un +3.
- `Ula`, `AySound` (de placa, Fuller o Melodik), `Joysticks`, `UlaPlus`, `Multiface`,
  `InterfaceOne`, `InterfaceTwo` y `Unread`.
- `MachineModel` con `Capability` y `Timings`: dieciocho filas. `Machine.forSnapshotModel`, que
  ya existe, pasa a recibir un `MachineModel`.
- `Snapshot`, su builder, `Provenance`, `Notes`, y `Snapshot.describe()`: el volcado canónico desde
  el catálogo, en las mismas líneas que los goldens del paso 0.

**Lo que un formato devuelve y lanza:** `Format`, `SnapshotFormat`, `Image`, `Read`, `Identity`, y
los `Refusal` (`NotThisFormat`, `Corrupt`, `Unsupported`, `Invalid`).

**La `Library`:** qué formato lee un archivo, por magia, tamaño o extensión, entre los que haya.
Todavía no la usa nadie: el paso 6 la conecta.

Sale un contrato sin ningún formato, con unos 100 tests, y nada del emulador cambia.

## Paso 2: el motor, en oozx-plugins

Un módulo nuevo, `devices/formats`, con artifactId `device-formats`.
- **El motor:** `Cursor` (el único que lanza `Truncated`), `Sink`, `Codec` (`Raw`, `Zlib`),
  `Layout`, `Codes`, `Encoding`, `Pages`, `Tagged`, `Framing` y `Context`. Cada uno con su test.
- **Los contratos que heredan los formatos, en su test-jar:** `LayoutContract` (toda tabla hace ida
  y vuelta y cubre cada propiedad a lo sumo una vez), `CodecContract`, `EncodingContract`,
  `BlockContract` y `FormatContract` (identidad, fixtures, ida y vuelta, cortado en cualquier
  byte, lo que no se coloca queda de fábrica y se anota).
- **El puente, temporal:** `AsSnapshotFile`, que presenta un `SnapshotFormat` nuevo como el
  `SnapshotFile` de hoy (el `Snapshot` convertido a `SpectrumState`, con test de ida y vuelta).
  Con él, cada formato nuevo reemplaza al viejo dentro de `device-snapshots` sin que el emulador
  note nada, hasta que el paso 6 lo conecte de verdad.
- **La red del paso 0 pasa a leer por la `Library`:** los goldens no cambian, porque ya están en
  los nombres del catálogo.

Es el paso que más vale después de los conceptos: lo que se escribe acá lo usan los cuatro
formatos, y después las cintas.

## Paso 3: SP — la prueba del molde

El más chico y sólo lee. Sirve para ver si el molde cierra antes de gastarlo en los grandes.
- `SpFormatTest extends SnapshotFormatContract`: identidad (`SP` en 0), las fixtures
  (`manicminer.sp` y `sixteen.sp`), y cortado en cada byte. Sin oráculo, por el bug de libspectrum.
- `SpSize`: 16384 es un 16K, 49152 un 48K, y cualquier otro es `Invalid`; un inicio que no sea
  `0x4000` también es `Invalid`.
- La tabla: 24 colocaciones, `SP_IM` (bit 3 es IM0, si no bit 1 es IM2, si no IM1) y `Pages.asMapped48k()`.
- Lo que no coloca (AY, T-states, joystick) queda de fábrica y se anota: lo prueba el contrato.

Cuando pasa los goldens del paso 0, reemplaza a `SnapshotSP` y `SnapshotSP` se borra.

## Paso 4: SNA — dos formas, y la memoria dicha por los conceptos

- `SnaShape.ofLength`: 49179, 131103 y 147487; cualquier otro es `Invalid`. `SnaShape.of(model)`
  elige la forma de 48K o la de 128K según si la máquina pagina.
- La tabla del header: 21 colocaciones, más IFF2 que vale también para IFF1, y el borde.
- `OnTheStack`: al leer, el PC se desapila y el SP sube dos (decisión 3); un SP menor que `0x4000`
  o igual a `0xffff` es `Corrupt` (los dos `.sna` del corpus); al escribir, el PC se apila, y un
  SP menor que `0x4002` es `Invalid`. Con `EncodingContract`.
- 128K: `Pages.contiguous(5, 2).then(Pages.bankAtTop()).then(Pages.remaining())`, y el byte de
  TR-DOS en 1 es `NotSnapshot`. La variante de 147487 bytes sale sola de `bankAtTop()`.
- El oráculo sobre `manicminer.sna`, `banks.sna` y `banks-5-on-top.sna`; y lo que escribimos,
  leído por libspectrum.

Reemplaza a `SnapshotSNA`.

## Paso 5: Z80 — tres versiones, una cabecera

Va después de SNA y antes de SZX porque introduce, con un solo tipo de bloque, lo que SZX usa con
quince: las páginas son bloques con etiqueta (`Tagged`) y viajan codificadas (`Codec`).
- `Z80Rle` como `Codec`: la corrida que se pasa del fin de página (Lazy Jones) se corta; el ED
  suelto en el último byte va literal (el bug del paso 0); la página que no achica va cruda, y la
  que comprime a exactamente `0x4000` también. Y la variante de la versión 1, con la marca
  `00 ED ED 00` al final.
- `SplitR` y `FrameCounter`: los cuartos de frame, y `WITHOUT_COUNTER = 69664`. El test de T-states
  contra libspectrum se muda acá desde `bridge`.
- `Z80Hardware`: una tabla, con el número de la 2 y el de la 3 (el 128K es 3 y 4 en la 2, y 4 y 5
  en la 3); lo que dice el "hardware modificado"; lo que trae enchufado.
- `PageIds.of(model)`, y `Z80Version` con `V1`, `V2` y `V3`. Se escribe la 3.
- Los ids que hoy se rechazan (SamRam, MGT, Scorpion, Timex) pasan a leerse como libspectrum los
  lee (decisión 4). Cambia goldens: se regeneran en ese commit, con la decisión escrita.
- La extensión SLT queda en `Unread`: libspectrum la lee, y acá nadie la usa todavía.

Reemplaza a `SnapshotZ80` en todo lo que lee por la `Library`. El `SnapshotZ80` de oozx sigue
vivo sólo para el traductor, hasta el paso 8.

## Paso 6: SZX — bloques, y cada bloque una tabla

- `SzxBlock` como rol; `Tagged<Snapshot>` con `Framing.SZX`; `Unread` con `Unknown.KEEP`; los
  nueve ids que libspectrum saltea a sabiendas, en `skipping`.
- Un bloque por commit, cada uno con su `szx-chunks/<ID>.szx` del corpus y `BlockContract`:
  - `CRTR`, que deja el `Creator` en el contexto;
  - `Z80R`, con `SwappedAF` y las colocaciones que existen desde la 1.4 y la 1.5. El fixture con
    `libspectrum: 0.4.0` se sintetiza, y libspectrum confirma la regla;
  - `SPCR`, `KEYB` y `AY`;
  - `RAMP`, con `Codec.ZLIB` o crudo según la bandera; más de 16K es `Corrupt`;
  - `MFCE`, `PLTT` (66 bytes o más, decisión 6), `IF1`, `IF2R`, `JOY`, `LEC` y `LCRP`.
- Qué páginas escribe `RamPageBlock` lo dice `MemoryMap`; qué bloques se escriben, si su pieza está.
- Un SZX sin procesador o sin memoria se completa con valores de fábrica y se anota (decisión 8).
- El oráculo bloque por bloque sobre `banks.szx` y `manicminer.szx`.

Reemplaza a `SnapshotSZX`. Los bloques de periféricos (`DIDE`, `B128`, `PLSD`, `OPUS`, `ZXAT`,
`ZXCF`, `COVX`, `DRUM`, `AMXM`, `ZXPR`, `SCLD`, `DOCK`, `SNET`…) no entran acá: cada plugin trae el
suyo cuando tenga su concepto. `TAPE` es del plugin de la cinta. Mientras tanto viajan en
`Unread`, y un archivo que los tiene ya no se rechaza (decisión 7).

## Paso 7: la máquina como modelo recorrible, en oozx

La costura deja de ser código a mano y pasa a ser un recorrido: cada parte de la máquina entrega
y recibe su pieza, y `Snapshots` sólo recorre. En este orden:
1. **La máquina habla el catálogo.** `Cpu.get(Reg)` y `Cpu.set(Reg, int)` son un adaptador sobre
   `RegistersBase`, con una tabla `Reg → getter/setter` escrita una vez (hoy son dos tablas de
   treinta líneas, en `SnapshotLoader` y `SnapshotSaver`). Además, `SpectrumMemory.page(PageNumber)`,
   y `machine.model()` devuelve un `MachineModel`: la máquina usa el mismo `MemoryMap` que el
   snapshot para saber qué bancos tiene. Tests: cada `Reg` va y vuelve por el adaptador, y cada
   banco por `PageNumber`.
2. **Los tres roles:** `PluggedBy<P>`, `Restores<P>` y `Captures<P>`. `Snapshots.load` y `save`
   quedan en diez líneas que no nombran a nadie, y abren y guardan por la `Library`.
3. **Las partes del núcleo, cada una con `MachinePartContract`** (capturar, restaurar en una
   máquina limpia, capturar de nuevo: igual, propiedad por propiedad): `ProcessorPart` (un bucle
   sobre `Reg`), `MemoryPart` (banco por banco; al capturar, los que `MemoryMap` diga),
   `PagingPart` (por el puerto, para que pase lo que pasa cuando un juego escribe), `UlaPart` y
   `JoysticksPart`.
4. **El formato propio de las sesiones.** Es genérico sobre el catálogo: cada pieza por su nombre,
   cada propiedad por su nombre, las páginas como datos, sin motor. Guarda todas las piezas,
   también las de los plugins, que un `.z80` no puede. Las sesiones viejas, que son `.z80`, las lee
   el plugin Z80 (decisión 10).
5. **El AY del plugin** pasa de `RestoredFromASnapshot(SpectrumState)` a `Restores<AySound>`,
   `Captures<AySound>` y `PluggedBy<AySound>`, con el mismo contrato.
6. **`ASnapshotStartsAMachine`** pasa a `Arrival<Snapshot>`.
7. **Se borran** `SnapshotSaver`, el puente `AsSnapshotFile` y `RestoredFromASnapshot`. Queda lo
   que usa el traductor: `SnapshotFile`, `SnapshotFactory`, `SnapshotZ80`, `SnapshotLoader` y
   `SpectrumState` con sus partes.

Lo cubren, además de los contratos, `SavingBringsTheMachineBackTest` (que pasa a probar que un 128K
guarda sus ocho bancos, en el formato propio), `LoadingASnapshotTest`, `TestGameExecution`,
`AyFromSnapshotTest` y `PagingTest`.

Desde acá, un periférico que quiera viajar en el snapshot trae su concepto, su bloque SZX y su
parte con el contrato: el DivIDE, el Beta128, el Multiface… uno por vez, cada uno en su plugin.
Y nunca un formato escribe desde la máquina viva: siempre por la foto, que es lo que se compara,
se inspecciona y viaja.

## Paso 8: el traductor, en oozx y oozx-lift

Es lo último que lee `.z80` desde oozx, y oozx no puede depender de un plugin (decisión 11).
Recomendado:
- **El traductor recibe un `Snapshot` ya leído.** `SnapshotLoader.setupStateWithSnapshot(registers,
  archivo, state)` pasa a ser `setupStateWith(registers, snapshot, state)`, y quien tiene el
  archivo lo lee por la `Library`, con los formatos que haya.
- **`EmulatedMiniZX` y `RemoteZ80Translator`** leen por la `Library`. Corren con los plugins de
  formato en el classpath, como la aplicación.
- **`JSWBytecodeCreationTests`** baja juegos `.z80` sólo para sacarles la memoria: pasa a leer un
  volcado de memoria, que es lo que usa.
- **oozx-lift**, que depende del artefacto `translator`: antes de cambiar la firma, ver qué usa
  de él. Si lee archivos, suma `device-snapshots` y `device-formats` a sus dependencias.
- **Se borran** `SnapshotFile`, `SnapshotFactory`, `SnapshotZ80`, `SnapshotLoader`, `SpectrumState`,
  `Z80State`, `MemoryState`, `AY8912State` y los dos cargadores muertos de `MiniZXWithEmulationBase`.

## Paso 9, en otro plan

Las cintas, en `device-tape`: `Signal` como concepto cerrado, con `SignalVisitor` para
reproducir, medir y dibujar; TAP, TZX, CSW, PZX y WAV; el deck, que sólo conoce `Ear`. Después, el
RZX (grabar, no sólo reproducir) y los discos (`DiskImage` separado de la controladora). El molde
es el mismo y la base ya está.

## El orden, y por qué

Red → contrato → motor → SP → SNA → Z80 → SZX → costura → traductor.
- **La red primero**, porque antes no había: ningún test miraba un byte de SNA, SP ni SZX.
- **El contrato y el motor antes que cualquier formato**, porque es lo que hace cortos a los
  formatos: sin `Processor` con su catálogo y sin `MemoryMap`, un formato vuelve a saber lo que no
  debe. El contrato va primero porque el motor y los formatos dependen de él.
- **De menor a mayor**: SP prueba el molde con lo mínimo en juego; SNA agrega las formas y el
  paginado; Z80, los bloques y la compresión con un solo tipo de bloque; SZX, quince tipos.
- **La costura después de los formatos**, porque hasta ahí el puente `AsSnapshotFile` deja que
  todo lo nuevo alimente a la máquina vieja sin tocarla.
- **El traductor al final**, porque es el único que todavía necesita lo viejo, y porque toca un
  artefacto del que depende otro proyecto.

## Los gates

Después de cada commit acá: `mvn -pl devices/formats test` (el motor, segundos), `mvn -pl
devices/snapshots test` (los formatos) y `mvn -pl devices/all test` (la máquina alrededor: 302
hoy). Con libspectrum presente, los de oráculo corren solos; sin ella, los goldens hacen el mismo
trabajo. En los pasos que tocan oozx (1, 7 y 8): sus tests de `spectrum`, `bridge`,
`media/snapshot`, `app` y `translation`; y el CI de este repo, que compila los plugins contra el
oozx de `main`.

## Decisiones tomadas por el diseño, y las que faltan

Ya decididas: `Snapshot` nuevo con conceptos y propiedades (no crecer `SpectrumState`);
`MachineModel` con dieciocho máquinas; el contrato en oozx y el motor y los formatos en
oozx-plugins; los layouts fijos en `Layout` propio (Kaitai, si se quiere, como IDE y spec, no como
generador).

Faltan, y cambian goldens cuando llegue cada paso:
1. **El creador que escribe SZX**: hoy `JSpeccy v0.93` con la versión mal codificada.
   Recomendado: `OOZX` y la versión real.
2. **Un 16K, o un +2A o un +3, guardado como SNA**: hoy el 16K revienta con `NullPointerException`
   y el +2A y el +3 se rechazan. Recomendado: guardarlos como libspectrum, el 16K como 48K y los
   otros como 128K, y que las notas digan qué se pierde.
3. **El SNA de 48K al leer**: desapilar el PC (como libspectrum) o dejar `0x72` y el RETN de la
   ROM (como hoy). Recomendado: desapilar; es lo que el formato significa.
4. **Los hardware ids del `.z80`** que hoy se rechazan: leerlos como libspectrum. Recomendado: sí.
5. **Los `System.out.println`** de SZX: recomendado sacarlos; lo salteado va a `Notes`.
6. **El bloque PLTT de SZX:** aceptar 66 bytes o más, como libspectrum. Recomendado: sí.
7. **Un bloque de SZX que nadie conoce:** hoy corta la lectura entera. Recomendado: guardarlo en
   `Unread` y seguir, como hace libspectrum.
8. **Un SZX sin procesador o sin memoria:** hoy deja esas partes vacías. Recomendado: completarlas
   con los valores de fábrica, como libspectrum, y anotarlo.
9. **El `.z80` que se escribe desde una página que termina en un ED suelto:** hoy revienta.
   Recomendado: escribir ese ED como literal (no hay nada que decidir, es un bug).
10. **Las sesiones del escritorio:** hoy se guardan como `.z80`. Recomendado: guardarlas en el
    formato propio, que guarda todas las piezas, y leer las viejas con el plugin Z80. Sin el
    plugin, una sesión vieja se pierde una vez.
11. **El traductor:** hoy lee `.z80` desde oozx. Recomendado: que reciba un `Snapshot` ya leído
    (paso 8). La alternativa es dejar un lector Z80 en oozx sólo para él, pero eso trae de vuelta
    el motor a oozx o deja vivo el lector viejo.

## Coordinación

- **oozx está en obras**: otra sesión reescribe su historia. Los pasos 0 y del 2 al 6 no tocan
  oozx. El 1 es aditivo, y entra cuando esa reescritura esté empujada y la sesión esté quieta; el 7
  y el 8 también se coordinan.
- **`~/.m2` es compartido**: antes de cada `install` de oozx, avisar a las otras sesiones y esperar.
- **El CI de este repo** construye oozx de `main` antes que los plugins, así que `device-formats` ve
  el contrato en cuanto el paso 1 entra en oozx.
- **`device-formats` es un plugin del que dependen otros**: se publica como cualquier `device-*`, y
  el framework trae lo que un plugin necesita de otro, como ya hace con `device-spectrum128`.
- **oozx-lift** depende del artefacto `translator`: el paso 8 se habla antes con quien trabaje ahí.

## Lo que no hay que hacer

- **No refactorizar los lectores viejos.** Se reemplazan y se borran.
- **No empezar un formato sin su concepto.** Si al escribir SNA aparece algo que `Paging` no sabe,
  se agrega a `Paging` con su test, no al formato.
- **No poner significado en un formato.** Un formato coloca; si hace un `if` sobre la máquina, es
  una pregunta que le falta a `MachineModel` o a `MemoryMap`.
- **No poner en oozx nada que no use o exponga el emulador.** Un formato, el motor o un concepto
  de un solo periférico van en un plugin.
- **No tocar `Snapshots`** antes del paso 7.
- **No regenerar un golden para que pase.** Un golden cambia sólo en un commit que dice por qué.
- **No traer bloques de periféricos** a `device-snapshots`: son de cada plugin, con su concepto.
- **No escribir un formato desde la máquina viva.** Siempre por la foto; y ninguna parte del
  núcleo o de un plugin entra sin su `MachinePartContract`.
