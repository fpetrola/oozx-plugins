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


package com.fpetrola.oozx.speccy.tools.games;

import com.fpetrola.oozx.api.Catalogues;
import com.fpetrola.oozx.api.GameDetail;
import com.fpetrola.oozx.api.GameSummary;
import com.fpetrola.oozx.api.Hit;
import com.fpetrola.oozx.api.KnowsTheGames;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.WhatGameThisIs;
import com.fpetrola.oozx.speccy.media.LocalGames;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que este jar sabe de juegos, que es lo que el escritorio no sabe: cual es el que esta
 * cargado y que dice el catalogo de el.
 * <p>
 * Antes esto vivia en el escritorio, que para mostrar una caratula tenia que hablarle al
 * catalogo de ZXInfo. Un emulador sin este jar ahora no muestra detalles, y lo dice.
 */
public class TheGamesKnown implements WhatGameThisIs {

  /**
   * Cual es, dicho por el catalogo y no por el nombre del archivo. Un archivo llamado
   * RENE256.SNA no encontraba ningun poke y mandaba los detalles a lo primero que devolviera
   * una busqueda.
   */
  @Override
  public Desk.Game gameIn(String file) {
    if (!java.nio.file.Files.isRegularFile(Path.of(file))) return null;
    if (file.toLowerCase().endsWith(".zip")) return gameInside(file);
    if (file.toLowerCase().endsWith(".rzx")) return gameRecorded(file);
    GameSummary known = LocalGames.whoIs(Path.of(file));
    return known == null ? null : new Desk.Game(file, machineOf(known.id), known.id, known.title);
  }

  /** A zip is not a game but has one inside: the first of its files that the catalogue knows, unpacked where the desk unpacks it. */
  private Desk.Game gameInside(String zip) {
    try {
      return com.fpetrola.oozx.speccy.media.DownloadAndUnzip.fetchAll(new java.io.File(zip).toURI().toString()).stream()
          .map(part -> gameIn(part.toString())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    } catch (java.io.IOException | RuntimeException notUnpacked) {
      return null;
    }
  }

  /** A recording starts from a snapshot it carries inside, and that snapshot is the game: it is what the catalogue is asked about. */
  private Desk.Game gameRecorded(String recording) {
    try {
      com.fpetrola.z80.ide.rzx.SnapshotBlock carried = new com.fpetrola.z80.ide.rzx.RzxParser().parseFile(recording).getSnapshotBlock();
      Path snapshot = java.nio.file.Files.createTempFile("recorded-", "." + carried.getSnapshotExtension().trim().toLowerCase());
      try {
        java.nio.file.Files.write(snapshot, carried.getSnapshotData());
        Desk.Game known = gameIn(snapshot.toString());
        return known == null ? null : new Desk.Game(recording, known.machine(), known.id(), known.title());
      } finally {
        java.nio.file.Files.deleteIfExists(snapshot);
      }
    } catch (java.io.IOException | RuntimeException notRead) {
      return null;
    }
  }

  /**
   * The machine the game was made for, as the catalogue that travels with the emulator says, so that a tape, which
   * does not say what it wants, starts on the right one. Only that catalogue is asked: this is on the way of
   * opening a file, and the one on the web can take seconds.
   */
  private static String machineOf(String id) {
    try {
      return GamesFrame.emulated(new com.fpetrola.oozx.api.Zxdb().game(id).machineType);
    } catch (RuntimeException notInIt) {
      return null;
    }
  }

  @Override
  public void show(Desk.Game game) {
    GameCard card = new GameCard(game, again -> show(new Desk.Game(null, null, null, again)));
    Desk.theOne().place(card);
    // At the right edge of the desk and no taller than it, so that it lands whole beside the machine playing the game.
    card.setSize(card.getWidth(), Math.min(card.getHeight(), Math.max(300, card.getParent().getHeight() - 24)));
    card.setLocation(Math.max(0, card.getParent().getWidth() - card.getWidth() - 12), 12);
    new SwingWorker<GameDetail, Void>() {
      protected GameDetail doInBackground() {
        Desk.Game known = game.id() != null || game.file() == null ? game : gameIn(game.file());
        return lookUp(known == null ? game : new Desk.Game(game.file(), game.machine(), known.id(), known.title()));
      }

      protected void done() {
        try {
          card.fill(get());
        } catch (Exception itWouldNot) {
          card.fill(null);
        }
      }
    }.execute();
  }

  /**
   * Por su numero cuando se lo sabe, por el archivo cuando el catalogo lo reconoce, y recien al
   * final por el nombre, que es una adivinanza: el primer resultado de una busqueda no tiene por
   * que ser el juego que esta corriendo.
   */
  private GameDetail lookUp(Desk.Game game) {
    String id = game.id();
    if (id == null && game.file() != null) {
      Desk.Game known = gameIn(game.file());
      if (known != null) id = known.id();
    }
    if (id != null) {
      return withSomethingToShow(Catalogues.details(id), game);
    }
    String name = nameOf(game);
    if (name == null || name.isEmpty()) return null;
    List<Hit> found = Catalogues.search(name, KnowsTheGames.Slice.EVERYTHING);
    return found == null || found.isEmpty() ? null
        : withSomethingToShow(Catalogues.details(found.get(0)._id), game);
  }

  /** Lo que el catalogo no contesto, dicho con lo poco que se sabe, en vez de una ventana vacia. */
  private static GameDetail withSomethingToShow(GameDetail detail, Desk.Game game) {
    if (detail != null) return detail;
    GameDetail little = new GameDetail();
    little.id = game.id();
    little.title = nameOf(game);
    little.screenshots = new ArrayList<>();
    little.description = "Game description not available";
    return little;
  }

  /** Como llamarlo: lo que diga el catalogo, y si no el archivo sin su extension. */
  private static String nameOf(Desk.Game game) {
    if (game.title() != null && !game.title().isEmpty()) return game.title();
    if (game.file() == null) return null;
    return Path.of(game.file()).getFileName().toString()
        .replaceAll("\\.(z80|sna|tap|tzx|szx|dsk|vg)$", "");
  }
}
