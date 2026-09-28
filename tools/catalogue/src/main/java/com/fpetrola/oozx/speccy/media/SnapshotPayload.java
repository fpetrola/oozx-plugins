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

package com.fpetrola.oozx.speccy.media;

import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.emulation.helpers.snapshots.SnapshotFactory;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.formats.SnapshotFormat;
import com.fpetrola.oozx.plugins.Plugins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * The game's own bytes inside a file: the RAM of a snapshot, bank after bank, and anything else as
 * it came. A format that is a declaration understands a file without a machine, so the RAM is
 * taken from that; a file only the old readers know goes to them.
 */
public final class SnapshotPayload {

  private SnapshotPayload() {
  }

  public static byte[] of(File file) throws IOException {
    for (SnapshotFormat format : Plugins.found(SnapshotFormat.class)) {
      if (format.reads(file) && format instanceof DeclaredFormat declared) {
        byte[] bytes = Files.readAllBytes(file.toPath());
        try {
          SnapshotFile understood = declared.understood(bytes);
          ByteArrayOutputStream ram = new ByteArrayOutputStream();
          understood.pages().forEach(bank -> ram.writeBytes(understood.pageOrZeros(bank)));
          return ram.toByteArray();
        } catch (SnapshotException unreadable) {
          // A snapshot that cannot be opened is still a file somebody has, and its bytes are a
          // worse fingerprint than its RAM but a better one than nothing.
          return bytes;
        }
      }
    }
    return SnapshotFactory.payloadOf(file);
  }
}
