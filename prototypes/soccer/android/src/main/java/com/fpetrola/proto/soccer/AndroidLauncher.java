package com.fpetrola.proto.soccer;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/** El juego en el teléfono: pantalla completa, acostado, con los botones en la pantalla o un mando. */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.depth = 16;
        config.numSamples = 2;
        initialize(new Soccer(new MandoAndroid()), config);
    }
}
