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

import com.fpetrola.oozx.api.*;
import com.fpetrola.oozx.speccy.config.OOZxConfiguration;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.media.DownloadAndUnzip;
import com.fpetrola.oozx.speccy.media.LocalGames;
import com.fpetrola.oozx.rzx.RzxArchive;
import com.fpetrola.oozx.rzx.RzxOption;
import com.fpetrola.oozx.rzx.RzxRecording;
import dev.plugdesk.desk.KnowsAbout;
import dev.plugdesk.desk.Shelves;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The games, on the shelves of a catalogue: the ones played lately and the favourites, the ones on this machine and in
 * each folder given, and the ones ZXInfo knows. Filters narrow them; a card is its loading screen, the one picked says
 * what ZXInfo knows of it, and its menu loads it on a machine, as one of its versions, or plays a recording of it.
 */
public class GamesFrame extends JInternalFrame implements Shelves.Of<GameSearchResult> {

  static final String PLAYED = "Played lately";
  static final String FAVOURITES = "Favourites";
  private static final String EVERYWHERE = "Everywhere";
  private static final String ON_THE_NET = "On the net";
  private static final String ON_THIS_MACHINE = "On this machine";
  /**
   * First entry of each filter combo, meaning "do not narrow by this". They name what they filter so the strip needs no
   * labels beside them.
   */
  private static final String ANY_MACHINE = "Any machine";
  private static final String ANY_GENRE = "Any genre";
  private static final String ANY_YEAR = "Any year";
  private static final String AS_FOUND = "As found";
  private static final String MOST_KNOWN = "Most known";
  private static final String BEST_RATED = "Best rated";
  private static final int PLAYED_SHOWN = 30;

  private final RzxArchive archive = new RzxArchive();
  private final OOZxConfiguration config =
      dev.plugdesk.app.Configuration.shared().of(OOZxConfiguration.class);
  private final JComboBox<String> order = new JComboBox<>(new String[]{AS_FOUND, MOST_KNOWN, BEST_RATED});
  private final JComboBox<String> machineFilter = new JComboBox<>(new String[]{ANY_MACHINE});
  private final JComboBox<String> genreFilter = new JComboBox<>(new String[]{ANY_GENRE});
  private final JComboBox<String> yearFilter = new JComboBox<>(java.util.stream.Stream.concat(
      java.util.stream.Stream.of(ANY_YEAR), KnowsTheGames.Slice.years().mapToObj(String::valueOf)).toArray(String[]::new));
  private final JCheckBox rzxFilter = new JCheckBox("RZX");
  private final JCheckBox mapFilter = new JCheckBox("Map");
  private final JCheckBox loadableFilter = new JCheckBox("Loadable", true);
  private final JCheckBox unknownFilter = new JCheckBox("Only unknown");
  private final JCheckBox colourFilter = new JCheckBox("256 colours");
  private final JLabel libraryLabel = new JLabel();
  private Shelves<GameSearchResult> shelves;
  /** While a whole search is put in the controls, so that each one chosen does not search on its own. */
  private boolean choosingAll;
  private boolean loading;

  /** What a file brings beside itself - colours, for one - said by whoever can recognise it. */
  private static String besides(String file) {
    String brings = com.fpetrola.oozx.plugins.BesideTheGame.whatIsBeside(file);
    return brings == null ? "" : "   -   " + brings;
  }

  private static int idOf(String id) {
    try {
      return Integer.parseInt(id);
    } catch (RuntimeException e) {
      return -1;
    }
  }

  /** A card of this catalogue as the desk takes it: which file, which machine, and what it is. */
  private static Desk.Game asAGame(GameSearchResult result) {
    String machine = result.machine != null ? result.machine : catalogued(result.id);
    return new Desk.Game(result.filename, emulated(machine), result.id, result.title);
  }

  /** The machine the catalogue says the game is for, when a file does not say it; null when unknown. */
  private static String catalogued(String id) {
    try {
      return id == null ? null : Catalogues.game(id).machineType;
    } catch (RuntimeException unknown) {
      return null;
    }
  }

  /**
   * This emulator's name for a machine as the catalogue names it: ZXDB says "ZX-Spectrum 128K" or
   * "ZX-Spectrum 48K/128K" where the emulator says "Spectrum 128K", and the biggest one a game
   * lists is the one it gets the most out of. A name chosen from the emulator's own list passes through.
   */
  static String emulated(String catalogued) {
    if (catalogued == null || !(catalogued.startsWith("ZX-Spectrum") || catalogued.startsWith("Pentagon"))) {
      return catalogued;
    }
    String biggest = catalogued.substring(catalogued.lastIndexOf('/') + 1);
    if (catalogued.startsWith("Pentagon")) return "Pentagon";
    if (biggest.contains("+2A") || biggest.contains("+3")) return "Spectrum Plus 3";
    if (biggest.contains("+2")) return "Spectrum Plus 2";
    if (biggest.contains("128")) return "Spectrum 128K";
    if (biggest.contains("16K") && !catalogued.contains("48K")) return "Spectrum 16K";
    return "Spectrum 48K";
  }

  public GamesFrame() {
    super("Game Browser", true, true, true, true);
    setSize(1150, 700);
    setLocation(50, 50);
    order.setToolTipText("<html>Most known: the most voted at World of Spectrum first, which is who has played it<br>"
        + "Best rated: the score, weighed by how many voted, so a few high votes do not beat many</html>");
    machineFilter.setToolTipText("Narrow to one machine, applied by the server");
    genreFilter.setToolTipText("Narrow to one genre, applied by the server");
    yearFilter.setToolTipText("Narrow to the year of first release, applied by the server");
    rzxFilter.setToolTipText("Only games with a recorded playthrough to replay");
    mapFilter.setToolTipText("Only games with a game map");
    loadableFilter.setToolTipText("Hide entries with nothing to download");
    unknownFilter.setToolTipText("Games on this machine the catalogue could not name");
    colourFilter.setToolTipText("Games with Spec256 colours of their own beside them");
    ActionListener again = e -> {
      if (!choosingAll && shelves != null) shelves.again();
    };
    for (JComboBox<String> combo : List.of(order, machineFilter, genreFilter, yearFilter)) combo.addActionListener(again);
    for (JCheckBox box : List.of(rzxFilter, mapFilter, loadableFilter, unknownFilter, colourFilter)) {
      box.setOpaque(false);
      box.addActionListener(again);
    }
    loadFilterValues();
    sayWhatIsOnThisMachine();
    shelves = new Shelves<>(this, config.getSnapshotHistory().isEmpty() ? EVERYWHERE : PLAYED,
        (name, looked) -> Desk.theOne().show(new dev.plugdesk.desk.Window() {
          public String path() {
            return name;
          }

          public JComponent open() {
            return looked;
          }
        }));
    add(shelves, BorderLayout.CENTER);
  }

  public String title() {
    return "Games";
  }

  public String subtitle() {
    return "Played here, kept, on this machine, and every one ZXInfo knows";
  }

  public String finding() {
    return "Find by title";
  }

  public List<Shelves.Shelf> shelves() {
    List<Shelves.Shelf> shelves = new ArrayList<>();
    shelves.add(new Shelves.Shelf("Yours", PLAYED, Math.min(PLAYED_SHOWN, config.getSnapshotHistory().size())));
    shelves.add(new Shelves.Shelf(null, FAVOURITES, config.getFavorites().size()));
    shelves.add(new Shelves.Shelf(ON_THIS_MACHINE, ON_THIS_MACHINE, -1));
    config.getGameFolders().forEach(folder -> shelves.add(new Shelves.Shelf(null, folder, -1)));
    shelves.add(new Shelves.Shelf("On the net", ON_THE_NET, -1));
    shelves.add(new Shelves.Shelf(null, EVERYWHERE, -1));
    return shelves;
  }

  public boolean far() {
    return true;
  }

  public boolean asksOnEveryCard() {
    return false;
  }

  /**
   * What the shelf holds, narrowed by the words and the filters, as they are now. Machine, genre and year are asked of
   * the server; what it cannot filter on, having a recording, a map or anything to load, is asked of what comes back.
   */
  public Supplier<List<GameSearchResult>> on(String shelf, String words) {
    Search now = search();
    if (PLAYED.equals(shelf)) return () -> narrowed(played(), words);
    if (FAVOURITES.equals(shelf)) return () -> narrowed(config.getFavorites().stream().map(this::asResult).toList(), words);
    String folder = config.getGameFolders().contains(shelf) ? shelf : null;
    String where = folder != null ? ON_THIS_MACHINE : shelf;
    KnowsTheGames.Slice slice = now.slice();
    String year = slice.year() == null ? null : String.valueOf(slice.year());
    return () -> {
      List<GameSearchResult> found = new ArrayList<>();
      if (!ON_THE_NET.equals(where)) found.addAll(gamesOnThisMachine(words, now.unknown(), year, folder));
      if (!ON_THIS_MACHINE.equals(where)) {
        // In an order that is not the catalogue's own: the web service answers with its first hundred and fifty titles
        // alphabetically, none of which anybody knows, so the most voted that match are asked for too, from all of it.
        List<Hit> hits = new ArrayList<>();
        if (!AS_FOUND.equals(now.order())) hits.addAll(Catalogues.mostVoted(words, slice));
        if (!words.isEmpty() || AS_FOUND.equals(now.order())) hits.addAll(Catalogues.search(words, slice));
        Set<String> seen = new java.util.HashSet<>();
        hits.removeIf(hit -> hit._id != null && !seen.add(hit._id));
        found.addAll(fromTheCatalogue(hits));
      }
      List<GameSearchResult> results = oneTilePerGame(found);
      if (MOST_KNOWN.equals(now.order())) results.sort(BY_VOTES);
      else if (BEST_RATED.equals(now.order())) results.sort(BY_RATING);
      // Not merely "has a file": one the archive will not hand over cannot be loaded either.
      results.removeIf(result -> (now.colour() && !result.inColour) || (now.rzx() && !result.hasRzx)
          || (now.map() && !result.hasMap) || (now.loadable() && (result.filename == null || !result.available)));
      return results;
    };
  }

  private static List<GameSearchResult> narrowed(List<GameSearchResult> results, String words) {
    return results.stream().filter(result -> result.title.toLowerCase().contains(words.toLowerCase())).toList();
  }

  /** The games played here, the last first, each once. */
  private List<GameSearchResult> played() {
    Map<String, GameSearchResult> played = new LinkedHashMap<>();
    config.getSnapshotHistory().values().stream()
        .sorted(java.util.Comparator.comparingLong(OOZxConfiguration.SnapshotHistoryEntry::getLoadedTime).reversed())
        .forEach(entry -> played.computeIfAbsent(entry.getFilePath(), path -> onThisMachine(path, entry.getGameName())));
    return played.values().stream().limit(PLAYED_SHOWN).toList();
  }

  /** A file of this machine, as the library knows it when it does. */
  private GameSearchResult onThisMachine(String path, String title) {
    return LocalGames.library().games().stream().filter(copy -> copy.path().equals(path)).findFirst().map(this::asResult)
        .orElseGet(() -> {
          GameSearchResult result = new GameSearchResult(null, title, null, null, null, path);
          result.available = true;
          result.files = List.of(path);
          result.onThisMachine = true;
          return result;
        });
  }

  /** A favourite: a file or an address to open, or a recording to play. */
  private GameSearchResult asResult(OOZxConfiguration.Favorite favorite) {
    String id = favorite.getGameId() != null ? favorite.getGameId() : favorite.getEntry();
    if (!favorite.isRecording()) {
      GameSearchResult result = favorite.getSource().startsWith("http")
          ? new GameSearchResult(id, favorite.getTitle(), null, null, null, favorite.getSource())
          : onThisMachine(favorite.getSource(), favorite.getTitle());
      result.id = result.id == null ? id : result.id;
      result.available = result.onThisMachine || DownloadAndUnzip.available(favorite.getSource());
      result.subtitle = result.subtitle == null ? DownloadAndUnzip.nameOf(favorite.getSource()) : result.subtitle;
      GameFingerprint.Known known = result.screenshot1 != null || id == null ? null : LocalGames.library().catalogue().known(id);
      if (known != null) {
        result.screenshot1 = known.screenshot();
        result.hasMap = known.hasMap();
      }
      return result;
    }
    String from = favorite.getSource();
    if (!from.startsWith("http") && !from.startsWith("file:")) from = new java.io.File(from).toURI().toString();
    GameSearchResult result = new GameSearchResult(favorite.getEntry(), favorite.getTitle(), null, null, null, null);
    result.recordings = List.of(new RzxOption(favorite.getTitle(), from));
    result.hasRzx = true;
    result.subtitle = "A recording";
    return result;
  }

  public String hint(String shelf, String words, int found) {
    String games = found + (found == 1 ? " game" : " games");
    if (!words.isEmpty()) return games + " called “" + words + "”";
    return switch (shelf) {
      case PLAYED -> "The ones played here, the last first";
      case FAVOURITES -> "The ones kept with the star";
      case ON_THE_NET -> games + " ZXInfo knows. Choose one to see it; twice to play it.";
      default -> games + ". Choose one to see it; twice to play it.";
    };
  }

  public String nothing(String shelf, String words) {
    if (PLAYED.equals(shelf) && words.isEmpty()) return "Nothing played yet: what you play is kept here.";
    if (FAVOURITES.equals(shelf) && words.isEmpty()) return "Nothing kept yet: the star of a game keeps it here.";
    return ON_THIS_MACHINE.equals(shelf) && config.getGameFolders().isEmpty()
        ? "No folder to look in yet: add one under the shelves." : "Nothing matches. Try fewer filters or fewer letters.";
  }

  public Shelves.Card card(GameSearchResult result) {
    List<String> chips = new ArrayList<>();
    if (result.onThisMachine) chips.add(result.copies > 1 ? result.copies + " here" : "Here");
    if (result.hasRzx) chips.add("RZX");
    if (result.inColour) chips.add("256 colours");
    if (result.filename == null && result.recordings.isEmpty()) chips.add("Nothing to open");
    else if (result.filename != null && !result.available) chips.add("Not available");
    return new Shelves.Card(result.title, result.subtitle,
        result.score == null ? null : "★ " + result.score + "  ", chips);
  }

  public List<Shelves.Picture> pictures(GameSearchResult result, KnowsAbout.About about) {
    List<Shelves.Picture> pictures = new ArrayList<>();
    java.util.stream.Stream.of(result.screenshot1, result.screenshot2).filter(java.util.Objects::nonNull).distinct()
        .forEach(at -> pictures.add(Shelves.Picture.at(at)));
    if (about != null) about.pictures().forEach(at -> pictures.add(Shelves.Picture.at(at)));
    return pictures;
  }

  public Shelves.Detail detail(GameSearchResult result, KnowsAbout.About about) {
    Map<String, String> facts = new LinkedHashMap<>();
    if (result.files.size() > 1) facts.put("Versions", String.valueOf(result.files.size()));
    if (!result.recordings.isEmpty()) facts.put("Recordings", String.valueOf(result.recordings.size()));
    if (result.onThisMachine) facts.put("Here", result.filename);
    boolean kept = favourite(result) != null;
    return new Shelves.Detail(about == null ? result.subtitle : about.then(), null,
        List.of(new Shelves.Doing(result.filename == null && !result.recordings.isEmpty() ? "Play the recording" : "Play",
                () -> open(result)),
            new Shelves.Doing(kept ? "★ Kept" : "☆ Keep", () -> keep(result))),
        config.getSnapshotHistory().values().stream().anyMatch(entry -> entry.getFilePath().equals(result.filename))
            ? "Played here lately" : null, facts);
  }

  public String known(GameSearchResult result) {
    return result.id == null || idOf(result.id) < 0 ? null : "zxdb:" + result.id;
  }

  public void open(GameSearchResult result) {
    if (result.filename == null && !result.recordings.isEmpty()) {
      RzxOption first = result.recordings.getFirst();
      Desk.theOne().play(first.url(), first.label(), result.id);
    } else startLoading(result);
  }

  private OOZxConfiguration.Favorite favourite(GameSearchResult result) {
    return config.getFavorites().stream().filter(favorite -> favorite.getSource().equals(result.filename)
        || !result.recordings.isEmpty() && favorite.isRecording() && result.recordings.getFirst().url().endsWith(
        favorite.getSource().replace(java.io.File.separatorChar, '/'))).findFirst().orElse(null);
  }

  /** Kept as a favourite, or no longer, and the shelves say so. */
  private void keep(GameSearchResult result) {
    OOZxConfiguration.Favorite kept = favourite(result);
    if (kept != null) config.removeFavorite(kept.getSource());
    else Desk.theOne().keep(asAGame(result));
    shelves.again();
  }

  public JComponent filters() {
    JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 2));
    for (JComponent each : List.of(order, machineFilter, genreFilter, yearFilter, rzxFilter, mapFilter, loadableFilter,
        unknownFilter, colourFilter))
      strip.add(each);
    JButton clear = new JButton("Clear filters");
    clear.setBorderPainted(false);
    clear.setContentAreaFilled(false);
    clear.addActionListener(e -> search(new Search(shelves.words(), String.valueOf(where()), AS_FOUND,
        new KnowsTheGames.Slice(null, null, null), false, false, true, false, false)));
    strip.add(clear);
    return strip;
  }

  /**
   * What this machine has, and the way to add to it. Scanning is what turns a folder of files with names like
   * RENE256.SNA into games with titles, and it is offered here rather than run by itself because reading every file of
   * a collection is not something to do behind somebody's back.
   */
  public JComponent underShelves() {
    JButton add = new JButton("Add folder…");
    add.addActionListener(e -> scanFolder());
    JButton rescan = new JButton("Rescan");
    rescan.setToolTipText("Look through every folder again, for games added since");
    rescan.addActionListener(e -> rescanEverything());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    buttons.setOpaque(false);
    buttons.add(add);
    buttons.add(Box.createHorizontalStrut(6));
    buttons.add(rescan);
    JPanel under = new JPanel(new BorderLayout(0, 6));
    libraryLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
    under.add(libraryLabel, BorderLayout.NORTH);
    under.add(buttons, BorderLayout.CENTER);
    under.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    return under;
  }

  /** A folder of games can be left out without forgetting it: its games stop showing until it is taken back. */
  public JPopupMenu menuOfShelf(String shelf) {
    if (!config.getGameFolders().contains(shelf)) return null;
    JPopupMenu menu = new JPopupMenu();
    JCheckBoxMenuItem taken = new JCheckBoxMenuItem("Take its games into account", config.takesIntoAccount(shelf));
    taken.addActionListener(e -> {
      config.takeIntoAccount(shelf, taken.isSelected());
      shelves.again();
    });
    menu.add(taken);
    return menu;
  }

  /** Its menu: a recording to play, the machine or the version to load it as, its whole card, keeping it. */
  public JPopupMenu menu(GameSearchResult result) {
    JPopupMenu menu = new JPopupMenu();
    if (!result.recordings.isEmpty()) {
      JMenu playRecording = new JMenu("Play Recording");
      for (RzxOption option : result.recordings) {
        JMenuItem item = new JMenuItem(option.label());
        item.addActionListener(e -> Desk.theOne().play(option.url(), option.label(), result.id));
        playRecording.add(item);
      }
      menu.add(playRecording);
      menu.addSeparator();
    }
    List<Runnable> refills = new ArrayList<>();
    if (result.filename != null) {
      JMenu loadItem = machineMenu("Load Game" + besides(result.files.isEmpty() ? result.filename : result.files.getFirst()), result, null);
      refills.add(() -> fill(loadItem, result, null));
      menu.add(loadItem);
    }
    if (result.files.size() > 1) {
      JMenu versions = new JMenu("Load Version");
      for (String each : result.files) {
        String shown = DownloadAndUnzip.nameOf(each) + (ZxInfoApiHandler.fromTosec(each) ? "  (found)" : "") + besides(each);
        JMenu item = machineMenu(DownloadAndUnzip.available(each) ? shown : shown + "  (not available)", result, each);
        item.setEnabled(DownloadAndUnzip.available(each));
        refills.add(() -> fill(item, result, each));
        versions.add(item);
      }
      menu.add(versions);
    }
    JMenuItem details = new JMenuItem("Everything about it…");
    details.addActionListener(e -> Desk.theOne().showDetails(asAGame(result)));
    details.setEnabled(result.id != null);
    menu.add(details);
    JMenuItem favourite = new JMenuItem(favourite(result) == null ? "Add to Favourites" : "Remove from Favourites");
    favourite.addActionListener(e -> keep(result));
    menu.add(favourite);
    menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
      public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
        refills.forEach(Runnable::run);
      }

      public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
      }

      public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
      }
    });
    return menu;
  }

  /**
   * Every folder again, for games that were not there last time. A scan skips what has not changed
   * in size or date, so this costs a listing of the folders and a fingerprint of what is new.
   */
  private void rescanEverything() {
    libraryLabel.setText("Looking again...");
    new SwingWorker<Integer, Void>() {
      @Override
      protected Integer doInBackground() throws Exception {
        int gone = LocalGames.library().forgetMissing();
        // Games under a folder nobody lists any more are dropped rather than lingering unseen.
        LocalGames.library().games().stream().filter(copy -> config.getGameFolders().stream()
                .noneMatch(folder -> copy.path().startsWith(folder + java.io.File.separator)))
            .forEach(copy -> LocalGames.library().forget(Path.of(copy.path())));
        for (String folder : config.getGameFolders()) {
          if (config.takesIntoAccount(folder) && Files.isDirectory(Path.of(folder))) {
            LocalGames.library().scan(Path.of(folder), LocalGames.CERTAINTY);
          }
        }
        LocalGames.library().save(LocalGames.file());
        return gone;
      }

      @Override
      protected void done() {
        looked(this, "Could not look again: ");
      }
    }.execute();
  }

  private void scanFolder() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Folder to look for games in");
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path folder = chooser.getSelectedFile().toPath();
    config.addGameFolder(folder.toString());
    libraryLabel.setText("Looking through " + folder.getFileName() + "...");
    new SwingWorker<Integer, Void>() {
      @Override
      protected Integer doInBackground() throws Exception {
        int seen = LocalGames.library().scan(folder, LocalGames.CERTAINTY);
        LocalGames.library().save(LocalGames.file());
        return seen;
      }

      @Override
      protected void done() {
        looked(this, "Could not read " + folder + ": ");
      }
    }.execute();
  }

  private void looked(SwingWorker<Integer, Void> looking, String failing) {
    try {
      looking.get();
    } catch (Exception failed) {
      Throwable cause = failed;
      while (cause.getCause() != null) cause = cause.getCause();
      JOptionPane.showMessageDialog(this, failing + (cause.getMessage() != null ? cause.getMessage() : cause),
          "Games", JOptionPane.WARNING_MESSAGE);
    }
    sayWhatIsOnThisMachine();
    shelves.again();
  }

  private void sayWhatIsOnThisMachine() {
    GameLibrary library = LocalGames.library();
    long named = library.games().stream().filter(GameLibrary.Copy::identified).count();
    libraryLabel.setText(library.games().size() + " games here, " + named + " named");
  }

  /**
   * A game of this machine said in the same terms as one from the catalogue, so that the cards,
   * the menu and the loading path do not have to know where it came from.
   */
  private GameSearchResult asResult(GameLibrary.Copy copy) {
    GameSearchResult result = new GameSearchResult(copy.identified() ? copy.game().id : null,
        copy.title(), null, null, null, copy.path());
    result.available = true;
    result.files = List.of(copy.path());
    result.onThisMachine = true;
    result.subtitle = copy.identified()
        ? copy.game().yearOfRelease + "  -  " + copy.game().publisher : "unknown";
    // What the catalogue knows about the game beyond its name, so that the filters and the
    // pictures work the same for a game on the disk as for one on the net, and with no network.
    GameFingerprint.Known known = copy.identified()
        ? LocalGames.library().catalogue().known(copy.game().id) : null;
    if (known != null) {
      result.screenshot1 = known.screenshot();
      result.hasMap = known.hasMap();
    }
    result.hasRzx = copy.identified() && archive.hasRecordings(idOf(copy.game().id));
    // Preguntado ahora y no guardado: depende de que plugins estan puestos, y eso cambia.
    result.inColour = com.fpetrola.oozx.plugins.BesideTheGame.anythingBeside(copy.path());
    return result;
  }

  /**
   * Whether a game is under one of the folders being taken into account. The library outlives the
   * list of folders - a folder dropped from it leaves its games written down - and showing them
   * anyway would disagree with the shelves that list the folders.
   */
  private boolean takenIntoAccount(GameLibrary.Copy copy) {
    return config.getGameFolders().stream()
        .anyMatch(folder -> copy.path().startsWith(folder + java.io.File.separator) && config.takesIntoAccount(folder))
        && config.takesIntoAccount(Path.of(copy.path()).getParent().toString());
  }

  /**
   * What this machine has, one result per file; putting the copies of one game together is done
   * afterwards, by the same merge that joins them to what the net has.
   */
  private List<GameSearchResult> gamesOnThisMachine(String query, boolean onlyUnknown, String year, String inFolder) {
    return LocalGames.library().games().stream()
        .filter(copy -> inFolder == null || copy.path().startsWith(inFolder))
        .filter(this::takenIntoAccount)
        .filter(copy -> !onlyUnknown || !copy.identified())
        .filter(copy -> year == null || copy.identified() && year.equals(copy.game().yearOfRelease))
        .filter(copy -> query.isEmpty() || copy.title().toLowerCase().contains(query.toLowerCase()))
        .map(this::asResult)
        .collect(Collectors.toList());
  }

  /**
   * One card per game, whatever it was found as. Seven copies of Manic Miner in seven folders and
   * the ZXInfo entry for it are eight things and one game; knowing which game each file is - which
   * is what the fingerprint is for - is what makes saying so possible. Every copy stays reachable
   * under Load Version, the one on this machine first, because it is already here.
   * <p>
   * What could not be identified is its own game, since there is nothing to say it is not.
   */
  private List<GameSearchResult> oneTilePerGame(List<GameSearchResult> found) {
    Map<String, GameSearchResult> byGame = new LinkedHashMap<>();
    for (GameSearchResult result : found) {
      GameSearchResult already = byGame.get(keyOf(result));
      if (already == null) {
        byGame.put(keyOf(result), result);
      } else {
        join(already, result);
      }
    }
    byGame.values().forEach(this::sayWhereItIs);
    return new ArrayList<>(byGame.values());
  }

  /** The most voted first: votes are who played it, and a game nobody knows has few or none. Unvoted last, as they came. */
  static final java.util.Comparator<GameSearchResult> BY_VOTES = java.util.Comparator
      .comparingInt((GameSearchResult result) -> result.votes == null ? 0 : -result.votes);

  /** How many votes a score needs before it counts for more than the average does. */
  static final double VOTES_TO_BE_BELIEVED = 25;
  /** The score a game with no votes to speak of is taken to have: about the catalogue's average. */
  static final double AVERAGE_SCORE = 6.5;

  /**
   * The score weighed by how many voted, as the lists of best films do: a few votes pull it toward
   * the average, so three 10s do not beat four hundred 9s. Unrated last, as they came.
   */
  static double weighed(GameSearchResult result) {
    if (result.score == null || result.votes == null) {
      return 0;
    }
    double votes = result.votes;
    return (votes * result.score + VOTES_TO_BE_BELIEVED * AVERAGE_SCORE) / (votes + VOTES_TO_BE_BELIEVED);
  }

  static final java.util.Comparator<GameSearchResult> BY_RATING = java.util.Comparator
      .comparingDouble((GameSearchResult result) -> -weighed(result));

  private static String keyOf(GameSearchResult result) {
    return result.id != null ? result.id : result.filename;
  }

  /** Folds the second into the first, keeping whichever of the two knows more. */
  private void join(GameSearchResult kept, GameSearchResult other) {
    // The best kind of file wins wherever it is, a TZX that says its machine over a TAP that cannot;
    // between two as good, the copy already on the disk, which is why those go first into a stable sort.
    kept.files = DownloadAndUnzip.byPreference(kept.onThisMachine
        ? concat(kept.files, other.files) : concat(other.files, kept.files), file -> file);
    kept.filename = kept.files.isEmpty() ? kept.filename : kept.files.get(0);
    kept.copies = kept.copies + other.copies;
    kept.onThisMachine |= other.onThisMachine;
    kept.available |= other.available;
    kept.hasRzx |= other.hasRzx;
    if (kept.score == null) {
      kept.score = other.score;
      kept.votes = other.votes;
    }
    kept.hasMap |= other.hasMap;
    kept.inColour |= other.inColour;
    kept.screenshot1 = kept.screenshot1 != null ? kept.screenshot1 : other.screenshot1;
    kept.screenshot2 = kept.screenshot2 != null ? kept.screenshot2 : other.screenshot2;
    kept.title = kept.title.length() >= other.title.length() ? kept.title : other.title;
    if (kept.recordings.isEmpty()) {
      kept.recordings = other.recordings;
    }
    if (kept.url == null) {
      kept.url = other.url;
    }
  }

  private static List<String> concat(List<String> first, List<String> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().toList();
  }

  private void sayWhereItIs(GameSearchResult result) {
    String where = result.onThisMachine
        ? (result.copies > 1 ? result.copies + " on disk" : "on disk") : "on the net";
    result.subtitle = result.subtitle == null ? where : result.subtitle + "  -  " + where;
  }

  /** Fills the combos from /metadata/, off the event thread, leaving them usable if it fails. */
  private void loadFilterValues() {
    new SwingWorker<Metadata, Void>() {
      @Override
      protected Metadata doInBackground() {
        return Catalogues.metadata();
      }

      @Override
      protected void done() {
        try {
          Metadata metadata = get();
          // 200 entries is enough to be worth a line in a combo; below that the list is noise.
          choosingAll = true;
          fill(machineFilter, Metadata.namesOf(metadata.machinetypes, 200));
          fill(genreFilter, Metadata.namesOf(metadata.genretypes, 200));
        } catch (Exception e) {
          System.err.println("Could not read the filter values: " + e);
        } finally {
          choosingAll = false;
        }
      }

      private void fill(JComboBox<String> combo, List<String> values) {
        values.stream().filter(value -> !listed(combo, value)).forEach(combo::addItem);
      }
    }.execute();
  }

  private String selected(JComboBox<String> combo) {
    Object value = combo.getSelectedItem();
    if (value == null || ANY_MACHINE.equals(value) || ANY_GENRE.equals(value) || ANY_YEAR.equals(value)) {
      return null;
    }
    return value.toString();
  }

  /**
   * A file, and under it the machines it can be started on.
   * <p>
   * Most tapes do not say which machine they were made for and cannot: a game that loads once and
   * then looks for a sound chip only finds out at run time. Where the file does say, the first
   * entry follows it, and the rest are there for when somebody knows better than the file - which
   * for a game with AY music and no way to declare it is every time.
   *
   * @param file the release to load, or null for whichever the entry already points at
   */
  private JMenu machineMenu(String label, GameSearchResult result, String file) {
    JMenu menu = new JMenu(label);
    // Clicking the name loads it; hovering offers the default and the machines for when the file is wrong about
    // which one it wants.
    menu.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent clicked) {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        load(result, file, null);
      }
    });
    fill(menu, result, file);
    return menu;
  }

  /**
   * Playing it as the file says, or on a machine picked from a level further down, so that the
   * long list of machines only opens for whoever asks for it.
   * <p>
   * Called again every time the menu is about to show, because a search is rendered before any
   * machine has been built: asking then answers nothing, and a menu filled once keeps the nothing
   * for the life of the window.
   */
  private void fill(JMenu menu, GameSearchResult result, String file) {
    menu.removeAll();
    JMenuItem byDefault = new JMenuItem("Play default");
    byDefault.addActionListener(e -> load(result, file, null));
    menu.add(byDefault);
    List<String> machines = Desk.theOne().machines();
    if (!machines.isEmpty()) {
      JMenu onMachine = new JMenu("Play on machine");
      for (String machine : machines) {
        JMenuItem item = new JMenuItem(machine);
        item.addActionListener(e -> load(result, file, machine));
        onMachine.add(item);
      }
      menu.add(onMachine);
    }
  }

  private void load(GameSearchResult result, String file, String machine) {
    if (file != null) {
      result.filename = file;
      result.available = DownloadAndUnzip.available(file);
    }
    result.machine = machine;
    startLoading(result);
  }

  /**
   * Loading a game downloads and unzips it before an emulator window can appear, which takes long enough that a click
   * used to look like nothing had happened. Say plainly when there is nothing to load rather than ignoring the click.
   */
  private void startLoading(GameSearchResult result) {
    if (result.filename == null) {
      JOptionPane.showMessageDialog(this,
          "There is nothing here this emulator can open for \"" + result.title + "\"."
              + (result.offers == null || result.offers.isBlank() ? ""
              : "\n\nThe archive has it as: " + result.offers + "."),
          "Nothing to load", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    if (!result.available) {
      // Known beforehand, so there is no reason to spend a download finding out.
      JOptionPane.showMessageDialog(this,
          "\"" + result.title + "\" is not available: the archive holds it but is not allowed "
              + "to hand it out, and no TOSEC set has a dump of it either.",
          "Not available", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    if (loading) {
      return;
    }
    loading = true;
    setTitle("Game Browser - loading " + result.title + "...");
    Desk.theOne().open(asAGame(result), () -> {
      loading = false;
      setTitle("Game Browser");
    });
  }

  private List<GameSearchResult> fromTheCatalogue(List<Hit> search) {
    List<GameSearchResult> results = new ArrayList<>();
    for (Hit hit : search) {
      GameEntry game = Catalogues.withTosecFiles(hit._id, hit._source);
      if (!game.contentType.equals("SOFTWARE")) continue;
      List<String> screenshots = new ArrayList<>();
      game.screens.forEach(each -> {
        Screen screen = Screen.from(each);
        if (screen != null) screenshots.add(ZxInfoApiHandler.mediaUrl(screen.url));
      });
      // What the entry offers that cannot be loaded, so a refusal can say what it was rather than "no tape available",
      // which is true and tells nobody anything.
      Map<String, String> offers = ZxInfoApiHandler.filesOf(game, DownloadAndUnzip::loadable);
      List<String> files = offers.keySet().stream().filter(DownloadAndUnzip::loadable).toList();
      Set<String> offered = offers.entrySet().stream()
          .filter(offer -> !DownloadAndUnzip.loadable(offer.getKey()))
          .map(Map.Entry::getValue).collect(Collectors.toCollection(LinkedHashSet::new));
      boolean hasMap = false;
      List<RzxOption> recordings = new ArrayList<>();
      // The RZX Archive lists recordings ZXDB does not, and knows who made them. ZXDB has come to
      // list the archive's own as well, from its copy at archive.org, and those are offered once.
      List<RzxRecording> playable = archive.recordingsFor(idOf(hit._id)).stream()
          .filter(RzxRecording::isPlayable).toList();
      Set<String> archived = playable.stream()
          .map(recording -> DownloadAndUnzip.nameOf(recording.download().url())).collect(Collectors.toSet());
      for (AdditionalDownload download : game.additionalDownloads == null
          ? List.<AdditionalDownload>of() : game.additionalDownloads) {
        if ("RZX playback file".equals(download.type) && !archived.contains(DownloadAndUnzip.nameOf(download.path))) {
          recordings.add(new RzxOption(DownloadAndUnzip.nameOf(download.path) + "  (ZXDB)",
              ZxInfoApiHandler.mediaUrl(download.path)));
        }
        hasMap |= ZxInfoApiHandler.GAME_MAP_TYPE.equalsIgnoreCase(download.type);
      }
      for (RzxRecording recording : playable) {
        String by = recording.submitter() == null || recording.submitter().isBlank()
            ? "RZX Archive" : "by " + recording.submitter();
        recordings.add(new RzxOption(recording.title() + "  (" + by + ")", recording.download().url()));
      }
      // The whole URL, not just the last part of it: the scorer needs the path to see that a
      // file sits under /denied/ and is not going to come down.
      String file = files.isEmpty() ? null : DownloadAndUnzip.preferred(files, url -> url);
      GameSearchResult result = new GameSearchResult(hit._id, game.title, null,
          screenshots.isEmpty() ? null : screenshots.get(0), screenshots.size() > 1 ? screenshots.get(1) : null, file);
      result.offers = String.join(", ", offered);
      result.available = DownloadAndUnzip.available(file);
      // Kept, not thrown away: an entry often has a 48K release and a 128K one, and the choice
      // between them is the person's to make rather than the scorer's to impose.
      result.files = DownloadAndUnzip.byPreference(files, url -> url);
      if (game.score != null) {
        result.score = game.score.score;
        result.votes = game.score.votes;
      }
      result.subtitle = java.util.stream.Stream.of(game.originalYearOfRelease == null ? null : String.valueOf(game.originalYearOfRelease),
              game.publishers == null || game.publishers.isEmpty() ? null : game.publishers.getFirst().name)
          .filter(java.util.Objects::nonNull).collect(Collectors.joining("  -  "));
      result.subtitle = result.subtitle.isEmpty() ? null : result.subtitle;
      result.hasRzx = !recordings.isEmpty();
      result.hasMap = hasMap;
      result.recordings = recordings;
      results.add(result);
    }
    return results;
  }

  /**
   * What it is looking for, as its controls say it: the words, where, in what order, the slice the
   * catalogue is asked for, and what narrows what comes back.
   */
  public record Search(String words, String where, String order, KnowsTheGames.Slice slice,
                       boolean rzx, boolean map, boolean loadable, boolean unknown, boolean colour) {
  }

  /** The shelf it looks on, as a place: a folder of this machine is this machine. */
  private String where() {
    String shelf = shelves == null ? EVERYWHERE : shelves.shelf();
    return config.getGameFolders().contains(shelf) ? ON_THIS_MACHINE : shelf;
  }

  public Search search() {
    String year = selected(yearFilter);
    return new Search(shelves == null ? "" : shelves.words(), where(), String.valueOf(order.getSelectedItem()),
        new KnowsTheGames.Slice(year == null ? null : Integer.valueOf(year), selected(genreFilter), selected(machineFilter)),
        rzxFilter.isSelected(), mapFilter.isSelected(), loadableFilter.isSelected(), unknownFilter.isSelected(),
        colourFilter.isSelected());
  }

  /** Looks for that: every control as it says, and a single search. */
  public void search(Search search) {
    choosingAll = true;
    order.setSelectedItem(search.order());
    choose(machineFilter, search.slice().machine());
    choose(genreFilter, search.slice().genre());
    choose(yearFilter, search.slice().year() == null ? null : String.valueOf(search.slice().year()));
    rzxFilter.setSelected(search.rzx());
    mapFilter.setSelected(search.map());
    loadableFilter.setSelected(search.loadable());
    unknownFilter.setSelected(search.unknown());
    colourFilter.setSelected(search.colour());
    choosingAll = false;
    shelves.type(search.words());
    shelves.shelf(search.where());
  }

  /** That one chosen, or the first, which narrows nothing: one the catalogue's lists have not brought yet goes in. */
  private static void choose(JComboBox<String> combo, String value) {
    if (value != null && !listed(combo, value)) combo.addItem(value);
    if (value == null) combo.setSelectedIndex(0);
    else combo.setSelectedItem(value);
  }

  private static boolean listed(JComboBox<String> combo, String value) {
    return ((DefaultComboBoxModel<String>) combo.getModel()).getIndexOf(value) >= 0;
  }
}
