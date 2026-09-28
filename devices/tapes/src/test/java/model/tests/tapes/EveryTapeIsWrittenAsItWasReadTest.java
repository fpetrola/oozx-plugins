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

package model.tests.tapes;

import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import com.fpetrola.oozx.tapes.CswFormat;
import com.fpetrola.oozx.tapes.TapFormat;
import com.fpetrola.oozx.tapes.TzxFormat;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Every tape a format reads it writes back byte for byte: the declaration of each block is read in
 * one direction and written in the other, so nothing a file had is lost on the way. A TZX is
 * compared past its version, which is written as the one this writes.
 */
class EveryTapeIsWrittenAsItWasReadTest {

  static final Path TAPES = Path.of("../tape/src/test/resources/tapes");
  static final List<TapeFormat> FORMATS = List.of(new TapFormat(), new TzxFormat(), new CswFormat());

  static Stream<Path> tapes() throws IOException {
    return Files.walk(TAPES).filter(Files::isRegularFile).filter(file -> !file.toString().contains("goldens"))
        .filter(file -> FORMATS.stream().anyMatch(format -> format.reads(file.toFile()))).sorted();
  }

  @TestFactory
  Stream<DynamicTest> whatIsReadIsWrittenTheSame() throws IOException {
    return tapes().map(tape -> DynamicTest.dynamicTest(TAPES.relativize(tape).toString(), () -> {
      TapeFormat format = FORMATS.stream().filter(one -> one.reads(tape.toFile())).findFirst().orElseThrow();
      byte[] file = Files.readAllBytes(tape);
      byte[] written;
      try {
        written = format.write(format.read(file));
      } catch (TapeRefused refused) {
        return;
      }
      if (format instanceof TzxFormat && file.length >= 10) {
        assertArrayEquals(Arrays.copyOfRange(file, 10, file.length), Arrays.copyOfRange(written, 10, written.length));
      } else {
        assertArrayEquals(file, written);
      }
    }));
  }
}
