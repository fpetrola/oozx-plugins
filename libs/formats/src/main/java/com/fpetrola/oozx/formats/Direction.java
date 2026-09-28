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

import com.fpetrola.oozx.speccy.parts.Visitable;

/** Which way a table is used: reading a file into the parts, or writing the parts into a file. */
public interface Direction {

  <P> void bind(Layout<P> layout, P part);

  /** A part no binding of the format takes. */
  void unbound(Visitable part);

  static Direction reading(SnapshotFile file) {
    return new Direction() {
      public <P> void bind(Layout<P> layout, P part) {
        layout.read(file, part);
      }

      public void unbound(Visitable part) {
      }
    };
  }

  static Direction writing(SnapshotFile file) {
    return new Direction() {
      public <P> void bind(Layout<P> layout, P part) {
        layout.write(part, file);
      }

      public void unbound(Visitable part) {
        file.note("not carried: " + part.getClass().getSimpleName());
      }
    };
  }
}
