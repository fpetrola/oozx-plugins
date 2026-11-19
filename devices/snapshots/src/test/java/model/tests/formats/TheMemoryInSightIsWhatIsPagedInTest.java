package model.tests.formats;

import com.fpetrola.oozx.snapshots.SnaFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * A 128K snapshot holds eight banks and a processor sees three: 5, 2 and the one its port pages
 * in. A game is recognised by those, because the others may still hold the program loaded before.
 */
class TheMemoryInSightIsWhatIsPagedInTest {

  @Test
  void aSnaOf128SaysBanksFiveTwoAndTheOneItsPortPagesIn() {
    assertArrayEquals(banks(5, 2, 3), new SnaFormat().ramInSight(sna128(3)).orElseThrow());
  }

  @Test
  void itsWholeRamIsStillEveryBank() {
    assertArrayEquals(banks(0, 1, 2, 3, 4, 5, 6, 7), new SnaFormat().ram(sna128(3)).orElseThrow());
  }

  @Test
  void aSnaOf48HasAllItsRamInSight() {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    byte[] header = new byte[27];
    header[24] = (byte) 0x80;
    file.writeBytes(header);
    file.writeBytes(banks(5, 2, 0));
    assertArrayEquals(new SnaFormat().ram(file.toByteArray()).orElseThrow(), new SnaFormat().ramInSight(file.toByteArray()).orElseThrow());
  }

  /** Each bank filled with its own number, so that which ones came back can be read off. */
  private static byte[] banks(int... numbers) {
    ByteArrayOutputStream ram = new ByteArrayOutputStream();
    for (int number : numbers) {
      byte[] bank = new byte[16384];
      Arrays.fill(bank, (byte) (number + 1));
      ram.writeBytes(bank);
    }
    return ram.toByteArray();
  }

  private static byte[] sna128(int paged) {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    file.writeBytes(new byte[27]);
    file.writeBytes(banks(5, 2, paged));
    file.writeBytes(new byte[]{0, (byte) 0x80, (byte) paged, 0});
    file.writeBytes(banks(java.util.stream.IntStream.range(0, 8).filter(bank -> bank != 5 && bank != 2 && bank != paged).toArray()));
    return file.toByteArray();
  }
}
