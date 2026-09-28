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

/** How to reach the pages of RAM a part holds, by bank number. */
public interface PageField<P> {

  byte[] get(P part, int bank);

  void set(P part, int bank, byte[] bytes);

  static <P> PageField<P> of(Getter<P> get, Setter<P> set) {
    return new PageField<>() {
      public byte[] get(P part, int bank) {
        return get.get(part, bank);
      }

      public void set(P part, int bank, byte[] bytes) {
        set.set(part, bank, bytes);
      }
    };
  }

  interface Getter<P> {
    byte[] get(P part, int bank);
  }

  interface Setter<P> {
    void set(P part, int bank, byte[] bytes);
  }
}
