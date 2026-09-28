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

import com.fpetrola.emulation.helpers.machine.MachineTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * How one file of a format is laid out: the machine it is of, its stretches in order, the rules
 * that cross them, and the banks of RAM that machine has.
 */
public record Shape(MachineTypes machine, List<Region> regions, List<Rule> rules, List<Integer> banks) {

  public static Shape of(MachineTypes machine, Region... regions) {
    return new Shape(machine, List.of(regions), List.of(), banksOf(machine));
  }

  /** The banks a machine has: the 16K one, the 48K three, the rest eight. */
  public static List<Integer> banksOf(MachineTypes machine) {
    if (machine == MachineTypes.SPECTRUM16K) return List.of(5);
    if (machine.codeModel == MachineTypes.CodeModel.SPECTRUM48K) return List.of(5, 2, 0);
    return IntStream.range(0, 8).boxed().toList();
  }

  public Shape with(Rule... more) {
    List<Rule> all = new ArrayList<>(rules);
    all.addAll(List.of(more));
    return new Shape(machine, regions, all, banks);
  }

  /** The same layout, as another machine. */
  public Shape as(MachineTypes other) {
    return new Shape(other, regions, rules, banksOf(other));
  }

  /** The file understood whole: every stretch read, the rules run, and what is left over noted. */
  public SnapshotFile parse(byte[] bytes) {
    SnapshotFile file = new SnapshotFile(this);
    Cursor in = Cursor.over(bytes);
    regions.forEach(region -> region.parse(in, file));
    rules.forEach(rule -> rule.afterParsing(file));
    if (in.left() > 0) {
      file.note("skipped: " + in.left() + " bytes at the end");
    }
    return file;
  }

  /** A file of this shape with nothing in it yet, for the parts to fill. */
  public SnapshotFile empty() {
    SnapshotFile file = new SnapshotFile(this);
    regions.stream().filter(Fixed.class::isInstance).map(Fixed.class::cast).forEach(region -> file.put(region, region.empty()));
    return file;
  }

  /** The rules, and then every stretch in order. */
  public byte[] assemble(SnapshotFile file) {
    rules.forEach(rule -> rule.beforeAssembling(file));
    Sink out = new Sink();
    regions.forEach(region -> region.assemble(file, out));
    return out.toBytes();
  }
}
