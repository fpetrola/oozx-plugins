/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package model.tests.formats;

import com.fpetrola.emulation.helpers.machine.Keyboard.JoystickModel;
import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.emulation.helpers.snapshots.AY8912State;
import com.fpetrola.emulation.helpers.snapshots.MemoryState;
import com.fpetrola.emulation.helpers.snapshots.SnapshotZ80;
import com.fpetrola.emulation.helpers.snapshots.SpectrumState;
import com.fpetrola.emulation.helpers.snapshots.Z80State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import z80core.IntMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Makes the snapshots the net needs and nobody had: run by hand, with libspectrum installed, and
 * what it writes is committed. The corpus comes from libspectrum's own tests; the rest is made
 * here, by libspectrum where it can write the format and by hand where nobody does.
 * <pre>
 * mvn -pl devices/snapshots test -Dtest=MakeFixtures -Dsnapshots.makeFixtures=true
 * </pre>
 */
@EnabledIfSystemProperty(named = "snapshots.makeFixtures", matches = "true")
class MakeFixtures {

  private static final Path CORPUS = Path.of(System.getProperty("snapshots.corpus", "../../../oozx/libspectrum/test"));
  private static final Path MANIC_MINER = Path.of("../all/src/test/resources/manicminer.z80");

  @Test
  void make() throws Exception {
    assertTrue(Reference.present(), "the fixtures are made with libspectrum, and it is not here");
    theCorpus();
    manicMiner();
    banks("banks", MachineTypes.SPECTRUM128K, 0x03, 0x00);
    banks("banks-5-on-top", MachineTypes.SPECTRUM128K, 0x05, 0x00);
    banks("plus3-1ffd", MachineTypes.SPECTRUMPLUS3, 0x06, 0x04);
    sixteen();
    aLoneEdAtTheEndOfAPage();
  }

  /** libspectrum's own snapshots, the gzipped ones unpacked: they are here to be read, not to test gzip. */
  private void theCorpus() throws IOException {
    Path into = Fixtures.HERE.resolve("libspectrum");
    for (String name : List.of("empty.z80", "plus3.z80", "empty.szx", "invalid.szx", "random.szx",
        "sp-2000.sna.gz", "sp-ffff.sna.gz")) {
      copy(CORPUS.resolve(name), into);
    }
    try (Stream<Path> chunks = Files.list(CORPUS.resolve("szx-chunks"))) {
      for (Path chunk : chunks.sorted().toList()) {
        copy(chunk, into.resolve("szx-chunks"));
      }
    }
  }

  /** A real game in every format there is a writer for, and the two older .z80 versions made from it. */
  private void manicMiner() throws Exception {
    byte[] v3 = Files.readAllBytes(MANIC_MINER);
    save("manicminer.z80", v3);
    save("manicminer.sna", Reference.convert(v3, "z80", "sna").orElseThrow());
    save("manicminer.szx", Reference.convert(v3, "z80", "szx").orElseThrow());
    SpectrumState state = new SnapshotZ80().loadFromBytes(v3);
    save("manicminer.sp", sp(state));
    save("manicminer-v2.z80", versionTwo(v3));
    save("manicminer-v1.z80", versionOne(v3, state, false));
    save("manicminer-v1-packed.z80", versionOne(v3, state, true));
  }

  /**
   * A 128K or a +3 with something different in every field: each bank has its own pattern, so a
   * page put in the wrong bank shows, and runs long and short and of ED, so the packing is used.
   */
  private void banks(String name, MachineTypes model, int port7ffd, int port1ffd) throws Exception {
    SpectrumState state = aMachine(model);
    state.setPort7ffd(port7ffd);
    state.setPort1ffd(port1ffd);
    state.setEnabledAY(true);
    AY8912State ay = new AY8912State();
    ay.setAddressLatch(0x07);
    int[] registers = new int[16];
    for (int register = 0; register < 16; register++) {
      registers[register] = 0x10 + register;
    }
    ay.setRegAY(registers);
    state.setAY8912State(ay);
    for (int bank = 0; bank < 8; bank++) {
      state.getMemoryState().setPageRam(bank, pattern(bank));
    }
    byte[] z80 = new SnapshotZ80().saveToBytes(state);
    save(name + ".z80", z80);
    save(name + ".szx", Reference.convert(z80, "z80", "szx").orElseThrow());
    if (model == MachineTypes.SPECTRUM128K) {
      save(name + ".sna", Reference.convert(z80, "z80", "sna").orElseThrow());
    }
  }

  /** A 16K: one page, in the three formats that can say so. */
  private void sixteen() throws Exception {
    SpectrumState state = aMachine(MachineTypes.SPECTRUM16K);
    state.getMemoryState().setPageRam(5, pattern(5));
    byte[] z80 = new SnapshotZ80().saveToBytes(state);
    save("sixteen.z80", z80);
    save("sixteen.szx", Reference.convert(z80, "z80", "szx").orElseThrow());
    save("sixteen.sp", sp(state));
  }

  /**
   * A 48K whose last page ends in an ED on its own. Written as a .z80 by today's writer, it reads
   * past the end of the page: the SNA is made by hand, so that the writer is not needed to make it.
   */
  private void aLoneEdAtTheEndOfAPage() throws IOException {
    ByteArrayOutputStream sna = new ByteArrayOutputStream();
    int sp = 0xff00, pc = 0x8000;
    sna.write(new byte[]{0x3f, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e,
        0x0f, 0x10, 0x11, 0x12, 0x04, 0x13, 0x14, 0x15, (byte) sp, (byte) (sp >> 8), 0x01, 0x01});
    byte[] ram = new byte[0xc000];
    ram[sp - 0x4000] = (byte) pc;
    ram[sp - 0x4000 + 1] = (byte) (pc >> 8);
    ram[0xbffe] = 0x01;
    ram[0xbfff] = (byte) 0xed;
    sna.write(ram);
    save("lone-ed-at-the-end.sna", sna.toByteArray());
  }

  private static SpectrumState aMachine(MachineTypes model) {
    SpectrumState state = new SpectrumState();
    state.setSpectrumModel(model);
    Z80State z80 = new Z80State();
    z80.setRegAF(0x1122);
    z80.setRegBC(0x3344);
    z80.setRegDE(0x5566);
    z80.setRegHL(0x7788);
    z80.setRegAFx(0x99aa);
    z80.setRegBCx(0xbbcc);
    z80.setRegDEx(0xddee);
    z80.setRegHLx(0xf001);
    z80.setRegIX(0x1234);
    z80.setRegIY(0x5678);
    z80.setRegSP(0xfe00);
    z80.setRegPC(0x8123);
    z80.setRegI(0x3f);
    z80.setRegR(0x55);
    z80.setIFF1(true);
    z80.setIFF2(true);
    z80.setIM(IntMode.IM1);
    state.setZ80State(z80);
    state.setMemoryState(new MemoryState());
    state.setBorder(2);
    state.setTstates(12345);
    state.setIssue2(true);
    state.setJoystick(JoystickModel.KEMPSTON);
    return state;
  }

  /** A bank nobody would mistake for another, that ends in its own number and never in an ED on its own. */
  private static byte[] pattern(int bank) {
    byte[] page = new byte[0x4000];
    int at = 0;
    for (int i = 0; i < 300; i++) page[at++] = (byte) (bank * 16 + 1);              // a run longer than one packing can say
    for (int i = 0; i < 5; i++) page[at++] = (byte) 0xed;                            // a run of ED
    page[at++] = (byte) 0xed;                                                        // an ED on its own, and what follows it
    page[at++] = 0x01;
    while (at < page.length) {
      page[at] = (byte) (bank * 31 + at * 7 + (at >> 8));
      at++;
    }
    page[page.length - 1] = (byte) bank;
    return page;
  }

  /** An SP, as libspectrum reads one: nothing writes the format, so it is written here. */
  private static byte[] sp(SpectrumState state) {
    Z80State z80 = state.getZ80State();
    boolean sixteen = state.getSpectrumModel() == MachineTypes.SPECTRUM16K;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write('S');
    out.write('P');
    word(out, sixteen ? 0x4000 : 0xc000);
    word(out, 0x4000);
    word(out, z80.getRegBC());
    word(out, z80.getRegDE());
    word(out, z80.getRegHL());
    out.write(z80.getRegF());
    out.write(z80.getRegA());
    word(out, z80.getRegIX());
    word(out, z80.getRegIY());
    word(out, z80.getRegBCx());
    word(out, z80.getRegDEx());
    word(out, z80.getRegHLx());
    out.write(z80.getRegFx());
    out.write(z80.getRegAx());
    out.write(z80.getRegR());
    out.write(z80.getRegI());
    word(out, z80.getRegSP());
    word(out, z80.getRegPC());
    word(out, 0);
    out.write(state.getBorder());
    out.write(0);
    int im = z80.getIM() == IntMode.IM0 ? 0x08 : z80.getIM() == IntMode.IM2 ? 0x02 : 0x00;
    word(out, (z80.isIFF1() ? 0x01 : 0) | (z80.isIFF2() ? 0x04 : 0) | im);
    for (int page : sixteen ? new int[]{5} : new int[]{5, 2, 0}) {
      out.writeBytes(state.getMemoryState().getPageRam(page));
    }
    return out.toByteArray();
  }

  /** The same .z80 as a version 2: a 23-byte extended header, and no counter of T-states. */
  private static byte[] versionTwo(byte[] v3) {
    int extended = (v3[30] & 0xff) | ((v3[31] & 0xff) << 8);
    byte[] v2 = new byte[32 + 23 + v3.length - 32 - extended];
    System.arraycopy(v3, 0, v2, 0, 30);
    v2[30] = 23;
    System.arraycopy(v3, 32, v2, 32, 23);
    System.arraycopy(v3, 32 + extended, v2, 32 + 23, v3.length - 32 - extended);
    return v2;
  }

  /** The same 48K as a version 1: the PC in the header, and the memory whole, raw or packed with its end marker. */
  private static byte[] versionOne(byte[] v3, SpectrumState state, boolean packed) {
    ByteArrayOutputStream memory = new ByteArrayOutputStream();
    for (int page : new int[]{5, 2, 0}) {
      memory.writeBytes(state.getMemoryState().getPageRam(page));
    }
    byte[] header = java.util.Arrays.copyOf(v3, 30);
    header[6] = v3[32];
    header[7] = v3[33];
    header[12] = (byte) (packed ? header[12] | 0x20 : header[12] & ~0x20);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(header);
    out.writeBytes(packed ? packed(memory.toByteArray()) : memory.toByteArray());
    return out.toByteArray();
  }

  /** The .z80 packing, and the four bytes a version 1 ends with. */
  private static byte[] packed(byte[] plain) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int at = 0;
    while (at < plain.length) {
      int value = plain[at] & 0xff, run = 1;
      while (at + run < plain.length && run < 255 && (plain[at + run] & 0xff) == value) run++;
      if (value == 0xed && run == 1) {
        out.write(0xed);
        at++;
        if (at < plain.length) out.write(plain[at++]);
      } else if (run >= 5 || value == 0xed && run >= 2) {
        out.writeBytes(new byte[]{(byte) 0xed, (byte) 0xed, (byte) run, (byte) value});
        at += run;
      } else {
        out.write(value);
        at++;
      }
    }
    out.writeBytes(new byte[]{0x00, (byte) 0xed, (byte) 0xed, 0x00});
    return out.toByteArray();
  }

  private static void word(ByteArrayOutputStream out, int word) {
    out.write(word & 0xff);
    out.write((word >> 8) & 0xff);
  }

  private static void copy(Path file, Path into) throws IOException {
    Files.createDirectories(into);
    String name = file.getFileName().toString();
    if (name.endsWith(".gz")) {
      try (GZIPInputStream unpacked = new GZIPInputStream(Files.newInputStream(file))) {
        Files.write(into.resolve(name.substring(0, name.length() - 3)), unpacked.readAllBytes());
      }
    } else {
      Files.copy(file, into.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Written, and read back: by libspectrum where it is asked, and by today's reader where it is not.
   * A fixture nobody can read is no fixture.
   */
  private static void save(String name, byte[] image) throws IOException {
    Path file = Fixtures.HERE.resolve(name);
    Files.createDirectories(file.getParent());
    Files.write(file, image);
    String format = TodaysFormats.extensionOf(file);
    Fields read = Reference.asks(format) ? Reference.read(image, format) : TodaysFormats.read(file);
    assertFalse(read.isRefused(), name + " cannot be read back: " + read.refusal());
  }
}
