# La pelota en los pies

Plan para que el jugador trate la pelota con el pie y que las jugadas del fútbol real (pisadas,
elástica, levantarla, jueguito, sombrero, caño, taco...) salgan de combinar pocas acciones
básicas, en vez de programar cada jugada como un caso aparte.

## El principio

Tres acciones físicas, dos botones y las flechas:

| Acción | Qué es | Cómo |
|---|---|---|
| El cuerpo | corre hacia donde dicen las flechas | flechas |
| La suela | el pie se apoya en la pelota y la pelota va adonde va el pie | mantener **Z** |
| El golpe | el pie le pega; la pelota sale según dónde y cómo le pega | **X** |

Las flechas son relativas a la cámara cuando mueven el cuerpo (como ahora) y relativas a hacia
dónde mira el jugador cuando mueven el pie.

Las jugadas se encadenan: lo que hace la suela deja la pelota en algún lugar y con alguna
velocidad, y lo que hace el golpe después depende de eso. Levantarla es arrastrarla hacia atrás
con la suela y pegarle mientras viene rodando hacia el pie: el pie le entra por abajo y la pelota
sube. No hay una "jugada levantar".

El control encarrila: acomoda el pie, espera a la pelota, hace que el cuerpo siga al pie. No
inventa jugadas.

## Qué hace cada botón según la situación

| Situación | Z | X |
|---|---|---|
| Con la pelota al pie | mantener: suela, las flechas mueven el pie · toque: amague (el pie pasa por arriba sin tocarla) | toque: pase, o toque corto si no hay a quién · mantener: fuerza; la primera flecha la deja fija y desde ahí las flechas dan la forma; suelta y sale |
| La pelota llega | mantener: la recibe con la suela; con una flecha, la orienta | de primera cuando llegue |
| Pelota en el aire cerca | bajarla | pie, muslo o cabeza según la altura: jueguito, volea, cabezazo |
| Sin la pelota | cerca de la jugada (a menos de 5 m de la pelota): mantener = contener de frente, con las piernas abiertas, y nunca cambia de jugador · lejos: cambiar de jugador | toque: puntazo para sacársela · mantener: barrida |
| Cargando un tiro | cancela el tiro (vuelve a la suela) | — |

Cambios respecto de hoy: X sin la pelota ya no barre apenas se aprieta (toque = puntazo, mantener
= barrida), y la pelota más floja que sale es mucho más floja: un toque sin compañero la empuja 6
a 8 m y el jugador sale a buscarla (el autopase).

Cómo se decide qué es Z al apretarlo: si el jugador tiene la pelota o le va a llegar en menos de
0,4 s, es suela; si no, cambia de jugador, y si se lo sigue manteniendo, el nuevo jugador contiene.

## Lo que no se programa caso por caso

- **Gestos.** No hay reconocedores de elástica, bicicleta ni ruleta. El pie se mueve adonde dicen
  las flechas, con su alcance y su velocidad, y la pelota lo sigue. La elástica es un costado y
  enseguida el otro; sale porque el pie hizo eso.
- **Animaciones.** No hay una por jugada. La pierna sigue al pie (rodillas con IK); el gesto sale
  de dónde está el pie.
- **Reacciones de la máquina.** Los defensores leen hacia dónde se mueven la pelota y el pie y se
  comprometen con un lado. Cualquier amague los engaña por eso, sin programar cada amague.
- **El caño.** Las piernas son dos cuerpos con un hueco en el medio. La pelota pasa o no pasa.

## Las partes

### 1. El pie (`Foot`)

Cada jugador tiene un pie de juego: un punto (con velocidad) dentro del alcance de la pierna,
relativo al cuerpo: adelante/atrás, costado y altura. En la física es una esfera cinemática chica
que empuja lo que toca con la velocidad con que se mueve, como hoy la pierna de la barrida.

- Alcance: 0,85 m del centro del cuerpo a ras del piso; 0,6 m si el jugador corre a más de 4 m/s.
  Altura: el pie llega a 0,6 m; de 0,8 a 1,2 m juega el muslo; de 1,6 a 2,1 m la cabeza.
- Velocidad del pie: hasta 7 m/s, aceleración 60 m/s². Alcanza para una elástica (medio metro ida
  y vuelta en 0,3 s).
- Dónde se para solo: al apretar Z va a la pelota si está al alcance (arriba si la pelota está
  quieta respecto del cuerpo, al costado si se viene corriendo con ella). Al apretar X va al
  encuentro de la pelota, también en altura.
- El pie del dueño no choca con su propia pelota: la relación entre los dos es el pegado (abajo).
  Sí choca con la pelota de los demás y con las piernas de los demás: de ahí salen el puntazo y
  las faltas, con la misma regla que la barrida (si toca la pierna antes que la pelota, es falta).

### 2. La suela (Z con la pelota)

- **Pegado.** Mientras Z está apretado, la pelota busca el punto bajo la suela (o delante del pie,
  si se la empuja) con un resorte: ganancia 12/s, aceleración máxima 60 m/s². Es un resorte y no
  una velocidad impuesta para que un rival que la toque pueda romperlo: si un cuerpo rival toca la
  pelota, o la pelota queda a más de 1,2 m del pie, el pegado se corta y la pelota sigue por la
  física.
- **El cuerpo sigue al pie.** Si las flechas llevan el pie más allá de 0,6 m del cuerpo, el cuerpo
  camina hacia el pie, a lo sumo a 3,5 m/s. Si la pelota queda detrás (producto con la mirada
  menor que −0,3), el cuerpo gira hacia ella a 360°/s. De ahí salen el giro de Riquelme (Z y
  atrás) y la ruleta (atrás en diagonal, sostenido: sigue girando con la pelota bajo la suela).
- **Soltar.** La pelota sigue con la velocidad que le dio el pie. Soltar Z vuelve a correr al
  instante: Z, soltar y flecha adelante es frenar y arrancar.
- **Amague.** Un toque de Z: el pie hace un arco por arriba de la pelota, hacia el costado de la
  flecha, sin tocarla. No hace nada por sí mismo; sirve porque la máquina mira el pie.
- **Correr con la pelota** sin apretar nada queda como hoy (`keepClose`): la pelota un poco
  adelante, con la zancada. Conceptualmente son toques del pie; en la etapa 3 se van a ver.

### 3. El golpe (X)

El golpe es un impulso calculado con la geometría del pie y la pelota, no una colisión cruda, para
que con ocho direcciones de teclado se pueda apuntar y para que la flecha de previsualización siga
diciendo la verdad. Lo que cambia respecto de hoy es que la pelota ya no sale sólo de lo que digan
las flechas: cuenta cómo le llega la pelota al pie.

- **Fuerza.** Un toque sale a 5 m/s (hoy 13); mantener la lleva a 32 m/s en 0,7 s. Por eso ahora
  pueden salir el sombrero, el globito, la Panenka y el autopase.
- **Forma.** Las flechas mientras se mantiene: atrás sube (el pie le entra por abajo), adelante
  baja (le entra por arriba, y pica), los costados dan comba (le pega de costado). Como hoy.
- **Cómo llega la pelota.** Si la pelota viene rodando hacia el pie, el pie le entra por abajo:
  suma elevación, 4° por cada m/s de acercamiento, hasta 45°. Si se aleja, resta. De ahí salen
  levantarla (arrastrar hacia atrás y pegarle) y el taco (flecha atrás: le pega desde adelante).
- **En el aire.** Según la altura a la que la agarra: con el pie (hasta 0,6 m) es una patada con
  forma; con el muslo (0,8 a 1,2 m) la sube 3 a 4 m/s con poco avance; con la cabeza como hoy. Una
  pelota que baja sobre el pie, con un toque, sube de nuevo: el jueguito son toques a tiempo. Con
  una flecha la empuja un poco para ese lado; con más fuerza hacia adelante es un sombrero o una
  volea.
- **Espera.** El golpe espera hasta 0,15 s a que la pelota llegue al pie, como hoy. En el jueguito
  perdona apretar un poco antes.
- **Sin la pelota.** Un toque es un puntazo: el pie se estira hasta 1,2 m durante 0,25 s y si toca
  la pelota se la lleva (o la saca). Mantener X 0,2 s es barrida, como hoy.
- Z mientras se carga cancela el tiro: el pie vuelve a la suela.

### 4. Las piernas y el caño

- El cuerpo del jugador pasa a ser una cápsula de la rodilla para arriba (de 0,55 a 1,86 m), sin
  gravedad, y dos piernas cinemáticas (radio 0,07 m) que siguen la zancada o la postura. Las
  piernas chocan con la pelota; los cuerpos chocan entre sí como hoy.
- Corriendo, las piernas van a ±0,12 m del eje: el hueco entre ellas es de 0,10 m, no pasa una
  pelota de 0,22 m. Parado conteniendo (Z sin pelota) o plantado de frente a la pelota, van a
  ±0,22 m: el hueco es de 0,30 m y la pelota pasa. Un defensor que se compromete con un amague
  abre las piernas un instante: también ahí pasa.
- Así el caño no es una jugada: es una pelota rasante (de un toque, o arrastrada con la suela)
  contra un rival con las piernas abiertas.

### 5. La máquina lee el pie

- La predicción de adónde va la pelota de un rival que la lleva pasa a ser pelota más 0,6 veces la
  velocidad de su pie. Un pie que se mueve a la derecha es una pelota que va a ir a la derecha.
- El defensor se compromete: cuando elige un lado (más de 0,3 m de desvío lateral) lo mantiene un
  tiempo antes de volver a mirar: 0,45 s en fácil, 0,3 en normal, 0,18 en difícil. Y tarda en
  reaccionar 0,25, 0,15 y 0,08 s.
- Con esas dos reglas, la bicicleta, la elástica y la croqueta engañan: el pie va para un lado, el
  defensor se compromete, la pelota va para el otro. En fácil casi siempre, en difícil pocas veces.

### 6. Las rodillas

- Las piernas del muñeco pasan a tener muslo y canilla con IK analítica de dos huesos: dado dónde
  está la cadera y dónde tiene que estar el pie, sale la rodilla. El polo apunta hacia adelante.
- El pie del muñeco sigue al pie de juego: la pisada, el arrastre, la elástica y el jueguito se
  ven porque la pierna va adonde fue el pie. Corriendo, las rodillas se doblan con la zancada.
- Lo demás del muñeco (brazos, tronco, barrida, estirada) queda como está.

### 7. Las ayudas del control

- El pie se acomoda solo: no hay que apuntarlo con precisión.
- El golpe espera a la pelota (de primera, jueguito).
- El cuerpo sigue al pie para no perder la pelota, y gira si queda atrás.
- En el aire, sin flechas, el cuerpo se queda debajo de donde va a caer la pelota.
- Después de un toque sin compañero o de un sombrero, el jugador sale solo a buscarla.
- La elástica cuenta si se hace en menos de 0,3 s: el pie va a la velocidad que haga falta para
  llegar ida y vuelta.
- Correr con la pelota sin apretar nada sigue siendo tan fácil como hoy.

## Etapas

Cada etapa termina con las pruebas en verde, un partido de diez minutos con teclas al azar sin
errores, y capturas del juego real de lo nuevo.

### Etapa 0: las pruebas al módulo

Las simulaciones que hoy viven en `target/sim` pasan a `src/test/java` como pruebas JUnit 5
(agregar `junit-jupiter` 5.11.4 y surefire al pom del prototipo). Corren sin ventana: Bullet carga
sus nativos sin GL. Las capturas quedan como una herramienta aparte, en `src/test/java`, que se
corre a mano. Un `Guion` común aprieta botones a 60 Hz; todas las pruebas fijan la semilla del azar.

### Etapa 1: el pie, la suela y el golpe

Lo más grande. `Foot` y su cuerpo en `Physics`; la suela y el golpe en `SensibleScheme`;
`strike` y el pegado en `Match`; la regla de Z en `Pad` y el esquema; el marcador del pie y la
ayuda en pantalla en `Soccer`. Las piernas siguen rígidas: lo que se mide es la pelota.

Se da por hecha cuando, sin tocar nada más que botones:

| Jugada | Guion | Qué tiene que pasar |
|---|---|---|
| Pisarla | corriendo a 6 m/s, mantener Z sin flechas | en 0,5 s el jugador va a menos de 1 m/s y la pelota está a menos de 0,4 m del cuerpo |
| Croqueta | Z y costado 0,2 s, soltar con flecha adelante | la pelota se corre 0,6 a 1,0 m de costado (contando lo que sigue después de soltar) y sigue siendo suya 1 s después |
| Elástica | Z y derecha 0,12 s, Z e izquierda 0,12 s | la pelota va al menos 0,4 m a la derecha y vuelve a la izquierda del eje; la sigue teniendo |
| Giro | Z y atrás 0,5 s | mira al revés (producto con la mirada inicial menor que −0,5) con la pelota bajo el pie |
| Levantarla | Z y atrás 0,2 s, soltar, toque de X | sube entre 0,8 y 1,5 m y cae a menos de 1,5 m del jugador |
| Jueguito | levantarla y un toque de X cada vez que baja de 0,7 m | cinco toques seguidos sin que toque el piso, sin moverse más de 1,5 m |
| Sombrero | desde el jueguito, cuando baja de 1,2 m, X 0,2 s con flecha adelante | pico de 2 m o más, pica entre 4 y 8 m adelante |
| Control orientado | pase que llega, mantener Z y derecha al llegar | la pelota queda entre 0,8 y 1,5 m a la derecha y es suya |
| Taco | toque de X con flecha atrás | sale hacia atrás (producto menor que −0,7) entre 5 y 10 m/s |
| Autopase | toque de X hacia donde no hay compañero | rueda 6 a 8 m y la vuelve a tener antes de 2 s sin tocar nada |
| Lo de antes | las pruebas de tiros, arquero y reglas | dan lo mismo que hoy, con un 10 % de tolerancia |

Capturas: secuencias de levantarla y hacer jueguito, de la elástica y del giro.

### Etapa 2: piernas, caño, puntazo, contener, y la máquina que lee el pie

Cuerpo de la rodilla para arriba y piernas en `Physics`; postura en `Player`; el puntazo y la
barrida con espera, y Z mantenido sin pelota, en `SensibleScheme`; la lectura del pie y el
compromiso en `Trajectory`, `Match` y `Difficulty`.

| Qué | Cómo se mide |
|---|---|
| Caño | pelota rasante a 10 m/s al centro de un rival parado conteniendo: pasa en el 60 % o más de 100 intentos; con el rival corriendo, rebota en el 90 % o más |
| Amague | elástica contra un defensor que viene a quitarla: se compromete al lado del primer movimiento en el 60 % o más (fácil) y el 25 % o menos (difícil); el que la lleva la conserva en el 50 % o más, y el 30 % o menos |
| Puntazo | toque de X a 1,5 m del que la lleva: se la saca si el pie llega antes a la pelota que a la pierna; si no, falta |
| Contener | Z mantenido sin pelota: queda de frente a la pelota a 3 m/s o menos, con las piernas abiertas |
| Dificultad | la máquina en fácil hace 10 goles o menos en seis minutos contra un humano quieto, como hoy |

### Etapa 3: las rodillas

`Puppet` con muslo y canilla e IK; el pie del muñeco sigue al pie de juego; la carrera con
rodillas. No hay números: capturas de las poses (pisada, arrastre, cuadros de la elástica,
jueguito, carrera) y una prueba de que ningún pie queda bajo el pasto ni da NaN.

### Etapa 4: alrededor de la pelota

Lo que no es el toque pero hace falta para que las jugadas sirvan en un partido: el pase adelanta
al compañero según su carrera; los delanteros se desmarcan cuando el equipo tiene la pelota; el que
da una pared sigue corriendo; un tiro cargado que cae cerca de un compañero lo marca como receptor;
el gesto del taco; la ayuda en pantalla al día.

## Cambios por archivo

| Archivo | Etapa 1 | Etapa 2 | Etapa 3 |
|---|---|---|---|
| `Foot.java` (nuevo) | estado del pie, alcance, velocidad, dónde se para solo | puntazo | |
| `Physics.java` | esfera del pie, contactos pie-pelota y pie-pierna, pares que se ignoran | cuerpo de la rodilla para arriba sin gravedad, piernas, postura | |
| `Player.java` | el pie | postura, puntazo, contener | |
| `Pad.java` | Z como botón con apretar, mantener y soltar | | |
| `SensibleScheme.java` | suela, golpe por geometría, regla de Z, cancelar carga | X sin pelota: puntazo y barrida; Z sin pelota: contener | |
| `Match.java` | `strike`, pegado, cortar la posesión por contacto del pie, autopase, recibir con la suela | lectura del pie, compromiso, puntazo y faltas | |
| `Trajectory.java` | | predicción con el pie | |
| `Difficulty.java` | | compromiso, reacción, peso del pie | |
| `Puppet.java` | pose del pie sobre la pelota (rígida) | | muslo, canilla, IK, carrera |
| `Soccer.java` | marcador del pie, ayuda en pantalla | ayuda en pantalla | |
| `pom.xml` y `src/test` | etapa 0: JUnit, las pruebas, la herramienta de capturas | | |

## Riesgos y decisiones

- **El pegado contra la física.** Un pie cinemático apoyado sobre la pelota la aplastaría contra el
  piso. Por eso el pie del dueño no choca con su pelota y la relación es el resorte, que un rival
  puede romper. Decidido así; si al jugar se siente artificial, se puede bajar la ganancia.
- **El golpe como impulso.** Una colisión cruda daría salidas imprecisas y la flecha dejaría de
  decir la verdad. Decidido: impulso calculado; la geometría entra por las reglas de cómo llega la
  pelota y a qué altura.
- **Velocidades del pie cinemático.** Bullet las saca de la diferencia de transformaciones por
  paso (1/240 s). Cuando el pie cambia de modo hay que reiniciar la transformación de
  interpolación, como con la pierna de la barrida, para que no salga un pico de velocidad.
- **Que la suela se sienta pegajosa.** El cuerpo en suela va a 3,5 m/s como mucho. Soltar Z vuelve
  a correr al instante; si igual molesta, se sube el tope.
- **Ocho direcciones.** Con teclado el pie se coloca en ocho lugares alrededor de la pelota; la
  elástica son dos toques. El modelo es analógico: con un joystick se gana control fino sin
  cambiar nada.
- **Tamaño de la etapa 1.** Si se hace larga, se parte en 1a (suela: pisar, croqueta, elástica,
  giro) y 1b (golpe: levantarla, jueguito, sombrero, taco, autopase).
- **Lo que sigue sin ser físico:** el salto del cabezazo, y si el arquero parado ataja o no.

## Cómo quedó

Lo que se decidió al hacerlo, donde difiere de lo de arriba o lo precisa.

**El pie y la suela (etapa 1).**

- Las flechas mueven el pie en la misma dirección de pantalla que mueven el cuerpo, no relativas a
  hacia dónde mira el jugador: con teclado, cambiar de sistema según el botón confunde. El giro
  sale igual: el pie va hacia la flecha, queda detrás del cuerpo y el cuerpo gira hacia él.
- El pie va a 6 m/s con 150 m/s² (no 7 y 60): con 60 la vuelta de la elástica tardaba más que los
  0,12 s de cada mitad. El pegado es un resorte de 25/s con 150 m/s² (no 12 y 60), para que la
  pelota no se quede atrás en esas vueltas.
- El pie de juego no es un cuerpo de la física: con la suela la relación es el resorte, y el
  golpe es un impulso calculado. Lo que sí está en la física son las piernas (etapa 2) y el pie
  del puntazo, que es lo que tiene que chocar con la pelota y con las piernas de los otros.
- Al apretar Z con la pelota, el pie se apoya donde está la pelota (no en un punto fijo): si no,
  la pelota pegaba un salto hasta el pie.
- Si el pie queda detrás del cuerpo más de 0,08 s, el cuerpo gira hacia él a 10 rad/s hasta
  mirarlo (no 360°/s): con 360°/s la media vuelta no entraba en 0,5 s.
- Levantarla se arma con un tirón corto: con 0,25 s de Z y atrás el pie ya llegó al tope de atrás
  y la pelota está quieta bajo la suela (y el cuerpo empieza a girar: es el giro). El guion pasó a
  0,2 s.
- Cuánto le entra el pie por abajo se mide con lo que la pelota rueda hacia atrás respecto de
  hacia dónde mira el jugador, y sólo con la pelota suelta (recién soltada la suela, o que llega):
  corriendo con la pelota pegada, la zancada la mueve para adelante y para atrás y un toque la
  levantaba al azar.
- El toque de X: en el aire, la vuelve a subir para que caiga 0,45 m delante del cuerpo (el
  jueguito; con flecha, un poco hacia ahí); al pie, pase si hay compañero hacia donde apunta; si
  no, la levanta si viene rodando hacia el pie, o la empuja para que ruede 7 m (el autopase).
- La fuerza del tiro va de 5 a 34 m/s y sube despacio al principio (la fracción del tiempo a la
  1,5), llena en 0,52 s: los toques suaves (el sombrero, un globito) se pueden dosificar. Las
  pruebas de tiros de antes mantienen un poco más para la misma fuerza (0,3 s pasó a 0,4).
- La altura con que sale es la de la parte del cuerpo que le pega (de abajo 8°, más lo que le
  entra el pie; con el pie en el aire 60°, muslo 60°, cabeza 25°) más lo que suben o bajan las
  flechas, y se vuelve a calcular al pegarle: una pelota que cae del pecho al pie mientras se
  carga sale como de pie. El sombrero se aprieta cuando la pelota baja de 1,2 m.
- La fuerza que sale es la que mostraba la flecha en el último cuadro apretado.
- Una pelota que llega a más de 0,5 m de alto no se para sola: se la baja con Z o se le pega con
  X. Si no, el jueguito se cortaba solo.
- La pelota que uno mismo tocó o tiró para arriba para ir a buscarla le pasa a través del cuerpo
  (si no, el taco y el jueguito rebotaban en el que la tocó).
- Z sin la pelota cerca cambia de jugador al soltarlo, si se soltó antes de 0,2 s; si se mantiene,
  el mismo jugador contiene (no se cambia y después contiene el nuevo).
- Recibir con la suela: Z apretado con la pelota en camino (o siendo el destinatario del pase) la
  espera; el cuerpo va a buscarla y las flechas acomodan el pie. Al llegar la toma, a la altura
  que venga.

**Las piernas y la máquina (etapa 2).**

- Las piernas son dos cápsulas cinemáticas de 0,07 m de radio y 0,55 m de alto, a ±0,12 m del
  eje (cerradas) o ±0,22 m (abiertas), que van y vienen con la zancada; el cuerpo es una cápsula
  de 0,55 m para arriba que no sube ni baja. La barrida y el puntazo chocan con las piernas.
- Dos cuidados con Bullet que salieron del partido con teclas al azar (la pelota salía a 62 m/s):
  si una pierna salta de lugar en un paso (el jugador se dio vuelta de golpe y las piernas
  cambiaron de lado), no empuja con esa velocidad; y cuando la pelota pasa a ignorar a un jugador
  (porque la agarró), se borra el contacto que tenían, que Bullet seguía resolviendo.
- Plantado es ir a menos de 1,2 m/s de frente a la pelota (a menos de 8 m): ahí abre las piernas.
- El defensor ve lo que hace el pie con retraso: lo que cree que hace se acerca a lo que pasa con
  una constante de tiempo igual a su reacción (0,25, 0,15, 0,08 s). Cuando ve que la pelota va a un
  costado (más de 0,3 m) se juega con una probabilidad según la dificultad (0,85, 0,55, 0,2): va un
  metro para ese lado, no vuelve a mirar por 0,45, 0,3 o 0,18 s y abre las piernas un instante.
  Sin esa probabilidad, todos caían igual (el compromiso siempre dura más que media elástica).
- El puntazo es un gesto de 0,4 s: el pie (una bola de 0,09 m) sale hacia la pelota si está a
  menos de 2 m (si no, hacia donde mira), se estira hasta 1,2 m a los 0,125 s y vuelve. Si toca la
  pelota primero, la saca a 7 m/s para ese lado y el que la llevaba no la puede volver a tocar por
  0,6 s; si toca primero una pierna de un rival, es falta.
- X sin la pelota: soltado antes de 0,2 s es puntazo; mantenido 0,2 s, barrida hacia las flechas.

**Las rodillas (etapa 3).** La pose se calcula aparte del dibujo (`Pose`), para poder probarla
sin ventana. Corriendo, cada pie va 0,4 m adelante y atrás con la zancada y sube 0,22 m al pasar;
la cadera baja 8 cm a toda carrera. En las acciones (barrida, estirada, cabezazo, patada) la pose
dice hacia dónde apunta cada pierna, casi estirada; si el pie quedaría bajo el pasto, se sube al
ras.

**Alrededor de la pelota (etapa 4).** El pase apunta adonde va a estar el compañero cuando le
llegue, según su carrera (dos vueltas de la cuenta: el viaje depende de adónde). El que da un pase
sigue corriendo hacia adelante 1,2 s. Con la pelota en su equipo, los delanteros van 9 m más
adelante y los volantes 4, y se abren 4 m del rival que tengan a menos de 6 m; los de la máquina
según la dificultad (2, 9 y 11 m los delanteros): con 9 en fácil, la máquina pasaba de los 12 goles
en seis minutos contra un humano quieto. Un tiro cargado que
no va al arco es para el compañero que llegue antes que cualquier rival (con 0,2 s de ventaja). El
taco es un toque con flecha hacia atrás de donde mira: no se da vuelta y le pega con el talón. La
pantalla dice qué hacen los botones en lo que se está haciendo (la suela, cargando, conteniendo).

### Después de jugarlo (primera vuelta de comentarios)

- **La pelota parecía patinar sobre hielo.** Era una esfera blanca lisa: no se la veía girar.
  Ahora tiene gajos con manchas negras y se dibuja girada como en la física, así que se la ve
  rodar. Además el pasto frena más (2 + 0,4·v m/s²), los piques son más secos (sale con 0,45 de
  la velocidad vertical) y en cada pique el roce le saca un 15 % de la horizontal y un 40 % del
  giro (en la física y en la copia que predice, para que la flecha siga diciendo la verdad).
- **La elástica dejaba la pelota lejos.** Al soltar la suela, la pelota se quedaba con toda la
  velocidad del pie (6 m/s) y se iba 2 m. Ahora se queda con la mitad de lo que iba más rápido que
  el cuerpo, y la vuelve a llevar pegada a los 0,2 s: termina 0,3 m del otro lado. El pie acelera
  más (220 m/s²) para que la vuelta sea seca.
- **Los trucos tenían que ser más fáciles de sacar.**
  - Levantarla: si la pelota quedó debajo o detrás del cuerpo después de pisarla (con Z todavía
    apretado o recién soltado), un toque de X la levanta siempre, sin depender de cuánto rodaba.
  - El jueguito: X apretado un poco antes, con la pelota bajando hacia el jugador, la espera (no
    salta a cabecear) y le pega con el pie aunque pase antes por la altura del muslo.
- **La máquina cortaba casi todos los pases.** Los pases por abajo llegaban flojos (5 m/s): ahora
  llegan a 9 m/s. La máquina tarda en darse cuenta de adónde va cada pelota pateada (0,25, 0,15 y
  0,08 s según la dificultad; los compañeros del humano, 0,08). A cambio, una pelota rápida se
  para un poco mejor que antes (a 14 m/s el alcance ya no se achica), y los de la máquina que no
  salen a buscarla la paran si les pasa al alcance. Con el defensor en el camino, el pase se
  corta; a 2 m o más, llega.
- **Fácil goleaba de más con los pases nuevos** (12 en seis minutos contra un humano quieto):
  en fácil la máquina le erra más al arco (4,5 m afuera del palo como mucho) y patea más flojo.
- **Los brazos tienen codos:** brazo y antebrazo, con el codo doblado al correr (hasta 85°) y casi
  estirado en las estiradas.

### Después de jugarlo (segunda vuelta)

- **Z cambiaba de jugador en medio de un truco.** Como es la misma tecla, si en ese momento la
  pelota no estaba exactamente al pie, Z cambiaba y se perdía el jugador. Ahora, a menos de 5 m de
  la pelota Z nunca cambia de jugador (pisa, o mantenido contiene); sólo cambia lejos de la
  jugada, y ahí cambia apenas se aprieta.
- **La fuerza y la forma se cargaban juntas.** Para llegar a un ángulo alto había que mantener
  más, y eso cargaba más fuerza: no salía un globito suave. Ahora la fuerza se carga mientras se
  mantiene X sin flechas de forma, y apenas se toca una queda fija: desde ahí las flechas sólo
  suben, bajan o curvan. La flecha que estaba apretada al apretar X (para dónde) no cuenta como
  forma mientras se la siga apretando, y si se aprieta otra junto con ella cuenta sólo la nueva.
  Además lo alto le baja el tope de fuerza a un tiro desde el piso, pero no el mínimo: el globito
  más suave sale igual a 5 m/s (antes salía a la mitad y apenas se despegaba).
  Medido: X y enseguida atrás sale con 1 % de fuerza a 61°, sube 1,2 m y pica a 3,3 m; cargar y
  después curvar deja la fuerza donde estaba.
- Si así todavía se sienten atadas, la alternativa es un segundo botón para la forma.

### Después de jugarlo (tercera vuelta)

- **Se le pasaba el control a un compañero lejos después de un sombrero en el lugar.** Dos causas:
  un tiro cargado se le asignaba al compañero que llegara antes que los rivales sin contar al que
  la pateó (en un globito en el lugar le vuelve a caer a él), y un toque de X sin flechas era un
  pase al compañero que hubiera para donde miraba, hasta a 45 m. Ahora el tiro cargado es del que
  lo pateó si a él le cae antes (y entonces no se cambia de jugador), y un toque sin flechas es
  siempre un toque: para pasarla hay que apuntar con una flecha.
- **Levantarla con la suela era difícil.** Ahora alcanza con pisarla (Z) y tocarla (X): si la
  pelota está bajo el pie o detrás, sube, sin tener que arrastrarla para atrás justo.

## El control, de nuevo y limpio (GolpeScheme)

Con tantas pruebas encima, el control había quedado enredado (la comba a veces salía y a veces no,
la potencia se trababa según el orden de las teclas). Se empezó de cero con un esquema chico,
`GolpeScheme`, que es el que usa el juego. Lo anterior quedó guardado sin usar, con sus pruebas:
`SensibleScheme` (con la suela) y su variante con botón de elevación (`new SensibleScheme(true)`).

- Flechas: mover, con la pelota pegada al pie.
- X mantenido: la potencia, que sube mientras se lo mantiene (llena en 0,6 s).
- C mantenido: baja el punto donde se le pega, de arriba de todo de la pelota (sale rasante, 6°,
  con efecto para adelante) a abajo de todo en un segundo (sale a 75°, con efecto para atrás).
- Mientras X o C están apretados el jugador no gira, y la flecha a la izquierda o a la derecha de
  hacia donde apunta le va sumando comba para ese lado (para el otro, se la saca).
- El golpe sale cuando se sueltan los dos. No hay tiempo máximo. La flecha del tiro muestra el
  recorrido si se soltara ahora: altura y curva en la forma, potencia en el color.
- Por ahora no hay pase, toque, suela ni defensa con botones: es sólo el golpe. Las pelotas paradas
  se sacan igual (las flechas apuntan, X y C arman el golpe). Lejos de la pelota, Z cambia de
  jugador.

Medido: X sola sale rasante (0,35 m) y recta; con C 0,2 s sube 1 m, 0,5 s 3,9 m y 1 s 8,7 m; la
comba con la flecha a la derecha va 2,2 m a la derecha a los 15 m en un tiro rasante (y lo mismo a
la izquierda); apretar X y C en distinto orden da el mismo golpe, y la potencia llega al máximo
aunque haya flechas apretadas; mantenido 3 s no sale, y sale recién al soltar los dos.

### La arena de práctica (tecla P) y otros arreglos

- Con P se prende y apaga la arena de práctica: los rivales de campo salen de la cancha (queda el
  arquero de enfrente), y alrededor hay paredes de 15 m, casi transparentes, medio metro afuera de
  las líneas, con hueco sólo en la boca de los arcos: la pelota no sale nunca. Las líneas no
  cuentan; después de un gol, la pelota vuelve a los pies del humano en el medio.
- Con el control nuevo Z no se usa para otra cosa: siempre cambia al compañero que llega primero a
  la pelota (antes, a menos de 5 m de la pelota, Z no hacía nada).
- El arquero que la agarra juega como cualquiera: si es del humano, se lo maneja y le pega como a
  cualquier jugador; si es de la máquina, a los 0,6 s se la pasa a un compañero libre (en la
  práctica, que no tiene compañeros, se la devuelve al humano). Una pelota floja la agarra siempre
  (antes la tapaba con un rebote porque pedía tenerla a menos de 35 cm).

- Las paredes quedaron 3 m afuera de los costados y 3,5 m detrás de las líneas de fondo, sin hueco:
  se ve si la pelota entró al arco o pasó al lado. Es gol sólo si está adentro de la red.
- La pelota hace ruido (sintetizado al arrancar, sin archivos): un golpe seco al patearla, un toc de
  vidrio contra la pared o los carteles, un clang metálico contra el palo y uno sordo contra un
  jugador, más fuerte cuanto más fuerte el choque.
- Con el control nuevo, F1 / F2 hacen más lenta o más rápida la potencia (cuánto tarda en
  llenarse), F3 / F4 la altura (cuánto tarda el punto en bajar de arriba a abajo) y F5 / F6 la comba
  (cuánta suma por segundo). Los valores se ven arriba a la izquierda.

### Lo que midieron las pruebas

Sin ventana, con Bullet de verdad y apretando sólo botones (`PieTest`, `DefensaTest`, `PoseTest`,
`JugadasTest`; las de antes siguen en verde).

| Jugada | Resultado |
|---|---|
| Pisarla | a 0 m/s, la pelota a 0,34 m del cuerpo |
| Croqueta | 0,83 m de costado, sigue siendo suya |
| Elástica | 0,73 m a la derecha, después termina 0,29 m a la izquierda; sigue siendo suya |
| Giro | mira al revés (−0,99), la pelota a 0,01 m del pie |
| Levantarla | sube a 1,29 m y cae a 0,43 m del jugador |
| Jueguito | 5 toques seguidos sin moverse |
| Sombrero | pico de 2,11 m, pica a 5,6 m |
| Control orientado | queda a 1,27 m al costado, suya |
| Taco | sale para atrás (−1,00) a 6,3 m/s |
| Autopase | rodaría 7,0 m; la vuelve a tener a los 0,35 s |
| Caño | parado de frente pasan 74 de 100; corriendo, 0 de 100 |
| Amague (elástica contra el que viene) | fácil: se juega al primer lado 31 de 40 y la conserva 26; difícil: 6 y 2 |
| Puntazo | de frente a la pelota se la saca; de atrás, pegándole primero a la pierna, falta |
| Contener | a 3,0 m/s, de frente a la pelota, piernas abiertas |
| Piernas | 3 minutos de teclas al azar: ningún pie ni rodilla bajo el pasto, sin NaN |
| Pase adelantado | el que corre la toma a los 1,5 s sin bajar de 5,4 m/s |
| Pared | el que la dio sigue 7,2 m en un segundo |
| Desmarque | con la pelota, el 9 va 8,7 m más adelante que su lugar |
| Partido de 10 minutos con teclas al azar | sin errores ni pelotas disparadas |
| Dificultad | contra un humano quieto, en seis minutos la máquina hace 7 goles en fácil y 25 en difícil |
| Pases por abajo a 15 m | con un defensor a 0,3 m del camino llegan 0 de 10; a 0,8 m, 4; a 2 m o más, todos |

## Qué queda igual

Las pelotas paradas, el arquero, las reglas, la cámara, el césped, las sombras, la flecha del
pase y del tiro, y la dificultad tal como está, salvo lo que le agrega la etapa 2.
