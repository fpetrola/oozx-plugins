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
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** How bytes travel in a file: as they are, or packed. Unpacking says how long the result has to be. */
public interface Codec {

  byte[] decode(byte[] packed, int length);

  byte[] encode(byte[] raw);

  /** The bytes as they are. */
  Codec RAW = new Codec() {
    public byte[] decode(byte[] packed, int length) {
      if (packed.length != length) {
        throw new Refused(packed.length + " bytes where there should be " + length);
      }
      return packed;
    }

    public byte[] encode(byte[] raw) {
      return raw;
    }
  };

  /** zlib, as SZX packs its pages. */
  Codec ZLIB = new Codec() {
    public byte[] decode(byte[] packed, int length) {
      Inflater inflater = new Inflater();
      try {
        inflater.setInput(packed);
        byte[] out = new byte[length];
        int made = 0;
        while (made < length && !inflater.finished()) {
          int now = inflater.inflate(out, made, length - made);
          if (now == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
            break;
          }
          made += now;
        }
        if (made != length) {
          throw new Refused("zlib gave " + made + " bytes where there should be " + length);
        }
        return out;
      } catch (DataFormatException notZlib) {
        throw new Refused("not zlib: " + notZlib.getMessage(), notZlib);
      } finally {
        inflater.end();
      }
    }

    public byte[] encode(byte[] raw) {
      Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
      try {
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
          out.write(buffer, 0, deflater.deflate(buffer));
        }
        return out.toByteArray();
      } finally {
        deflater.end();
      }
    }
  };
}
