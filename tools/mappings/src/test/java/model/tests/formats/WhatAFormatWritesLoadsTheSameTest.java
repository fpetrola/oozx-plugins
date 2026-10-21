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

package model.tests.formats;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.plugins.Plugins;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a format writes from a machine, loaded into another machine, leaves it the same, and is
 * written again the same, byte for byte: for every fixture any format reads, written by every
 * format that writes. The first write may lose what the format cannot carry; after it, nothing more.
 * A machine a format cannot write - a 48K SNA with nowhere to push the PC - is refused, and that is all.
 */
class WhatAFormatWritesLoadsTheSameTest {

  static Speccy machine() {
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    return speccy;
  }

  @TestFactory
  Stream<DynamicTest> writtenLoadedAndWrittenAgainIsTheSame() {
    return Plugins.found(SnapshotFormat.class).stream().filter(format -> format.writes(new java.io.File("x." + extension(format))))
        .flatMap(writer -> Fixtures.all().stream()
            .filter(fixture -> Plugins.found(SnapshotFormat.class).stream().anyMatch(reader -> reader.reads(fixture.toFile())))
            .map(fixture -> DynamicTest.dynamicTest(Fixtures.nameOf(fixture) + " -> " + writer.label(), () -> roundTrip(fixture, writer))));
  }

  private static String extension(SnapshotFormat format) {
    return format.getClass().getAnnotation(dev.crystal.plugins.api.Answers.class).value()[0];
  }

  private static void roundTrip(Path fixture, SnapshotFormat writer) throws Exception {
    SnapshotFormat reader = Plugins.found(SnapshotFormat.class).stream().filter(one -> one.reads(fixture.toFile())).findFirst().orElseThrow();
    Speccy first = machine(), second = machine(), third = machine();
    try {
      try {
        reader.read(Fixtures.bytes(fixture), first, note -> { });
      } catch (com.fpetrola.emulation.helpers.snapshots.SnapshotException refused) {
        return;
      }
      byte[] once;
      try {
        once = writer.write(first, note -> { });
      } catch (com.fpetrola.emulation.helpers.snapshots.SnapshotException cannotBeWrittenThere) {
        return;
      }
      writer.read(once, second, note -> { });
      byte[] twice = writer.write(second, note -> { });
      writer.read(twice, third, note -> { });
      assertEquals(MachineDescription.of(second), MachineDescription.of(third));
      assertArrayEquals(once, twice);
    } finally {
      first.end();
      second.end();
      third.end();
    }
  }
}
