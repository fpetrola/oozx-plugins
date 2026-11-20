package com.fpetrola.oozx.api;

import dev.plugdesk.desk.KnowsAbout;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.net.URI;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What ZXInfo knows of the real thing a plugin emulates, by its ZXDB entry ({@code zxdb:1000221}), asked the way the
 * game browser asks: its API, answered from the copy of ZXDB that ships when it cannot be reached.
 */
public class KnownToZxInfo implements KnowsAbout {

  private static final String KNOWN = "zxdb:";
  private static final int WAIT = 8000;

  @Override
  public About about(String known) {
    if (known == null || !known.startsWith(KNOWN)) return null;
    String id = known.substring(KNOWN.length());
    GameEntry entry = Catalogues.game(String.format("%07d", Integer.parseInt(id)));
    if (entry == null) return null;
    String made = entry.publishers == null ? null
        : entry.publishers.stream().map(publisher -> publisher.name).collect(Collectors.joining(", "));
    String year = entry.originalYearOfRelease == null ? null : String.valueOf(entry.originalYearOfRelease);
    String price = price(entry);
    Map<String, String> facts = new LinkedHashMap<>();
    if (made != null && !made.isEmpty()) facts.put("Made by", made);
    if (year != null) facts.put("Released", year);
    if (price != null) facts.put("Cost", price);
    facts.put("Known as", entry.title);
    String then = Stream.of(made, year, price).filter(Objects::nonNull).filter(said -> !said.isEmpty())
        .collect(Collectors.joining("  ·  "));
    String said = entry.hardwareBlurb != null ? entry.hardwareBlurb : entry.remarks;
    return new About(then.isEmpty() ? entry.title : then, said == null ? null : plain(said), picture(entry.additionalDownloads),
        facts, "https://zxinfo.dk/details/" + id, "ZXInfo");
  }

  private static String price(GameEntry entry) {
    GameEntry.Price price = entry.originalPrice != null ? entry.originalPrice
        : entry.releases == null || entry.releases.isEmpty() ? null : entry.releases.getFirst().releasePrice;
    if (price == null || price.amount == null) return null;
    String currency = price.currency == null ? "" : price.currency;
    return price.prefix == null || price.prefix != 0 ? currency + price.amount : price.amount + " " + currency;
  }

  /** ZXDB writes a mention of another entry as {Title|Publisher|id}: a person reads the title. */
  private static String plain(String said) {
    return said.replaceAll("\\{([^|}]*)\\|[^}]*}", "$1").strip();
  }

  /** The first photograph of the real one, fetched; null when there is none or it cannot be had. */
  private static Image picture(List<AdditionalDownload> downloads) {
    if (downloads == null) return null;
    for (AdditionalDownload download : downloads) {
      if (!"Hardware picture".equals(download.type) || download.path == null) continue;
      try {
        URLConnection connection = URI.create(ZxInfoApiHandler.mediaUrl(download.path)).toURL().openConnection();
        connection.setConnectTimeout(WAIT);
        connection.setReadTimeout(WAIT);
        try (var in = connection.getInputStream()) {
          Image image = ImageIO.read(in);
          if (image != null) return image;
        }
      } catch (Exception cannotBeHad) {
        // The next one, if there is one.
      }
    }
    return null;
  }
}
