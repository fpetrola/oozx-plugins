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

package com.fpetrola.oozx.mappings;

import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Layout.Medium;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.modules.tape.Tape;
import dev.crystal.plugins.api.OnlyWith;

import java.io.File;
import java.util.List;

/**
 * The deck in an SZX: TAPE says where the tape in it is and at which of its blocks the deck stands. A machine never
 * writes on a tape, so a tape goes by its file, which lasts: what comes out of a zip or the catalogue is kept with
 * the person's things. An empty deck writes no TAPE. A tape another emulator put inside the block is not read yet.
 */
@OnlyWith("device-tape")
public final class TapeMapping implements Mapping {

  static final Fixed TAPE = Fixed.of("TAPE", 28);

  static final Layout<Tape> TABLE = Layout.<Tape>of()
      .medium(TAPE, 2, 0, 1, 4, 8, -1, Field.of("tape.file", deck -> Medium.inFile(deck.getTapeFilename().getPath(), 0), TapeMapping::insert))
      .u16(TAPE, 0, Field.of("tape.block", Tape::getSelectedBlock, Tape::setSelectedBlock))
      .writtenWhen(deck -> deck.getTapeFilename() != null);

  /** The tape the file says, in place of whatever the deck had: it takes one only when it is empty and still. */
  private static void insert(Tape deck, Medium medium) {
    if (medium.isInside()) return;
    deck.stop();
    deck.eject();
    deck.insert(new File(medium.file()));
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(Tape.class, TABLE);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(TAPE);
  }
}
