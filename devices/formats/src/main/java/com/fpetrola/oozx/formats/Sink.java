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

import java.io.ByteArrayOutputStream;

/** A file written in order, little-endian. */
public final class Sink {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  public Sink u8(int value) {
    bytes.write(value);
    return this;
  }

  public Sink u16(int value) {
    return u8(value).u8(value >> 8);
  }

  public Sink u32(int value) {
    return u16(value).u16(value >> 16);
  }

  public Sink bytes(byte[] data) {
    bytes.writeBytes(data);
    return this;
  }

  public int length() {
    return bytes.size();
  }

  public byte[] toBytes() {
    return bytes.toByteArray();
  }
}
