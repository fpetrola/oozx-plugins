package model.tests.devices;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.devices.spec256.Spec256Peripheral;
import com.fpetrola.oozx.speccy.machine.Spec48;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import model.harness.MachineTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;

@EnabledIfSystemProperty(named = "probe", matches = ".+")
@org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
class SpeedProbe extends MachineTest {
  private Speccy started(String snapshot, boolean picture) {
    Speccy speccy = silentMachine();
    select(speccy, speccy.machine.model(Spec48.class));
    speccy.picture.active = picture;
    Snapshots.of(speccy).load(snapshot);
    speccy.loop.applyWhatWasDeferred();
    return speccy;
  }

  private void measure(String what, Speccy speccy) {
    runFrames(speccy, 300);
    double best = Double.MAX_VALUE;
    for (int block = 0; block < 5; block++) {
      long start = System.nanoTime();
      runFrames(speccy, 300);
      best = Math.min(best, (System.nanoTime() - start) / 1e6 / 300);
    }
    System.out.printf("== %-28s %.2f ms/frame%n", what, best);
  }

  @Test
  void probe() throws Exception {
    String game = System.getProperty("probe");
    Path alone = Files.createTempDirectory("alone").resolve(Path.of(game).getFileName());
    Files.copy(Path.of(game), alone);
    String only = System.getProperty("only", "");
    if (only.isEmpty() || only.equals("one")) measure("one core, picture", started(alone.toString(), true));
    if (only.isEmpty() || only.equals("nine-off")) measure("nine, picture off", started(game, false));
    if (only.isEmpty() || only.equals("nine-sinclair")) {
      Speccy speccy = started(game, true);
      ((Spec256Peripheral) speccy.peripheralRegistry.find(Spec256Peripheral.class)).inItsColours(false);
      measure("nine, sinclair picture", speccy);
    }
    if (only.isEmpty() || only.equals("nine-colours")) measure("nine, 256 colours", started(game, true));
  }
}
