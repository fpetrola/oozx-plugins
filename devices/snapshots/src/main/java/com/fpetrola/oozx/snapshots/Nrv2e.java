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

import java.util.Arrays;

/**
 * NRV2E from UCL, the compression UPX uses, with the bits read a byte at a time, highest first: a
 * 1 is a byte copied as it is, and the rest say how far back to copy from and how much.
 */
final class Nrv2e {
  private final byte[] in;
  private int at;
  private int bits;
  private int left;
  private byte[] out = new byte[1 << 16];
  private int written;

  private Nrv2e(byte[] in, int from) {
    this.in = in;
    this.at = from;
  }

  /** One stream from {@code from}, unpacked; {@link #end()} says where it stopped reading. */
  static Nrv2e unpack(byte[] in, int from) {
    Nrv2e stream = new Nrv2e(in, from);
    stream.run();
    return stream;
  }

  byte[] bytes() {
    return Arrays.copyOf(out, written);
  }

  int end() {
    return at;
  }

  private void run() {
    int lastOffset = 1;
    while (true) {
      while (bit() == 1) put(next());
      int offset = 1;
      while (true) {
        offset = offset * 2 + bit();
        if (bit() == 1) break;
        offset = (offset - 1) * 2 + bit();
      }
      int length;
      if (offset == 2) {
        offset = lastOffset;
        length = bit();
      } else {
        long far = (offset - 3L) * 256 + next();
        if (far >= 0xFFFFFFFFL) return;
        length = (int) (~far & 1);
        offset = (int) (far >> 1) + 1;
        lastOffset = offset;
      }
      if (length == 1) length = 1 + bit();
      else if (bit() == 1) length = 3 + bit();
      else {
        length = 1;
        do length = length * 2 + bit(); while (bit() == 0);
        length += 3;
      }
      if (offset > 0x500) length++;
      if (offset > written) throw new Refused("an NRV2E stream that copies from before its start");
      for (int i = 0; i <= length; i++) put(out[written - offset]);
    }
  }

  private int bit() {
    if (left == 0) {
      bits = next();
      left = 8;
    }
    return bits >> --left & 1;
  }

  private int next() {
    if (at >= in.length) throw new Refused("an NRV2E stream cut short");
    return in[at++] & 0xFF;
  }

  private void put(int value) {
    if (written == out.length) out = Arrays.copyOf(out, written * 2);
    out[written++] = (byte) value;
  }
}
