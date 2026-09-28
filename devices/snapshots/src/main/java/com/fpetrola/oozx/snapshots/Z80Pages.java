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
import com.fpetrola.oozx.formats.Region;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.formats.SnapshotFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The pages of a version 2 or 3: each a length (0xffff: not packed), a number, and its bytes.
 * Which bank a number is depends on the machine: without paging, 8, 4 and 5 are what is at 0x4000,
 * 0x8000 and 0xC000; with it, n is the bank n - 3.
 */
final class Z80Pages implements Region {

  private static final Map<Integer, Integer> UNPAGED = Map.of(8, 5, 4, 2, 5, 0);

  static Optional<Integer> bankOf(List<Integer> banks, int id) {
    Integer bank = banks.size() == 8 ? Integer.valueOf(id - 3) : UNPAGED.get(id);
    return Optional.ofNullable(bank).filter(banks::contains);
  }

  static int idOf(List<Integer> banks, int bank) {
    if (banks.size() == 8) return bank + 3;
    return UNPAGED.entrySet().stream().filter(entry -> entry.getValue() == bank).findFirst().orElseThrow().getKey();
  }

  @Override
  public void parse(Cursor in, SnapshotFile into) {
    while (in.left() > 0) {
      int length = in.u16();
      int id = in.u8();
      byte[] page = length == 0xffff ? in.take(Pages.LENGTH) : Z80Rle.decode(in.take(length), Pages.LENGTH);
      bankOf(into.shape().banks(), id).ifPresentOrElse(bank -> into.page(bank, page),
          () -> into.note("skipped: page " + id + ", a ROM or a bank this machine does not have"));
    }
  }

  @Override
  public void assemble(SnapshotFile from, Sink out) {
    for (int bank : from.shape().banks()) {
      byte[] raw = from.pageOrZeros(bank);
      byte[] packed = Z80Rle.encode(raw);
      boolean packs = packed.length < Pages.LENGTH;
      out.u16(packs ? packed.length : 0xffff).u8(idOf(from.shape().banks(), bank)).bytes(packs ? packed : raw);
    }
  }
}
