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

import java.util.Arrays;

/**
 * A stretch of a file - a header, a tail - read and written at places, little-endian. Its places
 * count from where it starts in the file, so a table can use the offsets the format's
 * specification uses.
 */
public final class Bytes {

  private final int start;
  private final byte[] bytes;

  private Bytes(int start, byte[] bytes) {
    this.start = start;
    this.bytes = bytes;
  }

  public static Bytes of(int start, byte[] data) {
    return new Bytes(start, data.clone());
  }

  public static Bytes zeros(int start, int length) {
    return new Bytes(start, new byte[length]);
  }

  public int start() {
    return start;
  }

  public int length() {
    return bytes.length;
  }

  public int u8(int offset) {
    return bytes[index(offset, 1)] & 0xff;
  }

  public int u16(int offset) {
    return u8(offset) | u8(offset + 1) << 8;
  }

  public int u32(int offset) {
    return u16(offset) | u16(offset + 2) << 16;
  }

  public Bytes u8(int offset, int value) {
    bytes[index(offset, 1)] = (byte) value;
    return this;
  }

  public Bytes u16(int offset, int value) {
    return u8(offset, value).u8(offset + 1, value >> 8);
  }

  public Bytes u32(int offset, int value) {
    return u16(offset, value).u16(offset + 2, value >> 16);
  }

  /** Sets those bits, and leaves the others as they are. */
  public Bytes or(int offset, int value) {
    return u8(offset, u8(offset) | value);
  }

  public byte[] slice(int offset, int count) {
    int index = index(offset, count);
    return Arrays.copyOfRange(bytes, index, index + count);
  }

  public Bytes put(int offset, byte[] data) {
    System.arraycopy(data, 0, bytes, index(offset, data.length), data.length);
    return this;
  }

  public byte[] toBytes() {
    return bytes.clone();
  }

  private int index(int offset, int count) {
    int index = offset - start;
    if (index < 0 || count < 0 || index + count > bytes.length) {
      throw new IndexOutOfBoundsException(count + " bytes at " + offset + ", in a stretch of " + bytes.length + " at " + start);
    }
    return index;
  }
}
