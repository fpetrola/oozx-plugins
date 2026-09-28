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

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * How to reach one thing a part of the machine has, through the part's own API: the register A of
 * the processor, the colour of the border. Not a copy of it, and not a second model of the part.
 */
public interface Field<P, V> {

  /** The name it is said by, in a description of the machine: "a", "border". */
  String label();

  V get(P part);

  void set(P part, V value);

  static <P, V> Field<P, V> of(String label, Function<P, V> get, BiConsumer<P, V> set) {
    return new Field<>() {
      public String label() {
        return label;
      }

      public V get(P part) {
        return get.apply(part);
      }

      public void set(P part, V value) {
        set.accept(part, value);
      }

      public String toString() {
        return label;
      }
    };
  }
}
