# Los formatos de snapshot, sobre la máquina recorrida

Plan para reemplazar los cuatro lectores de snapshot —Z80, SNA, SP y SZX, heredados de JSpeccy—
por formatos que leen y escriben **recorriendo la máquina de verdad** con visitors. Con
libspectrum (`analisis-libspectrum.md`) como oráculo de cada paso y como fuente del conocimiento
byte por byte de cada formato.

**Estado:** el paso 0 está hecho (27 de septiembre de 2026): 117 tests en `devices/snapshots`,
verdes con el código de hoy. Lo demás no empezó.

Es la cuarta versión del plan. La tercera ponía en oozx un modelo propio de la máquina hecho de
valores —un `Snapshot` con una clase por pieza (`Processor`, `AySound`, `Multiface`,
`InterfaceOne`…) y un catálogo de propiedades— y los formatos escribían sobre ese modelo. Se
llegó a escribir y se descartó el mismo 27: duplicaba lo que los dispositivos ya modelan, sacaba
el conocimiento de cada dispositivo de su plugin y metía comportamiento de formatos en oozx. De
`diseno-desde-cero.md`, `diseno-formatos.md`, `ejemplos-mapeo.md` y `formatos-sna-z80-tap.md`
sigue valiendo lo que dicen de cada formato (offsets, la tabla de hardware del `.z80`, la
compresión, el contador de frames, las reglas del SNA); no vale el modelo de piezas ni el
documento `Snapshot`.

## El principio

**La máquina es el modelo.** No hay un segundo modelo de la máquina para los archivos. Leer un
snapshot es **armar la máquina** que el archivo dice y **recorrerla**: cada parte que el recorrido
encuentra toma del archivo lo suyo. Escribir es recorrer la máquina y volcar lo que cada parte
tiene.

**Cada parte se presenta en el recorrido, y no sabe de formatos.** La CPU, la memoria, el
paginado, el borde, el AY, la Multiface, la Interface 1: cada una acepta el recorrido y se
presenta, con sus subpartes si las tiene. Ninguna tiene una línea sobre SNA, Z80 o SZX. Un formato
nuevo nunca obliga a tocar un dispositivo.

**El mapeo es del formato, y es una declaración.** Un formato declara sus tramos, sus formas, una
tabla por cada tipo de parte que lleva (cada campo de la parte, a un lugar de un tramo) y los
bindeos de tipo de parte a tabla. Leer y escribir son la misma tabla usada en los dos sentidos, y
los hace el motor, igual para todos. Lo que ningún bindeo toma va a las notas ("el SNA no guarda
el AY"). Un dispositivo nuevo que un formato quiera guardar se agrega en el módulo del formato, no
en el del dispositivo.

**En oozx, sólo la capa chica que conecta.** El recorrido, el rol de formato y lo mínimo que las
partes del núcleo tienen que dejar ver de sí. Ni formatos, ni motor, ni nada de un dispositivo en
particular.

**No es un refactor: es una reescritura guiada por tests.** Los lectores de hoy no se tocan. Cada
formato nuevo se escribe test-first y, cuando pasa la misma red que el viejo, lo reemplaza en un
commit y el viejo se borra. Un golden cambia sólo en un commit que dice por qué.

## Cómo se ve

El recorrido es interno: ningún formato escribe un visitor. Cada parte se presenta, y el motor
busca el bindeo del tipo de esa parte y lo aplica en el sentido que toque. Así la identificación
es por tipo, y el tipo sale de la declaración: `on` pide una clase y una tabla del mismo tipo, y el
compilador lo verifica.

```java
// oozx: el recorrido, y nada más
public interface Visitable { void accept(PartVisitor visitor); }   // se presenta, y presenta sus subpartes
public interface PartVisitor { void visit(Visitable part); }
```

El SNA entero, con las APIs reales de las partes (los registros por `RegistersBase`, los bancos
por `SpectrumMemory.ram(n)`, el 7ffd por `Paging`, el borde por `Border.becomes`):

```java
/** SNA: el header de 27 bytes y la RAM de 48K; un 128K agrega el PC, el 7ffd y los bancos que faltan. */
@Answers("sna")
public final class SnaFormat extends DeclaredFormat {

  // Los tramos del archivo
  static final Fixed HEADER  = Fixed.of(27);
  static final Fixed TAIL    = Fixed.of(4);                // 128K: PC, 7ffd, y un byte de TR-DOS que se escribe 0
  static final Fixed STACKED = Fixed.virtual(2);           // 48K: el PC que la regla saca de la pila, o mete
  static final Pages TOP     = Pages.bankAtTop(TAIL, 2);   // cuál es lo dice el TAIL, que viene después; si es 2 o 5, es una copia

  // Las dos formas
  static final Shape FORTY_EIGHT = Shape.of(SPECTRUM48K, HEADER, Pages.of(5, 2, 0)).with(new PcOnTheStack());
  static final Shape ONE_TWENTY_EIGHT = Shape.of(SPECTRUM128K, HEADER, Pages.of(5, 2), TOP, TAIL, Pages.remaining());

  // Cada campo de cada parte, a un lugar de un tramo
  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u8(HEADER, 0, I)
      .u16(HEADER, 1, HL_).u16(HEADER, 3, DE_).u16(HEADER, 5, BC_).u16(HEADER, 7, AF_)
      .u16(HEADER, 9, HL).u16(HEADER, 11, DE).u16(HEADER, 13, BC).u16(HEADER, 15, IY).u16(HEADER, 17, IX)
      .bit(HEADER, 19, 2, IFF2).alsoSets(IFF1)             // guarda una sola bandera
      .u8(HEADER, 20, R).u16(HEADER, 21, AF).u16(HEADER, 23, SP)
      .code(HEADER, 25, 0x03, IM, Codes.IM)
      .u16(TAIL, 0, PC).u16(STACKED, 0, PC);                // según la forma, está uno u otro
  static final Layout<Border> BORDER = Layout.<Border>of().bits(HEADER, 26, 0x07, COLOUR);
  static final Layout<Paging> PAGING = Layout.<Paging>of().u8(TAIL, 2, PORT_7FFD);
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);   // cada página del archivo, a su banco

  static final Bindings BINDINGS = Bindings.of()
      .on(Cpu.class, CPU).on(Border.class, BORDER).on(Paging.class, PAGING).on(SpectrumMemory.class, RAM);

  public Identity identity() { return Identity.named("SNA snapshot").extension("sna").sized(49179, 131103, 147487); }
  protected Bindings bindings() { return BINDINGS; }
  protected Shape shapeOf(Peek file) { return file.length() == 49179 ? FORTY_EIGHT : ONE_TWENTY_EIGHT; }
  protected Shape shapeFor(Spectrum machine) { return machine.pagesThrough7ffd() ? ONE_TWENTY_EIGHT : FORTY_EIGHT; }
}

/** Un SNA de 48K no tiene dónde guardar el PC: va en la pila, como si una interrupción lo hubiera empujado. */
final class PcOnTheStack implements Rule {
  public void afterParsing(SnapshotFile file) {          // leyendo, antes de tocar la máquina
    int sp = file.u16(HEADER, 23);
    if (sp < 0x4000 || sp == 0xffff) throw new SnapshotException("SP inválido (0x%04x): no hay de dónde sacar el PC".formatted(sp));
    file.u16(STACKED, 0, file.ram().word(sp));
    file.u16(HEADER, 23, sp + 2);
  }
  public void beforeAssembling(SnapshotFile file) {      // escribiendo, sobre la copia que va al archivo
    int sp = file.u16(HEADER, 23);
    if (sp < 0x4002) throw new SnapshotException("SP demasiado bajo (0x%04x) para apilar el PC".formatted(sp));
    file.ram().word(sp - 2, file.u16(STACKED, 0));
    file.u16(HEADER, 23, sp - 2);
  }
}
```

Una vez en el motor, para todos los formatos:

```java
// Cómo se llega a cada dato de las partes del núcleo, con su propia API
static final Field<Cpu, Integer> I = Field.of(cpu -> regs(cpu).getRegI(), (cpu, v) -> regs(cpu).setRegI(v));   // uno por registro
static final Field<Border, Integer> COLOUR = Field.of(Border::colour, Border::becomes);                   // colour() es el getter que falta
static final Field<Paging, Integer> PORT_7FFD = Field.of(p -> p.port7ffd() & 0xff, (p, v) -> p.write7ffd((byte) (int) v));
static final PageField<SpectrumMemory> PAGE = PageField.of((m, n) -> m.ram(n).bytes, (m, n, bytes) -> m.ram(n).fill(bytes));

// Leer y escribir, iguales para todos
public final Notes read(byte[] bytes, Speccy speccy) {
  Shape shape = shapeOf(Peek.of(bytes));
  SnapshotFile file = shape.parse(bytes);                  // tramos llenos y validados, reglas corridas
  speccy.machine().become(shape.machine());                // hasta acá la máquina no se tocó
  speccy.accept(part -> bindings().apply(part, Direction.reading(file)));
  return file.notes();
}
public final Written write(Speccy speccy) {
  SnapshotFile file = shapeFor(speccy.machine().current).empty();
  speccy.accept(part -> bindings().apply(part, Direction.writing(file)));
  return file.assemble();                                  // reglas, tramos en su orden, y notas de lo no llevado
}
```

Lo que sale de la declaración sin escribirlo: leer y escribir desde la misma tabla; un archivo
rechazado no toca la máquina, porque se entiende entero antes de elegirla; las notas de lo que no
se llevó, por parte (ningún bindeo la tomó) y por campo (ninguna tabla lo cubre); el 16K guardado
por la forma de 48K y el +3 por la de 128K. Un bindeo para una clase base o una interfaz toma a
sus subclases; una parte con dos facetas presenta cada una. Los campos de la CPU, el borde, el
paginado y la memoria los reusan tal cual el Z80, el SZX y el SP.

## Dónde vive cada cosa

**En oozx, la capa chica:**
- `Visitable`, `PartVisitor`, y el recorrido: `Speccy.accept(visitor)` presenta las partes del
  núcleo y los periféricos activos. `PeripheralRegistry` hoy no deja recorrer lo que tiene
  registrado: gana eso y nada más.
- Las partes del núcleo que un formato lleva se presentan: la máquina elegida, la CPU, la memoria,
  el paginado, el borde, el reloj.
- Lo que una parte del núcleo no deja ver hoy y un formato necesita, dicho en sus palabras: el
  color del borde tiene setter y no getter.
- El rol `SnapshotFormat` (`@RoleInterface`): qué archivos lee, leer un archivo sobre una máquina,
  escribir una máquina a un archivo. Devuelve notas de lo que no se llevó; rechaza con
  `SnapshotException`, que ya existe.
- `Snapshots` prueba primero un `SnapshotFormat` y cae al `SnapshotFile` de hoy si ninguno lee el
  archivo, hasta que el último formato viejo se borre.

**En cada plugin de dispositivo:** que se presente en el recorrido, y su estado a la vista en sus
palabras. Nada de formatos.

**En oozx-plugins, `devices/formats` (`device-formats`), el motor:** bytes (`Cursor`, `Sink`),
tramos y formas (`Fixed`, `Pages`, `Shape`), números con significado (`Codes`), compresiones
(`Codec`), campos sobre las partes del núcleo (`Field`, `PageField`), tablas (`Layout<P>`),
bindeos (`Bindings`), reglas (`Rule`), el sentido (`Direction`) y `DeclaredFormat`, que lee y
escribe. Más el contrato de tests que heredan los formatos. Depende de oozx; no de ningún
dispositivo.

**En oozx-plugins, `devices/snapshots` (`device-snapshots`), los cuatro formatos, Z80 incluido.**
Los campos y las tablas de los dispositivos (el AY, la Multiface, ULAplus…) viven acá, compartidos
por los formatos que los llevan, y este módulo depende de los plugins de esos dispositivos: una
dependencia de Maven común, como hoy depende de `device-spectrum128`.

## Lo que hay hoy

- **Nada recorre la máquina.** No hay visitor ni `accept` en el núcleo ni en los plugins.
- **Cargar** pasa por `SpectrumState`: `Snapshots.load` elige la máquina, copia la RAM (en un 48K,
  aplanada a 64K), escribe los puertos de paginado y aplica los registros con
  `SnapshotLoader.setZ80State`. La única parte que se restaura sola es el AY
  (`RestoredFromASnapshot`), y sólo fuera del 48K.
- **Varios campos de `SpectrumState` no se aplican en ningún lado:** el borde, el EAR, issue 2, el
  joystick, la Multiface, la Interface 1, la LEC, el grupo de paleta de ULAplus.
- **Guardar escribe siempre un 48K**: `SnapshotSaver` lee de 0x4000 a 0xFFFF por el bus y pierde
  los bancos, el paginado, el borde, el AY y los dispositivos.
- **El estado de algunas partes no se puede leer desde afuera:** los registros del AY son
  privados, y el borde no tiene getter.
- **Una máquina sin ventanas se arma en los tests** (`Speccy.create(...)` con sonido mudo, en
  `devices/all`), así que leer un archivo en un test es armar una.
- Cuatro lectores, 2832 líneas, con el conocimiento del Spectrum repetido en cada uno (los
  números están en `analisis-libspectrum.md`). `SnapshotZ80` vive en oozx; SNA, SZX y SP acá.
- Usan `SnapshotFile` o `SpectrumState`, además de `Snapshots`: las sesiones del escritorio
  (`.z80` empaquetado), el RZX (escribe el snapshot embebido a un archivo temporal), el core de
  libretro, el lanzador, el catálogo (`payloadOf`) y el traductor (`EmulatedMiniZX`,
  `RemoteZ80Translator`), del que depende oozx-lift.

## Paso 0: la red de seguridad — hecho

En `devices/snapshots/src/test`, paquete `model.tests.formats`, sin tocar código de producción ni oozx:
- **Fixtures:** 62. Son 43 del corpus de libspectrum (en `snapshots/libspectrum/`, con su `README.md`)
  y 19 hechas por `MakeFixtures`: Manic Miner en Z80 v1 crudo, v1 comprimido, v2 y v3, más SNA, SZX y SP;
  `banks` y `banks-5-on-top` (128K con un patrón por banco); `plus3-1ffd`; `sixteen` (16K); y
  `lone-ed-at-the-end.sna`.
- **Goldens:** en `snapshots/goldens/`, escritos por `WriteTheGoldens`. Usan nombres cortos (`a`,
  `bc`, `iff1`, `border`, `port7ffd`, `page.5`), los mismos que va a usar la red del paso 3.
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


Estos goldens son de `SpectrumState`, que desaparece al final. Sirven mientras los formatos
viejos vivan; la red que queda es la del paso 3.

## Paso 1: la máquina recorrible, en oozx

Aditivo: no cambia lo que la máquina hace.
- `Visitable`, `PartVisitor` y `Speccy.accept`. El orden es fijo: la máquina elegida, la CPU, la
  memoria, el paginado, el borde, el reloj, y después los periféricos activos en el orden en que
  se registraron.
- Las partes del núcleo se presentan, y el borde deja ver su color.
- `PeripheralRegistry` deja recorrer los periféricos activos.
- El rol `SnapshotFormat`, y `Snapshots` que lo prueba antes que al `SnapshotFile`.
- Tests: el recorrido presenta cada parte de cada modelo, una vez y en orden; un periférico activo
  aparece y uno inactivo no; `Snapshots` carga con un `SnapshotFormat` de prueba y cae al viejo si
  ninguno lee.

Entra en `main` de oozx coordinado con vos: toca `Speccy`, `PeripheralRegistry` y `Snapshots`.

## Paso 2: los dispositivos visitables, en oozx-plugins

Primero los que el SZX de hoy ya lleva, que son los que la red puede comprobar: el AY, la
Multiface, ULAplus, la Interface 1, la Interface 2, el joystick y la LEC. Cada uno, en su plugin:
- que se presente en el recorrido;
- su estado a la vista en sus palabras, donde hoy no se ve (los registros del AY, el registro
  seleccionado);
- un test: el recorrido lo presenta cuando está activo, y lo que se le pone por su API se lee
  igual.

Los que el SZX de hoy saltea (DivIDE, DivMMC, +D, Beta 128, Covox, SpecDrum, el mouse…) se
presentan cuando un formato los lleve, cada uno en su commit.

## Paso 3: la red sobre la máquina

La red del paso 0 compara `SpectrumState`. La que queda compara **la máquina**, que es lo que
importa:
- **Una descripción de la máquina**, en `devices/snapshots/src/test`: bindeos que, en vez de a un
  archivo, dicen cada campo de cada parte en una línea, con los nombres de los goldens de hoy
  (`a`, `bc`, `border`, `port7ffd`, `page.5`…). Usa los mismos campos que los formatos, y dice qué
  partes no sabe describir.
- **Goldens de máquina:** cada fixture cargada con el camino de hoy en una máquina sin ventanas, y
  descrita. Lo que el camino de hoy no aplica (el borde, issue 2…) queda en el golden tal como
  queda en la máquina, con la lista escrita en `known/`.
- **El oráculo, sobre la máquina:** libspectrum lee el archivo, y cada campo de su `snap` se compara
  con la línea de la máquina del mismo nombre.

Con esto, cada formato nuevo se mide contra lo que la máquina termina teniendo, no contra una
estructura intermedia.

## Paso 4: el motor, en oozx-plugins

`devices/formats`, `device-formats`. Cada pieza con su test:
- `Cursor` (el único que encuentra un archivo corto, y lo rechaza) y `Sink`.
- `Codes`: número y valor, con máscaras y un "si no". `Codec`: crudo y zlib; la compresión del
  `.z80` viene con el paso 7.
- `Fixed`, `Pages` y `Shape`: los tramos del archivo y sus formas, con `parse`, que lo entiende
  entero, y `assemble`, que lo arma en orden.
- `Field` y `PageField` sobre las partes del núcleo; `Layout<P>`, la tabla que los pone en lugares
  de los tramos; `Bindings`, de tipo de parte a tabla; `Rule`, sobre los bytes; `Direction`.
- `DeclaredFormat`: leer y escribir, con las notas de lo que no se llevó.
- **El contrato de un formato**, en el test-jar, para cada fixture: se reconoce; cargada y descrita
  da su golden de máquina; lo que se escribe desde esa máquina, cargado en otra, la describe igual;
  cortada en cualquier byte se rechaza sin reventar **y sin tocar la máquina**; y lo que el formato
  no lleva termina en las notas. Además, ninguna tabla pone un campo dos veces.

## Paso 5: SP — la prueba del molde

El más chico, y sólo lee: un header fijo, la CPU y el borde, y la RAM desde donde el header dice.
Sirve para ver si el molde cierra antes de gastarlo en los grandes. Reemplaza a `SnapshotSP`.

## Paso 6: SNA — dos formas y el paginado

La de 48K (el PC en la pila) y la de 128K (la página de arriba antes del puerto que dice cuál
es; si es la 2 o la 5 es una copia y tiene que ser igual). Escribir un 16K, un +2A o un +3 va por
la forma más parecida y las notas dicen qué no se llevó. Reemplaza a `SnapshotSNA`.

## Paso 7: Z80 — tres versiones, una cabecera

La cabecera de 30 bytes, el header extendido cortado por versión, la tabla de hardware como la
lee libspectrum, la compresión (con el ED suelto al final de una página escrito como literal), el
contador de frames, el AY (el de placa, la Melodik o la Fuller). El mapeo de la Interface 1 que
dice el hardware vive acá. Reemplaza a `SnapshotZ80` para todo lo que pase por `Snapshots`; el de
oozx queda, sólo para quienes todavía no pasan por la máquina, hasta el paso 9.

## Paso 8: SZX — bloques, y un mapeo por parte

Los bloques con etiqueta y largo, comprimidos o no. El escritor recorre la máquina y cada parte
que conoce da un bloque; el lector entiende todos los bloques antes de tocar la máquina, activa los
periféricos que los bloques nombran, y después recorre. Un bloque de algo que no está en este
build se saltea y se anota. Reemplaza a `SnapshotSZX`.

## Paso 9: sacar lo viejo de oozx

Cuando los cuatro formatos estén acá, se mueven a `SnapshotFormat` los que todavía usan
`SnapshotFile` o `SpectrumState`: las sesiones, el RZX, libretro, el lanzador y el catálogo. Todos
tienen una máquina a mano o pueden armar una. Después se borran `SnapshotFile`, `SnapshotFactory`,
`SpectrumState`, `SnapshotLoader`, `SnapshotSaver`, `SnapshotZ80` y `RestoredFromASnapshot`, y los
goldens del paso 0.

El traductor es el caso aparte: arma su propia máquina chica, no un `Speccy`. O arma un `Speccy`
sin ventanas para leer y copia de ahí, o su máquina se vuelve visitable con las mismas interfaces
del núcleo. Se decide con quien trabaje en oozx-lift, que depende del artefacto `translator`.

## Después, en otro plan

Las cintas. Una cinta no es una foto de la máquina: tiene tiempo adentro. Sus formatos (TAP, TZX,
CSW, PZX) leen al modelo de bloques del dispositivo de cinta, que ya existe en `device-tape`. De
esto sólo le toca el recorrido: la cinta, como parte de la máquina, es visitable, y el bloque TAPE
del SZX la encuentra así.

## El orden, y por qué

Red → máquina recorrible → dispositivos visitables → red sobre la máquina → motor → SP → SNA →
Z80 → SZX → sacar lo viejo.
- **El recorrido antes que cualquier formato**, porque es por donde todos leen y escriben.
- **La red sobre la máquina antes que el motor**, porque es la que mide a los formatos nuevos, y
  porque el visitor que describe prueba el recorrido con todas las partes antes de que un formato
  dependa de él.
- **De menor a mayor**: SP prueba el molde; SNA agrega las formas y el paginado; Z80, versiones,
  compresión y hardware; SZX, bloques y dispositivos.

## Lo que cuesta

- **Leer un archivo es armar una máquina.** En los tests, el catálogo y las conversiones hay que
  tener un `Speccy` sin ventanas a mano. Se reusa uno: elegir la máquina ya la deja limpia.
- **`device-snapshots` depende de los plugins de los dispositivos que mapea.** Si falta uno de
  esos plugins, falta el formato. Si eso llega a molestar, el mapeo de un dispositivo puede irse a
  un módulo chico propio (el SZX de la Multiface), encontrado como rol; hoy no hace falta.

## Los gates

Después de cada commit: `mvn -pl devices/formats test` (el motor), `mvn -pl devices/snapshots
test` (los formatos y las redes) y `mvn -pl devices/all test` (la máquina alrededor). En los pasos
que tocan oozx (1 y 9): sus tests de `core`, `spectrum`, `media/snapshot`, `bridge`, `app` y
`translation`, y el CI de este repo.

## Decisiones

Tomadas: la máquina es el modelo, sin documento intermedio; el recorrido es interno y cada parte
se presenta sin saber de formatos; el mapeo es una declaración en el módulo del formato (tramos,
tablas y bindeos por tipo de parte), y el motor lee y escribe desde ella; en oozx sólo el
recorrido, el rol y lo que las partes del núcleo tienen que dejar ver.

Faltan, y cada una cambia goldens cuando llega su paso:
1. **El creador que escribe SZX**: hoy `JSpeccy v0.93` con la versión mal codificada.
   Recomendado: `OOZX` y la versión real.
2. **Un 16K, un +2A o un +3 guardado como SNA**: hoy el 16K revienta y los otros se rechazan.
   Recomendado: como libspectrum, el 16K como 48K y los otros como 128K, con notas.
3. **El SNA de 48K al leer**: desapilar el PC, como libspectrum, o dejar el RETN de la ROM, como
   hoy. Recomendado: desapilar.
4. **Los hardware ids del `.z80` que hoy se rechazan**: leerlos como libspectrum. Recomendado: sí.
5. **Los `System.out.println` del SZX**: sacarlos; lo salteado va a las notas.
6. **El bloque PLTT**: aceptar 66 bytes o más, como libspectrum.
7. **Un bloque de SZX que nadie conoce**: hoy corta la lectura. Recomendado: saltearlo y anotarlo.
   Sin un documento intermedio no hay dónde guardarlo para reescribirlo.
8. **Un SZX sin procesador o sin memoria**: con la máquina como modelo sale solo: esas partes
   quedan como las dejó elegir la máquina, y se anota.
9. **El ED suelto al final de una página del `.z80`**: se escribe como literal. Es un bug.
10. **Las sesiones del escritorio**, hoy un `.z80` empaquetado que pierde casi todo. Recomendado:
    guardarlas en SZX, que lleva todas las partes que se sepan mapear.
11. **El traductor**: ver el paso 9.

## Coordinación

- **oozx**: los pasos 1 y 9 lo tocan y entran coordinados con vos. Nada de oozx se instala en
  `~/.m2` ni se empuja sin avisar.
- **El CI de este repo** construye oozx de `main` antes que los plugins: los pasos 2 en adelante
  necesitan el 1 en `main`.
- **oozx-lift**: el paso 9 se habla antes con quien trabaje ahí.

## Lo que no hay que hacer

- **No hacer un modelo paralelo de la máquina.** Si un formato necesita algo, se lo pide a la
  parte que lo tiene, y si la parte no lo deja ver, la parte lo deja ver en sus palabras.
- **No poner nada de formatos en un dispositivo**, ni en su plugin.
- **No poner en oozx un formato, el motor ni nada de un dispositivo en particular.**
- **No tocar la máquina antes de entender el archivo entero.** Un archivo rechazado la deja como
  estaba.
- **No refactorizar los lectores viejos.** Se reemplazan y se borran.
- **No regenerar un golden para que pase.** Un golden cambia sólo en un commit que dice por qué.
