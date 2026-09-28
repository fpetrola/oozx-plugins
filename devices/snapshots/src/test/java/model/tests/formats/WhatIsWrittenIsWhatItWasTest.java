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
 * What each writer makes of every snapshot that reads is what it made when the goldens were
 * written: the same bytes where nothing compresses them, the same fields read back, and the same
 * refusals - including the ones that are crashes today, which are written down to be decided on.
 */
class WhatIsWrittenIsWhatItWasTest {

  @TestFactory
  Stream<DynamicTest> everyFixtureThatReads() {
    return Fixtures.all().stream()
        .filter(fixture -> !TodaysFormats.read(fixture).isRefused())
        .map(fixture -> dynamicTest(Fixtures.nameOf(fixture), () ->
            assertEquals(Fixtures.text(Fixtures.golden(fixture, "written")), Goldens.written(fixture))));
  }
}
