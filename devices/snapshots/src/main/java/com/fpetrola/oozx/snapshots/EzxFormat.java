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
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Pages;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Shape;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.speccy.machine.Paging128;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.display.Border;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import dev.crystal.plugins.api.Answers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fpetrola.oozx.formats.CoreFields.*;

/**
 * EZX, from EmuZWin: 'Emuz', both ROMs and all eight banks, then the processor, the paging, the
 * AY and the tape, and after them named chunks - pokes, disks, the 256 colours of a Spec256 game -
 * that are skipped here. Since EmuZWin 2.4 it comes as 'EZX' and blocks of NRV2E, each a length
 * and a stream, which put together are the same file. Only read.
 */
@Answers("ezx")
public final class EzxFormat extends DeclaredFormat implements SnapshotFormat {

  static final int BANK = 16384;
  static final Fixed ROMS = Fixed.of("signature and ROMs", 4 + 2 * BANK);
  /** From Lock7FFD to the end of the tape state: what comes after the banks and before the chunks. */
  public static final Fixed STATE = Fixed.of("state", 95);
  static final int LOCK = ROMS.length() + 8 * BANK;
  /** A colour a bit: eight for each byte of the bank. */
  static final int COLOURS_OF_A_BANK = 8 * BANK;
  /** A GFXB after its number, width and height: 320 by 200, a colour each. */
  static final int BACKGROUND = 320 * 200;

  static final Layout<Cpu> CPU = Layout.<Cpu>of()
      .u16(STATE, 1, AF).u16(STATE, 3, BC).u16(STATE, 5, DE).u16(STATE, 7, HL)
      .u16(STATE, 9, IX).u16(STATE, 11, IY)
      .u16(STATE, 13, AF_).u16(STATE, 15, BC_).u16(STATE, 17, DE_).u16(STATE, 19, HL_)
      .u16(STATE, 21, PC).u16(STATE, 23, SP).u8(STATE, 25, I).u8(STATE, 26, R)
      .flag(STATE, 28, IFF1).flag(STATE, 29, IFF2).u8(STATE, 31, IM).flag(STATE, 41, EI_PENDING)
      .assuming(MEMPTR, 0);
  static final Layout<SpectrumZ80Clock> CLOCK = Layout.<SpectrumZ80Clock>of().u32(STATE, 32, TSTATES);
  static final Layout<Border> BORDER_TABLE = Layout.<Border>of().u8(STATE, 39, BORDER);
  static final Layout<Paging128> PAGING = Layout.<Paging128>of().encoded(STATE, 0, 40, new Port7ffd());
  static final Layout<SpectrumMemory> RAM = Layout.<SpectrumMemory>of().pages(PAGE);

  static final Bindings BINDINGS = Bindings.of()
      .on(Cpu.class, CPU).on(SpectrumZ80Clock.class, CLOCK).on(Border.class, BORDER_TABLE)
      .on(Paging128.class, PAGING).on(SpectrumMemory.class, RAM)
      .quiet(Quiet.PARTS);

  /** Locked to 48K: the banks a 48K has are read, and the other five are passed over. */
  static final Shape FORTY_EIGHT = Shape.of(MachineTypes.SPECTRUM48K, ROMS, Pages.of(0), Fixed.of("bank 1", BANK),
      Pages.of(2), Fixed.of("banks 3 and 4", 2 * BANK), Pages.of(5), Fixed.of("banks 6 and 7", 2 * BANK), STATE);
  static final Shape ONE_TWENTY_EIGHT = Shape.of(MachineTypes.SPECTRUM128K, ROMS, Pages.of(0, 1, 2, 3, 4, 5, 6, 7), STATE);

  @Override
  protected byte[] unwrapped(byte[] file) {
    String signature = new String(file, 0, Math.min(4, file.length), StandardCharsets.ISO_8859_1);
    if (signature.equals("Emuz")) return file;
    if (!signature.startsWith("EZX")) throw new Refused("not an EZX: it starts with neither Emuz nor EZX");
    ByteArrayOutputStream whole = new ByteArrayOutputStream();
    for (int at = 3; at < file.length; ) {
      Cursor length = Cursor.over(file).skip(at);
      Nrv2e block = Nrv2e.unpack(file, at + 4);
      whole.writeBytes(block.bytes());
      at += 4 + length.u32();
    }
    return whole.toByteArray();
  }

  /**
   * The 256 colours of a Spec256 game, as the files it would otherwise have beside it: the GFXM of
   * the banks at 0x4000, 0x8000 and 0xc000 one after the other are its .gfx, that of the 48K ROM
   * its .gfb and of the 128K ROM its .gfa, GFXC its .cfg, and each background of 320 by 200 a .b00
   * and on. A bank EmuZWin left out is one with no colours, which it does not store.
   */
  @Override
  public Map<String, byte[]> carried(byte[] file) {
    byte[] whole = unwrapped(file);
    Map<Integer, byte[]> banks = new HashMap<>();
    Map<String, byte[]> carried = new LinkedHashMap<>();
    int backgrounds = 0;
    for (int at = LOCK + STATE.length(); at + 8 <= whole.length; ) {
      String name = new String(whole, at, 4, StandardCharsets.ISO_8859_1);
      int length = Cursor.over(whole).skip(at + 4).u32();
      byte[] data = Arrays.copyOfRange(whole, at + 8, at + 8 + length);
      switch (name) {
        case "GFXM" -> banks.put(data[0] & 0xFF, Arrays.copyOfRange(data, 1, data.length));
        case "GFXC" -> carried.put("cfg", data);
        case "GFXB" -> {
          if (data.length == 10 + BACKGROUND) carried.put("b%02d".formatted(backgrounds++), Arrays.copyOfRange(data, 10, data.length));
        }
        default -> {
        }
      }
      at += 8 + length;
    }
    if (banks.isEmpty()) return carried;
    int top = whole[LOCK] != 0 ? 0 : whole[LOCK + 38] & 7;
    ByteArrayOutputStream colours = new ByteArrayOutputStream();
    for (int bank : new int[]{5, 2, top}) colours.writeBytes(banks.getOrDefault(bank, new byte[COLOURS_OF_A_BANK]));
    carried.put("gfx", colours.toByteArray());
    if (banks.containsKey(17)) carried.put("gfb", banks.get(17));
    if (banks.containsKey(16)) carried.put("gfa", banks.get(16));
    return carried;
  }

  @Override
  protected List<String> extensions() {
    return List.of("ezx");
  }

  @Override
  protected Bindings bindings() {
    return BINDINGS;
  }

  @Override
  protected boolean writesFiles() {
    return false;
  }

  @Override
  protected Shape shapeOf(Cursor peek) {
    if (peek.length() < LOCK + STATE.length()) throw new Refused("an EZX of " + peek.length() + " bytes");
    return peek.u8At(LOCK) != 0 ? FORTY_EIGHT : ONE_TWENTY_EIGHT;
  }

  @Override
  protected Shape shapeFor(SpectrumMachine machine) {
    return FORTY_EIGHT;
  }

  @Override
  public String label() {
    return "EZX snapshot";
  }

  /** The 7ffd put back together from what EZX keeps apart: the bank at the top, the screen, the ROM and the lock. */
  static final class Port7ffd implements Encoding<Paging128> {
    public void read(Bytes bytes, Paging128 paging, SnapshotFile file) {
      PORT_7FFD.set(paging, bytes.u8(37) & 7 | (bytes.u8(38) & 1) << 3 | (bytes.u8(36) == 1 ? 0x10 : 0)
          | (bytes.u8(0) != 0 ? 0x20 : 0));
    }

    public void write(Paging128 paging, Bytes bytes, SnapshotFile file) {
      throw new UnsupportedOperationException("EZX is only read");
    }

    public List<Object> fields() {
      return List.of(PORT_7FFD);
    }
  }
}
