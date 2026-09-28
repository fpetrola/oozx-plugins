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

package com.fpetrola.oozx.tapes;

import com.fpetrola.oozx.formats.Cursor;
import com.fpetrola.oozx.formats.Refused;
import com.fpetrola.oozx.formats.Sink;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassette;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.CswTape;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import dev.crystal.plugins.api.Answers;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * CSW: a recording, as the lengths of its pulses in samples. Version 1 says its rate in two bytes
 * and starts its data at 0x20; version 2 in four, and after a header of its own that can grow.
 */
@Answers("csw")
public final class CswFormat implements TapeFormat {

  @Override
  public boolean reads(File file) {
    return file.getName().toLowerCase().endsWith(".csw");
  }

  @Override
  public boolean writes(File file) {
    return reads(file);
  }

  @Override
  public Cassette read(byte[] file) throws TapeRefused {
    try {
      Cursor in = Cursor.over(file);
      int major = in.u8At(0x17);
      int start;
      CswTape tape;
      if (major == 0x01) {
        start = 0x20;
        tape = new CswTape(major, in.u16At(0x19), in.u8At(0x1b), (in.u8At(0x1c) & 0x01) != 0,
            Arrays.copyOf(file, start), Arrays.copyOfRange(file, start, file.length));
      } else {
        start = 0x34 + file[0x23];
        int rate = in.u16At(0x19) | in.u16At(0x1b) << 16;
        tape = new CswTape(major, rate, in.u8At(0x21), (in.u8At(0x22) & 0x01) != 0,
            Arrays.copyOf(file, start), Arrays.copyOfRange(file, start, file.length));
      }
      return new Cassette(List.of(tape));
    } catch (Refused | IndexOutOfBoundsException | IllegalArgumentException refused) {
      throw new TapeRefused("a CSW that could not be read: " + refused.getMessage(), refused);
    }
  }

  @Override
  public byte[] write(Cassette cassette) throws TapeRefused {
    if (cassette.blocks().size() != 1 || !(cassette.blocks().get(0) instanceof CswTape tape)) {
      throw new TapeRefused("a CSW is one recording, and nothing else");
    }
    return new Sink().bytes(tape.header()).bytes(tape.data()).toBytes();
  }
}
