# libspectrum y los hooks de Fuse, en objetos

Cómo quedaría la capa de formatos si se traduce libspectrum a Java con un diseño en capas: cada
cosa que en C es un `switch`, una tabla a mano o un `if( version )` es acá una tabla declarada o
un objeto que despacha; nada conoce más de una capa hacia abajo; y lo que un periférico se lleva
al archivo lo declara el periférico, no el formato. Más clases y más objetos por lectura, a
propósito: inflar 128K o reproducir una cinta cuesta mil veces más que cualquiera de ellos.

Es la forma a la que se va; el plan y el análisis están en `plan-snapshots.md` y
`analisis-libspectrum.md`. Los bocetos en Java son ilustrativos y los nombres, propuestos.

## Las capas

```
 máquina      Snapshots · TapeDeck · Recorder                    oozx media/*      ← fuse snapshot.c, tape.c
   roles      EnabledBySnapshot · RestoredFromASnapshot · WrittenToASnapshot        ← fuse module.c
 documento    Snapshot + Parts · Tape + TapeBlocks · Recording                      ← libspectrum_snap/_tape/_rzx
 formato      SnapshotFormat · TapeFormat · RecordingFormat, uno por archivo        ← z80.c sna.c szx.c tzx_read.c…
 bloques      Chunks · Chunk · Framing · FileContext                                ← read_chunks[], switch( id )
 layouts      Layout · Field · Codes · RegisterLayout                               ← los offsets escritos a mano
 bytes        Bytes · Written · Codec (Zlib, Z80Rle, Raw) · Container               ← libspectrum_buffer, zlib.c, zip.c
 identidad    Signature · Magic · Formats                                           ← libspectrum_identify_file_raw
 máquinas     MachineModel · Capability · Timings                                   ← libspectrum_machine, timings.c
```

Cada capa se prueba sola. Un `Layout` se prueba con un `byte[]` y un objeto; un `Chunk` con un
`Bytes` y un `Snapshot` vacío; un formato con un archivo y el oráculo; un participante con un
`Snapshot` armado a mano.

## 1. Bytes: la plomería, una vez

Lo que en los cuatro lectores de hoy son dieciséis bucles de "leer exactamente N" y cincuenta y
siete `throw FILE_READ_ERROR`, y en libspectrum es `libspectrum_read_word/dword` más un chequeo
de `buffer + n > end` antes de cada lectura.

```java
public final class Bytes {                       // un cursor sobre un byte[], delimitado
  public static Bytes over(byte[] image) { return new Bytes(image, 0, image.length); }
  public int u8()             { need(1); return image[at++] & 0xff; }
  public int u16()            { return u8() | u8() << 8; }                 // little-endian, como todo lo de acá
  public long u32()           { return u16() | (long) u16() << 16; }
  public byte[] take(int n)   { need(n); at += n; return Arrays.copyOfRange(image, at - n, at); }
  public Bytes slice(int n)   { need(n); Bytes part = new Bytes(image, at, at + n); at += n; return part; }
  public Bytes at(int offset) { … un cursor sobre el mismo tramo, parado ahí … }
  public byte[] rest()        { return take(left()); }
  public int left()           { return end - at; }
  private void need(int n)    { if (left() < n) throw new Corrupt("faltan " + n + " bytes en " + at); }
}

public final class Written {                     // el lado de escritura; reemplaza fOut.write(len >>> 24) nueve veces
  public Written u8(int v);  public Written u16(int v);  public Written u32(long v);
  public Written bytes(byte[] b);  public Written ascii(String s);  public Written zeros(int n);
  public byte[] toByteArray();
}
```

Cómo viaja un bloque de RAM dentro de un archivo es un objeto, no un `if`:

```java
public interface Codec {
  byte[] pack(byte[] plain);
  byte[] unpack(byte[] packed, int expected) throws Corrupt;   // Corrupt si no salen exactamente expected

  Codec RAW = new Raw();
  Codec ZLIB = new Zlib();       // SZX: RAMP, MFCE, IF2R, TAPE, LCRP… — cinco bucles de Inflater en uno
  Codec Z80_RLE = new Z80Rle();  // ED ED n v; una página que no achica se devuelve tal cual y el formato la marca cruda
}
```

`Z80Rle.unpack` es el `uncompress_block` de libspectrum: desempaqueta a un buffer que crece y
valida el largo al final. El Lazy Jones que hubo que parchar sale por diseño.

## 2. Layouts: las tablas de offsets, declaradas y en las dos direcciones

Un header es una tabla "offset → campo". Hoy cada formato la escribe dos veces (una en `load`,
otra en `save`) y una errata en una dirección no la ve nadie. Acá la tabla se escribe una vez y de
ella salen leer y escribir.

```java
public final class Layout<T> {
  public static <T> Layout<T> ofLength(int length);
  public Layout<T> u8  (int at, ToIntFunction<T> get, ObjIntConsumer<T> set);
  public Layout<T> u16 (int at, ToIntFunction<T> get, ObjIntConsumer<T> set);
  public Layout<T> u32 (int at, ToLongFunction<T> get, ObjLongConsumer<T> set);
  public Layout<T> flag(int at, int mask, Predicate<T> get, BiConsumer<T, Boolean> set);
  public Layout<T> bits(int at, int shift, int mask, ToIntFunction<T> get, ObjIntConsumer<T> set);
  public <E extends Enum<E>> Layout<T> code(int at, int shift, int mask, Codes<E> codes,
                                            Function<T, E> get, BiConsumer<T, E> set);
  public Layout<T> bytes(int at, int count, Function<T, byte[]> get, BiConsumer<T, byte[]> set);
  public Layout<T> magic(int at, String ascii);           // se verifica al leer (NotThisFormat), se escribe
  public Layout<T> reserved(int at, int count);           // documenta lo que el formato no define; va en cero
  public <P extends Part> Layout<T> part(Class<P> type, Layout<P> of);   // un sub-layout sobre los mismos bytes
  public Layout<T> since(Version v);                      // el último campo existe desde esa versión del formato

  public void readInto(Bytes exactly, T into, Version v);
  public byte[] write(T from);                            // dos campos en el mismo byte se combinan con OR
  public int length();
}
```

Cada campo es un objeto chico (`U8`, `U16`, `Flag`, `Code`, `Slice`, `Reserved`, `Magic`,
`SubLayout`) con `read` y `write`: el layout no tiene ningún `switch` sobre el tipo de campo.

Las tablas "código ↔ valor" son otra clase declarativa, en las dos direcciones:

```java
public final class Codes<E extends Enum<E>> {
  public static <E extends Enum<E>> Codes<E> of(Class<E> type);
  public Codes<E> is(int code, E value);          // un código; el primero de un valor es el que se escribe
  public Codes<E> bit(int mask, E value);         // "si está este bit"; se prueban en orden
  public Codes<E> otherwise(E value);
  public E decode(int code) throws Unsupported;
  public int encode(E value);
}

static final Codes<IntMode> INTERRUPT_MODE = Codes.of(IntMode.class).is(0, IM0).is(1, IM1).is(2, IM2);
static final Codes<IntMode> SP_INTERRUPT_MODE = Codes.of(IntMode.class).bit(0x08, IM0).bit(0x02, IM2).otherwise(IM1);
static final Codes<Joystick> Z80_JOYSTICK = Codes.of(Joystick.class)
    .is(0, CURSOR).is(1, KEMPSTON).is(2, SINCLAIR_LEFT).is(3, SINCLAIR_RIGHT);
```

Y los registros del procesador son un enum, para que la tabla de cada formato sea sólo offsets:

```java
public enum Reg {
  A(1), F(1), B(1), C(1), D(1), E(1), H(1), L(1),
  A_(1), F_(1), B_(1), C_(1), D_(1), E_(1), H_(1), L_(1),
  IX(2), IY(2), SP(2), PC(2), I(1), R(1), MEMPTR(2);
  final int width;
}

public final class RegisterLayout {             // Layout<Registers> hecho de offsets: u8 o u16 según el ancho del registro
  public static RegisterLayout ofLength(int length);
  public RegisterLayout at(Reg reg, int offset);
  public RegisterLayout bits7(Reg reg, int offset, int highBitAt, int highBitMask);   // R en .z80: 7 bits acá, el octavo allá
  public Layout<Registers> layout();
}
```

Con eso, los cinco headers de registros que hay (Z80, SNA, SP, Z80R de SZX en las dos
versiones de A/F) son cinco tablas de una línea por registro, y ninguna tiene código.

## 3. Bloques: lo que hoy es `read_chunks[]` y `switch( id )`

SZX, TZX, PZX, RZX y las páginas de un `.z80` v2/v3 tienen la misma forma: una secuencia de
bloques con un identificador y, casi siempre, un largo. En libspectrum son cuatro tablas y
cuatro bucles distintos. Acá son un registro polimórfico y una forma de enmarcar.

```java
public record ChunkId(String tag) { public static ChunkId of(String tag); }

public interface Chunk<D> {                      // un codec de bloque, para un documento D
  ChunkId id();
  void read(Bytes payload, D into, FileContext ctx) throws FormatException;
  void write(D from, ChunkSink out, FileContext ctx);        // emite cero, uno o varios bloques
}

public interface ChunkSink { void chunk(ChunkId id, byte[] payload); }

public interface Framing {                       // cómo se delimita un bloque en este archivo
  Framed next(Bytes in) throws Corrupt;          // (id, payload)
  void frame(Written out, ChunkId id, byte[] payload);

  Framing SZX = new LengthFramed(4, 4);          // id de 4 bytes, largo de 4
  Framing RZX = new LengthFramed(1, 4);
  Framing TZX = new SelfDelimited(1);            // id de 1 byte; el codec sabe cuánto ocupa lo suyo
  static Framing z80Pages(MachineModel m) { return new Z80PageFraming(m); }   // largo u16 (0xffff = cruda), id u8
}

public final class Chunks<D> {                   // el registro de bloques de un formato
  public Chunks<D> with(Chunk<D> chunk);
  public Chunks<D> withAll(Iterable<? extends Chunk<D>> arrived);     // lo que llega en un jar
  public Chunks<D> catchAll(Chunk<D> chunk);                          // para framings donde el id es un dato (páginas .z80)
  public Chunks<D> skipping(ChunkId... knownAndIgnored);              // los 9 que libspectrum saltea a sabiendas
  public Chunks<D> unknown(Unknown policy);                           // KEEP (queda en Unread), SKIP, REFUSE
  public void readAll(Bytes in, D into, Framing framing, FileContext ctx);
  public void writeAll(D from, Written out, Framing framing, FileContext ctx);   // en el orden de registro
}

public final class FileContext {                 // lo que un bloque necesita saber del archivo que lo contiene
  Version version();  Creator creator();  MachineModel machine();  Diagnostics notes();
}
```

`Diagnostics` es donde va lo que libspectrum imprime con `libspectrum_print_error( WARNING )`
(94 veces sólo en `szx.c`): el archivo se lee, y quien lo abrió puede preguntar qué se salteó.

## 4. El documento: `Snapshot` y sus partes

`libspectrum_snap` es un registro de 240 campos generado de una tabla. Acá es un documento
hecho de partes tipadas, opcionales, y **abiertas**: el núcleo define las de la máquina base y
cada plugin define la suya.

```java
public final class Snapshot {
  private MachineModel machine;
  private final Map<Class<? extends Part>, Part> parts = new LinkedHashMap<>();

  public MachineModel machine();  public void machine(MachineModel m);
  public <P extends Part> P part(Class<P> type);              // la crea si no está: un documento se llena bloque a bloque
  public <P extends Part> Optional<P> has(Class<P> type);
  public Collection<Part> parts();
}

public interface Part { }

public final class Registers implements Part {              // el procesador
  private final EnumMap<Reg, Integer> values = new EnumMap<>(Reg.class);
  public int get(Reg r);  public void set(Reg r, int v);
  public boolean iff1, iff2, halted, lastInstructionEi, flagQ;  public IntMode im = IntMode.IM0;
}
public final class Ula implements Part      { int border, tstates; boolean issue2, lateTimings; }
public final class Ram implements Part      { private final byte[][] pages = new byte[16][];  page(n); page(n, bytes); has(n); }
public final class Paging implements Part   { int port7ffd, port1ffd, portEff7, timexPort; }   // los out_* de libspectrum
public final class Ay implements Part       { int selected; final int[] registers = new int[16]; boolean fullerBox; }
public final class Joysticks implements Part { final List<Joystick> plugged = new ArrayList<>(); }
public final class UlaPlus implements Part  { boolean active; int group; final int[] palette = new int[64]; }
public final class Multiface implements Part, InterfaceOne, InterfaceTwo, … (las de la máquina base)

public final class Unread implements Part {                 // lo que ningún bloque de este build entendió
  final List<Framed> chunks = new ArrayList<>();            // viaja intacto y se reescribe igual: no se pierde nada
}
```

Las partes de periféricos — `DivIde`, `Beta128`, `PlusD`, `Opus`, `ZxAtasp`, `ZxCf`, `Spectranet`,
`Covox`, `SpecDrum`, `KempstonMouse`, `ZxPrinter`, `Scld`… — **no están en el núcleo**: las define
el plugin que emula ese periférico, junto con el bloque que las lee y el participante que las
pone en la máquina (sección 11). En libspectrum están todas en el mismo `struct`; acá cada una
vive con quien la entiende, y un snapshot de un DivIDE abierto sin el plugin conserva el bloque
en `Unread`.

## 5. Máquinas: 18 modelos, con lo que cada uno puede

```java
public enum Capability {
  BEEPER, AY, MEMORY_128, MEMORY_PLUS3, DISK_PLUS3, MEMORY_TIMEX, VIDEO_TIMEX, TIMEX_DOCK,
  DISK_TRDOS, JOYSTICK_SINCLAIR, JOYSTICK_KEMPSTON, MEMORY_SCORPION, EVEN_M1, MEMORY_SE, NTSC,
  MEMORY_PENTAGON_512, MEMORY_PENTAGON_1024
}

public record Timings(int clockHz, int tstatesPerFrame, int tstatesPerLine,
                      int linesTop, int linesScreen, int linesBottom, int interruptLength) { … }   // timings.c

public enum MachineModel {                       // libspectrum_machine, con capacidades y timings en la misma tabla
  SPECTRUM_16     ("ZX Spectrum 16K",   Timings.SPECTRUM_48,  BEEPER),
  SPECTRUM_48     ("ZX Spectrum 48K",   Timings.SPECTRUM_48,  BEEPER),
  SPECTRUM_48_NTSC("ZX Spectrum 48K (NTSC)", Timings.NTSC_48, BEEPER, NTSC),
  TC2048          ("Timex TC2048",      Timings.SPECTRUM_48,  BEEPER, MEMORY_TIMEX, VIDEO_TIMEX, JOYSTICK_KEMPSTON),
  TC2068, TS2068  (… TIMEX_DOCK, AY …),
  SPECTRUM_128    ("ZX Spectrum 128K",  Timings.SPECTRUM_128, BEEPER, AY, MEMORY_128, JOYSTICK_SINCLAIR),
  SPECTRUM_128E, PLUS2, PLUS2A (… MEMORY_PLUS3 …), PLUS3 (… MEMORY_PLUS3, DISK_PLUS3), PLUS3E,
  PENTAGON        ("Pentagon 128K",     Timings.PENTAGON,     BEEPER, AY, MEMORY_128, DISK_TRDOS, JOYSTICK_KEMPSTON),
  PENTAGON_512, PENTAGON_1024, SCORPION (… MEMORY_SCORPION …), SE (… MEMORY_SE …);

  public boolean can(Capability c);  public Timings timings();  public String longName();
}
```

Cómo cada formato nombra a una máquina es una tabla **del formato**, no un `switch` en la
máquina. La del `.z80`, que hoy está dos veces (v2 y v3, ~65 líneas cada una) y rechaza la
mitad de las filas de libspectrum:

```java
public enum Z80Hardware {                        // z80.c: una sola tabla, con el id de cada versión
  //                      v2   v3  modelo          trae
  FORTY_EIGHT            ( 0,   0, SPECTRUM_48),
  FORTY_EIGHT_IF1        ( 1,   1, SPECTRUM_48,   Fitted.INTERFACE_1),
  FORTY_EIGHT_SAMRAM     ( 2,   2, SPECTRUM_48,   Fitted.SAM_RAM),
  FORTY_EIGHT_MGT        (-1,   3, SPECTRUM_48,   Fitted.MGT),
  ONE_TWENTY_EIGHT       ( 3,   4, SPECTRUM_128),
  ONE_TWENTY_EIGHT_IF1   ( 4,   5, SPECTRUM_128,  Fitted.INTERFACE_1),
  ONE_TWENTY_EIGHT_MGT   (-1,   6, SPECTRUM_128,  Fitted.MGT),
  PLUS3                  ( 7,   7, PLUS3),
  PLUS3_XZX              ( 8,   8, PLUS3),
  PENTAGON               ( 9,   9, PENTAGON),
  SCORPION               (10,  10, SCORPION),
  PLUS2                  (12,  12, PLUS2),
  PLUS2A                 (13,  13, PLUS2A),
  TC2048                 (14,  14, TC2048),
  TC2068                 (15,  15, TC2068),
  TS2068                 (128, 128, TS2068);

  public static Z80Hardware of(Z80Version v, int code) throws Unsupported;
  public int code(Z80Version v);
  public MachineModel model();
  public MachineModel modified();                // bit 7 del byte 37: 48 → 16, 128 → +2, +3 → +2A
  public List<Fitted> fitted();                  // lo que el id dice que hay enchufado
}

static final Codes<MachineModel> SZX_MACHINES = Codes.of(MachineModel.class)
    .is(0, SPECTRUM_16).is(1, SPECTRUM_48).is(2, SPECTRUM_128).is(3, PLUS2).is(4, PLUS2A).is(5, PLUS3)
    .is(6, PLUS3E).is(7, PENTAGON).is(8, TC2048).is(9, TC2068).is(10, SCORPION).is(11, SE).is(12, TS2068)
    .is(13, PENTAGON_512).is(14, PENTAGON_1024).is(15, SPECTRUM_48_NTSC).is(16, SPECTRUM_128E);
```

Y el emulador, del otro lado, dice qué máquina suya es cada `MachineModel`
(`Machine.forSnapshotModel`, que ya existe): un Pentagon llega al plugin del Pentagon.

## 6. Formatos: uno por archivo, y nada más que la composición

```java
public interface Format<D> {                      // D es Snapshot, Tape o Recording
  Signature signature();                          // cómo se lo reconoce: extensiones y magia
  D read(byte[] image) throws FormatException;
  default byte[] write(D document) { throw new Unsupported(this + " no escribe"); }
  default boolean writes() { return false; }
  default String label() { return signature().label(); }
}

@RoleInterface public interface SnapshotFormat  extends Format<Snapshot>  { }   // lo que hoy es SnapshotFile
@RoleInterface public interface TapeFormat      extends Format<Tape>      { }
@RoleInterface public interface RecordingFormat extends Format<Recording> { }
```

### SP, entero

```java
@Answers("sp")
public final class SpFormat implements SnapshotFormat {

  static final Layout<Snapshot> HEADER = Layout.<Snapshot>ofLength(38)
      .magic(0, "SP")
      .part(Registers.class, RegisterLayout.ofLength(38)
          .at(C, 6).at(B, 7).at(E, 8).at(D, 9).at(L, 10).at(H, 11).at(F, 12).at(A, 13)
          .at(IX, 14).at(IY, 16)
          .at(C_, 18).at(B_, 19).at(E_, 20).at(D_, 21).at(L_, 22).at(H_, 23).at(F_, 24).at(A_, 25)
          .at(R, 26).at(I, 27).at(SP, 28).at(PC, 30)
          .layout()
          .flag(36, 0x01, r -> r.iff1, (r, on) -> r.iff1 = on)
          .flag(36, 0x04, r -> r.iff2, (r, on) -> r.iff2 = on)
          .code(36, 0, 0x0a, SP_INTERRUPT_MODE, r -> r.im, (r, im) -> r.im = im))
      .part(Ula.class, Layout.<Ula>ofLength(38).u8(34, Ula::border, Ula::border))
      .reserved(32, 2).reserved(35, 1).reserved(37, 1);

  public Signature signature() { return Signature.of("SP snapshot", List.of("sp"), Magic.at(0, "SP")); }

  public Snapshot read(byte[] image) {
    Bytes in = Bytes.over(image);
    Bytes header = in.slice(38);
    Ram48 ram = Ram48.ofLength(header.at(2).u16());          // 16384 → sólo la página 5; 49152 → 5, 2, 0; otro → Invalid
    if (header.at(4).u16() != 0x4000) throw new Invalid("un SP empieza en 0x4000");
    Snapshot snapshot = new Snapshot(ram.machine());          // SPECTRUM_16 o SPECTRUM_48
    HEADER.readInto(header, snapshot, Version.ANY);
    ram.readInto(in, snapshot.part(Ram.class));
    if (in.left() != 0) throw new Corrupt(in.left() + " bytes de más");
    snapshot.part(Joysticks.class);                           // vacío: el formato no lo dice
    return snapshot;
  }
  // write: lo que hereda — Unsupported. libspectrum tampoco lo escribe.
}
```

`Ram48` es el mapa de un 48K una sola vez (hoy está en ocho lugares): el orden `5, 2, 0`, el 16K
con sólo la 5, `readInto` y `writeFrom`.

### SNA: dos formas, elegidas por el tamaño

```java
@Answers("sna") @Needs({"device-spectrum128"})
public final class SnaFormat implements SnapshotFormat {
  static final Layout<Snapshot> HEADER = Layout.<Snapshot>ofLength(27)
      .part(Registers.class, RegisterLayout.ofLength(27)
          .at(I, 0).at(L_, 1).at(H_, 2).at(E_, 3).at(D_, 4).at(C_, 5).at(B_, 6).at(F_, 7).at(A_, 8)
          .at(L, 9).at(H, 10).at(E, 11).at(D, 12).at(C, 13).at(B, 14).at(IY, 15).at(IX, 17)
          .at(R, 20).at(F, 21).at(A, 22).at(SP, 23)
          .layout()
          .flag(19, 0x04, r -> r.iff2, (r, on) -> { r.iff1 = on; r.iff2 = on; })
          .code(25, 0, 0x03, INTERRUPT_MODE, r -> r.im, (r, im) -> r.im = im))
      .part(Ula.class, Layout.<Ula>ofLength(27).u8(26, Ula::border, Ula::border));

  public Snapshot read(byte[] image) {
    SnaShape shape = SnaShape.ofLength(image.length);         // 49179 → FortyEight; 131103 y 147487 → OneTwentyEight
    Bytes in = Bytes.over(image);
    Snapshot snapshot = new Snapshot(shape.machine());
    HEADER.readInto(in.slice(27), snapshot, Version.ANY);
    shape.readPages(in, snapshot);
    return snapshot;
  }

  public byte[] write(Snapshot snapshot) {
    SnaShape shape = SnaShape.of(snapshot.machine());         // +2A/+3 → Unsupported, como hoy
    Written out = new Written().bytes(HEADER.write(snapshot));
    shape.writePages(out, snapshot);
    return out.toByteArray();
  }
}

sealed interface SnaShape permits FortyEight, OneTwentyEight {
  MachineModel machine();
  void readPages(Bytes in, Snapshot into);
  void writePages(Written out, Snapshot from);
}

final class FortyEight implements SnaShape {     // tres páginas y el PC apilado
  public void readPages(Bytes in, Snapshot into) {
    Ram48.FORTY_EIGHT.readInto(in, into.part(Ram.class));
    StackedPc.pop(into);                         // el PC está en la pila: se desapila y SP sube 2 — como libspectrum
  }
  public void writePages(Written out, Snapshot from) {
    Ram48.FORTY_EIGHT.writeFrom(out, StackedPc.pushed(from));   // una copia con el PC apilado; SP < 0x4002 → Invalid
  }
}

final class OneTwentyEight implements SnaShape { // 5, 2, la que dice 7ffd, PC, 7ffd, TR-DOS, y el resto en orden
  …
}
```

`StackedPc` es la única regla rara del SNA y está en una clase con nombre, con su test. Y lo que
hoy es "SP fuera de la RAM → rechazado" es `StackedPc.pop` diciendo `Corrupt`, exactamente en
el caso que el corpus de libspectrum prueba (`sna_file_with_sp_0x4000`).

### SZX: la cabecera y el registro de bloques

```java
@Answers("szx")
public final class SzxFormat implements SnapshotFormat {
  private final Chunks<Snapshot> chunks;

  public SzxFormat(Iterable<SzxChunk> arrived) {                     // lo que cada plugin trae
    chunks = new Chunks<Snapshot>()
        .with(new CreatorChunk())            // CRTR — y de él sale ctx.creator()
        .with(new RegistersChunk())          // Z80R
        .with(new SpectrumRegistersChunk())  // SPCR
        .with(new KeyboardChunk())           // KEYB
        .with(new AyChunk())                 // AY
        .with(new RamPageChunk())            // RAMP, una por página
        .with(new MultifaceChunk())          // MFCE
        .with(new PaletteChunk())            // PLTT
        .with(new TapeChunk())               // TAPE
        .with(new InterfaceOneChunk())       // IF1
        .with(new InterfaceTwoRomChunk())    // IF2R
        .with(new JoystickChunk())           // JOY
        .with(new LecChunk()).with(new LecPageChunk())
        .withAll(arrived)                    // DIDE, DMMC, B128, PLSD, OPUS, ZXAT/ATRP, ZXCF/CFRP, COVX, DRUM, AMXM, ZXPR, SCLD, DOCK, SNET…
        .unknown(Unknown.KEEP);              // lo que nadie entiende viaja en Unread y se reescribe igual
  }

  public Snapshot read(byte[] image) {
    Bytes in = Bytes.over(image);
    if (!Arrays.equals(in.take(4), MAGIC)) throw new NotThisFormat();
    Version version = Version.of(in.u8(), in.u8());
    Snapshot snapshot = new Snapshot(SZX_MACHINES.decode(in.u8()));
    snapshot.part(Ula.class).lateTimings((in.u8() & ALTERNATE_TIMINGS) != 0);
    chunks.readAll(in, snapshot, Framing.SZX, new FileContext(version, snapshot.machine()));
    return snapshot;
  }

  public byte[] write(Snapshot snapshot) {
    Written out = new Written().bytes(MAGIC).u8(1).u8(5)
        .u8(SZX_MACHINES.encode(snapshot.machine()))
        .u8(snapshot.part(Ula.class).lateTimings ? ALTERNATE_TIMINGS : 0);
    chunks.writeAll(snapshot, out, Framing.SZX, new FileContext(Version.of(1, 5), snapshot.machine()));
    return out.toByteArray();
  }
}

@RoleInterface public interface SzxChunk extends Chunk<Snapshot> { }   // un bloque de SZX es una forma de entrar
```

Tres bloques, para ver la forma. El AY es un layout y nada más:

```java
public final class AyChunk implements SzxChunk {
  static final Layout<Ay> BODY = Layout.<Ay>ofLength(18)
      .flag(0, 0x01, a -> a.fullerBox, (a, on) -> a.fullerBox = on)
      .flag(0, 0x02, a -> a.on128, (a, on) -> a.on128 = on)
      .u8(1, a -> a.selected, (a, v) -> a.selected = v)
      .bytes(2, 16, Ay::registersAsBytes, Ay::registers);

  public ChunkId id() { return ChunkId.of("AY"); }
  public void read(Bytes payload, Snapshot into, FileContext ctx) { BODY.readInto(payload, into.part(Ay.class), ctx.version()); }
  public void write(Snapshot from, ChunkSink out, FileContext ctx) { from.has(Ay.class).ifPresent(ay -> out.chunk(id(), BODY.write(ay))); }
}
```

Las páginas: un bloque que emite varios, y un `Codec` elegido por una bandera:

```java
public final class RamPageChunk implements SzxChunk {
  public ChunkId id() { return ChunkId.of("RAMP"); }

  public void read(Bytes payload, Snapshot into, FileContext ctx) {
    Codec codec = (payload.u16() & COMPRESSED) != 0 ? Codec.ZLIB : Codec.RAW;
    int page = payload.u8();
    into.part(Ram.class).page(page, codec.unpack(payload.rest(), PAGE));
  }

  public void write(Snapshot from, ChunkSink out, FileContext ctx) {
    Ram ram = from.part(Ram.class);
    for (int page = 0; page < 16; page++) {
      if (!ram.has(page)) continue;
      out.chunk(id(), new Written().u16(COMPRESSED).u8(page).bytes(Codec.ZLIB.pack(ram.page(page))).toByteArray());
    }
  }
}
```

Los registros, con la regla del creador: A y F al revés cuando lo escribió libspectrum < 0.5.0.
No es un `if` adentro de la lectura de cada byte: son dos layouts, y el contexto elige uno.

```java
public final class RegistersChunk implements SzxChunk {
  static final Layout<Registers> NORMAL = RegisterLayout.ofLength(37)
      .at(F, 0).at(A, 1).at(C, 2).at(B, 3).at(E, 4).at(D, 5).at(L, 6).at(H, 7)
      .at(F_, 8).at(A_, 9).at(C_, 10).at(B_, 11).at(E_, 12).at(D_, 13).at(L_, 14).at(H_, 15)
      .at(IX, 16).at(IY, 18).at(SP, 20).at(PC, 22).at(I, 24).at(R, 25)
      .layout()
      .flag(26, 0x01, r -> r.iff1, (r, on) -> r.iff1 = on)
      .flag(27, 0x01, r -> r.iff2, (r, on) -> r.iff2 = on)
      .code(28, 0, 0x03, INTERRUPT_MODE, r -> r.im, (r, im) -> r.im = im)
      .flag(34, EILAST, r -> r.lastInstructionEi, (r, on) -> r.lastInstructionEi = on)
      .flag(34, HALTED, r -> r.halted, (r, on) -> r.halted = on)
      .flag(34, FSET,   r -> r.flagQ, (r, on) -> r.flagQ = on).since(Version.of(1, 5))
      .u16(35, r -> r.get(MEMPTR), (r, v) -> r.set(MEMPTR, v)).since(Version.of(1, 4));

  static final Layout<Registers> AF_SWAPPED = NORMAL.but(RegisterLayout.at(A, 0).at(F, 1).at(A_, 8).at(F_, 9));

  static final Layout<Ula> TSTATES = Layout.<Ula>ofLength(37).u32(29, u -> u.tstates, (u, v) -> u.tstates = (int) v);

  public ChunkId id() { return ChunkId.of("Z80R"); }

  public void read(Bytes payload, Snapshot into, FileContext ctx) {
    Layout<Registers> registers = ctx.creator().swapsAF() ? AF_SWAPPED : NORMAL;
    registers.readInto(payload.at(0), into.part(Registers.class), ctx.version());
    TSTATES.readInto(payload.at(0), into.part(Ula.class), ctx.version());
  }

  public void write(Snapshot from, ChunkSink out, FileContext ctx) {
    byte[] body = NORMAL.write(from.part(Registers.class));
    TSTATES.writeOver(body, from.part(Ula.class));
    out.chunk(id(), body);
  }
}

public record Creator(String name, int major, int minor, String note) {   // creator.c
  public boolean swapsAF() { … "libspectrum: 0.x.y" con x < 5, o 0.5.0 … }
}
```

### Z80: tres versiones, una cabecera

```java
@Answers("z80")
public final class Z80Format implements SnapshotFormat {
  static final Layout<Snapshot> HEADER = Layout.<Snapshot>ofLength(30)
      .part(Registers.class, RegisterLayout.ofLength(30)
          .at(A, 0).at(F, 1).at(C, 2).at(B, 3).at(L, 4).at(H, 5).at(PC, 6).at(SP, 8).at(I, 10)
          .bits7(R, 11, 12, 0x01)                                   // R: 7 bits en el 11, el octavo en el bit 0 del 12
          .at(E, 13).at(D, 14).at(C_, 15).at(B_, 16).at(E_, 17).at(D_, 18).at(L_, 19).at(H_, 20)
          .at(A_, 21).at(F_, 22).at(IY, 23).at(IX, 25)
          .layout()
          .flag(27, 0xff, r -> r.iff1, (r, on) -> r.iff1 = on)
          .flag(28, 0xff, r -> r.iff2, (r, on) -> r.iff2 = on)
          .code(29, 0, 0x03, INTERRUPT_MODE, r -> r.im, (r, im) -> r.im = im))
      .part(Ula.class, Layout.<Ula>ofLength(30)
          .bits(12, 1, 0x07, u -> u.border, (u, v) -> u.border = v)
          .flag(29, 0x04, u -> u.issue2, (u, on) -> u.issue2 = on))
      .part(Joysticks.class, Layout.<Joysticks>ofLength(30)
          .code(29, 6, 0x03, Z80_JOYSTICK, Joysticks::first, Joysticks::only));

  public Snapshot read(byte[] image) {
    Bytes in = Bytes.over(image);
    Snapshot snapshot = new Snapshot(SPECTRUM_48);
    HEADER.readInto(in.slice(30), snapshot, Version.ANY);
    Z80Version.of(snapshot, in).readRest(in, snapshot);         // PC ≠ 0 → v1; si no, el largo del header extendido: 23 → v2, 54 o 55 → v3
    return snapshot;
  }

  public byte[] write(Snapshot snapshot) { return Z80Version.V3.write(snapshot); }   // se escribe v3 siempre
}

sealed interface Z80Version permits V1, V2, V3 {
  void readRest(Bytes in, Snapshot into);
  byte[] write(Snapshot from);
}

final class V1 implements Z80Version {           // 48K siempre; tres páginas seguidas, crudas o RLE con la marca 00 ED ED 00
  public void readRest(Bytes in, Snapshot into) {
    boolean compressed = (in.at(12).u8() & 0x20) != 0;
    byte[] ram = compressed ? Codec.Z80_RLE.unpack(in.rest(), 0xC000) : in.take(0xC000);
    Ram48.FORTY_EIGHT.readInto(Bytes.over(ram), into.part(Ram.class));
    into.part(Ula.class).tstates = FrameCounter.WITHOUT_COUNTER;
  }
}

abstract class Extended implements Z80Version {  // v2 y v3 comparten todo menos tres cosas: el largo, la tabla de hardware, el contador
  static final Layout<Snapshot> EXTENDED = Layout.<Snapshot>ofLength(55)
      .part(Registers.class, RegisterLayout.ofLength(55).at(PC, 0).layout())
      .part(Paging.class, Layout.<Paging>ofLength(55).u8(3, p -> p.port7ffd, (p, v) -> p.port7ffd = v)
                                                       .u8(54, p -> p.port1ffd, (p, v) -> p.port1ffd = v).since(Version.of(3, 1)))
      .part(Ay.class, Layout.<Ay>ofLength(55).u8(6, a -> a.selected, (a, v) -> a.selected = v).bytes(7, 16, Ay::registersAsBytes, Ay::registers))
      .part(Ula.class, Layout.<Ula>ofLength(55).frameCounter(23, FrameCounter.QUARTERS).since(Version.of(3, 0)));
  // el byte 2 (hardware) y el 5 (banderas: hardware modificado, AY en 48K) los lee esta clase con Z80Hardware

  public void readRest(Bytes in, Snapshot into) {
    int length = in.u16();
    Bytes extended = in.slice(length);
    Z80Hardware hardware = Z80Hardware.of(this, extended.at(2).u8());
    boolean modified = (extended.at(5).u8() & 0x80) != 0;
    into.machine(modified ? hardware.modified() : hardware.model());
    hardware.fitted().forEach(fitted -> fitted.into(into));
    EXTENDED.readInto(extended, into, versionOf(length));
    PAGES.readAll(in, into, Framing.z80Pages(into.machine()), ctx);   // las páginas son bloques: largo u16 (0xffff = cruda), id u8
  }

  static final Chunks<Snapshot> PAGES = new Chunks<Snapshot>().catchAll(new Z80PageChunk());
}
```

`Z80PageChunk` traduce el id de página al banco con una tabla por modelo (48K: 8→5, 4→2, 5→0;
128K: n−3; Scorpion y Pentagon 1024 tienen la suya), y elige `Codec.Z80_RLE` o `Codec.RAW` por el
largo. `FrameCounter` es el contador por cuartos de frame con `WITHOUT_COUNTER = 69664`, que ya
prueba el test de T-states contra libspectrum.

## 7. Cintas: los bloques saben sus flancos, el reproductor no sabe de formatos

Es la separación más valiosa de libspectrum: `libspectrum_tape_get_next_edge` es lo único que
Fuse consume. `Tape.java` hoy hace las dos cosas en 1831 líneas.

```java
public final class Tape { final List<TapeBlock> blocks = new ArrayList<>(); }

public sealed interface TapeBlock
    permits RomBlock, TurboBlock, PureToneBlock, PulsesBlock, PureDataBlock, RawDataBlock,
            GeneralisedDataBlock, PauseBlock, GroupStart, GroupEnd, Jump, LoopStart, LoopEnd, Select,
            Stop48, SetSignalLevel, Comment, Message, ArchiveInfo, HardwareInfo, CustomInfo,
            RleBlock, PulseSequence, DataBlock {
  Edges edges();                                 // los flancos de este bloque, en T-states
  default Flow flow() { return Flow.NEXT; }      // qué hace el reproductor al terminarlo: seguir, saltar, repetir, parar
  default Optional<String> name() { return Optional.empty(); }
}

public record Edge(long tstates, int flags) { }   // BLOCK, STOP, STOP48, LEVEL_LOW, LEVEL_HIGH, NO_EDGE, SHORT, LONG, TAPE

public interface Edges { boolean hasNext();  Edge next(); }

public record StandardTimings(int pilot, int sync1, int sync2, int zero, int one,
                              int pilotHeader, int pilotData, int pauseMs) {
  public static final StandardTimings ROM = new StandardTimings(2168, 667, 735, 855, 1710, 8063, 3223, 1000);
}

public record RomBlock(byte[] data, int pauseMs) implements TapeBlock {
  public Edges edges() { return new PulseTrain(StandardTimings.ROM.withPause(pauseMs), data, 8).edges(); }
}

public record TurboBlock(StandardTimings timings, int bitsInLastByte, byte[] data) implements TapeBlock {
  public Edges edges() { return new PulseTrain(timings, data, bitsInLastByte).edges(); }
}

public record PureDataBlock(int zero, int one, int bitsInLastByte, int pauseMs, byte[] data) implements TapeBlock {
  public Edges edges() { return new PulseTrain(StandardTimings.dataOnly(zero, one, pauseMs), data, bitsInLastByte).edges(); }
}

public record LoopStart(int times) implements TapeBlock {
  public Edges edges() { return Edges.NONE; }
  public Flow flow() { return Flow.loop(times); }
}
```

`PulseTrain` genera piloto, sync, bits y pausa **una vez** para ROM, turbo y datos puros
(libspectrum lo hace en tres funciones que comparten helpers). `GeneralisedDataBlock` trae sus
tablas de símbolos. `RleBlock` y `PulseSequence` son lo que producen CSW y WAV.

Los formatos de cinta son formatos como los otros. TZX es un `Chunks<Tape>` con un codec por
bloque, y cada codec sabe además qué clase de bloque escribe:

```java
public interface TzxBlockCodec<B extends TapeBlock> extends Chunk<Tape> {
  Class<B> writes();                             // para que el escritor despache por la clase del bloque, sin instanceof
}

@Answers("tzx")
public final class TzxFormat implements TapeFormat {
  private final Chunks<Tape> blocks = new Chunks<Tape>()
      .with(new RomBlockCodec())           // 0x10
      .with(new TurboBlockCodec())         // 0x11
      .with(new PureToneCodec())           // 0x12
      .with(new PulsesCodec())             // 0x13
      .with(new PureDataCodec())           // 0x14
      .with(new RawDataCodec())            // 0x15
      .with(new CswRecordingCodec())       // 0x18
      .with(new GeneralisedDataCodec())    // 0x19
      .with(new PauseCodec())              // 0x20
      .with(new GroupStartCodec()).with(new GroupEndCodec())       // 0x21 0x22
      .with(new JumpCodec()).with(new LoopStartCodec()).with(new LoopEndCodec())   // 0x23 0x24 0x25
      .with(new CallSequenceCodec()).with(new ReturnCodec())       // 0x26 0x27
      .with(new SelectCodec()).with(new Stop48Codec()).with(new SetSignalLevelCodec())   // 0x28 0x2A 0x2B
      .with(new CommentCodec()).with(new MessageCodec()).with(new ArchiveInfoCodec())    // 0x30 0x31 0x32
      .with(new HardwareInfoCodec()).with(new CustomInfoCodec()).with(new GlueCodec())   // 0x33 0x35 0x5A
      .unknown(Unknown.SKIP_BY_DECLARED_LENGTH);   // un bloque que no se conoce dice su largo: se saltea

  public Tape read(byte[] image) { … "ZXTape!\u001A", versión; blocks.readAll(in, tape, Framing.TZX, ctx); … }
  public byte[] write(Tape tape)  { … por cada bloque, el codec que escribe su clase … }
}
```

`TapFormat` (cada bloque es un `RomBlock` con pausa de un segundo), `CswFormat`, `PzxFormat` (su
propio `Chunks<Tape>`: PZXT, PULS, DATA, PAUS, BRWS, STOP), `WavFormat` (`javax.sound` →
`PulseSequence`), `WarajevoFormat`, `Z80EmFormat`, y SPC/STA/LTP como variantes de TAP: una
clase cada uno, ninguna con más de una cosa adentro.

Y del lado de la máquina, `TapePlayer` (lo que queda de `Tape.java`) consume `Edges`: programa el
próximo flanco, invierte el EAR, obedece `STOP`, `STOP48` y `LEVEL_*`, y sigue los `Flow`. No
importa ni sabe de dónde salió el bloque.

## 8. Grabaciones: RZX

```java
public final class Recording { Creator creator; final List<RecordingBlock> blocks = new ArrayList<>(); }

public sealed interface RecordingBlock permits CreatorBlock, SnapshotBlock, InputFrames, SignatureStart, SignatureEnd { }
public record SnapshotBlock(Snapshot snapshot, boolean automatic) implements RecordingBlock { }    // leído con Formats, no con un formato fijo
public record InputFrames(long tstates, List<Frame> frames) implements RecordingBlock { }
public record Frame(int instructions, Optional<byte[]> inputs) { }                                 // vacío = "repite el anterior"

@Answers("rzx")
public final class RzxFormat implements RecordingFormat {
  private final Chunks<Recording> chunks = new Chunks<Recording>()
      .with(new CreatorChunk())      // 0x10
      .with(new SnapshotChunk())     // 0x30: el snapshot adentro, comprimido o no, o externo
      .with(new InputChunk())        // 0x80: los frames, comprimidos o no
      .with(new SignatureStartChunk()).with(new SignatureEndChunk());   // 0x20 0x21: DSA con java.security
  …
}
```

`Recorder` y `Player` (oozx `media/rzx`) son los dos clientes: hoy sólo existe el segundo.

## 9. Identidad y contenedores

libspectrum reconoce 46 firmas por extensión *y* por contenido, y abre gz, bz2 y zip antes de
mirar. Acá cada formato trae su `Signature`, y el registro pregunta primero por el contenido.

```java
public record Magic(int offset, byte[] bytes)          { public boolean in(byte[] image); }
public record Signature(String label, List<String> extensions, Optional<Magic> magic) { }

public final class Formats {                             // lo que hoy hace SnapshotFactory, para todos los documentos
  public Optional<Format<?>> identify(String name, byte[] image);          // 1) magia, 2) extensión
  public <D> Optional<Format<D>> reading(Class<D> kind, String name, byte[] image);
  public <D> Optional<Format<D>> writing(Class<D> kind, String name);
  public List<Format<?>> all();                                             // lo del build más lo que llegó en un jar
}

public interface Container {                             // Gzip, Bzip2, Zip: desenvuelve y vuelve a identificar
  Optional<Unwrapped> open(String name, byte[] image);
}
public record Unwrapped(String name, byte[] image) { }   // de un zip, el primer archivo que algún formato reconozca
```

## 10. La costura con la máquina: los tres roles de Fuse

En Fuse cada periférico registra `snapshot_enabled`, `snapshot_from` y `snapshot_to`, y
`snapshot.c` no nombra a ninguno. Hoy tenemos sólo el segundo (`RestoredFromASnapshot`) y la CPU,
la RAM y el paginado van por fuera, a mano, en `SnapshotLoader` y `SnapshotSaver`.

```java
@RoleInterface public interface EnabledBySnapshot     { void enabledBy(Snapshot snapshot); }   // "el snapshot dice si estoy": enchufa o desenchufa
@RoleInterface public interface RestoredFromASnapshot { void restore(Snapshot snapshot); }     // el que ya existe, con Snapshot en vez de SpectrumState
@RoleInterface public interface WrittenToASnapshot    { void writeTo(Snapshot snapshot); }

public final class Snapshots {                           // fuse snapshot.c: 182 líneas, y acá tampoco más
  public void load(Snapshot snapshot) {
    each(EnabledBySnapshot.class, part -> part.enabledBy(snapshot));   // lo que el archivo trae, enchufado antes de elegir máquina
    machine.become(snapshot.machine());                               // forSnapshotModel + select, o reset si ya es esa
    keys.releaseAll();
    each(RestoredFromASnapshot.class, part -> part.restore(snapshot)); // la CPU, la RAM, la ULA y el paginado son participantes como cualquier plugin
    memory.remap();
    display.refreshAll();
  }

  public Snapshot save() {
    Snapshot snapshot = new Snapshot(machine.current.model());
    each(WrittenToASnapshot.class, part -> part.writeTo(snapshot));
    return snapshot;
  }
}
```

Y los participantes del núcleo, que reemplazan a `SnapshotLoader` y `SnapshotSaver` enteros:

```java
public final class ProcessorFromASnapshot implements RestoredFromASnapshot, WrittenToASnapshot {
  public void restore(Snapshot s) {
    Registers r = s.part(Registers.class);
    for (Reg reg : Reg.values()) cpu.set(reg, r.get(reg));            // 32 setters copiados a mano, en un bucle
    cpu.interrupts(r.iff1, r.iff2, r.im, r.lastInstructionEi);
    cpu.halted(r.halted);
    clock.tstates(s.part(Ula.class).tstates);
  }
  public void writeTo(Snapshot s) {
    Registers r = s.part(Registers.class);
    for (Reg reg : Reg.values()) r.set(reg, cpu.get(reg));            // y los 26 getters, en el mismo bucle al revés
    …
  }
}

public final class RamFromASnapshot implements RestoredFromASnapshot, WrittenToASnapshot {   // banco por banco, los que vengan
  public void restore(Snapshot s) { Ram ram = s.part(Ram.class); for (int bank = 0; bank < 16; bank++) if (ram.has(bank)) banks.ram(bank).fill(ram.page(bank)); }
  public void writeTo(Snapshot s) { … cada banco que la máquina tiene, y ya no "las tres que ve la CPU" … }
}

public final class PagingFromASnapshot implements RestoredFromASnapshot, WrittenToASnapshot {   // por el puerto, para que pase lo que pasa cuando un juego escribe
  public void restore(Snapshot s) { Paging p = s.part(Paging.class); if (machine.can(MEMORY_PLUS3)) io.out(0x1ffd, p.port1ffd); if (machine.can(MEMORY_128)) io.out(0x7ffd, p.port7ffd); }
  …
}
```

`cpu.set(Reg, int)` es un adaptador sobre `RegistersBase` hecho de una tabla `Reg → setter`,
la misma en las dos direcciones. Con esto, guardar desde un 128K guarda los ocho bancos, y
guardar con un DivIDE guarda el DivIDE, sin que `Snapshots` sepa que existen.

## 11. Un plugin que trae lo suyo: el DivIDE

Lo que en libspectrum es `read_dide_chunk` + `write_dide_chunk` + siete campos en el `struct`, y en
Fuse `divide_enabled_snapshot` + `divide_from_snapshot` + `divide_to_snapshot`, acá vive entero en
`device-divide`, y el núcleo nunca lo nombra:

```java
public final class DivIde implements Part {              // la parte
  boolean active, writeProtected, paged; int control; byte[] eprom; final byte[][] ram = new byte[4][];
}

@Answers("DIDE")
public final class DivideChunk implements SzxChunk {     // el bloque (y DIRP para sus páginas, con un RamPageChunk parametrizado)
  static final Layout<DivIde> BODY = Layout.<DivIde>ofLength(4)
      .flag(0, 0x01, d -> d.writeProtected, (d, on) -> d.writeProtected = on)
      .flag(0, 0x02, d -> d.paged,          (d, on) -> d.paged = on)
      .u8(2, d -> d.control, (d, v) -> d.control = v)
      .u8(3, d -> d.ram.length, (d, v) -> { });
  public ChunkId id() { return ChunkId.of("DIDE"); }
  public void read(Bytes payload, Snapshot into, FileContext ctx) { DivIde d = into.part(DivIde.class); d.active = true; BODY.readInto(payload.slice(4), d, ctx.version()); d.eprom = Codec.ZLIB.unpack(payload.rest(), 0x2000); }
  public void write(Snapshot from, ChunkSink out, FileContext ctx) { from.has(DivIde.class).filter(d -> d.active).ifPresent(d -> out.chunk(id(), …)); }
}

public final class DividePeripheral … implements EnabledBySnapshot, RestoredFromASnapshot, WrittenToASnapshot {
  public void enabledBy(Snapshot s) { plugged(s.has(DivIde.class).map(d -> d.active).orElse(false)); }
  public void restore(Snapshot s)   { s.has(DivIde.class).ifPresent(this::become); }
  public void writeTo(Snapshot s)   { if (plugged) s.part(DivIde.class).from(this); }
}
```

Sin el plugin instalado, `DIDE` es un bloque desconocido: queda en `Unread` y se reescribe igual.
Con el plugin, el snapshot enchufa el DivIDE y lo carga. Igual que en Fuse, pero descubierto en vez
de compilado adentro.

## 12. Cómo fluye una lectura, sin ningún `switch`

1. `Formats.identify(nombre, bytes)`: un `Container` desenvuelve si hace falta; una `Signature`
   reconoce por magia, y si no, por extensión → `SzxFormat`.
2. `SzxFormat.read`: la cabecera con un `Layout`; la máquina con `Codes`; `Chunks.readAll`.
3. `Chunks.readAll`: `Framing.SZX` corta `(id, payload)`; el registro encuentra el `Chunk` por
   id, o aplica la política; el `Chunk` llena su `Part` con su `Layout` y su `Codec`.
4. `Snapshots.load`: `EnabledBySnapshot` enchufa lo que vino; `Machine.become` elige la máquina;
   cada `RestoredFromASnapshot` toma lo suyo.

Escribir es el mismo camino al revés: `WrittenToASnapshot` llena las partes; `Formats.writing`
elige el formato por la extensión pedida; cada `Chunk` emite lo suyo si su parte está; `Unread`
emite lo que nadie entendió. Ninguna capa pregunta `instanceof`, ninguna tiene un `case`.

## 13. La tabla de traducción

| en C | acá |
|---|---|
| `libspectrum_snap`, 240 accessors generados | `Snapshot` + `Part`s: las del núcleo acá, las de periféricos en su plugin |
| `libspectrum_machine`, capabilities, `timings.c` | `MachineModel` con `Capability` y `Timings` en una tabla |
| `libspectrum_identify_file_raw`, 46 firmas | `Signature` en cada formato, `Formats.identify` |
| `libspectrum_uncompress_file`, `zip.c`, `bzip2.c` | `Container`: `Gzip`, `Bzip2`, `Zip` |
| `libspectrum_buffer_*`, `read_word/dword`, `buffer + n > end` | `Bytes`, `Written` |
| `zlib.c`, `uncompress_block` de z80.c, la RLE al escribir | `Codec`: `Zlib`, `Z80Rle`, `Raw` |
| `szx.c`: `read_chunks[]`, 37 `read_*_chunk`, 72 `write_*_chunk` | `Chunks<Snapshot>` + un `SzxChunk` por bloque, con `read` y `write` juntos |
| `z80.c`: header, extended header, v1/v2/v3, tabla de hardware ×2 | `Z80Format`, `Z80Version` (`V1`, `Extended` → `V2`, `V3`), `Z80Hardware` ×1, `FrameCounter` |
| `sna.c`, `sp.c` | `SnaFormat` + `SnaShape` + `StackedPc`; `SpFormat` |
| `creator.c` | `Creator` |
| `tape.c` `get_next_edge`, `tape_block.c` | `TapeBlock.edges()`, `Edge`, `PulseTrain`, `Flow` |
| `tzx_read.c` `switch( id )` + 45 lectores, `tzx_write.c` | `TzxFormat` + un `TzxBlockCodec` por bloque |
| `tap.c`, `csw.c`, `pzx_read.c`, `wav.c`, `warajevo_read.c`, `z80em.c` | un `TapeFormat` cada uno |
| `rzx.c`, `crypto.c` | `RzxFormat`, `Recording`, `Recorder`, `Player`, `java.security` |
| `libspectrum_error` + `libspectrum_print_error` | `FormatException` (`Corrupt`, `Unsupported`, `Invalid`, `NotThisFormat`) + `Diagnostics` |
| fuse `module.c`: `snapshot_enabled` / `from` / `to` | `EnabledBySnapshot`, `RestoredFromASnapshot`, `WrittenToASnapshot` |
| fuse `snapshot.c`: `snapshot_copy_from` / `copy_to` | `Snapshots.load` / `save` |
| fuse `z80_from_snapshot`, `memory_from_snapshot`, `ula_from_snapshot`, `ay_from_snapshot`… | participantes del núcleo: `ProcessorFromASnapshot`, `RamFromASnapshot`, `UlaFromASnapshot`, `PagingFromASnapshot` |
| fuse `machine_select( libspectrum_machine )` | `Machine.become(MachineModel)` |
| fuse `tape.c` `tape_next_edge` | `TapePlayer` |

## 14. Lo declarativo, junto

Todo esto son datos, y se leen como una tabla:

- las firmas de cada formato (`Signature`, `Magic`);
- las 18 máquinas con capacidades y timings (`MachineModel`);
- cómo cada formato nombra una máquina (`Z80Hardware`, `SZX_MACHINES`, `SnaShape.ofLength`,
  `Ram48.ofLength`);
- los cinco layouts de registros (`RegisterLayout`) y los layouts de cada bloque y header;
- los códigos (`Codes`): modos de interrupción por formato, joysticks por formato, tipos de
  hardware de una cinta, tipos de disco;
- los registros de bloques (`Chunks`) de SZX, TZX, PZX, RZX y páginas Z80;
- el orden de páginas de un 48K (`Ram48`) y el mapa página → banco por modelo (`Z80PageChunk`);
- los timings de un bloque de cinta (`StandardTimings.ROM`).

Lo que **no** es una tabla y queda en código con nombre: `StackedPc`, `Creator.swapsAF`,
`FrameCounter`, `Z80Rle`, `PulseTrain`, la elección de versión de un `.z80`, la página repetida de
un SNA de 147487 bytes. Cada una es una clase chica con su test, y ninguna tiene más de una regla.

## 15. Dónde vive cada cosa

```
oozx  machine/media/formats            un módulo sin Swing ni emulador adentro, como libspectrum
        bytes/       Bytes, Written, Codec, Zlib, Z80Rle, Raw, Container, Gzip, Bzip2, Zip
        layout/      Layout, Field*, Codes, Reg, RegisterLayout, Version
        chunks/      ChunkId, Chunk, Chunks, ChunkSink, Framing*, Framed, FileContext, Diagnostics, Unknown
        machines/    MachineModel, Capability, Timings
        snapshot/    Snapshot, Part, Registers, Ula, Ram, Ram48, Paging, Ay, Joysticks, UlaPlus, Multiface,
                     InterfaceOne, InterfaceTwo, Creator, Unread, SnapshotFormat
        snapshot/z80 Z80Format, Z80Version, V1, V2, V3, Z80Hardware, Z80PageChunk, FrameCounter
        tape/        Tape, TapeBlock y sus 24 records, Edge, Edges, Flow, PulseTrain, StandardTimings, TapeFormat
        tape/tzx     TzxFormat, TzxBlockCodec y sus codecs      tape/tap, csw, pzx, wav, warajevo, z80em
        rzx/         Recording, RecordingBlock*, RzxFormat
        identify/    Signature, Magic, Formats
        errors/      FormatException, Corrupt, Unsupported, Invalid, NotThisFormat

oozx  machine/media/snapshot           Snapshots, los tres roles, los participantes del núcleo
oozx  machine/media/rzx                Recorder, Player

plugins  device-snapshots              SnaFormat, SnaShape, StackedPc, SpFormat, SzxFormat, SzxChunk y los bloques de la máquina base
plugins  device-tape                   TapePlayer, TapeDeck (la ventana)
plugins  device-divide, device-beta128, device-plusd, …   su Part, su SzxChunk, su participante
```

El Z80 queda en oozx porque es el formato con el que el emulador se guarda a sí mismo; SNA, SP y
SZX siguen en su plugin.

## 16. Lo que desaparece

| hoy | acá |
|---|---|
| 16 bucles de "leer exactamente N", 57 `throw FILE_READ_ERROR` | `Bytes.need` |
| 5 bucles de `InflaterInputStream`, 9 largos escritos byte a byte | `Codec.ZLIB`, `Written.u32` |
| la tabla de hardware del `.z80` dos veces; la de SZX dos veces (ida y vuelta) | `Z80Hardware` ×1, `Codes` ×1 |
| cada header mapeado dos veces (leer y escribir) | un `Layout` |
| el orden 5, 2, 0 en ocho lugares | `Ram48` |
| `setZ80State` (32 líneas) y `extractZ80State` (26) | un bucle sobre `Reg` |
| el `SpectrumState` armado dos veces en `SnapshotSaver` | `Snapshots.save` |
| 4 lectores con campos de instancia y `fresh()` | formatos sin estado |
| 72 `write_*_chunk` de libspectrum con el largo a mano | `ChunkSink` + `Framing` |
| 94 `print_error` de `szx.c` | `Diagnostics` y excepciones con causa |
| formato y reproducción juntos en `Tape.java` | `TapeBlock.edges()` y `TapePlayer` |

## 17. El costo, y cómo se prueba

**Costo.** Leer un SZX de 128K crea unos cuarenta objetos chicos y unas lambdas; inflar sus
páginas cuesta mil veces más. La cinta produce millones de `Edge`: es un `record` de dos campos
primitivos, y si alguna vez pesara, `Edges` puede exponer un cursor mutable sin que nadie arriba
cambie. Son unas 150 a 200 clases, la mayoría de menos de 40 líneas.

**Tests, por capa.** `Layout` y `Codes` se prueban una vez, genéricamente (toda tabla hace ida y
vuelta). Cada `Chunk` con un `Bytes` y un `Snapshot` vacío, con el archivo de un solo bloque del
corpus de libspectrum (`szx-chunks/`). Cada formato con el oráculo: lo que libspectrum lee, campo
por campo, y lo que lee de vuelta de lo que escribimos. Cada `TapeBlock` contra
`libspectrum_tape_get_next_edge` sobre los 15 TZX. Cada participante con un `Snapshot` armado a
mano y una máquina de test. Y los 362 tests de libspectrum, portados a JUnit, de semilla.
