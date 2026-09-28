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
 * A file read in order, little-endian, and peeked at anywhere. The only thing that finds a file too
 * short, and it says so: nothing that reads through it has to check.
 */
public final class Cursor {

  private final byte[] data;
  private int at;

  private Cursor(byte[] data) {
    this.data = data;
  }

  public static Cursor over(byte[] data) {
    return new Cursor(data);
  }

  public int at() {
    return at;
  }

  public int left() {
    return data.length - at;
  }

  public int length() {
    return data.length;
  }

  public int u8() {
    int value = u8At(at);
    at += 1;
    return value;
  }

  public int u16() {
    int value = u16At(at);
    at += 2;
    return value;
  }

  public int u32() {
    int value = u16At(at) | u16At(at + 2) << 16;
    at += 4;
    return value;
  }

  public byte[] take(int count) {
    need(at, count);
    byte[] taken = Arrays.copyOfRange(data, at, at + count);
    at += count;
    return taken;
  }

  public Cursor skip(int count) {
    need(at, count);
    at += count;
    return this;
  }

  /** All that is left, which is then read. */
  public byte[] rest() {
    return take(left());
  }

  public int u8At(int offset) {
    need(offset, 1);
    return data[offset] & 0xff;
  }

  public int u16At(int offset) {
    need(offset, 2);
    return (data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8;
  }

  private void need(int offset, int count) {
    if (count < 0 || offset < 0 || offset + count > data.length) {
      throw new Refused(count + " bytes wanted at " + offset + ", and the file has " + data.length);
    }
  }
}
