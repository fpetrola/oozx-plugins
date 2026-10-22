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

import com.fpetrola.oozx.Speccy;
import dev.crystal.plugins.api.RoleInterface;

import java.util.List;

/**
 * What a format carries of parts that are not the machine's own: their tables, the blocks they go
 * in, and what a file plugs in. Said by whoever knows both the format and the parts, so that
 * neither knows the other: a format asks which mappings are for it, and a part never hears of a
 * file.
 */
@RoleInterface
public interface Mapping {

  /** The format it is for; a mapping for {@link DeclaredFormat} is for every format. */
  Class<? extends DeclaredFormat> format();

  /** The tables of the parts it maps, and the parts it knows have nothing a file carries. */
  Bindings bindings();

  /** The blocks of the file those tables write into, for a format whose file is made of blocks. */
  default List<Fixed> blocks() {
    return List.of();
  }

  /**
   * What the file says is plugged in, plugged in before the machine is walked, so the walk meets it.
   * Switching it on, and the reset some ask for, comes after every mapping has plugged its own.
   */
  default void plug(SnapshotFile file, Speccy speccy) {
  }
}
