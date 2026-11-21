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

package com.fpetrola.oozx.speccy.tools.looks;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The windows a plugin opens, drawn while its jar is built, in the look the desk wears first: one PNG per thing that
 * opens one, under META-INF/pictures and named after its class, so that the jar carries a picture of what it brings and
 * the catalogue can show it before the plugin is here. It knows nothing of what opens a window but that its class is
 * listed in one of the services files named, and that it answers open().
 * <p>
 * Run by the build in a java of its own, on the plugin's classpath: {@code <classes folder> <services file>...}.
 */
public final class DrawsTheWindows {

  public static void main(String[] args) throws Exception {
    Path classes = Path.of(args[0]);
    Path pictures = classes.resolve("META-INF/pictures");
    new DarklafLooks().wear("Solarized Light");
    for (int i = 1; i < args.length; i++) {
      Path listed = classes.resolve("META-INF/services").resolve(args[i]);
      if (!Files.exists(listed)) continue;
      for (String name : Files.readAllLines(listed)) {
        if (name.isBlank() || name.startsWith("#")) continue;
        try {
          Class<?> kind = Class.forName(name.trim(), true, Thread.currentThread().getContextClassLoader());
          Object opening = kind.getConstructor().newInstance();
          BufferedImage picture = dev.plugdesk.desk.Widgets.drawn((JComponent) kind.getMethod("open").invoke(opening));
          if (picture == null || picture.getWidth() < 8 || picture.getHeight() < 8) continue;
          Files.createDirectories(pictures);
          ImageIO.write(picture, "png", pictures.resolve(name.trim() + ".png").toFile());
        } catch (Throwable cannotBeDrawnHere) {
          System.err.println("No picture of " + name + ": " + cannotBeDrawnHere);
        }
      }
    }
    // What the windows started - timers, a gamepad being looked for - is not waited for: the pictures are written.
    System.exit(0);
  }
}
