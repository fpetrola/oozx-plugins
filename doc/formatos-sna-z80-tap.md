# SNA, Z80 y TAP escritos sobre los visitors

Cómo quedarían tres formatos si el mecanismo ya existiera: la recorrida de la máquina (cada parte
entrega su pieza), el catálogo de propiedades de cada concepto, y las secciones con sus lugares.

El código es ilustrativo: el mecanismo no existe todavía y esto no compila. Pero cada byte está
tomado de libspectrum (`sna.c`, `z80.c`, `tap.c`) y de los lectores de hoy, así que lo que dice
cada formato es exacto.

Lo que se quiere comprobar: **un formato es una declaración más un par de reglas con nombre, y
leer y escribir salen de la misma declaración.**

```java
Read<Snapshot> read = new Z80Format().read(image);        // la máquina pelada, de valores, y las notas de lo que se salteó
snapshots.load(read.document());                           // entera y válida: se aplica en el orden de la máquina
Image saved     = new SnaFormat().write(machine.live());   // la recorrida de la máquina viva, en el hilo de la emulación, entre frames
Image converted = new SnaFormat().write(read.document());  // un .z80 pasado a .sna, sin máquina de por medio
```

## 1. Lo que se supone hecho

```java
// ── la recorrida ──────────────────────────────────────────────────────────────────────────────
public interface Walkable     { void walk(PieceVisitor visitor); }   // la máquina viva y el Snapshot
public interface PieceVisitor { void visit(Piece piece); default void end() { } }

// ── los conceptos y sus propiedades ───────────────────────────────────────────────────────────
Reg.A … Reg.L, Reg.A_ … Reg.L_, Reg.IX, Reg.IY, Reg.SP, Reg.PC, Reg.I, Reg.R
Reg.AF, Reg.BC, Reg.DE, Reg.HL y los alternativos (pares de 16 bits, alto·bajo, hechos de los de 8)
Processor.IFF1, Processor.IFF2, Processor.IM
Ula.BORDER, Ula.TSTATES, Ula.ISSUE_2
Paging.PORT_7FFD, Paging.PORT_1FFD, Paging.TIMEX_HSR, Paging.TIMEX_DEC
Memory.page(n), y MemoryMap.of(máquina): pages(), isPaged(), has(n)
AySound.SELECTED, AySound.REGISTERS, AySound.FLAVOUR (ON_BOARD, MELODIK, FULLER)
Joysticks.FIRST, InterfaceOne.ROM_PAGED
MachineModel: las 18, cada una con can(Capability) y timings()

// ── lo que declara un formato de snapshot ─────────────────────────────────────────────────────
public abstract class SnapshotFormat implements Format<Snapshot> {
  protected abstract Identity identity();
  protected abstract FileShape shapeOf(Peek image);              // leyendo: la forma de ESTE archivo, con su máquina
  protected abstract FileShape shapeFor(MachineModel machine);   // escribiendo: la forma en que se guarda esa máquina

  public final Read<Snapshot> read(Image image) { … }            // lo hace el mecanismo, igual para todos
  public final Image write(Walkable machine)     { … }
}
public record FileShape(MachineModel machine, List<Section> sections, List<Rule> rules, Assumed assumed) {
  public static FileShape of(MachineModel machine, Section... sections);
  public FileShape with(Rule... rules);
  public FileShape assuming(Property<?> property, Object value); // lo que vale si ninguna sección lo trae
}
```

**Lo que hace el mecanismo al leer:**
1. `shapeOf` mira lo mínimo (el tamaño, la versión, el hardware) y devuelve la forma, con su máquina.
2. Cada sección, en el orden del archivo, deja sus bytes en sus lugares.
3. Corren las codificaciones y las reglas.
4. La máquina pelada (un `Snapshot` vacío de esa máquina) se arma pieza por pieza. Una pieza está
   si el archivo le dio algún lugar o si una codificación la enchufó; lo que falta lo completa lo
   que la forma asume.

**Al escribir:**
1. `shapeFor` elige la forma.
2. La recorrida: cada pieza visitada deja sus propiedades en los lugares.
3. Corren las reglas y las codificaciones.
4. Cada sección escribe sus bytes.
5. Lo que no tuvo lugar va a las notas ("el SNA no guarda el AY").

**Las secciones, y las dos clases de regla:**

```java
Fixed.of(n)  /  Fixed.span(desde, hasta)     un tramo fijo, con offsets del tramo o del archivo
  .at(off, prop)                   1 o 2 bytes según la propiedad, little-endian
  .bit(off, bit, prop)   .bits(off, shift, mask, prop)   .code(off, shift, mask, prop, codes)
  .flag(off, prop)                 0 es falso y cualquier otra cosa verdadero; se escribe 1
  .text(off, n, prop)   .zeros(off, n)   .constant(off, v)   .reserved(off, n)   .expect(off, v, porqué)
  .derived(off, cálculo)           un byte que se calcula de los otros: una paridad, un largo
  .lengthOfRest(off)               el largo de lo que sigue en el tramo
  .encoded(desde, hasta, encoding) unos bytes con una regla propia
  .when(Capability…) / .unless(…)  el lugar anterior existe sólo en las máquinas que pueden eso
  .upTo(fin)                       el mismo tramo, cortado ahí
Pages.of(5, 2, 0)   Pages.bankAtTop()   Pages.remaining()   el orden de las páginas, dicho con MemoryMap y Paging
Encoded.of(codec, sección)                                   un tramo que viaja codificado
Blocks.of(framing, bloque)                                   bloques con etiqueta

interface Encoding { List<Property<?>> covers(); void read(Bytes tramo, Slots s, Context c); void write(Slots s, Sink tramo, Context c); }
interface Rule     { List<Property<?>> covers(); void afterReading(Slots s, Context c); void beforeWriting(Slots s, Context c); }
```

Una `Encoding` es una regla sobre unos bytes de un mismo tramo; una `Rule` cruza tramos o piezas.

## 2. SNA

```java
/** SNA: el formato más viejo. El PC de un 48K viaja en la pila, y un 128K dice tarde qué banco era el tercero. */
@Answers("sna") @Needs({"device-spectrum128"})
public final class SnaFormat extends SnapshotFormat {

  /** Los 27 bytes que abren todo SNA: el procesador y el borde. */
  static final Fixed HEADER = Fixed.of(27)
      .at(0, I)
      .at(1, HL_).at(3, DE_).at(5, BC_).at(7, AF_)
      .at(9, HL).at(11, DE).at(13, BC).at(15, IY).at(17, IX)
      .bit(19, 2, IFF2).alsoSets(IFF1)                  // guarda una sola bandera; al leer vale para las dos
      .at(20, R).at(21, AF).at(23, SP)
      .code(25, 0, 0x03, IM, Codes.IM)
      .bits(26, 0, 0x07, Ula.BORDER);

  static final FileShape FORTY_EIGHT = FileShape.of(SPECTRUM_48, HEADER, Pages.of(5, 2, 0))
      .with(new PcOnTheStack());

  static final FileShape ONE_TWENTY_EIGHT = FileShape.of(SPECTRUM_128,
      HEADER,
      Pages.of(5, 2),
      Pages.bankAtTop(),                                // el que 7ffd pone en 0xC000; si es el 2 o el 5, es una copia y tiene que ser igual
      Fixed.of(4).at(0, PC).at(2, Paging.PORT_7FFD).expect(3, 0x00, "la ROM de TR-DOS estaba paginada"),
      Pages.remaining());                               // los que faltan, en orden: cinco, o seis si el de arriba era una copia

  protected Identity identity() { return Identity.named("SNA snapshot").extension("sna").sized(49179, 131103, 147487); }

  protected FileShape shapeOf(Peek image) { return image.length() == 49179 ? FORTY_EIGHT : ONE_TWENTY_EIGHT; }

  protected FileShape shapeFor(MachineModel machine) { return MemoryMap.of(machine).isPaged() ? ONE_TWENTY_EIGHT : FORTY_EIGHT; }
}

/** Un SNA de 48K no tiene dónde guardar el PC: lo guarda en la pila, como si una interrupción lo hubiera empujado. */
final class PcOnTheStack implements Rule {
  public List<Property<?>> covers() { return List.of(PC); }

  public void afterReading(Slots s, Context c) {
    int sp = s.get(SP);
    if (sp < 0x4000 || sp == 0xffff) throw new Corrupt("SP inválido (0x%04x): no hay de dónde sacar el PC".formatted(sp));
    s.set(PC, s.memory48k().word(sp));
    s.set(SP, sp + 2);
  }

  public void beforeWriting(Slots s, Context c) {
    int sp = s.get(SP);
    if (sp < 0x4002) throw new Invalid("SP demasiado bajo (0x%04x) para apilar el PC".formatted(sp));
    s.memory48k().word(sp - 2, s.get(PC));             // sobre la copia que va al archivo: la máquina no se toca
    s.set(SP, sp - 2);
  }
}
```

Eso es todo el SNA. Lo demás lo hace el mecanismo a partir de la declaración:
- **Guardar un 128K con AY** deja en las notas: "el SNA no guarda el AY, los T-states, issue 2 ni el joystick".
- **Guardar un +3** va por la forma de 128K: "se guarda como ZX Spectrum 128K; no guarda el puerto 1ffd".
- **Guardar un Pentagon 1024**, además: "no guarda las páginas 8 a 63".

Nadie escribió esas notas: salen de que esas propiedades no tienen lugar en la forma.

## 3. Z80

```java
/** Z80: la cabecera de 30 bytes es de las tres versiones; la 2 y la 3 dicen la máquina y guardan la memoria en páginas. */
@Answers("z80")
public final class Z80Format extends SnapshotFormat {

  /** Los 30 bytes de las tres. El PC va acá sólo en la 1; en la 2 y la 3, un 0 en su lugar avisa que sigue más header. */
  static final Fixed HEADER = Fixed.of(30)
      .at(0, A).at(1, F).at(2, BC).at(4, HL).at(8, SP).at(10, I)
      .encoded(11, 12, new SplitR())
      .bits(12, 1, 0x07, Ula.BORDER)
      .at(13, DE).at(15, BC_).at(17, DE_).at(19, HL_).at(21, A_).at(22, F_).at(23, IY).at(25, IX)
      .flag(27, IFF1).flag(28, IFF2)
      .code(29, 0, 0x03, IM, Codes.IM).bit(29, 2, Ula.ISSUE_2).code(29, 6, 0x03, Joysticks.FIRST, JOYSTICK);

  static final Codes<Joystick> JOYSTICK = Codes.of(Joystick.class).is(0, CURSOR).is(1, KEMPSTON).is(2, SINCLAIR_1).is(3, SINCLAIR_2);

  /** El header extendido, con los offsets del archivo tal como los numera la especificación. Cada versión lo corta donde termina. */
  static final Fixed EXTENDED = Fixed.span(30, 87)
      .lengthOfRest(30)                                              // 23, 54 o 55: la versión
      .at(32, PC)
      .encoded(34, 37, new HardwareByte())                           // qué máquina, qué traía enchufado, "hardware modificado"
      .at(35, Paging.PORT_7FFD).when(MEMORY_128)
      .at(35, Paging.TIMEX_HSR).when(MEMORY_TIMEX)
      .at(36, Paging.TIMEX_DEC).when(VIDEO_TIMEX)
      .code(36, 0, 0xff, InterfaceOne.ROM_PAGED, Codes.FF_MEANS_YES).unless(VIDEO_TIMEX)
      .encoded(37, 37, new AyFitted())                               // un AY en una máquina que no lo trae de placa
      .at(38, AySound.SELECTED).bytes(39, 16, AySound.REGISTERS)
      .encoded(55, 57, new FrameCounter())                           // desde la 3
      .constant(61, 0xff).constant(62, 0xff)                         // "0–8K y 8–16K son ROM"
      .at(86, Paging.PORT_1FFD).when(MEMORY_PLUS3, MEMORY_SCORPION); // sólo en la 3 de 55 bytes

  static final FileShape V1_RAW = FileShape.of(SPECTRUM_48, HEADER.at(6, PC), Pages.of(5, 2, 0))
      .assuming(Ula.TSTATES, FrameCounter.WITHOUT_COUNTER);

  static final FileShape V1_PACKED = FileShape.of(SPECTRUM_48, HEADER.at(6, PC), Encoded.of(Z80Rle.WITH_END_MARKER, Pages.of(5, 2, 0)))
      .assuming(Ula.TSTATES, FrameCounter.WITHOUT_COUNTER);

  protected Identity identity() { return Identity.named("Z80 snapshot").extension("z80"); }

  protected FileShape shapeOf(Peek image) {
    if (image.u16(6) != 0) return (image.u8(12) & 0x20) != 0 ? V1_PACKED : V1_RAW;
    Z80Version version = Z80Version.ofExtendedLength(image.u16(30));
    Z80Hardware hardware = Z80Hardware.read(version, image.u8(34));
    return version.shape(hardware.machine((image.u8(37) & 0x80) != 0));
  }

  protected FileShape shapeFor(MachineModel machine) { return Z80Version.forWriting(machine).shape(machine); }
}
```

### Las versiones

```java
/** La 2 y la 3, y dónde termina el header extendido de cada una. La 1 no tiene: sus dos formas están arriba. */
enum Z80Version {
  V2(55)           { int codeOf(Z80Hardware h) { return h.v2; } },
  V3(86)           { int codeOf(Z80Hardware h) { return h.v3; } },
  V3_WITH_1FFD(87) { int codeOf(Z80Hardware h) { return h.v3; } };

  private final int end;
  Z80Version(int end) { this.end = end; }
  abstract int codeOf(Z80Hardware hardware);                         // la 2 y la 3 numeran distinto las primeras máquinas

  static Z80Version ofExtendedLength(int length) {
    return Arrays.stream(values()).filter(v -> v.end - 32 == length).findFirst()
        .orElseThrow(() -> new Unsupported("un header extendido de %d bytes".formatted(length)));
  }

  /** Se escribe la 3, como libspectrum, y la de 55 bytes sólo si la máquina tiene un segundo puerto de memoria. */
  static Z80Version forWriting(MachineModel machine) { return Z80Hardware.forWriting(machine).writes1ffd() ? V3_WITH_1FFD : V3; }

  FileShape shape(MachineModel machine) {
    return FileShape.of(machine, HEADER.zeros(6, 2), EXTENDED.upTo(end), Blocks.of(Framing.Z80_PAGES, new Z80Page()))
        .assuming(Ula.TSTATES, FrameCounter.WITHOUT_COUNTER);        // la 2 no trae contador; en la 3 lo pone FrameCounter
  }
}
```

### El hardware: una tabla

```java
/** El byte 34: qué máquina era y qué traía enchufado. La 2 y la 3 numeran distinto las primeras filas; desde el 7 coinciden. */
enum Z80Hardware {
  //                     la 2  la 3  máquina        traía        1ffd
  FORTY_EIGHT           (   0,    0, SPECTRUM_48,   NOTHING,     false),
  FORTY_EIGHT_IF1       (   1,    1, SPECTRUM_48,   INTERFACE_1, false),
  FORTY_EIGHT_SAMRAM    (   2,    2, SPECTRUM_48,   SAM_RAM,     false),
  FORTY_EIGHT_MGT       (NONE,    3, SPECTRUM_48,   MGT,         false),
  ONE_TWENTY_EIGHT      (   3,    4, SPECTRUM_128,  NOTHING,     false),
  ONE_TWENTY_EIGHT_IF1  (   4,    5, SPECTRUM_128,  INTERFACE_1, false),
  ONE_TWENTY_EIGHT_MGT  (NONE,    6, SPECTRUM_128,  MGT,         false),
  PLUS_3                (   7,    7, PLUS3,         NOTHING,     true),
  PLUS_3_XZX            (   8,    8, PLUS3,         NOTHING,     true),   // xzx escribió 8 donde iba 7: se lee, nunca se escribe
  PENTAGON              (   9,    9, PENTAGON,      NOTHING,     true),
  SCORPION              (  10,   10, SCORPION,      NOTHING,     true),
  PLUS_2                (  12,   12, PLUS2,         NOTHING,     false),
  PLUS_2A               (  13,   13, PLUS2A,        NOTHING,     true),
  TC_2048               (  14,   14, TC2048,        NOTHING,     false),
  TC_2068               (  15,   15, TC2068,        NOTHING,     false),
  TS_2068               ( 128,  128, TS2068,        NOTHING,     false);

  /** Bit 7 del 37, de Spectaculator: el 48 era un 16, el 128 un +2, el +3 un +2A. */
  private static final Map<MachineModel, MachineModel> MODIFIED = Map.of(SPECTRUM_48, SPECTRUM_16, SPECTRUM_128, PLUS2, PLUS3, PLUS2A);

  /** Las que no tienen número propio se guardan como la más parecida; el 16K, como un 48 modificado. */
  private static final Map<MachineModel, MachineModel> WRITTEN_AS = Map.of(
      SPECTRUM_16, SPECTRUM_48, SPECTRUM_48_NTSC, SPECTRUM_48, SPECTRUM_128E, SPECTRUM_128, SE, SPECTRUM_128,
      PLUS3E, PLUS3, PENTAGON_512, PENTAGON, PENTAGON_1024, PENTAGON);

  final int v2, v3;  private final MachineModel machine;  private final Fitted fitted;  private final boolean port1ffd;

  static Z80Hardware read(Z80Version version, int code) {
    return Arrays.stream(values()).filter(h -> version.codeOf(h) == code).findFirst()
        .orElseThrow(() -> new Unsupported("el hardware %d de un .z80 %s".formatted(code, version)));
  }

  static Z80Hardware forWriting(MachineModel machine) { return forWriting(machine, NOTHING); }

  static Z80Hardware forWriting(MachineModel machine, Fitted fitted) {
    MachineModel as = WRITTEN_AS.getOrDefault(machine, machine);
    return Arrays.stream(values()).filter(h -> h.machine == as && h.fitted == fitted && h != PLUS_3_XZX).findFirst()
        .orElseGet(() -> forWriting(machine));                  // un +2 con Interface 1 no tiene número: va sin ella
  }

  MachineModel machine(boolean modified) { return modified ? MODIFIED.getOrDefault(machine, machine) : machine; }
  boolean modifies(MachineModel written) { return MODIFIED.get(machine) == written; }   // al escribir, sólo el 16K
  boolean writes1ffd() { return port1ffd; }
  Fitted fitted() { return fitted; }
}

/** Lo que un número de hardware dice que estaba enchufado. */
enum Fitted {
  NOTHING     { void plugInto(Slots s, Context c) { } },
  INTERFACE_1 { void plugInto(Slots s, Context c) { s.plug(InterfaceOne.class); } },
  SAM_RAM     { void plugInto(Slots s, Context c) { c.notes().notEmulated("la SamRam"); } },
  MGT         { void plugInto(Slots s, Context c) { c.notes().notCarried("el +D o el DISCiPLE"); } };   // son de su plugin, que todavía no trae sus lugares en el .z80

  abstract void plugInto(Slots s, Context c);
}
```

### Las cuatro codificaciones

```java
/** El byte 34 y el bit 7 del 37. La máquina ya la eligió la forma: esto enchufa lo que el número dice que había, y al escribir elige el número. */
final class HardwareByte implements Encoding {
  public List<Property<?>> covers() { return List.of(); }

  public void read(Bytes h, Slots s, Context c) {
    Z80Hardware.read(Z80Version.ofExtendedLength(h.u16(30)), h.u8(34)).fitted().plugInto(s, c);
  }

  public void write(Slots s, Sink h, Context c) {
    Fitted wanted = s.has(InterfaceOne.class) ? INTERFACE_1 : NOTHING;
    Z80Hardware hardware = Z80Hardware.forWriting(c.machine(), wanted);
    if (hardware.fitted() != wanted) c.notes().notCarried(InterfaceOne.class);
    if (hardware.machine(hardware.modifies(c.machine())) != c.machine()) c.notes().writtenAs(hardware.machine(false));
    h.u8(34, Z80Version.ofExtendedLength(h.u16(30)).codeOf(hardware)).or(37, hardware.modifies(c.machine()) ? 0x80 : 0x00);
  }
}

/** R: los siete bits de abajo en el byte 11, el octavo en el bit 0 del 12. */
final class SplitR implements Encoding {
  public List<Property<?>> covers() { return List.of(R); }
  public void read(Bytes h, Slots s, Context c) { s.set(R, h.u8(11) & 0x7f | (h.u8(12) & 0x01) << 7); }
  public void write(Slots s, Sink h, Context c) { h.u8(11, s.get(R) & 0x7f).or(12, s.get(R) >>> 7 & 0x01); }
}

/** Bits 2 y 6 del 37: un AY en una máquina que no lo trae de placa, la Melodik (0x04) o la Fuller Box (0x44). */
final class AyFitted implements Encoding {
  static final Codes<AyFlavour> ADDED = Codes.of(AyFlavour.class).mask(0x44, FULLER).mask(0x04, MELODIK);   // en ese orden: la Fuller también tiene el bit 2

  public List<Property<?>> covers() { return List.of(AySound.FLAVOUR); }

  public void read(Bytes h, Slots s, Context c) {
    if (c.machine().can(AY)) s.plug(AySound.class, ON_BOARD);
    else ADDED.find(h.u8(37)).ifPresent(flavour -> s.plug(AySound.class, flavour));
  }

  public void write(Slots s, Sink h, Context c) {
    s.value(AySound.FLAVOUR).filter(flavour -> flavour != ON_BOARD).ifPresent(flavour -> h.or(37, ADDED.encode(flavour)));
  }
}

/** Bytes 55 a 57 de la 3: en qué cuarto de frame iba, y cuánto le faltaba a ese cuarto. */
final class FrameCounter implements Encoding {
  static final int WITHOUT_COUNTER = 69664;                          // lo que libspectrum le da a una 1 o una 2

  public List<Property<?>> covers() { return List.of(Ula.TSTATES); }

  public void read(Bytes h, Slots s, Context c) {
    int quarter = c.machine().timings().tstatesPerFrame() / 4;
    int tstates = ((h.u8(57) + 1) % 4 + 1) * quarter - (h.u16(55) + 1);
    s.set(Ula.TSTATES, tstates >= 4 * quarter ? 0 : tstates);
  }

  public void write(Slots s, Sink h, Context c) {
    int quarter = c.machine().timings().tstatesPerFrame() / 4, tstates = s.get(Ula.TSTATES);
    h.u16(55, quarter - tstates % quarter - 1).u8(57, (tstates / quarter + 3) % 4);
  }
}
```

### Las páginas

```java
/** Una página de la 2 o la 3: largo (0xffff: sin comprimir), número, bytes. Qué banco es lo dice la máquina. */
final class Z80Page implements BlockFormat {
  public void read(Framed block, Slots into, Context c) {
    Data page = block.raw() ? block.payload() : Z80Rle.PAGES.decode(block.payload(), 0x4000);
    Z80PageIds.bankOf(c.machine(), block.tag()).ifPresentOrElse(
        bank -> into.set(Memory.page(bank), page),
        () -> c.notes().skipped("la página %d: una ROM, o un banco que esta máquina no tiene".formatted(block.tag())));
  }

  public void write(Slots from, BlockSink out, Context c) {
    for (PageNumber bank : MemoryMap.of(c.machine()).pages())
      from.page(bank).ifPresent(page -> out.block(Z80PageIds.idOf(c.machine(), bank), Z80Rle.PAGES.encode(page)));
  }
}

/** Los números de página del .z80. Sin paginado, 8, 4 y 5 son lo que está en 0x4000, 0x8000 y 0xC000; con paginado, el n es el banco n − 3. */
final class Z80PageIds {
  private static final Map<Integer, Integer> UNPAGED = Map.of(8, 5, 4, 2, 5, 0);

  static Optional<PageNumber> bankOf(MachineModel machine, int id) {
    MemoryMap map = MemoryMap.of(machine);
    Integer bank = map.isPaged() ? Integer.valueOf(id - 3) : UNPAGED.get(id);
    return Optional.ofNullable(bank).filter(b -> b >= 0).map(PageNumber::new).filter(map::has);
  }

  static int idOf(MachineModel machine, PageNumber bank) {
    if (MemoryMap.of(machine).isPaged()) return bank.number() + 3;
    return UNPAGED.entrySet().stream().filter(e -> e.getValue() == bank.number()).findFirst().orElseThrow().getKey();
  }
}

/** La compresión del .z80: ED ED n v son n veces v. Se usa para cinco o más bytes iguales, o para dos o más ED. */
final class Z80Rle implements Codec {
  static final Z80Rle PAGES = new Z80Rle();
  /** La 1 comprime la memoria entera y la cierra con 00 ED ED 00. */
  static final Codec WITH_END_MARKER = PAGES.closedBy(Data.of(0x00, 0xED, 0xED, 0x00));

  public Data decode(Data packed, int length) {
    byte[] out = new byte[length];
    int from = 0, to = 0;
    while (from < packed.length() && to < length) {
      boolean run = packed.u8(from) == 0xED && from + 3 < packed.length() && packed.u8(from + 1) == 0xED;
      if (run) {
        int fits = Math.min(packed.u8(from + 2), length - to);      // una corrida que se pasa del fin de la página se corta: Lazy Jones tiene una
        Arrays.fill(out, to, to + fits, (byte) packed.u8(from + 3));
        to += fits;
        from += 4;
      } else {
        out[to++] = (byte) packed.u8(from++);                       // un ED solo, o el último byte, van tal cual
      }
    }
    if (to != length) throw new Corrupt("la página dio %d bytes y son %d".formatted(to, length));
    return Data.of(out);
  }

  /** Empaquetada si achica; si no, cruda, y el formato la marca con 0xffff. */
  public Coded encode(Data page) {
    Sink out = new Sink();
    for (int at = 0; at < page.length(); ) {
      int value = page.u8(at), run = page.runAt(at, 255);          // cuántos iguales desde acá, hasta 255
      if (value == 0xED && run == 1) {                              // un ED solo va tal cual, y el byte que sigue también
        out.u8(0xED);
        if (++at < page.length()) out.u8(page.u8(at++));
      } else if (run >= 5 || value == 0xED && run >= 2) {
        out.u8(0xED).u8(0xED).u8(run).u8(value);
        at += run;
      } else {
        out.u8(value);
        at++;
      }
    }
    return out.length() < page.length() ? Coded.packed(out.toData()) : Coded.raw(page);
  }
}
```

Z80 es el formato grande, y aun así cada pieza hace una sola cosa:
- una tabla de cabecera, compartida por las tres versiones;
- un header extendido que cada versión corta donde termina;
- una tabla de hardware para leer y escribir, con una columna por versión;
- cuatro codificaciones con nombre;
- las páginas como bloques, con el banco que dice la máquina;
- la compresión en su propia clase.

## 4. TAP

La cinta no pasa por la máquina: su documento es la `Tape`, y el visitor es el de los bloques.
Como los tipos de bloque de cinta son un conjunto cerrado (los define TZX), acá sí va el visitor
clásico, con un método por clase.

### El concepto compartido: el header de la ROM

Los 19 bytes de un header no son de ningún formato: son de la ROM. Los usan TAP, el bloque 0x10
de TZX, el explorador de cintas y el autoloader. Por eso se declaran una sola vez, con el mismo
`Fixed`, sobre las propiedades del header:

```java
/** El header que la ROM graba antes de cada archivo: flag, tipo, nombre, largo, dos parámetros, paridad. */
public record TapeHeader(Kind kind, String name, int length, int param1, int param2) {
  public enum Kind { PROGRAM, NUMBER_ARRAY, CHARACTER_ARRAY, BYTES }

  static final Fixed LAYOUT = Fixed.of(19)
      .constant(0, 0x00)                                            // el flag de un header
      .code(1, 0, 0xff, KIND, Codes.of(Kind.class).is(0, PROGRAM).is(1, NUMBER_ARRAY).is(2, CHARACTER_ARRAY).is(3, BYTES))
      .text(2, 10, NAME)                                            // diez caracteres, rellenos con espacios
      .at(12, LENGTH).at(14, PARAM_1).at(16, PARAM_2)
      .derived(18, Parity.XOR);                                     // el XOR de todo lo anterior

  public static Optional<TapeHeader> in(Data block) { return block.length() == 19 && block.u8(0) == 0x00 ? Optional.of(LAYOUT.read(block)) : Optional.empty(); }
  public Optional<Integer> autostart() { return kind == PROGRAM && param1 < 32768 ? Optional.of(param1) : Optional.empty(); }
}

/** Un bloque como los graba la ROM: suena con sus tiempos, piloto largo si es un header y corto si son datos. */
public record StandardData(Data data, Milliseconds pause) implements TapeBlock {
  public Signal signal() { return Signal.standard(data, pause); }
  public Optional<TapeHeader> header() { return TapeHeader.in(data); }
  public void accept(TapeBlockVisitor v) { v.standardData(this); }
}
```

### El visitor de bloques

Cada método cae, por defecto, en la cara del bloque: lo que suena, lo que dirige o lo que informa.
Así un visitor sólo escribe lo que le importa.

```java
public interface TapeBlockVisitor {
  void sound(TapeBlock block);       // lo que suena y no tiene método propio en este visitor
  void control(TapeBlock block);     // lo que dirige al reproductor
  void info(TapeBlock block);        // lo que sólo cuenta algo

  default void standardData(StandardData b) { sound(b); }   default void turboData(TurboData b)   { sound(b); }
  default void pureData(PureData b)         { sound(b); }   default void pureTone(PureTone b)     { sound(b); }
  default void pulses(Pulses b)             { sound(b); }   default void rawData(RawData b)       { sound(b); }
  default void generalised(GeneralisedData b) { sound(b); } default void rlePulses(RlePulses b)   { sound(b); }
  default void pause(Pause b)               { control(b); } default void jump(Jump b)             { control(b); }
  default void loopStart(LoopStart b)       { control(b); } default void loopEnd(LoopEnd b)       { control(b); }
  default void select(Select b)             { control(b); } default void signalLevel(SetSignalLevel b) { control(b); }
  default void groupStart(GroupStart b)     { control(b); } default void groupEnd(GroupEnd b)     { control(b); }
  default void stop48(Stop48 b)             { control(b); }
  default void comment(Comment b)           { info(b); }    default void message(Message b)       { info(b); }
  default void archiveInfo(ArchiveInfo b)   { info(b); }    default void hardwareInfo(HardwareInfo b) { info(b); }
}
```

### El formato

```java
/** TAP: cada bloque es un largo y los bytes tal como los graba la ROM (flag, datos, paridad), y todos suenan con los tiempos de la ROM. */
@Answers("tap")
public final class TapFormat implements TapeFormat {
  static final Milliseconds PAUSE = Milliseconds.of(1000);                  // entre bloque y bloque, como libspectrum

  public Identity identity() { return Identity.named("TAP tape").extension("tap").magic(0, 0x13, 0x00, 0x00); }   // lo habitual: empieza con un header
  public boolean writes() { return true; }

  public Read<Tape> read(Image image) {
    Cursor in = Cursor.over(image);
    Tape.Builder tape = Tape.builder();
    while (in.left() > 0) tape.add(new StandardData(in.take(in.u16()), PAUSE));
    return Read.of(tape.build());
  }

  public Image write(Tape tape) {
    TapWriter writer = new TapWriter();
    tape.blocks().forEach(block -> block.accept(writer));
    return Image.written(identity(), writer.bytes(), writer.notes());
  }
}

/** Lo que un TAP puede llevar de una cinta: bloques de datos, que va a cargar con los tiempos de la ROM. Lo demás, en las notas. */
final class TapWriter implements TapeBlockVisitor {
  private final Sink out = new Sink();
  private final Notes notes = new Notes();

  public void standardData(StandardData b) { record(b.data()); }
  public void turboData(TurboData b)       { notes.lost(b, "sus tiempos: en un TAP carga con los de la ROM"); record(b.data()); }
  public void pureData(PureData b)         { notes.lost(b, "sus tiempos"); record(b.data()); }

  public void sound(TapeBlock b)           { notes.dropped(b, "sin él, casi seguro que no carga"); }   // tonos, pulsos, grabaciones directas
  public void loopStart(LoopStart b)       { sound(b); }                     // lo que se repetía deja de sonar igual
  public void loopEnd(LoopEnd b)           { sound(b); }
  public void control(TapeBlock b)         { notes.dropped(b, "puede no cargar"); }                    // pausas, saltos, selecciones, nivel
  public void groupStart(GroupStart b)     { }                               // lo que no cambia lo que suena se va sin decir nada
  public void groupEnd(GroupEnd b)         { }
  public void stop48(Stop48 b)             { }
  public void info(TapeBlock b)            { }

  private void record(Data data) { out.u16(data.length()).bytes(data); }
  Data bytes() { return out.toData(); }
  Notes notes() { return notes; }
}
```

Los mensajes y la clasificación son los de `tap.c`:
- lo que suena, y los loops: "casi seguro que no carga";
- pausas, saltos, selecciones y nivel: "puede no cargar";
- grupos, stop48 e información: nada.

La diferencia es que acá la clasificación es la jerarquía del visitor, y no un `switch` de quince casos.

SPC, STA y LTP son TAP con otra regla para el largo y la paridad: una subclase de pocas líneas cada una.

## 5. Los tests de cada uno

Cada formato hereda un contrato de tests y sólo agrega sus fixtures y lo que tiene de propio:

```java
class SnaFormatTest extends SnapshotFormatContract {
  protected SnapshotFormat format() { return new SnaFormat(); }
  protected List<Fixture> fixtures() {
    return List.of(Fixture.of("manicminer.sna").asTheReferenceReadsIt(),
                   Fixture.of("banks.sna").asTheReferenceReadsIt(),                // 128K, un patrón por banco, el 3 arriba
                   Fixture.of("banks-5-on-top.sna").asTheReferenceReadsIt(),       // 147487 bytes: la página repetida
                   Fixture.of("sp-2000.sna").refusedAs(Corrupt.class),             // los dos del corpus de libspectrum
                   Fixture.of("sp-ffff.sna").refusedAs(Corrupt.class));
  }
  @Test void aFortyEightTakesItsPcOffTheStack() { … }
  @Test void aPcCannotBeStackedBelow0x4002() { … }
  @Test void aRepeatedPageThatDiffersIsCorrupt() { … }
  @Test void savingAPlusThreeSaysWhatTheSnaCannotCarry() { … }                    // notas: 1ffd, "se guarda como 128K"
}
```

El contrato ya prueba, para cada fixture:
- que la identidad reconoce el archivo y no reconoce basura;
- que se lee igual que la referencia, propiedad por propiedad;
- que lo que se escribe se vuelve a leer igual;
- que cortado en cualquier byte se rechaza sin reventar;
- que toda propiedad sin lugar termina en las notas.

Lo específico de cada formato:
- **Z80:** `V1_RAW` y `V1_PACKED` (sintetizados), una 2 armada desde Manic Miner (como hace hoy el test de T-states), `manicminer.z80`, `plus3.z80` y `empty.z80` del corpus. Además, `Z80RleTest` con Lazy Jones, un ED suelto como último byte de una página y una página que no achica; `Z80HardwareTest`, que lee cada fila como la lee libspectrum; y `FrameCounterTest`.
- **TAP:** `TapFormatTest` con los TAP del corpus, y `TapWriterTest` con una cinta que tiene un turbo, un loop y un comentario, verificando las tres notas.

## 6. Lo que cambia respecto de hoy

Escribir estos tres contra libspectrum sacó diferencias con los lectores de hoy. Cada una cambia
un golden, y se decide antes de su paso del plan:

1. **SNA de 48K, al leer.** libspectrum desapila el PC y sube SP en 2. Hoy se deja PC en `0x72`
   (el RETN de la ROM) y SP como estaba. Los dos arrancan, pero el archivo significa lo primero.
2. **SNA de 128K con 7ffd apuntando al 2 o al 5.** libspectrum exige que la página repetida sea
   igual (si no, `Corrupt`); hoy se descarta sin mirarla.
3. **SNA de 128K: qué máquina es.** libspectrum lo lee como Pentagon; hoy (y acá) como 128K.
4. **SNA desde un +2A, +3 o Pentagon 1024.** libspectrum lo escribe como 128K, perdiendo cosas;
   acá es igual, y las notas dicen qué se pierde. Hoy el +2A y el +3 se rechazan. **Desde un 16K**,
   hoy salta un `NullPointerException`; acá se guarda como 48K y la nota lo dice.
5. **Z80, al leer el hardware.** SamRam, MGT, el +3 de xzx, Scorpion y los Timex hoy se rechazan;
   acá se leen como en libspectrum. El Pentagon hoy se lee como 128K; acá, como Pentagon.
6. **Z80, al escribir el largo del header extendido.** libspectrum escribe 54 en las máquinas sin
   segundo puerto de memoria; hoy se escribe siempre 55.
7. **Z80, al escribir los bytes 61 y 62** ("0–16K es ROM"). Acá valen `0xff`; hoy se escriben en 0.
8. **Z80, un ED suelto como último byte de una página.** El escritor de hoy lee fuera de la página
   (`readByte(page, 0x4000)`) y revienta con `ArrayIndexOutOfBoundsException`: lo confirmó la red
   del paso 0 con `lone-ed-at-the-end.sna`. Acá ese byte va tal cual y hay un test para el caso.
9. **Z80, T-states de más de un frame.** libspectrum los lleva a 0; hoy quedan como vienen.

## 7. Cuánto queda

| | acá | hoy (Java) | libspectrum |
|---|---|---|---|
| SNA | ~40 líneas: una tabla, dos formas, una regla | 375 | 503 (`sna.c`) |
| Z80 | ~230: dos tablas, las versiones, la tabla de hardware, cuatro codificaciones, las páginas y la compresión | 853 | 1870 (`z80.c`) |
| TAP | ~40 de formato y visitor, más ~20 del header de la ROM, que se comparte | adentro de `Tape.java` (1831 líneas, con la reproducción) | 314 (`tap.c`) |

Lo que no aparece en ninguno de los tres está en el mecanismo o en los conceptos, y se escribe una
sola vez:
- abrir y recorrer bytes;
- las notas;
- qué páginas tiene cada máquina y cuál está arriba;
- cómo suena un bloque de la ROM;
- que una propiedad sin lugar se anote.

Lo que queda en los formatos es lo que de verdad es de cada uno: sus tablas y sus rarezas.
