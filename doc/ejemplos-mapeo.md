# Ejemplos: el doble despacho sobre la máquina, y el mapeo a cada formato

Cómo quedaría el código, de punta a punta, para dos snapshots (SNA y SZX) y para cinta (TAP y
TZX), con el diseño de `diseno-desde-cero.md`. Todo es Java 18 (records y `sealed`; el despacho
es siempre polimórfico). Los nombres de la máquina son los que hay hoy en oozx: `Cpu`,
`SpectrumMemory`, `IO`, `Machine`, `KeyMatrix`, `PeripheralRegistry`.

Hay tres tramos, y cada uno es independiente de los otros dos:

```
 máquina viva ──(doble despacho: cada parte entrega/recibe su pieza)──▶ Snapshot ◀──(mapeo por formato)──▶ bytes
```

## 1. La máquina se identifica sola: el doble despacho

Cada parte de la máquina —del núcleo o de un plugin— declara **qué pieza entrega y recibe**.
`Snapshots` recorre y no nombra a nadie.

```java
@RoleInterface public interface Captures<P extends Piece>  { Class<P> piece();  Optional<P> capture(); }
@RoleInterface public interface Restores<P extends Piece>  { Class<P> piece();  void restore(P piece); }
@RoleInterface public interface PluggedBy<P extends Piece> { Class<P> piece();  void plugged(Optional<P> piece); }

@Singleton
public final class Snapshots {
  private final Machine machine;  private final KeyMatrix keys;  private final PeripheralRegistry parts;

  public Snapshot save() {
    Snapshot.Builder photo = Snapshot.of(machine.current.model());
    parts.all(Captures.class).forEach(part -> part.capture().ifPresent(photo::piece));
    return photo.build();
  }

  public void load(Snapshot photo) {
    parts.all(PluggedBy.class).forEach(part -> part.plugged(photo.piece(part.piece())));   // lo que el archivo trae, enchufado antes
    machine.become(photo.model());                                                        // forSnapshotModel + select, o reset
    keys.releaseAll();
    parts.all(Restores.class).forEach(part -> photo.piece(part.piece()).ifPresent(part::restore));
    machine.memoryMap().rebuild();
  }
}
```

Las partes del núcleo. La CPU habla el catálogo (`Cpu.get(Reg)` es un adaptador sobre
`RegistersBase`, una tabla escrita una vez), así que su parte es un bucle:

```java
public final class ProcessorPart implements Captures<Processor>, Restores<Processor> {
  private final Cpu cpu;  private final Clock clock;

  public Class<Processor> piece() { return Processor.class; }

  public Optional<Processor> capture() {
    Processor.Builder p = Processor.builder();
    for (Reg reg : Reg.values()) p.set(reg, cpu.get(reg));
    return Optional.of(p.interrupts(cpu.iff1(), cpu.iff2(), cpu.im(), cpu.eiPending()).halted(cpu.halted()).build());
  }

  public void restore(Processor p) {
    for (Reg reg : Reg.values()) cpu.set(reg, reg.of(p));
    cpu.interrupts(p.interrupts());
    cpu.halted(p.halted());
  }
}

public final class MemoryPart implements Captures<Memory>, Restores<Memory> {
  private final SpectrumMemory banks;  private final Machine machine;

  public Optional<Memory> capture() {
    Memory.Builder m = Memory.builder();
    for (PageNumber page : MemoryMap.of(machine.current.model()).pages())    // los bancos que ESTA máquina tiene
      m.page(page, banks.page(page).copy());
    return Optional.of(m.build());
  }
  public void restore(Memory m) { m.pages().forEach((page, data) -> banks.page(page).fill(data)); }
}

public final class PagingPart implements Captures<Paging>, Restores<Paging> {
  private final IO io;  private final Machine machine;
  public Optional<Paging> capture() { return Optional.of(new Paging(io.lastOut(0x7ffd), io.lastOut(0x1ffd), 0, 0)); }
  public void restore(Paging p) {                                               // por el puerto: pasa lo que pasa cuando un juego escribe
    if (machine.current.can(MEMORY_PLUS3)) io.out(0x1ffd, p.port1ffd());
    if (machine.current.can(MEMORY_128))   io.out(0x7ffd, p.port7ffd());
  }
}

public final class UlaPart implements Captures<Ula>, Restores<Ula> { … border, tstates, issue2 … }
```

Y una parte de un plugin, el AY, tal como lo haría hoy `AyFromASnapshot`, pero en las dos
direcciones y con el enchufe:

```java
public final class AyPart implements Captures<AySound>, Restores<AySound>, PluggedBy<AySound> {
  private final AyPeripheral ay;  private final IO io;

  public Class<AySound> piece() { return AySound.class; }
  public void plugged(Optional<AySound> piece) { ay.fitted(piece.isPresent() || machine.current.can(AY)); }
  public Optional<AySound> capture() { return ay.fitted() ? Optional.of(new AySound(ay.selected(), ay.registers(), ay.flavour())) : Optional.empty(); }
  public void restore(AySound s) {
    for (int r = 0; r < 16; r++) { io.out(0xfffd, r); io.out(0xbffd, s.registers()[r]); }
    io.out(0xfffd, s.selected());
  }
}
```

Cada parte hereda `MachinePartContract`: capturar, restaurar en una máquina limpia, capturar de
nuevo: igual. Con eso, el tramo máquina ↔ foto está probado sin ningún formato.

## 2. La foto: el modelo que todos los formatos comparten

```java
public record Snapshot(MachineModel model, Pieces pieces, Provenance provenance) { … }

public record Processor(Registers main, Registers alternate, int ix, int iy, int sp, int pc, int i, int r, int memptr,
                        Interrupts interrupts, boolean halted, boolean flagQ) implements Piece {
  public static final Property<Boolean> IFF1, IFF2, HALTED, EI_PENDING, FLAG_Q;
  public static final Property<IntMode> IM;
}
public enum Reg implements Property<Integer> { A(8), F(8), B(8), C(8), D(8), E(8), H(8), L(8), A_(8), F_(8), B_(8), C_(8), D_(8), E_(8), H_(8), L_(8),
                                               IX(16), IY(16), SP(16), PC(16), I(8), R(8), MEMPTR(16); … }
public record Memory(Map<PageNumber, Data> pages) implements Piece { public static Property<Data> PAGE(PageNumber n); }
public record Paging(int port7ffd, int port1ffd, int portEff7, int timexPort) implements Piece {
  public static final Property<Integer> PORT_7FFD, PORT_1FFD;
  public PageNumber bankAtTop() { return new PageNumber(port7ffd & 0x07); }
}
public record Ula(Border border, TStates tstates, boolean issue2, boolean lateTimings) implements Piece { public static final Property<Border> BORDER; … }
public record AySound(int selected, int[] registers, AyFlavour flavour) implements Piece { … }
```

## 3. SNA, entero: leer y escribir desde una sola tabla

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
  public boolean writes() { return true; }

  public Read<Snapshot> read(Image image) {
    SnaShape shape = SnaShape.ofLength(image.length());              // 49179 → FortyEight; 131103 y 147487 → OneTwentyEight
    Cursor in = Cursor.over(image);
    Snapshot.Builder photo = Snapshot.of(shape.machine());
    Context ctx = Context.reading(this, image);
    HEADER.readInto(in.slice(27), photo, ctx);
    shape.pages().readInto(in, photo, ctx);
    if (in.left() != 0) throw new Corrupt(in.left() + " bytes de más");
    return Read.of(photo.build(), ctx);
  }

  public Image write(Snapshot photo) {
    SnaShape shape = SnaShape.of(photo.model());                     // +2A/+3 → Unsupported; 16K → Unsupported
    Sink out = new Sink();
    Context ctx = Context.writing(this, photo);
    HEADER.writeFrom(photo, out, ctx);
    shape.pages().writeFrom(photo, out, ctx);
    return Image.written(identity(), out.toData());
  }
}

sealed interface SnaShape permits FortyEight, OneTwentyEight {
  static SnaShape ofLength(int length) { … };  static SnaShape of(MachineModel m) { … };
  MachineModel machine();
  Pages pages();
}
final class FortyEight implements SnaShape {
  public Pages pages() { return Pages.contiguous(5, 2, 0).then(Pages.encoded(PC, new OnTheStack())); }   // el PC vive en la pila
}
final class OneTwentyEight implements SnaShape {
  static final Layout TAIL = Layout.of(4).at(0, PC).at(2, Paging.PORT_7FFD).expect(3, 0x00, "TR-DOS paginado no se soporta");
  public Pages pages() { return Pages.contiguous(5, 2).then(Pages.bankAtTop(TAIL)).then(Pages.remaining()); }
}
```

`OnTheStack` es la única regla propia del SNA:

```java
final class OnTheStack implements Encoding<Integer> {          // al leer: desapila; al escribir: apila
  public void read(Snapshot.Builder into, Context ctx) {
    int sp = into.get(SP);
    if (sp < 0x4000 || sp == 0xffff) throw new Corrupt("SP fuera de la RAM: no hay PC que desapilar");
    into.set(PC, into.memory().word(sp)).set(SP, sp + 2);
  }
  public void write(Snapshot from, Snapshot.Builder staged, Context ctx) {
    if (from.get(SP) < 0x4002) throw new Invalid("no hay lugar en la pila para el PC");
    staged.memory().word(from.get(SP) - 2, from.get(PC)).set(SP, from.get(SP) - 2);
  }
}
```

Eso es todo el SNA: 27 colocaciones, dos formas, una codificación. Leer y escribir salen de las
mismas tablas.

### Lo mismo como archivo de configuración, para comparar

La tabla se podría escribir así, y un cargador la convertiría en el mismo `Layout`:

```yaml
format: sna
identity: { extensions: [sna], sizes: [49179, 131103, 147487] }
header:
  - { at: 0, property: I }
  - { at: 1, property: L_ }
  # …
  - { at: 19, bit: 2, property: IFF2 }
  - { same-as: IFF1, from: IFF2 }
  - { at: 25, code: IM, table: standard-im, mask: 0x03 }
  - { at: 26, property: ULA.BORDER }
shapes:
  48k:  { sizes: [49179],          pages: [contiguous: [5, 2, 0], encoded: { PC: on-the-stack }] }
  128k: { sizes: [131103, 147487], pages: [contiguous: [5, 2], bank-at-top: tail, remaining] }
```

No lo recomiendo: es la misma tabla sin el compilador. En Java, `at(19, Reg.IFF2)` no compila
porque `IFF2` no es un `Reg`; en YAML hay que esperar al test. Lo que sí vale del YAML es la
idea: **el formato es datos**, y así se lee la clase.

## 4. SZX: bloques, y cada bloque es una tabla más una contribución

```java
@Answers("szx")
public final class SzxFormat implements SnapshotFormat {
  private final Tagged<Snapshot> blocks;

  public SzxFormat(Iterable<SzxBlock> arrived) {                     // lo que trae cada plugin (DIDE, B128, PLSD…)
    blocks = new Tagged<Snapshot>()
        .with(new CreatorBlock()).with(new RegistersBlock()).with(new SpectrumRegistersBlock())
        .with(new AyBlock()).with(new RamPageBlock()).with(new KeyboardBlock()).with(new PaletteBlock())
        .withAll(arrived)
        .unknown(Unknown.KEEP);                                      // lo que nadie entiende viaja en Unread
  }

  static final Layout HEAD = Layout.of(8).magic(0, "ZXST").version(4, 5).code(6, 0, 0xff, MACHINE, SZX_MACHINES).bit(7, 0, Ula.LATE_TIMINGS);

  public Read<Snapshot> read(Image image) {
    Cursor in = Cursor.over(image);
    Context ctx = Context.reading(this, image);
    Snapshot.Builder photo = Snapshot.builder();
    HEAD.readInto(in.slice(8), photo, ctx);
    blocks.readAll(in, photo, Framing.SZX, ctx);
    return Read.of(photo.build(), ctx);
  }
  public Image write(Snapshot photo) { Sink out = new Sink(); Context ctx = Context.writing(this, photo); HEAD.writeFrom(photo, out, ctx); blocks.writeAll(photo, out, Framing.SZX, ctx); return Image.written(identity(), out.toData()); }
}

@RoleInterface public interface SzxBlock extends BlockFormat<Snapshot> { }
```

Tres bloques, para ver los tres casos: un layout puro, uno con codificación, uno que emite varios.

```java
public final class AyBlock implements SzxBlock {                         // un layout y nada más
  static final Layout BODY = Layout.of(18)
      .code(0, 0, 0x03, AySound.FLAVOUR, Codes.of(AyFlavour.class).bit(0x01, FULLER).bit(0x02, ON_BOARD).otherwise(ADD_ON))
      .at(1, AySound.SELECTED)
      .bytes(2, 16, AySound.REGISTERS);
  public Tag tag() { return Tag.of("AY"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) { return BODY.contribution(payload, ctx); }
  public void write(Snapshot photo, BlockSink out, Context ctx) { if (photo.has(AySound.class)) out.block(tag(), BODY.bytesFrom(photo, ctx)); }
}

public final class RegistersBlock implements SzxBlock {                  // con una codificación que depende del creador
  static final Layout BODY = Layout.of(37)
      .at(0, F).at(1, A).at(2, C).at(3, B).at(4, E).at(5, D).at(6, L).at(7, H)
      .at(8, F_).at(9, A_).at(10, C_).at(11, B_).at(12, E_).at(13, D_).at(14, L_).at(15, H_)
      .at(16, IX).at(18, IY).at(20, SP).at(22, PC).at(24, I).at(25, R)
      .bit(26, 0, IFF1).bit(27, 0, IFF2).code(28, 0, 0x03, IM, STANDARD_IM)
      .at(29, Ula.TSTATES)
      .bit(34, 0, EI_PENDING).bit(34, 1, HALTED).bit(34, 2, FLAG_Q).since(Version.of(1, 5))
      .at(35, MEMPTR).since(Version.of(1, 4))
      .encoded(A, new SwappedAF());                                      // libspectrum < 0.5.0 escribía A y F al revés
  public Tag tag() { return Tag.of("Z80R"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) { return BODY.contribution(payload, ctx); }
  public void write(Snapshot photo, BlockSink out, Context ctx) { out.block(tag(), BODY.bytesFrom(photo, ctx)); }
}

public final class RamPageBlock implements SzxBlock {                    // uno por página, y un codec por bandera
  public Tag tag() { return Tag.of("RAMP"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) {
    Codec codec = (payload.u16() & 1) != 0 ? Codec.ZLIB : Codec.RAW;
    PageNumber page = new PageNumber(payload.u8());
    return Contribution.set(Memory.PAGE(page), codec.decode(payload.rest(), 0x4000));
  }
  public void write(Snapshot photo, BlockSink out, Context ctx) {
    for (PageNumber page : MemoryMap.of(photo.model()).pages())
      photo.memory().page(page).ifPresent(data -> out.block(tag(), new Sink().u16(1).u8(page.number()).bytes(Codec.ZLIB.encode(data)).toData()));
  }
}
```

Un bloque de un plugin es idéntico y vive en el plugin:

```java
@Answers("DIDE")                                                         // en device-divide
public final class DivideBlock implements SzxBlock {
  static final Layout BODY = Layout.of(4).bit(0, 0, DivIde.WRITE_PROTECTED).bit(0, 1, DivIde.PAGED).at(2, DivIde.CONTROL).at(3, DivIde.RAM_PAGES);
  public Tag tag() { return Tag.of("DIDE"); }
  public Contribution<Snapshot> read(Cursor payload, Context ctx) { return BODY.contribution(payload.slice(4), ctx).and(Contribution.set(DivIde.EPROM, Codec.ZLIB.decode(payload.rest(), 0x2000))); }
  public void write(Snapshot photo, BlockSink out, Context ctx) { photo.piece(DivIde.class).ifPresent(d -> out.block(tag(), …)); }
}
public final class DividePart implements Captures<DivIde>, Restores<DivIde>, PluggedBy<DivIde> { … }   // el mismo doble despacho
```

## 5. Cinta: el modelo es una señal, y el deck sólo escucha

```java
public record Tape(List<TapeBlock> blocks, Provenance provenance) implements Document { }

public interface TapeBlock {
  Signal signal();                                                       // lo que suena
  default Control control() { return Control.NEXT; }                    // seguir, saltar, repetir, parar
  default Info info() { return Info.NONE; }
}

public sealed interface Signal permits Tone, Pulses, Bits, Pause, Level, Sequence {
  <R> R accept(SignalVisitor<R> v);
  static Signal standard(Data data, Milliseconds pause) {                // la carga de la ROM: una vez para TAP y TZX 0x10
    int pilot = (data.at(0) & 0x80) == 0 ? 8063 : 3223;
    return new Sequence(List.of(new Tone(pilot, TStates.of(2168)), new Pulses(List.of(TStates.of(667), TStates.of(735))),
                                new Bits(data, 8, TStates.of(855), TStates.of(1710)), new Pause(pause)));
  }
}

public record StandardData(Data data, Milliseconds pause) implements TapeBlock {
  public Signal signal() { return Signal.standard(data, pause); }
  public Info info() { return Info.ofHeader(data); }                     // "Program: MANIC" si es un header
}
public record TurboData(PulseTimings timings, Data data, int bitsInLastByte, Milliseconds pause) implements TapeBlock {
  public Signal signal() { return timings.signal(data, bitsInLastByte, pause); }
}
public record PauseOrStop(Milliseconds ms) implements TapeBlock {
  public Signal signal() { return ms.isZero() ? Signal.NONE : new Pause(ms); }
  public Control control() { return ms.isZero() ? Control.STOP : Control.NEXT; }
}
```

El deck, del lado de la máquina, es un `Ear` y una parte:

```java
public final class TapeDeck implements Ear, Captures<DeckState>, Restores<DeckState> {
  private Tape tape;  private int block;  private boolean playing;

  public void insert(Tape t) { tape = t; block = 0; }
  public void play()         { playing = true; tape.blocks().get(block).signal().accept(new SignalPlayer(this)); }   // el visitor emite flancos acá
  public void edge(TStates after)   { scheduler.at(clock.now().plus(after), () -> ear.toggle()); }
  public void level(boolean high)   { ear.set(high); }
  public void silence(TStates t)    { scheduler.at(clock.now().plus(t), this::nextBlock); }

  public Optional<DeckState> capture() { return tape == null ? Optional.empty() : Optional.of(new DeckState(tape, block, playing)); }   // el bloque TAPE de SZX
  public void restore(DeckState s)    { insert(s.tape()); block = s.block(); }
}
```

### TAP, entero

```java
@Answers("tap")
public final class TapFormat implements TapeFormat {
  public Identity identity() { return Identity.named("TAP tape").extension("tap").magic(0, new byte[]{0x13, 0x00, 0x00}); }
  public boolean writes() { return true; }

  public Read<Tape> read(Image image) {
    Cursor in = Cursor.over(image);
    Tape.Builder tape = Tape.builder();
    while (in.left() > 0) tape.add(new StandardData(in.take(in.u16()), Milliseconds.of(1000)));
    return Read.of(tape.build(), Context.reading(this, image));
  }

  public Image write(Tape tape) {
    Sink out = new Sink();
    for (TapeBlock block : tape.blocks()) {
      Data data = ((StandardData) block).data();                          // un TAP sólo puede llevar bloques estándar; otro → Unsupported
      out.u16(data.length()).bytes(data);
    }
    return Image.written(identity(), out.toData());
  }
}
```

### TZX: un bloque por tipo, y cada uno es una tabla sobre las propiedades del bloque

```java
@Answers("tzx")
public final class TzxFormat implements TapeFormat {
  private final Tagged<Tape> blocks = new Tagged<Tape>()
      .with(new StandardDataBlockFormat())     // 0x10
      .with(new TurboDataBlockFormat())        // 0x11
      .with(new PauseBlockFormat())            // 0x20
      … // 24 en total, uno por tipo de bloque
      .unknown(Unknown.SKIP_BY_DECLARED_LENGTH);

  static final Layout HEAD = Layout.of(10).magic(0, "ZXTape!\u001A").version(8, 9);

  public Read<Tape> read(Image image) { … HEAD; blocks.readAll(in, tape, Framing.TZX, ctx) … }
  public Image write(Tape tape)      { … HEAD; blocks.writeAll(tape, out, Framing.TZX, ctx) … }   // por cada bloque, el formato que escribe su clase
}

public final class StandardDataBlockFormat implements TzxBlock {         // 0x10
  static final Layout BODY = Layout.of(StandardData.class).at(0, StandardData.PAUSE).u16(2, StandardData.LENGTH).bytes(4, StandardData.LENGTH, StandardData.DATA);
  public Tag tag() { return Tag.of(0x10); }
  public Class<StandardData> writes() { return StandardData.class; }
  public Contribution<Tape> read(Cursor payload, Context ctx) { return Contribution.block(BODY.build(payload, ctx)); }
  public void write(TapeBlock block, BlockSink out, Context ctx) { out.block(tag(), BODY.bytesFrom(block, ctx)); }
}

public final class TurboDataBlockFormat implements TzxBlock {            // 0x11
  static final Layout BODY = Layout.of(TurboData.class)
      .at(0, PulseTimings.PILOT).at(2, PulseTimings.SYNC_1).at(4, PulseTimings.SYNC_2).at(6, PulseTimings.ZERO).at(8, PulseTimings.ONE)
      .at(10, PulseTimings.PILOT_PULSES).at(12, TurboData.BITS_IN_LAST_BYTE).at(13, TurboData.PAUSE)
      .u24(15, TurboData.LENGTH).bytes(18, TurboData.LENGTH, TurboData.DATA);
  public Tag tag() { return Tag.of(0x11); }
  public Class<TurboData> writes() { return TurboData.class; }
  …
}

public final class PauseBlockFormat implements TzxBlock {                // 0x20
  static final Layout BODY = Layout.of(PauseOrStop.class).at(0, PauseOrStop.MS);
  …
}
```

Un TAP y el bloque 0x10 de un TZX producen el mismo `StandardData` con la misma señal; escribir un
TAP a partir de un TZX es trivial, y al revés también. CSW y WAV producen `Pulses` medidos, y el
deck no distingue.

## 6. Lo que cuesta agregar cosas, medido en este molde

| qué | qué se escribe |
|---|---|
| un snapshot de layout fijo (SNP, SP, PLUSD) | una clase con `identity()` y un `Layout` de 20–30 colocaciones; hereda `SnapshotFormatContract` |
| un bloque de SZX para un periférico | una clase de ~10 líneas en el plugin: `Tag` + `Layout` sobre las propiedades de su pieza |
| un periférico que quiere viajar en el snapshot | su record con catálogo, su bloque, su parte con `Captures`/`Restores`/`PluggedBy`; hereda `MachinePartContract` |
| un tipo de bloque de TZX | una clase de ~10 líneas: `Tag` + `Layout` sobre el record del bloque + la clase que escribe |
| un formato de cinta nuevo (PZX) | un `Tagged<Tape>` con sus bloques; los bloques producen los mismos records |

Todo lo que no está en estas tablas —qué páginas tiene una máquina, cuál está arriba, cómo suena
un bloque estándar, cuántos registros tiene el AY— está en los conceptos, una vez.
