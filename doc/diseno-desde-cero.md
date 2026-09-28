# Los formatos, diseñados de cero: conceptos, colocaciones y documentos

Si no hubiera ni JSpeccy ni libspectrum atrás: cómo modelaría los manejadores de formatos de
archivo de un emulador de Spectrum para que el código diga lo que el archivo *significa*, para
que agregar un formato sea una clase corta, para que lo que dos formatos comparten esté escrito
una sola vez, y para que cada variación —formato, versión, máquina, bloque, codificación,
periférico, llegada a la máquina— sea un objeto y no un `switch`.

La idea central: **los conceptos del Spectrum y de sus periféricos se modelan una vez, con sus
propiedades y sus reglas, y un formato sólo dice dónde coloca cada propiedad y cómo viaja.** Lo
que hoy sabe cada lector —qué registros hay, en qué orden van las páginas de un 48K, cuál banco
está en 0xC000— lo sabe el concepto; el formato es una tabla de colocaciones.

Java 18: records y `sealed` sí, `switch` con patrones no. Así que todo despacho es un método
polimórfico, que es lo que se busca de todos modos. Los bocetos son ilustrativos.

## Los principios

1. **Se modela el significado, no los bytes.** Un archivo es un *documento*: un snapshot es una
   máquina detenida, una cinta es una señal en el tiempo, un disco es un medio con sectores, una
   grabación es una máquina y sus entradas. El documento no sabe de ningún formato.
2. **Los conceptos saben; los formatos colocan.** Todo lo que es del Spectrum o de un periférico
   —el procesador, el mapa de memoria, el paginado, el AY, un joystick, un DivIDE— es un
   *concepto* con *propiedades* que son objetos: tipo, ancho, valor de fábrica, y cómo leerse y
   escribirse. Un formato dice, propiedad por propiedad, en qué offset va y con qué codificación.
   De esa tabla salen leer **y** escribir.
3. **Cada variación es un objeto.** Formato, versión, máquina, bloque, codificación, contenedor,
   pieza de periférico, forma de llegar a la máquina. Ninguna capa pregunta `instanceof` ni tiene
   un `case`.
4. **Valores, no primitivos.** `TStates`, `Address`, `PageNumber`, `Version`, `Creator`, `Magic`.
5. **Los documentos son inmutables** y se arman con builders. Leer produce uno; guardar produce
   otro; nadie edita uno a medias.
6. **Extender es una clase y una anotación**, y la clase trae sus tests: cada abstracción viene
   con un contrato que cualquier implementación hereda.
7. **Lo algorítmico vive en clases chicas con nombre** —la RLE del `.z80`, el PC apilado del SNA,
   el A/F al revés del SZX viejo— como *codificaciones* de una propiedad, nunca inline.
8. **Un concepto existe si lo tocan al menos dos formatos o la máquina.** El largo de un SP, las
   banderas de un RAMP, el id de hardware de un `.z80` son del formato y se quedan en el formato.

## El mapa del dominio

| término | qué es | ejemplos |
|---|---|---|
| **documento** (`Document`) | lo que un archivo significa | `Snapshot`, `Tape`, `DiskImage`, `Recording`, `Screen`, `Cartridge` |
| **concepto** (`Piece`) | una parte de la máquina, con lo que sabe de sí misma | `Processor`, `MemoryMap`, `Paging`, `Ula`, `AySound`, `Joysticks`, `UlaPlus`, `DivIde` |
| **propiedad** (`Property`) | un dato de un concepto, como objeto: tipo, ancho, fábrica, leer y escribir | `Reg.A`, `Reg.SP`, `Processor.IM`, `Ula.BORDER`, `Paging.PORT_7FFD`, `Ay.SELECTED` |
| **formato** (`Format<D>`) | una manera de escribir un documento en bytes | `Z80`, `SNA`, `SP`, `SZX` · `TAP`, `TZX`, `CSW`, `PZX` · `DSK`, `TRD` · `RZX` |
| **colocación** (`Layout`, `Pages`) | dónde pone un formato cada propiedad, y en qué orden van las páginas | "SNA: `I` en 0, `L'` en 1… `BORDER` en 26"; "48K: páginas 5, 2, 0" |
| **codificación** (`Encoding`, `Codes`, `Codec`) | cómo viaja una propiedad o un tramo | `OnTheStack`, `SplitR`, `SwappedAF`, la tabla de joysticks del `.z80`, `Zlib`, `Z80Rle` |
| **bloque** (`BlockFormat`) | un formato chico con etiqueta, dentro de otro | los chunks de SZX, los bloques de TZX y PZX, las páginas de un `.z80` |
| **contenedor** (`Container`) | lo que envuelve a un archivo | `Gzip`, `Bzip2`, `Zip` |
| **identidad** (`Identity`) | cómo se reconoce un formato | extensiones, magia, tamaños |
| **llegada** (`Arrival<D>`) | qué hace la máquina cuando le llega un documento | restaurar, insertar en el deck, poner en la disquetera, reproducir |
| **señal** (`Signal`) | lo que una cinta suena | tono, pulsos, bits, pausa, nivel, secuencia |

## Las capas

```
 máquina      Snapshots · TapeDeck · Drive · Player                              Snapshots en oozx; el resto, en su plugin
   roles      Arrival<D> · PluggedBy<P> · Restores<P> · Captures<P>            el visitor de la máquina, de conjunto abierto
 documentos   Snapshot + Pieces · Tape + TapeBlocks · DiskImage · Recording
 conceptos    Processor · MemoryMap · Paging · Ula · AySound · Joysticks · …  con sus Property
 formatos     SnapshotFormat · TapeFormat · … = identidad + estructura + colocaciones + codificaciones
 colocación   Layout (Property → offset) · Pages · Codes · Encoding
 bloques      Tagged · BlockFormat · Framing · Contribution · Context
 bytes        Cursor · Sink · Codec (Raw, Zlib, Z80Rle) · Container
 identidad    Identity · Library
 máquinas     MachineModel · Capability · Timings
```

Cada capa se prueba sola: un `Layout` con un `byte[]` y un builder; un `BlockFormat` con un
`Cursor` y un `Snapshot` vacío; un formato con un archivo y el oráculo; un concepto con sus
propiedades y sus reglas; un participante con un `Snapshot` armado a mano.

## Capa 1: valores

```java
public record TStates(long count)   { public TStates plus(TStates o);  public boolean within(Timings frame); }
public record Milliseconds(int count) { public TStates at(Timings t); }
public record Address(int value)    { Address { require(0 <= value && value <= 0xffff); } }
public record PageNumber(int number) { PageNumber { require(0 <= number && number < 16); } }
public record Version(int major, int minor) implements Comparable<Version> { public boolean atLeast(Version v); }
public record Creator(String name, Version version, String note) { public boolean swapsAF(); }
public record Magic(int offset, byte[] bytes) { public boolean in(byte[] image); }
public record Data(byte[] bytes)    { public int length();  public String fingerprint();  /* igual por contenido, nunca se muta */ }
public record Border(int colour)    { Border { require(0 <= colour && colour < 8); } }
```

## Capa 2: conceptos, y sus propiedades como objetos

Un concepto es un record inmutable con lo que la máquina sabe de esa parte, más un **catálogo de
propiedades**. Una propiedad es un objeto: sabe su nombre, su tipo, su ancho, qué vale cuando un
formato no la trae, y cómo leerse y escribirse. Con eso, un formato nunca nombra un getter: nombra
propiedades.

```java
public interface Piece { }                                   // un concepto dentro de un snapshot

public interface Property<V> {                               // una propiedad de un concepto, como objeto
  String name();                                             // "processor.a", "ula.border"
  Class<V> type();                                           // Integer, Boolean, IntMode, Joystick, Data…
  int bits();                                                // para enteros: 8 o 16
  V fallback();                                              // lo que vale si el formato no la coloca
  V of(Snapshot snapshot);                                   // leer del documento
  void into(Snapshot.Builder builder, V value);              // escribir en el documento
}
```

### El procesador

```java
public record Processor(Registers main, Registers alternate, int ix, int iy, int sp, int pc,
                        int i, int r, int memptr, Interrupts interrupts, boolean halted, boolean flagQ) implements Piece {
  public static final Property<Boolean> IFF1, IFF2, HALTED, EI_PENDING, FLAG_Q;
  public static final Property<IntMode> IM;
  public static List<Property<?>> properties();              // el catálogo, en orden: los 23 registros y estas seis
}

public record Registers(int a, int f, int b, int c, int d, int e, int h, int l) {   // el juego principal y el alternativo son el mismo tipo
  public int af();  public int bc();  public int de();  public int hl();
}
public record Interrupts(boolean iff1, boolean iff2, IntMode mode, boolean eiPending) { }

public enum Reg implements Property<Integer> {              // los registros, como objetos
  A(8), F(8), B(8), C(8), D(8), E(8), H(8), L(8),
  A_(8), F_(8), B_(8), C_(8), D_(8), E_(8), H_(8), L_(8),
  IX(16), IY(16), SP(16), PC(16), I(8), R(8), MEMPTR(16);
  …
}
```

Los cuatro formatos de snapshot, `SnapshotLoader` y `SnapshotSaver` escriben hoy, cada uno por su
cuenta, una tabla de treinta líneas para estos registros. Acá hay un catálogo, y cada formato una
tabla de offsets.

### La memoria y el paginado

Es donde más conocimiento del Spectrum hay disperso: el 16K tiene sólo la página 5; el 48K va 5,
2, 0 y se aplana; el 128K tiene ocho bancos y **cuál está en 0xC000 lo dice el puerto 7ffd**; la
pantalla es el banco 5 o el 7; el +3 tiene sus modos especiales por 1ffd; Pentagon y Scorpion
tienen más. Hoy está en ocho lugares. Acá está en dos conceptos:

```java
public record Memory(Map<PageNumber, Data> pages, Optional<Data> rom) implements Piece {
  public static final Property<Data> PAGE(PageNumber n);     // una propiedad por página
  public Optional<Data> page(PageNumber n);
}

public final class MemoryMap {                               // lo que cada máquina sabe de su memoria; se pregunta, no se switchea
  public static MemoryMap of(MachineModel model);
  public List<PageNumber> pages();                           // 16K: [5]; 48K: [5, 2, 0]; 128K: [0..7]; Pentagon 1024: [0..63]
  public List<PageNumber> asMapped48k();                     // el orden en que un 48K se aplana: 5, 2, 0
  public PageNumber screen(Paging paging);                   // 5, o 7 si el bit 3 de 7ffd está
}

public record Paging(int port7ffd, int port1ffd, int portEff7, int timexPort) implements Piece {
  public static final Property<Integer> PORT_7FFD, PORT_1FFD, PORT_EFF7, TIMEX_PORT;
  public PageNumber bankAtTop()   { return new PageNumber(port7ffd & 0x07); }   // "la que dice 7ffd"
  public boolean locked()         { return (port7ffd & 0x20) != 0; }
  public int romSelected()        { … }
}
```

La página repetida del SNA de 147487 bytes deja de ser un `if` en el lector: es `Pages.bankAtTop()`,
una colocación que le pregunta a `Paging`.

### Los demás conceptos de la máquina base

```java
public record Ula(Border border, TStates tstates, boolean issue2, boolean lateTimings) implements Piece {
  public static final Property<Border> BORDER;  public static final Property<TStates> TSTATES;
  public static final Property<Boolean> ISSUE_2, LATE_TIMINGS;
}
public record AySound(int selected, int[] registers, AyFlavour flavour) implements Piece {      // de placa, Fuller, Melodik
  public static final Property<Integer> SELECTED;  public static Property<Integer> REGISTER(int n);
  public static final Property<AyFlavour> FLAVOUR;
}
public record Joysticks(List<Joystick> plugged) implements Piece {   // KEMPSTON, CURSOR, SINCLAIR_1, SINCLAIR_2, FULLER, TIMEX_1, TIMEX_2
  public static final Property<Joystick> FIRST;                       // lo que un formato con un solo campo puede decir
}
public record UlaPlus(boolean active, int group, int[] colours) implements Piece { … }
public record Multiface(MultifaceModel model, boolean paged, boolean locked, Data ram) implements Piece { … }
public record InterfaceOne(boolean romPaged, int drives, Optional<Data> rom) implements Piece { … }
public record InterfaceTwo(Data rom) implements Piece { … }
```

Y las de periféricos —`DivIde`, `Beta128`, `PlusD`, `Opus`, `ZxAtasp`, `ZxCf`, `Spectranet`,
`Covox`, `SpecDrum`, `KempstonMouse`, `ZxPrinter`, `Scld`…— las define **el plugin que emula ese
periférico**, con el mismo molde: un record, su catálogo de propiedades, y las reglas que el
periférico sabe (qué bit del control del DivIDE es MAPRAM, cuántos drives puede tener un IF1).

Lo que ningún bloque de este build entendió queda en `Unread`, y se reescribe intacto.

### El documento

```java
public record Snapshot(MachineModel model, Pieces pieces, Provenance provenance) implements Document {
  public Processor processor() { return piece(Processor.class).orElseThrow(); }   // toda máquina detenida tiene procesador…
  public Memory memory()       { return piece(Memory.class).orElseThrow(); }      // …y memoria; el resto es opcional
  public <P extends Piece> Optional<P> piece(Class<P> type);
  public static Builder of(MachineModel model);
  public <V> V get(Property<V> property) { return property.of(this); }            // el catálogo, desde afuera
}
public record Provenance(Optional<Creator> creator, Identity format, Version version, Notes notes) { }   // de dónde salió, qué se salteó
```

### Lo que sale gratis del catálogo

Como todo concepto publica sus propiedades, hay cosas que ya no se escriben por formato:

- **El volcado canónico** de un snapshot —cada propiedad, en orden, una por línea— es un bucle
  sobre el catálogo. Son los goldens de los tests, y ya no se mantienen a mano.
- **Comparar dos snapshots** dice *qué propiedad* difiere, no "byte 1234". El oráculo contra
  libspectrum habla en propiedades.
- **Un inspector**: una ventana que muestra cualquier snapshot con todas sus propiedades y de
  qué formato vino.
- **Tests genéricos**: toda colocación cubre cada propiedad a lo sumo una vez; toda propiedad
  colocada sobrevive a leer y escribir; toda propiedad no colocada queda en su valor de fábrica
  y se anota.

## Capa 3: colocaciones, la tabla de cada formato

Un formato de layout fijo es una tabla "propiedad → offset y codificación". De la tabla salen
leer y escribir, porque la propiedad sabe hacer las dos cosas.

```java
public final class Layout {
  public static Layout of(int length);
  public Layout at(int offset, Property<Integer> p);                                  // u8 o u16 según p.bits(), little-endian
  public Layout bit(int offset, int bit, Property<Boolean> p);
  public Layout bits(int offset, int shift, int mask, Property<Integer> p);
  public <E extends Enum<E>> Layout code(int offset, int shift, int mask, Property<E> p, Codes<E> codes);
  public Layout bytes(int offset, int count, Property<Data> p);
  public Layout encoded(Property<?> p, Encoding e);                                   // una propiedad que viaja raro
  public Layout sameAs(Property<Boolean> p, Property<Boolean> from);                  // SNA: IFF1 = IFF2
  public Layout magic(int offset, String ascii);
  public Layout reserved(int offset, int count);
  public Layout since(Version v);                                                     // la última colocación existe desde esa versión

  public void readInto(Cursor exactly, Snapshot.Builder into, Context ctx);
  public void writeFrom(Snapshot from, Sink out, Context ctx);
  public List<Property<?>> covers();                                                  // para los tests genéricos
}

public final class Codes<E extends Enum<E>> {                // código ↔ valor, en las dos direcciones
  public static <E extends Enum<E>> Codes<E> of(Class<E> type);
  public Codes<E> is(int code, E value);
  public Codes<E> bit(int mask, E value);                    // "si está este bit", en orden
  public Codes<E> otherwise(E value);
}
static final Codes<IntMode> STANDARD_IM    = Codes.of(IntMode.class).is(0, IM0).is(1, IM1).is(2, IM2);
static final Codes<IntMode> SP_IM          = Codes.of(IntMode.class).bit(0x08, IM0).bit(0x02, IM2).otherwise(IM1);
static final Codes<Joystick> Z80_JOYSTICK  = Codes.of(Joystick.class).is(0, CURSOR).is(1, KEMPSTON).is(2, SINCLAIR_1).is(3, SINCLAIR_2);

public interface Encoding {                                  // cómo viaja una propiedad cuando no viaja derecha
  void read(Cursor header, Snapshot.Builder into, Context ctx);
  void write(Snapshot from, byte[] header, Context ctx);
}
final class OnTheStack implements Encoding { … }             // SNA 48K: PC apilado bajo SP; SP < 0x4002 al escribir → Invalid
final class SplitR implements Encoding { … }                 // .z80: 7 bits en el 11, el octavo en el bit 0 del 12
final class SwappedAF implements Encoding { … }              // SZX: A y F al revés si ctx.creator().swapsAF()
```

Y las páginas se colocan con el concepto de memoria:

```java
public interface Pages {                                     // en qué orden van las páginas, dicho con MemoryMap y Paging
  static Pages contiguous(int... numbers);                   // SP, SNA 48K, Z80 v1: 5, 2, 0 — o sólo la 5
  static Pages asMapped48k();                                // lo que MemoryMap.asMapped48k() diga para esa máquina
  static Pages bankAtTop();                                  // SNA 128K: la que dice 7ffd; si es 2 o 5, la copia se descarta
  static Pages remaining();                                  // las que faltan, en orden
  static Pages tagged(Framing framing, PageIds ids);         // .z80 v2/v3: bloques con id ↔ banco según la máquina
  Pages then(Pages next);
}
```

## Capa 4: bytes y bloques

```java
public final class Cursor { int u8(); int u16(); long u32(); Data take(int n); Cursor slice(int n); Data rest(); int left(); }   // Truncated(at, needed) es el único throw
public final class Sink   { Sink u8(int v); Sink u16(int v); Sink u32(long v); Sink bytes(Data d); Sink zeros(int n); Data toData(); }

public interface Codec {                                     // cómo viaja un tramo
  Data encode(Data plain);
  Data decode(Data coded, int expectedLength) throws Corrupt;
  Codec RAW = new Raw();  Codec ZLIB = new Zlib();  Codec Z80_RLE = new Z80Rle();   // una clase cada uno, con su test de ida y vuelta
}

public interface Container { Optional<Image> unwrap(Image image); }   // Gzip, Bzip2, Zip
```

Los formatos hechos de bloques —SZX, TZX, PZX, RZX, las páginas de un `.z80`— comparten un
registro polimórfico y una forma de enmarcar:

```java
public record Tag(String text) { }

public interface BlockFormat<D extends Document> {          // un formato chico, con etiqueta
  Tag tag();
  Contribution<D> read(Cursor payload, Context ctx);         // qué le aporta al documento
  void write(D document, BlockSink out, Context ctx);        // cero, uno o varios bloques
}
public interface Contribution<D extends Document> { void applyTo(Document.Builder<D> builder); }
public interface BlockSink { void block(Tag tag, Data payload); }

public interface Framing {                                   // cómo se delimita un bloque
  Framed next(Cursor in);  void frame(Sink out, Tag tag, Data payload);
  Framing SZX = tagAndLength(4, 4);  Framing RZX = tagAndLength(1, 4);  Framing TZX = tagThenSelfSized(1);
  static Framing z80Pages() { return lengthThenTag(2, 1); }  // largo u16 (0xffff = cruda), id u8
}

public final class Tagged<D extends Document> {              // el registro de bloques de un formato
  public Tagged<D> with(BlockFormat<D> block);
  public Tagged<D> withAll(Iterable<? extends BlockFormat<D>> arrived);   // lo que llega en un jar
  public Tagged<D> catchAll(BlockFormat<D> block);                        // cuando la etiqueta es un dato (páginas .z80)
  public Tagged<D> skipping(Tag... knownAndIgnored);
  public Tagged<D> unknown(Unknown policy);                               // KEEP (queda en Unread), SKIP, REFUSE
  public void readAll(Cursor in, Document.Builder<D> into, Framing framing, Context ctx);
  public void writeAll(D from, Sink out, Framing framing, Context ctx);
}

public final class Context { Version version();  Creator creator();  MachineModel machine();  Notes notes(); }
```

## Capa 5: formatos

```java
public interface Format<D extends Document> {
  Identity identity();
  Read<D> read(Image image) throws Refusal;
  default Image write(D document) throws Refusal { throw new Unsupported(identity(), "no escribe"); }
  default boolean writes() { return false; }
}
public record Read<D>(D document, Notes notes, Identity format, Version version) { }
public record Image(String name, Data bytes, Origin origin) { }

@RoleInterface public interface SnapshotFormat  extends Format<Snapshot>  { }   // las familias: una por documento
@RoleInterface public interface TapeFormat      extends Format<Tape>      { }
@RoleInterface public interface DiskFormat      extends Format<DiskImage> { }
@RoleInterface public interface RecordingFormat extends Format<Recording> { }
```

### SP, entero

```java
@Answers("sp")
public final class SpFormat implements SnapshotFormat {

  static final Layout HEADER = Layout.of(38)
      .magic(0, "SP")
      .at(6, C).at(7, B).at(8, E).at(9, D).at(10, L).at(11, H).at(12, F).at(13, A)
      .at(14, IX).at(16, IY)
      .at(18, C_).at(19, B_).at(20, E_).at(21, D_).at(22, L_).at(23, H_).at(24, F_).at(25, A_)
      .at(26, R).at(27, I).at(28, SP).at(30, PC)
      .reserved(32, 2).at(34, Ula.BORDER).reserved(35, 1)
      .bit(36, 0, IFF1).bit(36, 2, IFF2).code(36, 0, 0x0a, IM, SP_IM)
      .reserved(37, 1);

  public Identity identity() { return Identity.named("SP snapshot").extension("sp").magic(0, "SP"); }

  public Read<Snapshot> read(Image image) {
    Cursor in = Cursor.over(image);
    Cursor header = in.slice(38);
    MachineModel model = SpSize.machineFor(header.at(2).u16());      // 16384 → 16K; 49152 → 48K; otro → Invalid
    if (header.at(4).u16() != 0x4000) throw new Invalid("un SP empieza en 0x4000");
    Snapshot.Builder snapshot = Snapshot.of(model);
    Context ctx = Context.reading(this, image, model);
    HEADER.readInto(header, snapshot, ctx);
    Pages.asMapped48k().readInto(in, snapshot, ctx);                  // la 5 sola, o 5, 2, 0: lo dice MemoryMap
    if (in.left() != 0) throw new Corrupt(in.left() + " bytes de más");
    return Read.of(snapshot.build(), ctx);
  }
}
```

Treinta líneas, sin un solo getter, y se leen como la especificación. No escribe, porque no lo
declara; el día que se quiera, `write` es la misma tabla al revés.

### SNA: la forma la dice el tamaño

```java
@Answers("sna") @Needs({"device-spectrum128"})
public final class SnaFormat implements SnapshotFormat {

  static final Layout HEADER = Layout.of(27)
      .at(0, I).at(1, L_).at(2, H_).at(3, E_).at(4, D_).at(5, C_).at(6, B_).at(7, F_).at(8, A_)
      .at(9, L).at(10, H).at(11, E).at(12, D).at(13, C).at(14, B).at(15, IY).at(17, IX)
      .bit(19, 2, IFF2).sameAs(IFF1, IFF2)
      .at(20, R).at(21, F).at(22, A).at(23, SP)
      .code(25, 0, 0x03, IM, STANDARD_IM)
      .at(26, Ula.BORDER);

  public Identity identity() { return Identity.named("SNA snapshot").extension("sna").sized(49179, 131103, 147487); }

  public Read<Snapshot> read(Image image) {
    SnaShape shape = SnaShape.ofLength(image.bytes().length());
    Cursor in = Cursor.over(image);
    Snapshot.Builder snapshot = Snapshot.of(shape.machine());
    Context ctx = Context.reading(this, image, shape.machine());
    HEADER.readInto(in.slice(27), snapshot, ctx);
    shape.readPages(in, snapshot, ctx);
    return Read.of(snapshot.build(), ctx);
  }

  public byte[] write(Snapshot snapshot) { … SnaShape.of(snapshot.model()) — +2A/+3 → Unsupported — y la misma tabla al revés … }
  public boolean writes() { return true; }
}

sealed interface SnaShape permits FortyEight, OneTwentyEight {
  MachineModel machine();
  void readPages(Cursor in, Snapshot.Builder into, Context ctx);
  void writePages(Snapshot from, Sink out, Context ctx);
}
final class FortyEight implements SnaShape {                 // 5, 2, 0 y el PC apilado
  static final Layout STACK = Layout.of(0).encoded(PC, new OnTheStack());
  public void readPages(Cursor in, Snapshot.Builder into, Context ctx) { Pages.contiguous(5, 2, 0).readInto(in, into, ctx); STACK.readInto(in, into, ctx); }
}
final class OneTwentyEight implements SnaShape {             // 5, 2, la que dice 7ffd, PC, 7ffd, TR-DOS, el resto
  static final Layout TAIL = Layout.of(4).at(0, PC).at(2, Paging.PORT_7FFD).reserved(3, 1);   // el byte 3 (TR-DOS) se valida: 1 → NotSnapshot
  public void readPages(Cursor in, Snapshot.Builder into, Context ctx) {
    Pages.contiguous(5, 2).then(Pages.bankAtTop()).then(Pages.remaining()).readInto(in, into, ctx);   // bankAtTop lee TAIL antes de decidir
  }
}
```

`OnTheStack` es la única rareza de verdad, y está afuera, con nombre y con test. Y el rechazo
"SP fuera de la RAM" es `OnTheStack` diciendo `Corrupt`, exactamente en el caso que el corpus de
libspectrum prueba.

### SZX: bloques, y cada bloque una tabla

```java
@RoleInterface public interface SzxBlock extends BlockFormat<Snapshot> { }   // un bloque de SZX es una forma de entrar

@Answers("szx")
public final class SzxFormat implements SnapshotFormat {
  private final Tagged<Snapshot> blocks;

  public SzxFormat(Iterable<SzxBlock> arrived) {                       // lo que cada plugin trae
    blocks = new Tagged<Snapshot>()
        .with(new CreatorBlock()).with(new RegistersBlock()).with(new SpectrumRegistersBlock()).with(new KeyboardBlock())
        .with(new AyBlock()).with(new RamPageBlock()).with(new MultifaceBlock()).with(new PaletteBlock()).with(new EmbeddedTapeBlock())
        .with(new InterfaceOneBlock()).with(new InterfaceTwoRomBlock()).with(new JoystickBlock()).with(new LecBlock()).with(new LecPageBlock())
        .withAll(arrived)
        .unknown(Unknown.KEEP);
  }

  static final Codes<MachineModel> MACHINES = Codes.of(MachineModel.class)
      .is(0, SPECTRUM_16).is(1, SPECTRUM_48).is(2, SPECTRUM_128).is(3, PLUS2).is(4, PLUS2A).is(5, PLUS3).is(6, PLUS3E)
      .is(7, PENTAGON).is(8, TC2048).is(9, TC2068).is(10, SCORPION).is(11, SE).is(12, TS2068)
      .is(13, PENTAGON_512).is(14, PENTAGON_1024).is(15, SPECTRUM_48_NTSC).is(16, SPECTRUM_128E);

  public Identity identity() { return Identity.named("SZX snapshot").extension("szx").magic(0, "ZXST"); }

  public Read<Snapshot> read(Image image) {
    Cursor in = Cursor.over(image);
    in.magic("ZXST");
    Version version = Version.of(in.u8(), in.u8());
    Snapshot.Builder snapshot = Snapshot.of(MACHINES.decode(in.u8()));
    Ula.LATE_TIMINGS.into(snapshot, (in.u8() & ALTERNATE_TIMINGS) != 0);
    Context ctx = Context.reading(this, image, version);
    blocks.readAll(in, snapshot, Framing.SZX, ctx);
    return Read.of(snapshot.build(), ctx);
  }

  public byte[] write(Snapshot snapshot) { … cabecera, y blocks.writeAll … }
  public boolean writes() { return true; }
}

public final class AyBlock implements SzxBlock {
  static final Layout BODY = Layout.of(18)
      .code(0, 0, 0x03, AySound.FLAVOUR, Codes.of(AyFlavour.class).bit(0x01, FULLER).bit(0x02, ON_BOARD).otherwise(ADD_ON))
      .at(1, AySound.SELECTED)
      .bytes(2, 16, AySound.REGISTERS);
  public Tag tag() { return Tag.of("AY"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) { return BODY.contribution(payload, ctx); }
  public void write(Snapshot from, BlockSink out, Context ctx) { from.piece(AySound.class).ifPresent(ay -> out.block(tag(), BODY.bytesFrom(from, ctx))); }
}

public final class RamPageBlock implements SzxBlock {
  public Tag tag() { return Tag.of("RAMP"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) {
    Codec codec = (payload.u16() & COMPRESSED) != 0 ? Codec.ZLIB : Codec.RAW;
    PageNumber page = new PageNumber(payload.u8());
    return Contribution.set(Memory.PAGE(page), codec.decode(payload.rest(), PAGE_LENGTH));
  }
  public void write(Snapshot from, BlockSink out, Context ctx) {
    for (PageNumber page : MemoryMap.of(from.model()).pages())        // las que la máquina tiene: lo sabe MemoryMap
      from.memory().page(page).ifPresent(data -> out.block(tag(), new Sink().u16(COMPRESSED).u8(page.number()).bytes(Codec.ZLIB.encode(data)).toData()));
  }
}

public final class RegistersBlock implements SzxBlock {
  static final Layout BODY = Layout.of(37)
      .at(0, F).at(1, A).at(2, C).at(3, B).at(4, E).at(5, D).at(6, L).at(7, H)
      .at(8, F_).at(9, A_).at(10, C_).at(11, B_).at(12, E_).at(13, D_).at(14, L_).at(15, H_)
      .at(16, IX).at(18, IY).at(20, SP).at(22, PC).at(24, I).at(25, R)
      .bit(26, 0, IFF1).bit(27, 0, IFF2).code(28, 0, 0x03, IM, STANDARD_IM)
      .at(29, Ula.TSTATES)
      .bit(34, 0, EI_PENDING).bit(34, 1, HALTED)
      .bit(34, 2, FLAG_Q).since(Version.of(1, 5))
      .at(35, MEMPTR).since(Version.of(1, 4))
      .encoded(A, new SwappedAF());                                   // según ctx.creator(), que el CRTR ya dejó en el contexto
  public Tag tag() { return Tag.of("Z80R"); }
  …
}
```

Este bloque y el header de SP **usan las mismas propiedades**: sólo difieren los offsets. Y qué
páginas escribir no lo decide el bloque: le pregunta a `MemoryMap`.

### Z80: tres versiones, una cabecera, dos codificaciones raras

```java
@Answers("z80")
public final class Z80Format implements SnapshotFormat {
  static final Layout HEADER = Layout.of(30)
      .at(0, A).at(1, F).at(2, C).at(3, B).at(4, L).at(5, H).at(6, PC).at(8, SP).at(10, I)
      .encoded(R, new SplitR())                                       // 7 bits en el 11, el octavo en el bit 0 del 12
      .bits(12, 1, 0x07, Ula.BORDER)
      .at(13, E).at(14, D).at(15, C_).at(16, B_).at(17, E_).at(18, D_).at(19, L_).at(20, H_).at(21, A_).at(22, F_)
      .at(23, IY).at(25, IX)
      .bit(27, 0, IFF1).bit(28, 0, IFF2)
      .code(29, 0, 0x03, IM, STANDARD_IM).bit(29, 2, Ula.ISSUE_2).code(29, 6, 0x03, Joysticks.FIRST, Z80_JOYSTICK);

  public Read<Snapshot> read(Image image) {
    Cursor in = Cursor.over(image);
    Snapshot.Builder snapshot = Snapshot.of(SPECTRUM_48);
    Context ctx = Context.reading(this, image);
    HEADER.readInto(in.slice(30), snapshot, ctx);
    Z80Version.of(snapshot, in).readRest(in, snapshot, ctx);          // PC ≠ 0 → v1; si no, el largo del header extendido: 23 → v2, 54 o 55 → v3
    return Read.of(snapshot.build(), ctx);
  }
  public byte[] write(Snapshot s) { return Z80Version.V3.write(s); }
  public boolean writes() { return true; }
}

sealed interface Z80Version permits V1, V2, V3 { void readRest(Cursor in, Snapshot.Builder into, Context ctx);  byte[] write(Snapshot from); }

final class V1 implements Z80Version {                       // 48K: las tres páginas seguidas, crudas o RLE con marca al final
  public void readRest(Cursor in, Snapshot.Builder into, Context ctx) {
    Codec codec = in.header().bit(12, 5) ? Codec.Z80_RLE_WITH_TRAILER : Codec.RAW;
    Pages.contiguous(5, 2, 0).readInto(Cursor.over(codec.decode(in.rest(), 0xC000)), into, ctx);
    Ula.TSTATES.into(into, FrameCounter.WITHOUT_COUNTER);
  }
}
abstract class Extended implements Z80Version {              // v2 y v3 comparten todo menos el largo, la tabla de hardware y el contador
  static final Layout EXTENDED = Layout.of(55)
      .at(0, PC).at(3, Paging.PORT_7FFD).bit(4, 0, InterfaceOne.ROM_PAGED)
      .at(6, AySound.SELECTED).bytes(7, 16, AySound.REGISTERS)
      .encoded(Ula.TSTATES, new FrameCounter(23)).since(V3)
      .at(54, Paging.PORT_1FFD).since(V3_WITH_1FFD);
  // el byte 2 (hardware) y el bit 7 del 5 (modificado) los lee Z80Hardware: una tabla con el id de cada versión, el modelo, y lo que trae enchufado
  static final Tagged<Snapshot> PAGES = new Tagged<Snapshot>().catchAll(new Z80PageBlock());   // largo u16 (0xffff = cruda), id u8; id ↔ banco lo dice PageIds.of(model)
}
```

`Z80Hardware` es el enum-tabla con una fila por hardware (código v2, código v3, modelo, lo que
trae enchufado); `PageIds.of(model)` es del formato (48K: 8→5, 4→2, 5→0; 128K: n−3), y qué bancos
existen es de `MemoryMap`.

## Capa 6: cintas

Una cinta es una lista de bloques, y un bloque tiene hasta tres caras: **suena**, **dirige** el
reproductor, o **cuenta** algo. La señal es un concepto compartido: un árbol de tonos, pulsos,
bits, pausas y niveles, que TAP, TZX, CSW, PZX y WAV producen y el deck recorre.

```java
public record Tape(List<TapeBlock> blocks, Provenance provenance) implements Document { }

public interface TapeBlock {
  Signal signal();                                           // lo que suena; Signal.NONE si no suena
  default Control control() { return Control.NEXT; }        // seguir, saltar, repetir, elegir, parar
  default Info info() { return Info.NONE; }                  // nombre, comentario, hardware
}

public sealed interface Signal permits Tone, Pulses, Bits, Pause, Level, Sequence {
  <R> R accept(SignalVisitor<R> visitor);                    // conjunto cerrado: acá sí va el visitor clásico
  static Signal standard(Data data, Milliseconds pause) { … piloto según el flag, 667/735, bits 855/1710, pausa … }   // la carga de la ROM, una vez
}
public interface SignalVisitor<R> { R tone(Tone t);  R pulses(Pulses p);  R bits(Bits b);  R pause(Pause p);  R level(Level l);  R sequence(Sequence s); }
// SignalPlayer (emite flancos a un Ear), SignalLength, SignalWaveform (la dibuja), CswWriter: un visitor cada uno, y ningún instanceof
public interface Ear { void edge(TStates after);  void level(boolean high);  void silence(TStates during); }

public record StandardData(Data data, Milliseconds pause) implements TapeBlock {          // TAP entero, y el 0x10 de TZX
  public Signal signal() { return Signal.standard(data, pause); }
  public Info info() { return Info.ofHeader(data); }
}
public record TurboData(PulseTimings timings, Data data, int bitsInLastByte, Milliseconds pause) implements TapeBlock { … }
public record Loop(int times) implements TapeBlock { public Signal signal() { return Signal.NONE; }  public Control control() { return new LoopStart(times); } }
```

Los bloques de cinta también tienen propiedades, y los formatos de bloque son tablas:

```java
public final class TurboDataBlockFormat implements TzxBlock {          // 0x11
  static final Layout BODY = Layout.of(TurboData.class)
      .at(0, PulseTimings.PILOT).at(2, PulseTimings.SYNC_1).at(4, PulseTimings.SYNC_2).at(6, PulseTimings.ZERO).at(8, PulseTimings.ONE)
      .at(10, PulseTimings.PILOT_PULSES).at(12, TurboData.BITS_IN_LAST_BYTE).at(13, TurboData.PAUSE)
      .u24(15, TurboData.LENGTH).bytes(18, TurboData.LENGTH, TurboData.DATA);
  public Tag tag() { return Tag.of(0x11); }
  public Contribution<Tape> read(Cursor payload, Context ctx) { return Contribution.block(BODY.build(payload, ctx)); }
  public Class<TurboData> writes() { return TurboData.class; }
  …
}
```

`TapFormat` (cada bloque un `StandardData` con pausa de un segundo), `TzxFormat` (un `Tagged<Tape>`
con un `TzxBlock` por tipo, 24 en total), `CswFormat`, `PzxFormat`, `WavFormat`, `WarajevoFormat`,
`Z80EmFormat`: una clase cada uno. Y `TapeDeck`, del lado de la máquina, sólo conoce `Ear`.

## Capa 7: discos, grabaciones, pantallas

```java
public record DiskImage(Geometry geometry, List<Track> tracks, Provenance provenance) implements Document { }
public record Track(int side, int number, List<Sector> sectors) { }
public record Sector(SectorId id, Data data, SectorFlags flags) { }          // la controladora consume esto; los formatos lo producen

public record Recording(Snapshot start, List<Frame> frames, Provenance provenance) implements Document { }
public record Frame(int instructions, Optional<Data> inputs) { }             // vacío: repite las entradas del anterior

public record Screen(Data pixels, Data attributes, Provenance provenance) implements Document { }
```

`RzxFormat` es un `Tagged<Recording>` con cinco bloques; el snapshot embebido lo lee la `Library`
por su extensión. Las firmas DSA son bytes para el formato y `java.security` para quien verifica.

## Capa 8: identidad y biblioteca

```java
public record Identity(String name, List<String> extensions, Optional<Magic> magic, Set<Integer> sizes) {
  public boolean recognises(Image image);                    // por magia si la hay; por tamaño si los hay; por extensión al final
}
public final class Library {                                 // todos los formatos: los del build y los que llegaron en un jar
  public Optional<Format<?>> identify(Image image);
  public <D extends Document> Optional<Format<D>> reading(Class<D> kind, Image image);
  public <D extends Document> Optional<Format<D>> writing(Class<D> kind, String extension);
}
```

## Capa 9: la máquina como modelo recorrible

La máquina ya es un modelo de objetos: `Cpu` con su `State`, `SpectrumMemory` con sus bancos, el
paginado detrás de `io.out(0x7ffd)`, la ULA, el AY del plugin, cada periférico en el
`PeripheralRegistry`. Guardar es **recorrer** esas partes y pedirle a cada una su foto; cargar es
recorrerlas y darle a cada una la suya. Es un visitor, con una particularidad: **el conjunto de
partes es abierto** —el DivIDE llega en un jar que el núcleo no conoce—, así que un `Visitor`
clásico con `visitCpu`, `visitBank`, `visitDivIde` es imposible. La forma que funciona con
plugins es el doble despacho por rol: cada parte declara qué pieza entrega y recibe, y el
recorrido es genérico. Es el `module_snapshot_from`/`to` de Fuse. (Donde el conjunto es cerrado
—`Signal`, `Control`— sí va el visitor clásico; ver la capa 6.)

Dos cosas, y las dos son roles: **qué hace la máquina con cada clase de documento**, y **qué
entrega y recibe cada parte de la máquina**. `Snapshots` no nombra a nadie, y la CPU, la RAM, la
ULA y el paginado son partes como cualquier plugin.

```java
@RoleInterface public interface Arrival<D extends Document> { Class<D> of();  void arrive(D document, Speccy machine); }
public final class ASnapshotArrives implements Arrival<Snapshot>  { public void arrive(Snapshot s, Speccy m) { Snapshots.of(m).load(s); } }
public final class ATapeArrives     implements Arrival<Tape>      { public void arrive(Tape t, Speccy m)     { TapeDeck.of(m).insert(t); } }
public final class ADiskArrives     implements Arrival<DiskImage> { public void arrive(DiskImage d, Speccy m) { Drive.of(m).insert(d); } }

@RoleInterface public interface Restores<P extends Piece>  { Class<P> piece();  void restore(P piece); }          // toma lo suyo
@RoleInterface public interface Captures<P extends Piece>  { Class<P> piece();  Optional<P> capture(); }         // deja lo suyo
@RoleInterface public interface PluggedBy<P extends Piece> { Class<P> piece();  void plugged(Optional<P> piece); } // el snapshot dice si está

public final class Snapshots {
  public void load(Snapshot snapshot) {
    machine.parts(PluggedBy.class).forEach(part -> part.plugged(snapshot.piece(part.piece())));   // enchufa lo que vino, antes de elegir máquina
    machine.become(snapshot.model());
    keys.releaseAll();
    machine.parts(Restores.class).forEach(part -> snapshot.piece(part.piece()).ifPresent(part::restore));
    memory.remap();
    display.refreshAll();
  }
  public Snapshot save() {
    Snapshot.Builder builder = Snapshot.of(machine.current.model());
    machine.parts(Captures.class).forEach(part -> part.capture().ifPresent(builder::piece));
    return builder.build();
  }
}

public final class ProcessorPart implements Restores<Processor>, Captures<Processor> {
  public void restore(Processor p) { for (Reg reg : Reg.values()) cpu.set(reg, reg.of(p)); cpu.interrupts(p.interrupts()); cpu.halted(p.halted()); }
  public Optional<Processor> capture() { … el mismo bucle al revés … }
}
public final class MemoryPart implements Restores<Memory>, Captures<Memory> { … banco por banco, los que vengan; al capturar, los que MemoryMap diga … }
public final class PagingPart implements Restores<Paging>, Captures<Paging> { … por el puerto, para que pase lo que pasa cuando un juego escribe … }
```

Con esto guardar desde un 128K guarda los ocho bancos, y guardar con un DivIDE guarda el DivIDE.

### La foto es la moneda, no un segundo modelo

Las piezas no describen la máquina por segunda vez: son **lo que cada parte viva entrega y
recibe**. Hacen falta porque un snapshot tiene que existir sin máquina: el archivo elige la
máquina, así que se lee antes de que exista; el catálogo toma huellas sin emulador; los tests y
el oráculo comparan snapshots entre sí; un RZX lleva uno adentro; los settings guardan uno
empaquetado; un inspector muestra uno sin arrancar nada. Y los objetos vivos son mutables,
cableados por Guice, con listeners y timing atrás: no se comparan ni se hashean. La foto sí.

Para que la foto y la parte viva no describan lo mismo dos veces, **la máquina habla el
catálogo**: la parte viva implementa el concepto en vez de tener su propia nomenclatura.

```java
cpu.get(Reg.A);  cpu.set(Reg.PC, 0x8000);        // Cpu habla el catálogo: un adaptador sobre RegistersBase, una tabla Reg → setter, una vez
banks.page(new PageNumber(5));                   // SpectrumMemory habla en PageNumber
MemoryMap.of(machine.model()).pages();           // la máquina viva y el snapshot le preguntan al mismo MemoryMap
machine.model();                                 // un MachineModel, el mismo enum que el snapshot nombra
```

Con eso `ProcessorPart` es un bucle y no una tabla, y las dos tablas de treinta líneas de hoy
(`setZ80State` y `extractZ80State`) desaparecen.

### El contrato de una parte: capturar, restaurar, capturar

Cada parte de la máquina hereda un test que clava que su foto es fiel:

```java
public abstract class MachinePartContract<P extends Piece> {
  protected abstract Speccy aMachine();                                 // limpia, del modelo que la pieza necesita
  protected abstract P aPiece();                                        // armada a mano, con algo en cada propiedad

  @Test void whatIsRestoredIsWhatIsCaptured() {
    Speccy machine = aMachine();
    part(machine).restore(aPiece());
    assertEquals(aPiece(), part(machine).capture().orElseThrow());      // propiedad por propiedad, con el catálogo
  }
  @Test void aPluggedPieceIsCapturedAndAMissingOneIsNot() { … }         // sólo para las que implementan PluggedBy
}
```

Con este contrato, la pieza y la parte viva no pueden divergir sin que un test lo diga.

### Lo que se podría hacer, y no conviene

Un formato podría escribirse directamente desde la máquina viva, sin foto en el medio, con una
interfaz de lectura común que implementen la máquina y el `Snapshot`. Ahorra copiar 128K al
guardar; a cambio, guardar deja de producir un valor que se inspecciona, se compara con el
oráculo o se mete en un RZX. Un snapshot son 130 KB: la copia no vale ese precio.

## Agregar cosas

**Un formato nuevo** (digamos `.snp`, un snapshot de 48K con header fijo): `SnpFormat implements
SnapshotFormat` con `identity()` y un `Layout` de veinte líneas sobre las propiedades que ya
existen; `@Answers("snp")`; `SnpFormatTest extends SnapshotFormatContract` con sus fixtures. Los
tests del contrato ya están escritos. Nada más.

**Un bloque nuevo de SZX**: `MiBloque implements SzxBlock` en el plugin del periférico, con su
`Tag` y su `Layout` sobre las propiedades de su concepto. `@Answers("MIBL")`.

**Un concepto nuevo** (un periférico que quiere viajar en el snapshot): su record con su catálogo
de propiedades, el bloque que lo coloca, y la parte de la máquina que implementa `Restores`,
`Captures` y, si el snapshot puede enchufarla, `PluggedBy`, y hereda `MachinePartContract`.

**Un documento nuevo** (un archivo de POKEs): su record, su familia de formato, su `Arrival`.

## Los tests vienen con la abstracción

```java
public abstract class FormatContract<D extends Document> {
  protected abstract Format<D> format();
  protected abstract List<Fixture<D>> fixtures();            // archivos y lo que se espera de ellos, o una Reference

  @TestFactory Stream<DynamicTest> contract() {
    return Stream.of(
        test("se reconoce por su identidad y no reconoce basura", …),
        test("lee cada fixture como se espera, propiedad por propiedad", …),
        test("lo que escribe lo vuelve a leer igual", …),                                  // sólo si writes()
        test("cortado en cualquier byte, rechaza con un Refusal y nunca revienta", …),
        test("toda propiedad que coloca sobrevive a leer y escribir", …),
        test("toda propiedad que no coloca queda en su valor de fábrica, y se anota", …),
        test("lo que saltea queda en las notas", …));
  }
}
// LayoutContract (ida y vuelta de cualquier tabla), BlockContract, CodecContract, EncodingContract, SignalContract,
// PieceContract (el catálogo cubre el record), MachinePartContract (capturar, restaurar, capturar)
```

El oráculo es un objeto más: `Reference<D>` con una implementación `LibspectrumReference` (por
JNA, como hoy), y lo que dice lo dice en propiedades.

## Dónde vive cada cosa

El emulador no depende de ningún plugin, así que el reparto sale solo: en oozx va lo que el
emulador usa o expone, y todo lo demás en un plugin.

| dónde | qué |
|---|---|
| oozx, `machine/spectrum` | el contrato: los roles (`SnapshotFormat`, `TapeFormat`, `Arrival`, `Captures`, `Restores`, `PluggedBy`); `Snapshot` y los conceptos que tocan dos formatos, con su catálogo; `MachineModel`, `MemoryMap`, `Timings`; `Format`, `Image`, `Read`, `Identity`, `Notes` y los `Refusal`; la `Library` |
| oozx, `media/snapshot` y el núcleo | `Snapshots`, las partes del núcleo (`ProcessorPart`, `MemoryPart`, `PagingPart`, `UlaPart`) y el formato propio de las sesiones, genérico sobre el catálogo |
| oozx-plugins, `device-formats` | el motor: `Cursor`, `Sink`, `Codec`, `Layout`, `Codes`, `Encoding`, `Pages`, `Tagged`, `Framing`, `Context`, y los contratos de tests |
| oozx-plugins, `device-snapshots` | Z80, SNA, SP y SZX, con sus bloques de la máquina base |
| oozx-plugins, `device-tape` | la cinta entera: `Tape`, `Signal`, los formatos y el deck |
| oozx-plugins, cada periférico | su concepto si sólo lo toca él, su bloque de SZX y su parte |

El emulador ya no guarda sus sesiones en `.z80`, sino en un formato propio que guarda todas las
piezas, también las de los plugins. Así ningún formato de intercambio tiene que vivir en oozx. El
único que todavía lee `.z80` desde oozx es el traductor, y el plan le da su propio paso.

## Kaitai, si se quiere

Un `.ksy` describe campos con nombres de texto, y después hace falta otra tabla que diga qué
propiedad es cada nombre. Con propiedades como objetos, el `Layout` es tan corto como el `.ksy` y
ya está mapeado: **una tabla en vez de dos**. Por eso los layouts fijos se escriben acá. Kaitai
queda como herramienta opcional para tres cosas que no compiten con esto: visualizar un archivo
en su Web IDE, publicar la spec de un formato para otros, y un segundo oráculo (`ksdump` sobre el
corpus). Si se usa, sus clases generadas no entran al código: se quedan en una herramienta.

## Lo que no haría

- **Un lenguaje de estructuras general.** `Layout`, `Pages`, `Tagged` y los `Codec` son lo que estos
  formatos usan. Cuando uno pida otra cosa, se agrega una.
- **Reflexión ni anotaciones para mapear.** Una propiedad es un objeto escrito a mano una vez.
- **Herencia entre documentos**, ni un `MachineState` con todo adentro. Composición de conceptos.
- **Un `switch` sobre el modelo de máquina.** `MachineModel` y `MemoryMap` responden preguntas.
- **Un registro central que haya que editar** para que un formato, un bloque o un concepto
  exista: los roles del framework de plugins ya hacen ese trabajo.
- **Conceptos por si acaso.** Un concepto existe si lo tocan dos formatos o la máquina.
- **Escribir un formato desde la máquina viva.** Siempre por la foto: es lo que se compara, se
  inspecciona y viaja.

## Qué cambia respecto de los diseños anteriores

| en `diseno-formatos.md` | en la primera versión de este | ahora | por qué |
|---|---|---|---|
| `Layout` con getters y setters por formato | vocabulario de nombres + `Binding` compartido | **propiedades como objetos** en el concepto; el formato sólo coloca | el conocimiento vive con el concepto, una vez; una tabla en vez de dos |
| partes mutables llenadas bloque a bloque | documentos inmutables | igual | un snapshot leído no cambia |
| el orden de páginas en el formato | `Ram48` compartido | `MemoryMap` y `Paging` responden: `asMapped48k()`, `bankAtTop()` | la página repetida del SNA y los ocho lugares del 5, 2, 0 |
| `part(Class)` desde el participante | `Restores<P>` tipados | igual, y `ProcessorPart` es un bucle sobre el catálogo | el compilador sabe qué pieza toma cada parte |
| `Snapshots` con casos especiales para CPU, RAM y paginado | roles por pieza | **la máquina como modelo recorrible**: un visitor de conjunto abierto, y la máquina habla el catálogo (`Cpu.get(Reg)`, `PageNumber`, `MemoryMap`) | la foto y la parte viva no describen lo mismo dos veces; el contrato capturar–restaurar–capturar lo clava |
| goldens escritos a mano | — | el volcado es el catálogo | ya no se mantienen |

Lo que se conserva de todos: `Codec`, `Container`, `MachineModel` con capacidades y timings, la
tabla `Z80Hardware`, los bloques con etiqueta como registro polimórfico, `Unread`, los tres
hooks de Fuse.

## El costo

Unas 300 clases, la mayoría records de diez líneas. Un motor de colocaciones de unas 600 líneas
—`Layout`, `Pages`, `Codes`, `Tagged`, `Cursor`, `Sink`— escrito una vez y probado genéricamente.
Leer un SZX crea unas decenas de objetos; inflar sus páginas cuesta mil veces más. Reproducir una
cinta recorre un árbol de señales que emite flancos a un `Ear`: sin asignaciones por flanco.

Lo que se paga de verdad es escribir bien los conceptos: que `Processor` tenga sus 29 propiedades,
que `MemoryMap` sepa las dieciocho máquinas, que cada periférico diga lo suyo. La vara para saber
si están completos es objetiva: los 240 campos del `snap` de libspectrum, agrupados por familia, y
lo que Fuse sabe de cada periférico. A cambio, un formato se lee como su especificación, y el que
llega nuevo entiende SNA leyendo `SnaFormat` y `OnTheStack`, sin abrir ninguna otra clase.
