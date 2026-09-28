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
import com.fpetrola.oozx.speccy.peripherals.Peripheral;

import java.util.ArrayList;
import java.util.List;

/**
 * Which table a format uses for each kind of part. A part is met as what it is: a binding for a
 * class or an interface takes whatever part is one. A peripheral no binding takes is what the
 * format does not carry, unless it is one the format says holds nothing worth carrying.
 */
public final class Bindings {

  private record Binding<P>(Class<P> type, Layout<P> layout) {
    boolean takes(Visitable part) {
      return type.isInstance(part);
    }

    void apply(Visitable part, Direction way) {
      way.bind(layout, type.cast(part));
    }
  }

  private final List<Binding<?>> bindings;
  private final List<Class<?>> quiet;

  private Bindings(List<Binding<?>> bindings, List<Class<?>> quiet) {
    this.bindings = List.copyOf(bindings);
    this.quiet = List.copyOf(quiet);
  }

  public static Bindings of() {
    return new Bindings(List.of(), List.of());
  }

  public <P> Bindings on(Class<P> type, Layout<P> layout) {
    List<Binding<?>> more = new ArrayList<>(bindings);
    more.add(new Binding<>(type, layout));
    return new Bindings(more, quiet);
  }

  /** Peripherals that hold nothing a file would carry, and are not said as left out. */
  public Bindings quiet(Class<?>... types) {
    List<Class<?>> more = new ArrayList<>(quiet);
    more.addAll(List.of(types));
    return new Bindings(bindings, more);
  }

  public void apply(Visitable part, Direction way) {
    boolean taken = false;
    for (Binding<?> binding : bindings) {
      if (binding.takes(part)) {
        binding.apply(part, way);
        taken = true;
      }
    }
    if (!taken && part instanceof Peripheral && quiet.stream().noneMatch(type -> type.isInstance(part))) {
      way.unbound(part);
    }
  }
}
