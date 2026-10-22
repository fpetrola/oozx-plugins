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

package com.fpetrola.oozx.snapshots;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.Direction;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.PageField;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.speccy.parts.PartVisitor;
import com.fpetrola.oozx.speccy.parts.Visitable;
import com.sun.jna.Pointer;
import model.tests.formats.Libspectrum;
import model.tests.formats.Reference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What is plugged in, in the blocks SZX has for it: memory a page a block, 16K and 8K pages; memory
 * after a block's fields, packed when a bit of the block says so or always, a whole 32K of ROM; and
 * a block that only says its part is there. Over parts made up for the test, so that nothing but
 * the engine is tried: each block is read from the file libspectrum's own tests read it from, and
 * what is written of the parts is what libspectrum reads back.
 */
class WhatIsPluggedInIsKeptInItsBlocksTest {

  /** Memory a 16K page a block, as an IDE interface keeps its RAM. */
  static final class Ata implements Visitable {
    final byte[][] ram = filled(32, 0x4000);

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** A few registers, an EPROM after them, and RAM an 8K page a block, as a DivIDE. */
  static final class Div implements Visitable {
    boolean paged;
    int control;
    byte[] eprom = filled(0x2000);
    final byte[][] ram = filled(4, 0x2000);

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Its RAM after its flags, packed when one of them says so, as a Multiface. */
  static final class Face implements Visitable {
    boolean paged;
    byte[] ram = filled(0x2000);

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** A ROM always packed, how long it is packed before it, as an Interface 2 cartridge. */
  static final class Cartridge implements Visitable {
    byte[] rom = filled(0x4000);

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Nothing to say but that it is there, as a simple IDE interface. */
  static final class Side implements Visitable {
    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** The machine's own ROMs, 32K of a 128K's, as somebody's own ROMs replace them. */
  static final class Roms implements Visitable {
    byte[] rom = filled(0x8000);

    public void accept(PartVisitor visitor) {
      visitor.visit(this);
    }
  }

  static final Fixed ATRP = Fixed.pages("ATRP", 0x4000);
  static final Fixed DIDE = Fixed.of("DIDE", 4);
  static final Fixed DIRP = Fixed.pages("DIRP", 0x2000);
  static final Fixed MFCE = Fixed.of("MFCE", 2);
  static final Fixed IF2R = Fixed.of("IF2R", 4);
  static final Fixed SIDE = Fixed.of("SIDE", 0);
  static final Fixed ROM = Fixed.of("ROM\0", 6);

  static final Bindings BINDINGS = Bindings.of()
      .on(Ata.class, Layout.<Ata>of()
          .pages(ATRP, ata -> ata.ram.length, PageField.of((ata, page) -> ata.ram[page], (ata, page, bytes) -> ata.ram[page] = bytes)))
      .on(Div.class, Layout.<Div>of()
          .bit(DIDE, 0, 1, Field.of("paged", div -> div.paged, (div, paged) -> div.paged = paged))
          .u8(DIDE, 2, Field.of("control", div -> div.control, (div, control) -> div.control = control))
          .u8(DIDE, 3, Field.of("pages", div -> div.ram.length, (div, pages) -> { }))
          .rest(DIDE, 0, 2, 0x2000, Field.of("eprom", div -> div.eprom, (div, eprom) -> div.eprom = eprom))
          .pages(DIRP, div -> div.ram.length, PageField.of((div, page) -> div.ram[page], (div, page, bytes) -> div.ram[page] = bytes)))
      .on(Face.class, Layout.<Face>of()
          .bit(MFCE, 1, 0, Field.of("paged", face -> face.paged, (face, paged) -> face.paged = paged))
          .rest(MFCE, 1, 1, 0x2000, Field.of("ram", face -> face.ram, (face, ram) -> face.ram = ram)))
      .on(Cartridge.class, Layout.<Cartridge>of()
          .packedRest(IF2R, 0, 0x4000, Field.of("rom", cartridge -> cartridge.rom, (cartridge, rom) -> cartridge.rom = rom)))
      .on(Side.class, Layout.<Side>of().present(SIDE))
      .on(Roms.class, Layout.<Roms>of()
          .u32(ROM, 2, Field.of("length", roms -> roms.rom.length, (roms, length) -> { }))
          .rest(ROM, 0, 0, 0x8000, Field.of("rom", roms -> roms.rom, (roms, rom) -> roms.rom = rom)));

  static final Shape SHAPE = Shape.of(MachineTypes.SPECTRUM128K, SzxFormat.HEADER,
      new SzxBlocks(List.of(ATRP, DIDE, DIRP, MFCE, IF2R, SIDE, ROM))).with(new SzxFormat.Signed(MachineTypes.SPECTRUM128K));

  @Test
  void eachBlockIsReadAsLibspectrumsOwnTestsReadIt() throws IOException {
    for (String name : List.of("ATRP", "ATRP-uncompressed")) {
      Ata ata = new Ata();
      read(name, ata);
      assertArrayEquals(new byte[0x4000], ata.ram[0], name);
    }
    Div div = new Div();
    read("DIDE", div);
    read("DIRP", div);
    assertEquals(List.of(true, 0xab, 0x12), List.of(div.paged, div.control, (int) div.eprom[0]));
    assertArrayEquals(new byte[0x2000 - 1], Arrays.copyOfRange(div.eprom, 1, 0x2000));
    assertArrayEquals(new byte[0x2000], div.ram[0]);
    Face face = new Face();
    read("MFCE", face);
    assertTrue(face.paged, "paged in");
    assertArrayEquals(new byte[0x2000], face.ram);
    Cartridge cartridge = new Cartridge();
    read("IF2R", cartridge);
    assertArrayEquals(new byte[0x4000], cartridge.rom);
    assertTrue(SHAPE.parse(fixture("SIDE")).has(SIDE), "the simple IDE is not there");
  }

  @Test
  void whatIsWrittenIsWhatLibspectrumReads() {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
    Ata ata = new Ata();
    for (int page = 0; page < ata.ram.length; page++) ata.ram[page] = pattern(page, 0x4000);
    Div div = new Div();
    div.paged = true;
    div.control = 0x5c;
    div.eprom = pattern(40, 0x2000);
    for (int page = 0; page < div.ram.length; page++) div.ram[page] = pattern(50 + page, 0x2000);
    Face face = new Face();
    face.paged = true;
    face.ram = pattern(60, 0x2000);
    Cartridge cartridge = new Cartridge();
    cartridge.rom = pattern(70, 0x4000);
    Roms roms = new Roms();
    roms.rom = pattern(80, 0x8000);
    SnapshotFile file = SHAPE.empty();
    List.of(ata, div, face, cartridge, new Side(), roms).forEach(part -> BINDINGS.apply(part, Direction.writing(file)));
    byte[] written = SHAPE.assemble(file);

    Libspectrum lib = Libspectrum.INSTANCE;
    Reference.withSnap(written, "szx", snap -> {
      for (int page = 0; page < ata.ram.length; page++) assertArrayEquals(ata.ram[page], bytes(lib.libspectrum_snap_zxatasp_ram(snap, page), 0x4000), "ATRP " + page);
      assertEquals(List.of(1, 0x5c), List.of(lib.libspectrum_snap_divide_paged(snap), lib.libspectrum_snap_divide_control(snap) & 0xff));
      assertArrayEquals(div.eprom, bytes(lib.libspectrum_snap_divide_eprom(snap, 0), 0x2000), "DIDE");
      for (int page = 0; page < div.ram.length; page++) assertArrayEquals(div.ram[page], bytes(lib.libspectrum_snap_divide_ram(snap, page), 0x2000), "DIRP " + page);
      assertEquals(List.of(1, 0x2000L), List.of(lib.libspectrum_snap_multiface_paged(snap), lib.libspectrum_snap_multiface_ram_length(snap, 0).longValue()));
      assertArrayEquals(face.ram, bytes(lib.libspectrum_snap_multiface_ram(snap, 0), 0x2000), "MFCE");
      assertArrayEquals(cartridge.rom, bytes(lib.libspectrum_snap_interface2_rom(snap, 0), 0x4000), "IF2R");
      assertEquals(1, lib.libspectrum_snap_simpleide_active(snap), "SIDE");
      assertEquals(1, lib.libspectrum_snap_custom_rom(snap), "ROM");
      for (int page = 0; page < 2; page++) {
        assertEquals(0x4000L, lib.libspectrum_snap_rom_length(snap, page).longValue());
        assertArrayEquals(Arrays.copyOfRange(roms.rom, page * 0x4000, (page + 1) * 0x4000), bytes(lib.libspectrum_snap_roms(snap, page), 0x4000), "ROM " + page);
      }
      return true;
    }).orElseThrow(() -> new AssertionError("libspectrum refused it"));
  }

  private static void read(String name, Visitable part) throws IOException {
    BINDINGS.apply(part, Direction.reading(SHAPE.parse(fixture(name))));
  }

  private static byte[] fixture(String name) throws IOException {
    try (InputStream in = WhatIsPluggedInIsKeptInItsBlocksTest.class.getResourceAsStream("/snapshots/libspectrum/szx-chunks/" + name + ".szx")) {
      return in.readAllBytes();
    }
  }

  private static byte[] bytes(Pointer memory, int length) {
    return memory.getByteArray(0, length);
  }

  /** Something no part starts with, so that what was read shows. */
  private static byte[] filled(int length) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) 0xff);
    return bytes;
  }

  private static byte[][] filled(int pages, int length) {
    byte[][] all = new byte[pages][];
    for (int page = 0; page < pages; page++) all[page] = filled(length);
    return all;
  }

  private static byte[] pattern(int seed, int length) {
    byte[] bytes = new byte[length];
    for (int at = 0; at < length; at++) bytes[at] = (byte) (seed + at * 7 + at / 256);
    return bytes;
  }
}
