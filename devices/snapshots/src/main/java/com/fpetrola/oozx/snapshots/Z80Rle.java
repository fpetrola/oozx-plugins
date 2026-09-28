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

import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Sink;

/**
 * The compression of a .z80: ED ED n v is n times v. Used for five or more equal bytes, or for two
 * or more EDs; a lone ED is written as it is, and so is the byte after it, so that it never starts
 * a run.
 */
final class Z80Rle {

  private Z80Rle() {
  }

  /** Unpacked to exactly that length; a run that goes past the end is cut there, as Lazy Jones has one. */
  static byte[] decode(byte[] packed, int length) {
    byte[] out = new byte[length];
    int from = 0;
    int to = 0;
    while (from < packed.length && to < length) {
      int value = packed[from] & 0xff;
      if (value == 0xed && from + 1 < packed.length && (packed[from + 1] & 0xff) == 0xed) {
        if (from + 3 >= packed.length) {
          throw new Refused("a run cut short at the end of a page");
        }
        int count = Math.min(packed[from + 2] & 0xff, length - to);
        java.util.Arrays.fill(out, to, to + count, packed[from + 3]);
        to += count;
        from += 4;
      } else {
        out[to++] = (byte) value;
        from++;
      }
    }
    if (to != length) {
      throw new Refused("a page that unpacks to " + to + " bytes, not " + length);
    }
    return out;
  }

  /** How many packed bytes it took to fill that length: a version 1 has its end marker after them. */
  static int packedLength(byte[] packed, int length) {
    int from = 0;
    int to = 0;
    while (from < packed.length && to < length) {
      if ((packed[from] & 0xff) == 0xed && from + 1 < packed.length && (packed[from + 1] & 0xff) == 0xed) {
        to += from + 2 < packed.length ? packed[from + 2] & 0xff : 0;
        from += 4;
      } else {
        to++;
        from++;
      }
    }
    return from;
  }

  static byte[] encode(byte[] raw) {
    Sink out = new Sink();
    int at = 0;
    while (at < raw.length) {
      int value = raw[at] & 0xff;
      int run = 1;
      while (at + run < raw.length && run < 255 && (raw[at + run] & 0xff) == value) {
        run++;
      }
      if (run >= 5 || value == 0xed && run >= 2) {
        out.u8(0xed).u8(0xed).u8(run).u8(value);
        at += run;
      } else if (value == 0xed) {
        out.u8(0xed);
        at++;
        if (at < raw.length) {
          out.u8(raw[at]);
          at++;
        }
      } else {
        out.u8(value);
        at++;
      }
    }
    return out.toBytes();
  }
}
