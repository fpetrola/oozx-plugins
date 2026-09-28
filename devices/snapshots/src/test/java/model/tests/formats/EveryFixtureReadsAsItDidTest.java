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

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Every snapshot here reads as it read when the goldens were written, field by field - or is
 * refused for the same reason. This is what holds a reader that replaces today's to what today's
 * did, and it needs nothing installed.
 */
class EveryFixtureReadsAsItDidTest {

  @TestFactory
  Stream<DynamicTest> everyFixture() {
    return Fixtures.ofTodaysFormats().stream().map(fixture -> dynamicTest(Fixtures.nameOf(fixture), () ->
        assertEquals(Fixtures.text(Fixtures.golden(fixture, "read")), Goldens.read(fixture))));
  }
}
