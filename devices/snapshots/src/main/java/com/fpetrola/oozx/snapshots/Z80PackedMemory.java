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

import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.Pages;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Region;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.formats.SnapshotFile;

import java.util.Arrays;

/** The memory of a packed version 1: the 48K from 0x4000 packed as one, and 00 ED ED 00 after it. */
final class Z80PackedMemory implements Region {

  private static final byte[] END = {0x00, (byte) 0xed, (byte) 0xed, 0x00};

  @Override
  public void parse(Cursor in, SnapshotFile into) {
    byte[] rest = in.rest();
    int used = Z80Rle.packedLength(rest, 3 * Pages.LENGTH);
    byte[] ram = Z80Rle.decode(Arrays.copyOf(rest, Math.min(used, rest.length)), 3 * Pages.LENGTH);
    if (!Arrays.equals(Arrays.copyOfRange(rest, Math.min(used, rest.length), rest.length), END)) {
      throw new Refused("a packed version 1 without its end marker right after the memory");
    }
    into.page(5, Arrays.copyOfRange(ram, 0, 0x4000));
    into.page(2, Arrays.copyOfRange(ram, 0x4000, 0x8000));
    into.page(0, Arrays.copyOfRange(ram, 0x8000, 0xc000));
  }

  @Override
  public void assemble(SnapshotFile from, Sink out) {
    throw new Refused("a version 1 is never written");
  }
}
