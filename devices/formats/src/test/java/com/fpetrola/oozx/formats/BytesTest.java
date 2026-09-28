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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Bytes read in order, written in order, and a stretch read and written at places: little-endian, as the Spectrum. */
class BytesTest {

  @Test
  void aCursorReadsInOrderAndRefusesToRunOut() {
    Cursor in = Cursor.over(new byte[]{1, 0x34, 0x12, (byte) 0xaa});
    assertEquals(1, in.u8());
    assertEquals(0x1234, in.u16());
    assertEquals(0x1234, in.u16At(1));
    assertArrayEquals(new byte[]{(byte) 0xaa}, in.take(1));
    Refused refused = assertThrows(Refused.class, in::u8);
    assertEquals("1 bytes wanted at 4, and the file has 4", refused.getMessage());
  }

  @Test
  void aSinkWritesInOrder() {
    assertArrayEquals(new byte[]{1, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12}, new Sink().u8(1).u16(0x1234).u32(0x12345678).toBytes());
  }

  @Test
  void aStretchCountsFromWhereItStarts() {
    Bytes stretch = Bytes.zeros(30, 4).u16(30, 0x1234).u8(33, 0x0f).or(33, 0xf0);
    assertArrayEquals(new byte[]{0x34, 0x12, 0, (byte) 0xff}, stretch.toBytes());
    assertThrows(IndexOutOfBoundsException.class, () -> stretch.u8(29));
  }

  @Test
  void codesGoBothWaysAndSayWhatTheyDoNotKnow() {
    Codes<String> codes = Codes.<String>of("colour").is(0, "red").is(1, "green").mask(0x44, "fuller");
    assertEquals("green", codes.decode(1));
    assertEquals(0x44, codes.encode("fuller"));
    assertEquals("colour 9 is not known", assertThrows(Refused.class, () -> codes.decode(9)).getMessage());
    assertEquals("red", codes.otherwise("red").decode(9));
  }

  @Test
  void zlibGoesAndComesBack() {
    byte[] page = new byte[0x4000];
    assertArrayEquals(page, Codec.ZLIB.decode(Codec.ZLIB.encode(page), 0x4000));
    assertThrows(Refused.class, () -> Codec.ZLIB.decode(new byte[]{1, 2, 3}, 3));
  }
}
