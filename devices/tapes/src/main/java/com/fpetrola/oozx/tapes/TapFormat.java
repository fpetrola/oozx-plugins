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
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.TapData;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import dev.crystal.plugins.api.Answers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** TAP: each block a length and the bytes as the ROM saves them, and nothing else. */
@Answers("tap")
public final class TapFormat implements TapeFormat {

  @Override
  public boolean reads(File file) {
    return file.getName().toLowerCase().endsWith(".tap");
  }

  @Override
  public boolean writes(File file) {
    return reads(file);
  }

  @Override
  public Cassette read(byte[] file) throws TapeRefused {
    try {
      Cursor in = Cursor.over(file);
      List<CassetteBlock> blocks = new ArrayList<>();
      while (in.left() > 0) {
        blocks.add(new TapData(in.take(in.u16())));
      }
      return new Cassette(blocks);
    } catch (Refused refused) {
      throw new TapeRefused("a TAP that could not be read: " + refused.getMessage(), refused);
    }
  }

  /** The blocks that are bytes as the ROM loads them; anything else a TAP has no room for. */
  @Override
  public byte[] write(Cassette cassette) throws TapeRefused {
    Sink out = new Sink();
    for (CassetteBlock block : cassette.blocks()) {
      byte[] data = block instanceof TapData tap ? tap.data()
          : block instanceof CassetteBlock.StandardData standard ? standard.data() : null;
      if (data != null) {
        out.u16(data.length).bytes(data);
      }
    }
    return out.toBytes();
  }
}
