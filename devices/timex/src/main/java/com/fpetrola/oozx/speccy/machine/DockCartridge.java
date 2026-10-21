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

package com.fpetrola.oozx.speccy.machine;

import com.fpetrola.oozx.speccy.modules.memory.MemoryPart;
import com.fpetrola.oozx.speccy.modules.memory.Ram;
import com.fpetrola.oozx.speccy.modules.memory.Rom;
import com.fpetrola.oozx.speccy.modules.memory.Storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * A cartridge for the Timex dock as a .dck file keeps it; this is the only reader of the format.
 * The file is blocks, each for one bank: the dock (0), the EXROM behind it (254) or HOME (255),
 * which is the machine's own memory. A block says in eight bytes what each eight K of its bank is
 * - nothing, empty RAM, ROM, or RAM with something in it - and then brings the eight K of every
 * page that has bytes.
 * <p>
 * Where the picture is, the dock and the EXROM wait for the ULA as the RAM there does. The first
 * sixteen K of HOME are the machine's own ROM, which no dock replaces: a file that brings them is
 * refused, as Fuse refuses it.
 */
public final class DockCartridge {

  public static final int DOCK = 0, EXROM = 254, HOME = 255;
  private static final int NOTHING = 0, EMPTY_RAM = 1, ROM = 2, RAM = 3;
  private static final int PAGES = 8, PAGE = 0x2000;

  private final String name;
  private final Map<Integer, MemoryPart[]> banks = new HashMap<>();

  private DockCartridge(String name) {
    this.name = name;
  }

  public static boolean isOne(String fileName) {
    return fileName.toLowerCase().endsWith(".dck");
  }

  public static DockCartridge read(File file) throws IOException {
    return read(file.getName().replaceFirst("\\.[^.]*$", ""), Files.readAllBytes(file.toPath()));
  }

  public static DockCartridge read(String name, byte[] file) throws IOException {
    DockCartridge cartridge = new DockCartridge(name);
    int at = 0;
    while (at < file.length) {
      if (at + 1 + PAGES > file.length) throw new IOException(name + " ends in the middle of a block");
      int bank = file[at] & 0xff;
      if (bank != DOCK && bank != EXROM && bank != HOME) throw new IOException(name + " has a block for bank " + bank + ", which the dock does not have");
      int[] types = new int[PAGES];
      int bringing = 0;
      for (int page = 0; page < PAGES; page++) {
        types[page] = file[at + 1 + page] & 0xff;
        if (types[page] > RAM) throw new IOException(name + " has a page of type " + types[page] + ", which is no kind of page");
        if (types[page] >= ROM) bringing++;
      }
      at += 1 + PAGES;
      if (at + bringing * PAGE > file.length) throw new IOException(name + " ends before the pages it says it brings");
      if (bank == HOME && (types[0] != NOTHING || types[1] != NOTHING)) throw new IOException(name + " brings the machine's own ROM, which no dock replaces");
      MemoryPart[] pages = cartridge.banks.computeIfAbsent(bank, nothingYet -> new MemoryPart[PAGES]);
      for (int page = 0; page < PAGES; page++) {
        if (types[page] == NOTHING) continue;
        Storage part = types[page] == ROM ? new Rom(PAGE) : new Ram(PAGE);
        if (types[page] != EMPTY_RAM) {
          System.arraycopy(file, at, part.bytes, 0, PAGE);
          at += PAGE;
        }
        part.contended = bank != HOME && (page == 2 || page == 3);
        pages[page] = part;
      }
    }
    return cartridge;
  }

  /** What the cartridge puts in that bank, eight K at a time, with nulls where it puts nothing. */
  public MemoryPart[] bank(int id) {
    return banks.getOrDefault(id, new MemoryPart[PAGES]);
  }

  public String name() {
    return name;
  }
}
