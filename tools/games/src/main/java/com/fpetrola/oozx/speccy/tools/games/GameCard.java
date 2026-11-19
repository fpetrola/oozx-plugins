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

import com.fpetrola.oozx.api.AdditionalDownload;
import com.fpetrola.oozx.api.GameDetail;
import com.fpetrola.oozx.api.ZxInfoApiHandler;
import com.fpetrola.oozx.rzx.RzxArchive;
import com.fpetrola.oozx.rzx.RzxRecording;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.media.DownloadAndUnzip;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * What is known of one game, on the desk: which game the file turned out to be, and everything the catalogue has
 * on it counted by kind. Each count is a way in: pressing it opens that kind here, as a gallery or a table, and
 * the bar above the counts leads back.
 * <p>
 * The name of the file stays in sight beside the name of the game, because the point is that one did not come
 * from the other. A file nobody recognises still gets its card, which says so and asks.
 */
class GameCard extends JInternalFrame {
  private static final Color INK = new Color(0x17171C), PAPER = Color.WHITE, QUIET = new Color(0xB9B9C6);
  /** The four stripes of the 48K's case, which are also what tells the kinds apart. */
  private static final Color[] STRIPES = {new Color(0xD80000), new Color(0xD8D800), new Color(0x00C000), new Color(0x00C8C8)};
  /** How ZXDB calls a kind of file, as a person would count them; a kind not here keeps ZXDB's name. */
  private static final Map<String, String> KINDS = Map.ofEntries(
      Map.entry("Game map", "Maps"), Map.entry("Inlay - Front", "Inlays"), Map.entry("Inlay - Back", "Inlays"),
      Map.entry("Inlay - Side", "Inlays"), Map.entry("Advertisement", "Adverts"), Map.entry("Media scan", "Media"),
      Map.entry("Loading screen", "Screens"), Map.entry("Running screen", "Screens"),
      Map.entry("Opening screen", "Screens"), Map.entry("POK pokes file", "Pokes files"),
      Map.entry("Ripped in-game/theme music in AY format", "Music"),
      Map.entry("Sampled in-game/theme music in MP3 format", "Music"));

  /** The colours of whatever look the desk wears, asked for when painting so that a change of look is followed. */
  private static Color worn(String key, Color otherwise) {
    Color worn = javax.swing.UIManager.getColor(key);
    return worn == null ? otherwise : worn;
  }

  private static Color ground() {
    return worn("TextField.background", Color.WHITE);
  }

  private static Color ink() {
    return worn("Label.foreground", INK);
  }

  private static Color soft() {
    return worn("Label.disabledForeground", new Color(0x6B6B78));
  }

  private static Color edge() {
    return worn("Separator.foreground", new Color(0xD9D9E0));
  }

  private final CardLayout turning = new CardLayout();
  private final JPanel pages = new JPanel(turning);
  private final JLabel where = new JLabel();
  private final JButton back = new JButton("← Everything");
  private int kinds;
  private String game;
  private final Desk.Game asked;
  private final Consumer<String> askAgain;
  private boolean filled;

  /** The card at once, saying it is looking: what is known of the game is {@link #fill filled} in as it comes. */
  GameCard(Desk.Game asked, Consumer<String> askAgain) {
    super(asked.file() == null ? "Looking..." : new File(asked.file()).getName(), true, true, true, true);
    this.asked = asked;
    this.askAgain = askAgain;
    setSize(780, 660);
    shown(null, true);
  }

  /**
   * What the catalogue said, put in the card; nothing, when it does not know the game. It may be told twice, first
   * what the catalogue on this disk has and then what the one on the web adds: the second is taken only while the
   * person is still at the wall, so that nothing they opened is pulled from under them.
   */
  void fill(GameDetail known) {
    if (filled && (known == null || back.isVisible())) return;
    filled = true;
    shown(known, false);
  }

  private void shown(GameDetail known, boolean looking) {
    game = known == null ? null : known.title;
    if (!looking) setTitle(known == null ? "Not recognised" : known.title);
    kinds = 0;
    pages.removeAll();
    show("", "");
    JPanel card = new JPanel(new BorderLayout());
    card.add(new Heading(asked, known, looking), BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout());
    body.add(bar(askAgain, known == null && !looking), BorderLayout.NORTH);
    body.add(pages, BorderLayout.CENTER);
    card.add(body, BorderLayout.CENTER);
    if (known != null) {
      pages.add(scrolling(wall(asked, known)), "");
      bringAhead(known);
    }
    setContentPane(card);
    revalidate();
    repaint();
  }

  /** Where the person is in the card, the way back, and a place to ask for another game. */
  private JComponent bar(Consumer<String> askAgain, boolean unknown) {
    JPanel bar = new JPanel(new BorderLayout(8, 0));
    bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, edge()),
        BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    back.setVisible(false);
    back.setFocusPainted(false);
    back.addActionListener(pressed -> show("", ""));
    where.setFont(where.getFont().deriveFont(Font.BOLD, 14f));
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    left.add(back);
    left.add(where);
    bar.add(left, BorderLayout.WEST);
    if (!unknown) return bar;
    JTextField another = new JTextField(18);
    another.setToolTipText("The name of a game, and Enter");
    another.addActionListener(typed -> askAgain.accept(another.getText().trim()));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    JLabel asking = new JLabel("Which game is it?");
    asking.setForeground(soft());
    right.add(asking);
    right.add(another);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  private void show(String page, String title) {
    turning.show(pages, page);
    where.setText(title);
    back.setVisible(!page.isEmpty());
  }

  /** One tile per kind of thing there is, with how many, and the first of them as its picture. */
  private JComponent wall(Desk.Game asked, GameDetail known) {
    JPanel wall = new JPanel(new GridLayout(0, 3, 12, 12));
    wall.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    way(wall, "About", 0, null, () -> about(known));
    way(wall, "Play it", 0, null, () -> toPlay(known));
    Desk.Game game = new Desk.Game(asked.file(), asked.machine(), known.id, known.title);
    // What each plugin has of this game: the card does not know what they are, only that they have a name, a count and a page.
    for (com.fpetrola.oozx.speccy.devices.AboutAGame part : dev.plugdesk.app.Plugins.found(com.fpetrola.oozx.speccy.devices.AboutAGame.class)) {
      int howMany = part.howMany(game);
      if (howMany > 0) way(wall, part.what(), howMany, null, () -> part.page(game));
    }
    List<RzxRecording> recordings = known.id == null ? List.of() : new RzxArchive()
        .recordingsFor(Integer.parseInt(known.id)).stream().filter(RzxRecording::isPlayable).toList();
    if (!recordings.isEmpty()) {
      way(wall, "Recordings", recordings.size(), null, () -> table(new String[]{"Recording", "By", "Note"},
          recordings.stream().map(one -> new String[]{one.title(), one.submitter(), one.note()}).toList(),
          row -> Desk.theOne().play(recordings.get(row).download().url(), recordings.get(row).title()),
          "Pick a recording and watch it; you can take over whenever you like", "\u25B6 Watch"));
    }
    if (known.releases != null && !known.releases.isEmpty()) {
      String[] columns = {"Title", "Year", "Publisher", "Price", "Code", "Barcode", "Files"};
      way(wall, "Editions", known.releases.size(), null, () -> table(columns, known.releases.stream()
          .map(release -> java.util.Arrays.stream(columns).map(release::get).toArray(String[]::new)).toList(), row -> {
      }, "Every release of this game the catalogue knows"));
    }
    Map<String, List<AdditionalDownload>> byKind = new LinkedHashMap<>();
    for (AdditionalDownload file : known.additionalDownloads == null ? List.<AdditionalDownload>of() : known.additionalDownloads) {
      if (file.type != null && file.path != null && !"RZX playback file".equals(file.type)) {
        byKind.computeIfAbsent(KINDS.getOrDefault(file.type, file.type), kind -> new ArrayList<>()).add(file);
      }
    }
    byKind.entrySet().stream().sorted((one, other) -> other.getValue().size() - one.getValue().size())
        .forEach(kind -> way(wall, kind.getKey(), kind.getValue().size(), firstPicture(kind.getValue()),
            () -> firstPicture(kind.getValue()) != null ? gallery(kind.getValue()) : files(kind.getValue())));
    JPanel top = new JPanel(new BorderLayout());
    top.add(wall, BorderLayout.NORTH);
    return top;
  }

  /** A tile on the wall and the page it leads to, which is made the first time somebody goes there. */
  private void way(JPanel wall, String kind, int howMany, String picture, Supplier<JComponent> page) {
    Color stripe = STRIPES[kinds++ % STRIPES.length];
    boolean[] made = {false};
    wall.add(new Tile(howMany, kind, picture, stripe, () -> {
      if (!made[0]) pages.add(scrolling(page.get()), kind);
      made[0] = true;
      show(kind, howMany == 0 ? kind : howMany + " " + kind);
    }));
  }

  private static JComponent about(GameDetail known) {
    List<String[]> rows = new ArrayList<>();
    said(rows, "Title", known.title);
    said(rows, "Released", joined("-", known.yearOfRelease, text(known.originalMonthOfRelease), text(known.originalDayOfRelease)));
    said(rows, "Publishers", known.publishers == null ? known.publisher : String.join(", ", known.publishers));
    said(rows, "Authors", known.authors == null ? null : String.join(", ", known.authors));
    said(rows, "Genre", joined(" / ", known.genre, known.genreType, known.genreSubType));
    said(rows, "Machine", known.machineType);
    said(rows, "Availability", known.availability);
    said(rows, "Kind", known.contentType);
    said(rows, "Score", known.score == null ? null : known.score + " out of 10");
    said(rows, "ZXDB id", known.id);
    return table(new String[]{"", ""}, rows, row -> {
    }, "What the catalogue says of it");
  }

  /** The files of the game that a machine can be started from, asked of the catalogue when somebody comes to see them. */
  private static JComponent toPlay(GameDetail known) {
    JPanel page = new JPanel(new BorderLayout());
    JLabel looking = new JLabel("Asking the catalogue for its files...", JLabel.CENTER);
    looking.setBorder(BorderFactory.createEmptyBorder(30, 0, 30, 0));
    page.add(looking, BorderLayout.NORTH);
    new SwingWorker<List<String>, Void>() {
      protected List<String> doInBackground() {
        return ZxInfoApiHandler.filesOf(com.fpetrola.oozx.api.Catalogues.game(known.id), DownloadAndUnzip::loadable)
            .keySet().stream().filter(DownloadAndUnzip::loadable).toList();
      }

      protected void done() {
        try {
          List<String> files = get();
          page.remove(looking);
          page.add(table(new String[]{"File", "From"}, files.stream().map(url -> new String[]{nameOf(url), java.net.URI.create(url.replace(" ", "%20")).getHost()}).toList(),
              row -> Desk.theOne().open(new Desk.Game(files.get(row), null, known.id, known.title), () -> {
              }), files.isEmpty() ? "The catalogue has no file of it that can be loaded" : "Pick a file and it starts on a machine of its own", "\u25B6 Load"));
        } catch (Exception notAnswered) {
          looking.setText("The catalogue did not answer: " + notAnswered.getMessage());
        }
        page.revalidate();
      }
    }.execute();
    return page;
  }

  private static void said(List<String[]> rows, String what, String value) {
    if (value != null && !value.isBlank()) rows.add(new String[]{what, value});
  }

  /** The pictures of one kind, large enough to tell apart, each opening whole. */
  private JComponent gallery(List<AdditionalDownload> files) {
    JPanel gallery = new JPanel(new GridLayout(0, 3, 12, 12));
    gallery.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    for (AdditionalDownload file : files) {
      JLabel picture = new JLabel("", JLabel.CENTER);
      picture.setPreferredSize(new Dimension(220, 165));
      picture.setOpaque(true);
      picture.setBackground(ground());
      picture.setBorder(BorderFactory.createLineBorder(edge()));
      arriving(file.path, 220, 165, picture::setIcon);
      JLabel caption = new JLabel(nameOf(file.path) + (file.size == null ? "" : "  ·  " + file.size / 1024 + " KB"), JLabel.CENTER);
      caption.setForeground(soft());
      caption.setFont(caption.getFont().deriveFont(11f));
      JPanel one = new JPanel(new BorderLayout(0, 4));
      one.add(picture, BorderLayout.CENTER);
      one.add(caption, BorderLayout.SOUTH);
      one.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      picture.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent pressed) {
          opened(file);
        }
      });
      gallery.add(one);
    }
    JPanel top = new JPanel(new BorderLayout());
    top.add(gallery, BorderLayout.NORTH);
    return top;
  }

  private JComponent files(List<AdditionalDownload> files) {
    return table(new String[]{"File", "Kind", "Format", "Size", "Language"}, files.stream().map(file -> new String[]{
        nameOf(file.path), file.type, file.format, file.size == null ? "" : file.size / 1024 + " KB", file.language}).toList(),
        row -> opened(files.get(row)), "Pick a file and open it; music opens in its player", "Open");
  }

  private void opened(AdditionalDownload file) {
    new SwingWorker<java.nio.file.Path, Void>() {
      protected java.nio.file.Path doInBackground() throws Exception {
        return kept(file.path);
      }

      protected void done() {
        try {
          java.nio.file.Path brought = get();
          String name = brought.getFileName().toString();
          if (name.toLowerCase().endsWith(".ay")) {
            Desk.theOne().place(new AyPlayer(game, brought));
          } else if (DownloadViewerDialog.shows(name)) {
            new DownloadViewerDialog((Frame) null, brought.toUri().toString(), name).setVisible(true);
          } else {
            java.awt.Desktop.getDesktop().open(brought.toFile());
          }
        } catch (Exception notBrought) {
          javax.swing.JOptionPane.showMessageDialog(null, "Could not bring " + nameOf(file.path) + ": " + notBrought.getMessage());
        }
      }
    }.execute();
  }

  private static JComponent table(String[] columns, List<String[]> rows, IntConsumer chosen, String saying) {
    return table(columns, rows, chosen, saying, null);
  }

  /** A table of things with, when something can be done with one, a button that says what and does it to the one picked. */
  private static JComponent table(String[] columns, List<String[]> rows, IntConsumer chosen, String saying, String doing) {
    JTable table = new JTable(rows.toArray(String[][]::new), columns);
    table.setDefaultEditor(Object.class, null);
    table.setRowHeight(26);
    table.setShowVerticalLines(false);
    table.setGridColor(edge());
    table.setFillsViewportHeight(true);
    if (columns[0].isEmpty()) table.setTableHeader(null);
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent pressed) {
        if (pressed.getClickCount() == 2 && table.getSelectedRow() >= 0) chosen.accept(table.getSelectedRow());
      }
    });
    JLabel hint = new JLabel(saying);
    hint.setForeground(soft());
    JPanel above = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    above.setBorder(BorderFactory.createEmptyBorder(10, 4, 8, 14));
    if (doing != null && !rows.isEmpty()) {
      table.setRowSelectionInterval(0, 0);
      JButton does = new JButton(doing);
      does.setFont(does.getFont().deriveFont(Font.BOLD));
      does.addActionListener(pressed -> {
        if (table.getSelectedRow() >= 0) chosen.accept(table.getSelectedRow());
      });
      above.add(does);
    }
    above.add(hint);
    JPanel page = new JPanel(new BorderLayout());
    page.add(above, BorderLayout.NORTH);
    JPanel framed = new JPanel(new BorderLayout());
    framed.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
    if (table.getTableHeader() != null) framed.add(table.getTableHeader(), BorderLayout.NORTH);
    framed.add(table, BorderLayout.CENTER);
    page.add(framed, BorderLayout.CENTER);
    return page;
  }

  private static JScrollPane scrolling(JComponent page) {
    JScrollPane scrolling = new JScrollPane(page);
    scrolling.setBorder(null);
    scrolling.getVerticalScrollBar().setUnitIncrement(16);
    return scrolling;
  }

  private static String firstPicture(List<AdditionalDownload> files) {
    return files.stream().map(file -> file.path).filter(path -> path.toLowerCase()
        .matches(".*\\.(jpg|jpeg|png|gif)")).findFirst().orElse(null);
  }

  /** A picture from wherever the catalogue says it is, given when it has arrived; one that does not arrive is not missed. */
  private static void arriving(String path, int width, int height, Consumer<javax.swing.ImageIcon> given) {
    BRINGING.execute(() -> {
      try {
        java.awt.image.BufferedImage whole = ImageIO.read(kept(path).toFile());
        double fit = Math.min((double) width / whole.getWidth(), (double) height / whole.getHeight());
        Image fitted = whole.getScaledInstance(Math.max(1, (int) (whole.getWidth() * fit)), Math.max(1, (int) (whole.getHeight() * fit)), Image.SCALE_SMOOTH);
        javax.swing.SwingUtilities.invokeLater(() -> given.accept(new javax.swing.ImageIcon(fitted)));
      } catch (java.io.IOException | RuntimeException notThere) {
      }
    });
  }

  /** One lock per file being brought, so that a slow one holds back nobody but whoever asked for that same file. */
  private static final Map<String, Object> COMING = new java.util.concurrent.ConcurrentHashMap<>();
  /** Who brings files: several at once, apart from the threads Swing keeps for its own workers. */
  private static final java.util.concurrent.ExecutorService BRINGING = java.util.concurrent.Executors.newFixedThreadPool(6, work -> {
    Thread bringing = new Thread(work, "game card: bringing a file");
    bringing.setDaemon(true);
    return bringing;
  });

  /** That file on this disk, brought the first time it is asked for and kept: a picture seen once is not fetched again. */
  private static java.nio.file.Path kept(String path) throws java.io.IOException {
    String url = path.startsWith("http") ? path : ZxInfoApiHandler.mediaUrl(path);
    java.nio.file.Path place = DownloadAndUnzip.placeFor(url);
    synchronized (COMING.computeIfAbsent(url, first -> new Object())) {
      if (!java.nio.file.Files.exists(place)) DownloadAndUnzip.fetchAll(url);
      if (!java.nio.file.Files.isDirectory(place)) return place;
      // What came in a zip is a folder of its name; what was asked for is the file inside it, which is the one the
      // zip is named after when it holds more than one.
      String named = place.getFileName().toString().replaceAll("(?i)\\.zip$", "");
      try (java.util.stream.Stream<java.nio.file.Path> inside = java.nio.file.Files.walk(place)) {
        List<java.nio.file.Path> files = inside.filter(java.nio.file.Files::isRegularFile).sorted().toList();
        return files.stream().filter(file -> file.getFileName().toString().equalsIgnoreCase(named)).findFirst()
            .or(() -> files.stream().findFirst()).orElseThrow(() -> new java.io.IOException("nothing inside " + place));
      }
    }
  }

  /** Everything light the game has, brought while the person is still reading the wall, so that it is there when they go in. */
  private static void bringAhead(GameDetail known) {
    List<AdditionalDownload> light = known.additionalDownloads == null ? List.of() : known.additionalDownloads.stream()
        .filter(file -> file.path != null && file.size != null && file.size < 3_000_000).toList();
    light.forEach(file -> BRINGING.execute(() -> {
      try {
        kept(file.path);
      } catch (java.io.IOException | RuntimeException notNow) {
      }
    }));
  }

  private static String joined(String between, String... parts) {
    return String.join(between, java.util.Arrays.stream(parts).filter(part -> part != null && !part.isBlank()).toList());
  }

  private static String text(Integer number) {
    return number == null ? null : String.valueOf(number);
  }

  private static String nameOf(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /** The top of the card: the game's name large over a dark ground, its screen, and the 48K's stripes in the corner. */
  private static final class Heading extends JPanel {
    Heading(Desk.Game asked, GameDetail known, boolean looking) {
      super(new BorderLayout(18, 0));
      setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 90));
      JPanel words = new JPanel();
      words.setOpaque(false);
      words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
      String file = asked.file() == null ? null : new File(asked.file()).getName();
      if (file != null) words.add(line(file + (known == null ? "" : "   →   recognised by what is inside it"), 12f, Font.PLAIN, STRIPES[3]));
      words.add(javax.swing.Box.createVerticalStrut(6));
      words.add(line(looking ? "Looking at what is inside it..." : known == null ? "I do not know this one" : known.title, 30f, Font.BOLD, PAPER));
      words.add(javax.swing.Box.createVerticalStrut(8));
      if (looking) {
        words.add(line("The catalogue is being asked what it has on it.", 14f, Font.PLAIN, QUIET));
      } else if (known == null) {
        words.add(line("It plays all the same. Tell me which game it is, below.", 14f, Font.PLAIN, QUIET));
      } else {
        words.add(line(joined("  ·  ", known.publisher, known.yearOfRelease, known.machineType), 15f, Font.PLAIN, PAPER));
        words.add(javax.swing.Box.createVerticalStrut(4));
        words.add(line(known.authors == null ? "" : "by " + String.join(", ", known.authors), 13f, Font.PLAIN, QUIET));
        words.add(javax.swing.Box.createVerticalStrut(10));
        words.add(line(joined("     ", known.score == null ? null : "★ " + known.score, known.genre), 13f, Font.BOLD, STRIPES[1]));
        if (known.screenshots != null && !known.screenshots.isEmpty()) {
          JLabel screen = new JLabel("", JLabel.CENTER);
          screen.setPreferredSize(new Dimension(224, 168));
          screen.setBorder(BorderFactory.createLineBorder(new Color(0x3A3A44), 2));
          arriving(known.screenshots.get(0), 220, 164, screen::setIcon);
          add(screen, BorderLayout.WEST);
        }
      }
      add(words, BorderLayout.CENTER);
    }

    private static JLabel line(String text, float size, int style, Color colour) {
      JLabel line = new JLabel(text);
      line.setFont(line.getFont().deriveFont(style, size));
      line.setForeground(colour);
      return line;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D pen = (Graphics2D) g.create();
      pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      pen.setColor(INK);
      pen.fillRect(0, 0, getWidth(), getHeight());
      for (int stripe = 0; stripe < STRIPES.length; stripe++) {
        pen.setColor(STRIPES[stripe]);
        int foot = getWidth() - 150 + stripe * 16, lean = getHeight() * 2 / 3;
        pen.fillPolygon(new int[]{foot, foot + 16, foot + 16 + lean, foot + lean}, new int[]{getHeight(), getHeight(), 0, 0}, 4);
      }
      pen.dispose();
    }
  }

  /** One kind of thing on the wall: how many there are, large; what they are; and the first of them, when it is a picture. */
  private static final class Tile extends JComponent {
    private final int howMany;
    private final String kind;
    private final Color stripe;
    private Image picture;
    private boolean under;

    Tile(int howMany, String kind, String picture, Color stripe, Runnable pressed) {
      this.howMany = howMany;
      this.kind = kind;
      this.stripe = stripe;
      setPreferredSize(new Dimension(230, 84));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      if (picture != null) arriving(picture, 160, 160, arrived -> {
        this.picture = arrived.getImage();
        repaint();
      });
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          pressed.run();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          under = true;
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          under = false;
          repaint();
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D pen = (Graphics2D) g.create();
      pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      int width = getWidth() - 1, height = getHeight() - 1;
      pen.setColor(under ? ground().brighter() : ground());
      pen.fillRoundRect(0, 0, width, height, 14, 14);
      pen.setColor(under ? ink() : edge());
      pen.setStroke(new BasicStroke(under ? 1.6f : 1f));
      pen.drawRoundRect(0, 0, width, height, 14, 14);
      pen.setColor(stripe);
      pen.fillRoundRect(18, (getHeight() - 40) / 2, 40, 40, 12, 12);
      pen.setColor(INK);
      pen.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      sign(pen, 38, getHeight() / 2);
      if (picture != null) {
        java.awt.Shape whole = pen.getClip();
        int left = getWidth() - 86, top = (getHeight() - 60) / 2;
        pen.clip(new java.awt.geom.RoundRectangle2D.Float(left, top, 72, 60, 10, 10));
        double fill = Math.max(72.0 / picture.getWidth(null), 60.0 / picture.getHeight(null));
        int wide = (int) (picture.getWidth(null) * fill), tall = (int) (picture.getHeight(null) * fill);
        pen.drawImage(picture, left + (72 - wide) / 2, top + (60 - tall) / 2, wide, tall, null);
        pen.setClip(whole);
        pen.setColor(edge());
        pen.setStroke(new BasicStroke(1f));
        pen.drawRoundRect(left, top, 72, 60, 10, 10);
      }
      int text = 70;
      pen.setColor(ink());
      if (howMany > 0) {
        pen.setFont(getFont().deriveFont(Font.BOLD, 26f));
        pen.drawString(String.valueOf(howMany), text, 40);
      }
      pen.setFont(getFont().deriveFont(howMany > 0 ? Font.PLAIN : Font.BOLD, howMany > 0 ? 13f : 16f));
      pen.setColor(howMany > 0 ? soft() : ink());
      pen.drawString(kind, text, howMany > 0 ? 60 : 47);
      pen.dispose();
    }

    /** A small drawing of what the kind is, centred there: a few strokes each, so that the wall reads before its words do. */
    private void sign(Graphics2D pen, int x, int y) {
      switch (kind) {
        case "Play it" -> pen.fillPolygon(new int[]{x - 6, x + 9, x - 6}, new int[]{y - 9, y, y + 9}, 3);
        case "Recordings" -> {
          pen.drawOval(x - 10, y - 10, 20, 20);
          pen.fillOval(x - 5, y - 5, 10, 10);
        }
        case "Pokes" -> {
          pen.drawArc(x - 6, y - 12, 12, 14, 0, 180);
          pen.fillRoundRect(x - 9, y - 4, 18, 14, 4, 4);
        }
        case "Maps" -> pen.drawPolyline(new int[]{x - 11, x - 4, x + 4, x + 11, x + 11, x + 4, x - 4, x - 11, x - 11},
            new int[]{y - 8, y - 11, y - 8, y - 11, y + 8, y + 11, y + 8, y + 11, y - 8}, 9);
        case "Music" -> {
          pen.fillOval(x - 9, y + 3, 9, 7);
          pen.drawLine(x - 1, y + 6, x - 1, y - 10);
          pen.drawLine(x - 1, y - 10, x + 8, y - 7);
        }
        case "Screens" -> {
          pen.drawRoundRect(x - 11, y - 9, 22, 16, 4, 4);
          pen.drawLine(x - 5, y + 11, x + 5, y + 11);
        }
        case "Media" -> {
          pen.drawRoundRect(x - 12, y - 8, 24, 16, 4, 4);
          pen.drawOval(x - 8, y - 3, 6, 6);
          pen.drawOval(x + 2, y - 3, 6, 6);
        }
        case "Editions" -> {
          pen.drawRect(x - 10, y - 5, 14, 15);
          pen.drawPolyline(new int[]{x - 6, x - 6, x + 9, x + 9, x + 4}, new int[]{y - 5, y - 10, y - 10, y + 6, y + 6}, 5);
        }
        case "About" -> {
          pen.fillOval(x - 2, y - 11, 5, 5);
          pen.fillRoundRect(x - 2, y - 3, 5, 14, 3, 3);
        }
        case "Adverts" -> pen.drawPolygon(new int[]{x - 11, x - 3, x + 9, x + 9, x - 3, x - 11}, new int[]{y - 4, y - 4, y - 11, y + 11, y + 4, y + 4}, 6);
        default -> {
          pen.drawPolyline(new int[]{x - 8, x - 8, x + 3, x + 8, x + 8, x - 8}, new int[]{y + 11, y - 11, y - 11, y - 6, y + 11, y + 11}, 6);
          pen.drawLine(x - 4, y - 2, x + 4, y - 2);
          pen.drawLine(x - 4, y + 4, x + 4, y + 4);
        }
      }
    }
  }
}
