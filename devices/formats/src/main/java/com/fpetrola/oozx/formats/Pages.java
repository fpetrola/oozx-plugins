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

package com.fpetrola.oozx.formats;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Pages of RAM one after the other, as they are, in an order the format gives: by number, the one
 * a port puts at the top, or the machine's banks not placed yet.
 */
public abstract sealed class Pages implements Region {

  public static final int LENGTH = 0x4000;

  /** Those banks, in that order. */
  public static Pages of(int... banks) {
    return new ByNumber(IntStream.of(banks).boxed().toList());
  }

  /** The bank at 0xC000, which that byte of that stretch names in its three low bits - read later in the file. */
  public static Pages bankAtTop(Fixed port, int offset) {
    return new AtTheTop(port, offset);
  }

  /** The machine's banks not placed yet, in order. */
  public static Pages remaining() {
    return new Remaining();
  }

  static final class ByNumber extends Pages {
    private final List<Integer> banks;

    ByNumber(List<Integer> banks) {
      this.banks = banks;
    }

    public void parse(Cursor in, SnapshotFile into) {
      banks.forEach(bank -> into.placed(bank, in.take(LENGTH)));
    }

    public void assemble(SnapshotFile from, Sink out) {
      banks.forEach(bank -> out.bytes(from.written(bank)));
    }
  }

  static final class AtTheTop extends Pages {
    private final Fixed port;
    private final int offset;

    AtTheTop(Fixed port, int offset) {
      this.port = port;
      this.offset = offset;
    }

    public void parse(Cursor in, SnapshotFile into) {
      into.pending(this, in.take(LENGTH));
    }

    int bank(SnapshotFile file) {
      return file.bytes(port).u8(offset) & 0x07;
    }

    public void assemble(SnapshotFile from, Sink out) {
      out.bytes(from.written(bank(from)));
    }
  }

  static final class Remaining extends Pages {
    public void parse(Cursor in, SnapshotFile into) {
      into.placeWhatWasPending();
      for (int bank : into.shape().banks()) {
        if (!into.isPlaced(bank)) {
          into.placed(bank, in.take(LENGTH));
        }
      }
    }

    public void assemble(SnapshotFile from, Sink out) {
      for (int bank : from.shape().banks()) {
        if (!from.isWritten(bank)) {
          out.bytes(from.written(bank));
        }
      }
    }
  }
}
