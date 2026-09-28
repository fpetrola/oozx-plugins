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

import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.plugins.Plugins;
import com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice;
import com.fpetrola.oozx.speccy.modules.sound.SoundCard;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What every format that walks the machine owes: a file cut anywhere is refused, with a
 * SnapshotException and nothing else, and the machine is as it was - the file is understood
 * whole before the machine is touched. Cut at every byte of the first 128, and then every 997.
 */
class EveryFormatRefusesACutFileTest {

  @TestFactory
  Stream<DynamicTest> aCutFileIsRefusedAndTheMachineIsUntouched() {
    return Plugins.found(SnapshotFormat.class).stream().flatMap(format -> Fixtures.all().stream()
        .filter(fixture -> format.reads(fixture.toFile()))
        .map(fixture -> DynamicTest.dynamicTest(format.label() + " " + Fixtures.nameOf(fixture), () -> cuts(format, fixture))));
  }

  private static void cuts(SnapshotFormat format, Path fixture) {
    byte[] whole = Fixtures.bytes(fixture);
    Speccy speccy = Speccy.create(new SpectrumZ80Clock(), binder -> binder.bind(SoundCard.class).to(SilentSoundDevice.class));
    speccy.init();
    speccy.picture.active = false;
    try {
      String before = MachineDescription.of(speccy);
      IntStream.concat(IntStream.range(0, Math.min(128, whole.length)), IntStream.iterate(128, at -> at < whole.length, at -> at + 997))
          .forEach(length -> {
            byte[] cut = Arrays.copyOf(whole, length);
            assertThrows(SnapshotException.class, () -> format.read(cut, speccy, note -> { }), "cut at " + length);
          });
      assertEquals(before, MachineDescription.of(speccy));
    } finally {
      speccy.end();
    }
  }
}
