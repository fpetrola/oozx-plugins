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
import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.speccy.machine.Spectrum;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A snapshot format as a declaration: the extensions it answers to, the shape of a file and of a
 * machine, and its bindings. Reading and writing are the same for every format, and are here:
 * reading understands the whole file before it chooses the machine and walks it; writing walks the
 * machine into a file and puts it together.
 */
public abstract class DeclaredFormat implements SnapshotFormat {

  /** The extensions its files go by, without the dot. */
  protected abstract List<String> extensions();

  protected abstract Bindings bindings();

  /** The shape of this file, from as little of it as it takes. */
  protected abstract Shape shapeOf(Cursor peek);

  /** The shape a machine is written in, which may be another machine's. */
  protected abstract Shape shapeFor(SpectrumMachine machine);

  /** Whether it writes; a format that only reads says so. */
  protected boolean writesFiles() {
    return true;
  }

  @Override
  public boolean reads(File file) {
    return extensions().contains(extensionOf(file));
  }

  @Override
  public boolean writes(File file) {
    return writesFiles() && reads(file);
  }

  @Override
  public final void read(byte[] bytes, Speccy speccy, Consumer<String> notes) throws SnapshotException {
    SnapshotFile file = understood(bytes);
    become(speccy, file);
    plug(file, speccy);
    speccy.accept(part -> bindings().apply(part, Direction.reading(file)));
    file.notes().forEach(notes);
  }

  /** The file whole, or refused: until this answers, the machine has not been touched. */
  public final SnapshotFile understood(byte[] bytes) throws SnapshotException {
    try {
      return shapeOf(Cursor.over(bytes)).parse(bytes);
    } catch (Refused refused) {
      throw new SnapshotException(label() + ": " + refused.getMessage(), refused);
    } catch (RuntimeException fall) {
      throw new SnapshotException(label() + " that could not be read: " + fall, fall);
    }
  }

  @Override
  public final byte[] write(Speccy speccy, Consumer<String> notes) throws SnapshotException {
    if (!writesFiles()) {
      return SnapshotFormat.super.write(speccy, notes);
    }
    Shape shape = shapeFor(speccy.machine.current);
    SnapshotFile file = shape.empty();
    if (speccy.machine.current.snapshotModel() != shape.machine()) {
      file.note("written as: " + shape.machine().getLongModelName());
    }
    speccy.accept(part -> bindings().apply(part, Direction.writing(file)));
    try {
      byte[] bytes = shape.assemble(file);
      file.notes().forEach(notes);
      return bytes;
    } catch (Refused refused) {
      throw new SnapshotException(label() + ": " + refused.getMessage(), refused);
    }
  }

  /** What the file says is plugged in, plugged in before the machine is walked, so the walk meets it. */
  protected void plug(SnapshotFile file, Speccy speccy) {
  }

  /**
   * Becomes the machine the file is of, if it is not already it: choosing a model resets it, and
   * one that already is that model has nothing to gain from it. A machine this build does not have
   * is said to the person, and the file goes into the one that was running, as the loader before
   * this did: a recording taken on a +2A still opens where there is no +2A.
   */
  private void become(Speccy speccy, SnapshotFile file) {
    MachineTypes wanted = file.shape().machine();
    java.util.Optional<Spectrum> model = speccy.machine.forSnapshotModel(wanted);
    if (model.isEmpty()) {
      String running = speccy.machine.current == null ? "machine" : speccy.machine.current.getName();
      file.note("this build has no " + wanted.getLongModelName() + ": loaded into the " + running + " that was running");
      com.fpetrola.oozx.TellsThePerson.thisBuildCannot(("This snapshot was taken on a %s, and this build carries no machine that runs its code. "
          + "It was loaded into the %s that was already running, where it will most likely show nothing at all.")
          .formatted(wanted.getLongModelName(), running), wanted.name());
    } else if (speccy.machine.current != model.get()) {
      speccy.machine.select(model.get());
    }
  }

  private static String extensionOf(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
