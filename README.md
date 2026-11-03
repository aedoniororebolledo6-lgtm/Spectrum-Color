
|Cómo se juega|

- Toca la pantalla para impulsar la pelota hacia arriba (cae sola por la gravedad).
- Atraviesa solo el color de tu pelota. Los anillos repiten sus colores dos veces por vuelta (abajo y arriba igual) y las barras son
  de un solo carril: basta esperar a que llegue "tu" color y cruzar de un salto. En cuadrados y triángulos a veces toca esperar dentro.
- Los niveles empiezan con 2 colores, pasan a 3 y solo los últimos tres (18 a 20) usan 4.
- El orbe de colores cambia el color de tu pelota (el centro del orbe muestra el color que te tocará).
- Cada obstáculo superado suma 1 punto.

|Modos|

| Modo | Qué es |
|------|--------|
| |Jugar| | 20 niveles jugables, de 3 estrellas cada uno (3 = sin morir, 2 = hasta 2 intentos fallidos). Se abren de uno en uno. |
| |Modo infinito| | Recorrido sin fin que se pone más difícil. Se guarda el récord. |
| Multijugador local| | Dos celulares en el mismo Wi-Fi (o uno haciendo punto de acceso). Mismo recorrido para los dos; gana quien sume más puntos en total tras 1, 3 o 5 rondas (75 s por ronda). Hay revancha. |


|Multijugador, paso a paso|

1. Los dos celulares en el mismo Wi-Fi (o uno crea un punto de acceso y el otro se conecta).
2. Uno toca |Multijugador local| → Crear sala. El otro toca |Unirse a una sala| y elige la sala que aparece
   (si no aparece, |Escribir IP| con la IP que muestra el anfitrión).
3. El anfitrión toca |¡Empezar!|. Cuenta atrás y a jugar

|Modo Aventura: enemigos|

| Enemigo | Qué hace | Cómo vencerlo |
|---------|----------|---------------|
| Arquero | Mantiene la distancia y dispara flechas en parábola apuntando adonde vas | Acércate rápido; el látigo desvía las flechas |
| Slime | Salta hacia ti; al morir se divide en dos pequeños | Golpea a los pequeños antes de que te rodeen |
| Bombardero | Vuela encima y suelta bombas que explotan en el suelo | Salta y golpéalo, o aléjate cuando esté justo arriba |
| Mago | Se desvanece, aparece a tu lado y lanza orbes | Después de lanzar queda cansado: es el momento de atacar |
| Lobo | Se agacha (aviso "!") y salta hacia donde vas a estar | Frena o cambia de dirección justo después del aviso |
| Calavera | Da vueltas y se lanza en picada | Muévete al ver el aviso y golpéala cuando pase |
| Gólem | Lento y duro; golpea el piso y lanza ondas | Salta las ondas y pégale mientras está agachado |

- Todos los enemigos retroceden y se aturden un instante al recibir un golpe.
- **Pisotón**: caer sobre la cabeza de un enemigo lo daña y te hace rebotar (también funciona con los caballeros, que bloquean de frente).
- Del mundo 7 en adelante aparecen **élites** (brillo dorado): más vida, más rápidos y más daño, pero sueltan mejores premios.
- En los mundos finales hay **desafíos de élite**, emboscadas con una tercera oleada (con carne para curarte entre oleadas) y jefes con 3 fases que encadenan ataques.

Pruebas del modo Aventura: `tools/run_adv_tests.sh real` (el bot juega con daño y debe ganar los 12 mundos)
y `tools/run_adv_tests.sh god 640 rooms` (todas las salas se pueden recorrer).

La IA de los enemigos (`AdvAi.kt`) adapta ideas de [libgdx/gdx-ai](https://github.com/libgdx/gdx-ai) (Apache 2.0):
comportamientos de dirección (Seek, Arrive, Pursue, Flee, Wander) y árboles de comportamiento.
