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

import java.util.List;

/**
 * Some bytes of a stretch with a rule of their own for a part: a frame counter that depends on the
 * machine's frame, R split in two bytes. Reads and writes both ways, like any line of a table.
 */
public interface Encoding<P> {

  void read(Bytes bytes, P part, SnapshotFile file);

  void write(P part, Bytes bytes, SnapshotFile file);

  /** The fields it reads and writes. */
  List<Object> fields();
}
