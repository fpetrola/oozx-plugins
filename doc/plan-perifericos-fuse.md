# Lo que falta de Fuse, y cómo se trae

Relevado el 27 de septiembre de 2026, comparando `oozx/fuse-emulator-fuse` (`machines/*.c`,
`peripherals/**/*.c`) con los módulos de `oozx-plugins/devices` y `oozx/machine`, clase por clase.
Retoma las secciones 15 a 19 del `plan-perifericos.md` de la otra línea de historia (hay copia en
`versions/oozx_2/oozx/doc/`), que ya no está en `main`, puestas al día con lo que el emulador tiene hoy.

**Estado: nada empezado.**

## Lo que hay

**Máquinas: están las diecisiete de Fuse.** 16K, 48K, 48K NTSC, 128, +2, +2A, +3, +3e, SE, TC2048,
TC2068, TS2068, Pentagon 128/512/1024 y Scorpion. Además hay clones que Fuse no tiene: Inves, TK90X,
CZ Spectrum, Chloe 280SE y Chrome.

**Periféricos: están todos, salvo siete.** Ya existen AY, Fuller Box, Melodik, Covox, SpecDrum,
Beta 128, +D, DISCiPLE, Opus Discovery, Didaktik 40/80, la disquetera del +3, Interface 1 (con
microdrives, RS232 y ZX Net), Interface 2, Multiface One/128/3, DivIDE, DivMMC, ZXMMC, Simple 8-bit
IDE, ZXATASP, ZXCF, Kempston Mouse, los joysticks (Kempston, Cursor, Sinclair 1/2, Timex 1/2,
Fuller), ZX Printer, la impresora paralela del +2A/+3, SCLD y ULAplus.

| # | falta | en Fuse | líneas de Fuse | ROM |
|---|---|---|---|---|
| 1 | salida serie del 128/+2 (RS232 e impresora por el puerto A del AY) | `printer.c` (`printer_serial_write`) | ~40 | — |
| 2 | Currah µSource | `usource.c` | 305 | no disponible |
| 3 | cartuchos DOCK de Timex (`.dck`) | `dck.c` + `libspectrum_dck_read` | 267 | la del cartucho |
| 4 | Currah µSpeech | `sound/uspeech.c` + `sound/sp0256.c` | 444 + 1.545 | `uspeech.rom` y la del SP0256-AL2, no disponibles |
| 5 | TTX2000S (teletexto) | `ttx2000s.c` | 544 | no disponible |
| 6 | Spectranet | `spectranet.c` + `nic/w5100*.c` + `flash/am29f010.c` | 603 + 1.210 + 161 | firmware libre, se elige |
| 7 | SpeccyBoot | `speccyboot.c` + `nic/enc28j60.c` | 295 + 330 | `speccyboot-1.4.rom`, viene con Fuse |

De esos siete, la wiki (`Limits-and-what-is-not-there.md`) nombra cinco (2, 4, 5, 6 y 7). Los otros
dos (1 y 3) salieron de este relevamiento.

**Un faltante que no es un dispositivo: el estado en el snapshot.** `SnapshotSZX` descarta sin leer
los bloques de periféricos que sí existen: `B128`, `BDSK`, `PLSD`, `PDSK`, `OPUS`, `ODSK`, `ZXAT`,
`ATRP`, `ZXCF`, `CFRP`, `COVX`, `DRUM`, `AMXM`, `ZXPR`, `SIDE`, `SCLD`, `+3DK`, `DSKF`, `ROM`, `DOCK`,
`USPE` y `SNET`. Eso no es de este plan: `plan-snapshots.md` (paso 4 y después del paso 6) ya dice
que cada plugin trae su bloque cuando tiene su concepto. La regla acá es que cada periférico nuevo
nace con su bloque SZX cuando la base de ese plan exista. Hasta entonces, el bloque va anotado como
deuda en su sección.

## Las costuras que ya hay

Todo lo que el plan viejo le pedía al núcleo ya está, salvo lo marcado:

| costura | dónde | quién la usa acá |
|---|---|---|
| ROM o RAM que tapa los 16K bajos (el `/ROMCS` de Fuse) | `MappedMemory` sobre el `MemoryBus` (así lo hacen Multiface y Opus) | 2, 3, 4, 5, 6, 7 |
| trampas de PC antes del fetch y después de la instrucción | `Cpu.beforeFetch()`, `Cpu.afterInstruction()` (`PcTraps`) | 4, 6 |
| NMI | `Cpu.nmi()`, `onNmi` | 5, 6 |
| un DAC que va al mezclador | `Dac` (Covox, SpecDrum) | 4 |
| la ROM que el plugin declara y la persona elige | `RomsOfItsOwn` (Multiface); las ROMs locales están en `~/detodo/spectrum/Roms` | 2, 4, 5, 6, 7 |
| una tarjeta que habla SPI | `MmcCard` en `devices/ide` | 7 (el protocolo de bits; el chip es otro) |
| un cartucho como medio | `Cartridge` en `devices/interface2` | 3 |
| el texto que sale de una impresora | `ParallelPrinter.print` | 1 |
| **falta:** escrituras al puerto A del AY | el AY no avisa hoy quién escribe el registro 14 | 1 |
| **falta:** evento de RETN | `Cpu` tiene `onNmi` pero no un aviso de RETN | 6 |
| **falta:** trampa de acceso a memoria (no de PC) | Fuse conmuta el µSpeech con cualquier lectura o escritura de 0x0038 | 4 (se decide en su paso) |
| **falta:** red del host | sockets de Java para 5 y 6; una interfaz TAP por JNA para 7 | 5, 6, 7 |

Cada costura que falta va en su dueño y no en el plugin que la pide. El aviso del puerto A es del
AY. El RETN es de `Cpu`, como `onNmi`. Si hace falta la trampa de acceso, es de `MemoryBus`. Un
módulo `host/network` en oozx es el dueño de la red y el TAP, del mismo modo que `host/sound` lo es
del audio. No va en un plugin: 5, 6 y 7 lo compartirían.

## El orden

De lo chico a lo grande, y de lo que no toca el host a lo que sí:

1. **Salida serie del 128.** Chica, sin ROM, y deja la costura del puerto A del AY.
2. **µSource.** Es la plantilla mínima de "ROM que se conmuta por un puerto".
3. **DOCK.** Solo falta el medio: la paginación del dock y del exrom ya está en `TimexMemoryPeripheral`
   (`devices/scld`).
4. **µSpeech.** El SP0256 es lo más grande que no toca la red.
5. **TTX2000S.** La primera que usa `host/network`, y la más simple de las tres de red.
6. **Spectranet.** Lleva el RETN, la flash y el W5100 sobre `java.nio`.
7. **SpeccyBoot.** El chip entero, y el TAP solo si el sistema lo da (pide permisos de root).

Uno por commit, con la receta de siempre: el módulo en `oozx-plugins/devices/<nombre>` con su
`Extension`, su `Settings.mirror` y su ventana. El test nace rojo. Al final, el neto de líneas y lo
que se reusó.

## Cada uno

### 1. Salida serie del 128/+2 — en `devices/parallel-printer`

- Fuse (`printer_serial_write`) decodifica el RS232 que las ROMs del 128 sacan bit a bit por el
  registro 14 del AY: un bit por escritura, bit 3 el dato, un start, ocho datos y un stop. Es
  independiente de los baudios porque la ROM escribe una vez por bit.
- Se agrega un aviso de escritura del registro 14 en `AyRegisters`, y el plugin de la impresora lo
  escucha y entrega cada byte a `ParallelPrinter.print`. Es la misma impresora de texto; lo que
  cambia es por dónde entra el byte.
- Test: `LPRINT` en un 128 y el texto sale en la impresora.

### 2. Currah µSource — `devices/usource`

- ROM de 8K espejada en 0x0000 y 0x2000. Se conmuta con cualquier lectura o escritura del puerto
  exacto 0x2bae, que devuelve 0xff. Arranca despaginada. No tiene RAM, trampas ni NMI.
- `MappedMemory` para la ROM, un `Wired` de máscara 0xffff para el puerto y `RomsOfItsOwn` para la
  ROM. Ventana: la ROM a elegir y un LED de paginado.
- Snapshot: activo, paginado y ROM propia (bloque SZX pendiente del plan de snapshots).
- Test: sin ROM (`assumeTrue`), el conmutador con una ROM sintética de 8K.

### 3. Cartuchos DOCK de Timex — en `devices/timex`

- `.dck` según libspectrum: una lista de bloques, cada uno con su banco (DOCK, EXROM o HOME) y ocho
  bytes de tipo por página de 8K (vacía, RAM vacía, ROM o RAM con datos), seguidos por los datos.
  Fuse (`dck.c`) carga las páginas en los bancos del dock y del exrom.
- La paginación ya está: `TimexMemoryPeripheral` (`devices/scld`) tiene ocho trozos de 8K, `slot`
  (el cartucho en el dock) y `behind` (el exrom, que `Tc2068` ya llena con su ROM). El bit 7 del
  registro de pantalla elige cuál de los dos, y el puerto 0xf4, qué trozos. Falta solo el medio: un
  `DockCartridge` que lee el formato y llena `slot`. Hay que decidir si el `Cartridge` de
  Interface 2 se vuelve el concepto común de "cartucho" o si cada uno queda con el suyo. El formato
  tiene un solo dueño.
- Hay que ver si `Opens` del escritorio acepta `.dck` o si lo abre la ventana del Timex.
- Test: un `.dck` sintético con una página de ROM en el dock; se pagina con 0xf4 y se lee.

### 4. Currah µSpeech — `devices/uspeech`

- ROM de 2K espejada en 0x0000 y 0x0800; el resto del bloque de 16K es 0xff. Se conmuta con
  cualquier acceso a memoria a 0x0038 y con el puerto exacto 0x0038. Escribir en 0x1000 (memoria o
  puerto) manda un alófono (`b & 0x3f`); leerlo da BUSY. 0x3000 y 0x3001 eligen la entonación
  (cristal de 3,05 o 3,26 MHz).
- **La decisión de este paso:** en 0x0038 casi todo acceso es el fetch del vector de IM1, y eso ya
  lo cubre `beforeFetch`. Si un test contra el comportamiento de Fuse encuentra un acceso de datos
  que importe, se agrega la trampa de acceso a `MemoryBus`. Si no, se documenta la diferencia.
- **SP0256-AL2**: microsecuenciador más filtro LPC de 12 polos (`qtbl`, `datafmt`, `df_idx`), con la
  ROM de 2K duplicada a 4K. Produce muestras a su propio ritmo (unos 350 t-states por muestra) y va
  al mezclador por un `Dac`. Corre en cada frame aunque nadie escriba.
- Ventana: el nivel de salida, el alófono en curso y la entonación; expandida, la cola de alófonos
  con sus nombres.
- Test: el SP0256 contra muestras de Fuse para una secuencia fija de alófonos (se generan una vez
  con Fuse y quedan como golden). Sin las ROMs, `assumeTrue`.

### 5. TTX2000S — `devices/ttx2000s`

- ROM de 8K en 0x0000 y RAM de 1K espejada cuatro veces en 0x2000-0x3fff; cada acceso a la RAM
  latchea `line_counter = (addr >> 6) & 0xf`. El puerto responde con A7 = 0: los bits 0-1 eligen el
  canal (1-4) y el bit 3 despagina. Arranca **paginado**, el único de la lista que lo hace.
- **Red**: TCP al servidor del canal, sin handshake. Llegan campos de 672 bytes (16 líneas de 42)
  que se vuelcan a la RAM fila por fila (paso 0x40, con 0x27 delante de cada línea no vacía). Cada
  1/50 s, si llegó un campo y está paginado, dispara un NMI.
- `host/network` da la conexión. El hilo de red solo deja el campo listo, y es el evento del frame
  el que lo vuelca, así la emulación no depende de cuándo llegan los bytes.
- Ventana: el canal, el LED de conexión y, expandida, la página dibujada desde la RAM.
- Test: un servidor local de prueba que manda un campo conocido; la RAM y el NMI.

### 6. Spectranet — `devices/spectranet`

- **Memoria**: flash AM29F010 de 128K (32 páginas de 4K), RAM de 128K y la ventana del W5100. Los
  16K bajos son cuatro ranuras de 4K: 0x0000 fija en la flash 0, 0x1000 página A (puerto 0x003b),
  0x2000 página B (0x013b) y 0x3000 fija en RAM. La flash se programa solo por la página B, con las
  secuencias 0x555/0x2aa de programar, borrar chip, borrar sector y autoselect.
- **Puertos** (máscara 0xffff): 0x003b y 0x013b paginan. 0x023b da la versión de la CPLD (3) al
  leerlo y, al escribirlo, fija la trampa programable. 0x033b es el control: el bit 0 pagina, el
  bit 3 activa la trampa.
- **Trampas**: antes del fetch, `PC == 0x0008 || (PC & 0xfff8) == 0x3ff8` pagina y `PC == trampa`
  dispara el NMI. Después, `PC == 0x007c` despagina. El NMI es un flip-flop que `RETN` limpia:
  **acá entra el evento de RETN en `Cpu`**.
- **W5100**: registros comunes, 4 sockets TCP/UDP con buffers de 2K, y los comandos OPEN, LISTEN,
  CONNECT, DISCON, CLOSE, SEND y RECV. Se hace con `java.nio` y un selector en `host/network`, con
  los mismos estados que Fuse (0x13, 0x14, 0x17, 0x1c, 0x22).
- Snapshot: todo, incluidas las dos imágenes de 128K. La flash se guarda al lado de la
  configuración, como un disco.
- Ventana: el botón de NMI, los cuatro sockets con su estado e IP, las páginas A y B y, expandido,
  un volcado del W5100.
- Test: la flash por sus secuencias; un socket TCP contra un servidor local desde código Z80
  mínimo; paginar y despaginar por las trampas.

### 7. SpeccyBoot — `devices/speccyboot`

- ROM de 8K en 0x0000 (la de Fuse, que se puede distribuir junto con su licencia). Se pagina con el
  bit 5 del puerto (máscara 0x00e0, valor 0x0080; el canónico es 0x9f). Bit 0 = SCK (el flanco de
  subida desplaza un bit), bit 3 = /CS del ENC28J60, bit 6 = /RST, bit 7 = MOSI. Al leer, el bit 0 es
  MISO.
- **ENC28J60**: comandos RCR, RBM, WCR, WBM, BFS, BFC y SRC; 4 bancos de 32 registros y un buffer de
  8K. TXRTS manda la trama y PKTDEC decrementa EPKTCNT.
- Tramas hacia el host por **TAP** (`/dev/net/tun` con `TUNSETIFF`, por JNA, en `host/network`).
  Sin TAP, la interfaz queda "sin cable": el chip contesta pero no hay red.
- Ventana: LEDs de link, RX y TX, el nombre del TAP y un contador de tramas.
- Test: el chip por SPI (leer y escribir registros y el buffer) y una trama de ida y vuelta sobre
  un TAP falso.

## Cómo se prueba

- Cada periférico, contra el comportamiento de Fuse leído en su fuente. Donde hay datos (muestras
  del SP0256, campos de teletexto), el golden se genera una vez con Fuse y se guarda.
- **A verificar:** si el núcleo de Fuse del bridge (`machine/bridge`, libretro) deja enchufar estos
  periféricos. Si lo deja, el test de oráculo compara frame por frame igual que con la máquina.
- Las ROMs que no se pueden distribuir no entran al repo. Los tests que las necesitan hacen
  `assumeTrue(file.isFile())`, como Multiface e Interface 1.
- Las pruebas de red usan un servidor en el mismo test (loopback): ningún test sale a internet.

## Fuera de este plan

- Lo que Fuse tiene y no es un dispositivo (debugger, profiler, pokefinder, películas FMF, phantom
  typist, capturas SVG): es de otro relevamiento.
- El estado de los periféricos en SZX: es de `plan-snapshots.md`.
- General Sound (bloques `GS` de SZX): Fuse no lo emula.
