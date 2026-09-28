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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What number a format writes for each value of a choice, and what it means by each number it
 * reads: the table a switch would be. A number is a value, or a mask whose bits all have to be set;
 * they are tried in order, and a value is written as its first.
 */
public final class Codes<E> {

  private record Code<E>(int number, E value, boolean mask) {
    boolean matches(int read) {
      return mask ? (read & number) == number : read == number;
    }
  }

  private final String what;
  private final List<Code<E>> codes;
  private final Optional<E> otherwise;

  private Codes(String what, List<Code<E>> codes, Optional<E> otherwise) {
    this.what = what;
    this.codes = List.copyOf(codes);
    this.otherwise = otherwise;
  }

  /** Codes named as a refusal names them: "hardware 42". */
  public static <E> Codes<E> of(String what) {
    return new Codes<E>(what, List.<Code<E>>of(), Optional.<E>empty());
  }

  public Codes<E> is(int number, E value) {
    return with(new Code<>(number, value, false));
  }

  public Codes<E> mask(int bits, E value) {
    return with(new Code<>(bits, value, true));
  }

  /** What a number nobody knows means, and how a value without a number is written. */
  public Codes<E> otherwise(E value) {
    return new Codes<>(what, codes, Optional.of(value));
  }

  public Optional<E> find(int number) {
    return codes.stream().filter(code -> code.matches(number)).map(Code::value).findFirst();
  }

  public E decode(int number) {
    return find(number).or(() -> otherwise).orElseThrow(() -> new Refused(what + " " + number + " is not known"));
  }

  public int encode(E value) {
    return codes.stream().filter(code -> code.value().equals(value)).mapToInt(Code::number).findFirst()
        .orElseGet(() -> otherwise.filter(fallback -> !fallback.equals(value)).map(this::encode)
            .orElseThrow(() -> new Refused(what + " has no number for " + value)));
  }

  private Codes<E> with(Code<E> code) {
    List<Code<E>> more = new ArrayList<>(codes);
    more.add(code);
    return new Codes<>(what, more, otherwise);
  }
}
