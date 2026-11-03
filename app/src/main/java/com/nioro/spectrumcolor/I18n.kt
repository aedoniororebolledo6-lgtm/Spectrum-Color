package com.nioro.spectrumcolor

import java.util.Locale

/**
 * Idiomas: 0 = español (texto original del código), 1 = inglés, 2 = ruso.
 * Todo texto dibujado pasa por [t]: busca la frase exacta en el diccionario o, si lleva números/nombres,
 * una plantilla. Lo que no esté traducido se muestra tal cual.
 */
object I18n {
    var lang = 0

    val langNames = arrayOf("Español", "English", "Русский")

    fun systemDefault(): Int = when (Locale.getDefault().language) {
        "es" -> 0
        "ru" -> 2
        else -> 1
    }

    private class Pat(val re: Regex, val en: String, val ru: String)

    private fun d(es: String, en: String, ru: String) { dict[es] = arrayOf(en, ru) }

    private val dict = HashMap<String, Array<String>>()
    private val pats = ArrayList<Pat>()
    private val cache = HashMap<String, String>()
    private var cacheLang = -1

    private fun p(re: String, en: String, ru: String) { pats.add(Pat(Regex("^$re$"), en, ru)) }

    init {
        // --- menú y ajustes ---
        d("Jugar", "Play", "Играть")
        d("Modo infinito", "Endless mode", "Бесконечный режим")
        d("Multijugador local", "Local multiplayer", "Игра по сети")
        d("Ajustes", "Settings", "Настройки")
        d("AJUSTES", "SETTINGS", "НАСТРОЙКИ")
        d("Música", "Music", "Музыка")
        d("Efectos", "Effects", "Эффекты")
        d("Vibración", "Vibration", "Вибрация")
        d("Dificultad", "Difficulty", "Сложность")
        d("Fácil", "Easy", "Легко")
        d("Normal", "Normal", "Обычная")
        d("Jugador", "Player", "Игрок")
        d("Tema", "Theme", "Тема")
        d("Idioma", "Language", "Язык")
        d("Niveles", "Levels", "Уровни")
        d("Abrir todos", "Open all", "Открыть все")
        d("Todos abiertos", "All open", "Все открыты")
        d("Progreso", "Progress", "Прогресс")
        d("Borrar progreso", "Reset progress", "Сбросить прогресс")
        d("¿Seguro? Toca otra vez", "Sure? Tap again", "Уверены? Нажмите ещё")
        // modo Aventura y menús
        d("Aventura", "Adventure", "Приключение")
        d("AVENTURA", "ADVENTURE", "ПРИКЛЮЧЕНИЕ")
        d("Editar", "Edit", "Редактор")
        d("SONIDO", "SOUND", "ЗВУК")
        d("JUEGO", "GAME", "ИГРА")
        d("DATOS", "DATA", "ДАННЫЕ")
        d("Los colores huyeron de los mundos. Salva cada uno.", "The colors fled the worlds. Save each one.", "Цвета покинули миры. Спаси каждый.")
        d("Mapa completo: toca o arrastra para elegir", "Full map: tap or drag to choose", "Вся карта: нажми или проведи, чтобы выбрать")
        d("Termina un mundo del modo normal para abrir su aventura", "Finish a world in normal mode to open its adventure", "Пройди мир в обычном режиме, чтобы открыть его приключение")
        d("✓ SALVADO", "✓ SAVED", "✓ СПАСЁН")
        d("Sin arma", "No weapon", "Без оружия")
        d("Nada", "Nothing", "Ничего")
        d("Daga de luz", "Light dagger", "Кинжал света")
        d("Hacha prisma", "Prism axe", "Призменный топор")
        d("Aro bumerán", "Boomerang ring", "Кольцо-бумеранг")
        d("Fuego líquido", "Liquid fire", "Жидкий огонь")
        d("Reloj del tiempo", "Time clock", "Часы времени")
        d("Libro de luz", "Book of light", "Книга света")
        d("Estrellas ninja", "Ninja stars", "Сюрикены")
        d("Rayo celeste", "Sky bolt", "Небесная молния")
        d("Orbes guía", "Guiding orbs", "Самонаводящиеся сферы")
        d("+4 vida", "+4 health", "+4 здоровья")
        d("Botón espada: ataque normal   Botón estrella: arma especial (gasta corazones)", "Sword button: normal attack   Star button: special weapon (uses hearts)", "Кнопка меча: обычная атака   Кнопка звезды: особое оружие (тратит сердца)")
        d("Explora las salas del mapa (pausa = mapa completo). Ataca junto a una escalera para subir o bajar.", "Explore the map's rooms (pause = full map). Attack next to a stairway to go up or down.", "Исследуй комнаты карты (пауза = вся карта). Атакуй у лестницы, чтобы подняться или спуститься.")
        d("Llaves = puertas doradas. Estatuas rojas = curar y guardar. Muros agrietados: rómpelos con el látigo.", "Keys = golden doors. Red statues = heal and save. Cracked walls: break them with the whip.", "Ключи = золотые двери. Красные статуи = лечение и сохранение. Треснувшие стены разбивай кнутом.")
        d("Necesitas una llave", "You need a key", "Нужен ключ")
        d("Progreso guardado", "Progress saved", "Прогресс сохранён")
        d("Puerta abierta", "Door opened", "Дверь открыта")
        d("Puerta con llave", "Locked door", "Запертая дверь")
        d("Toca para empezar", "Tap to start", "Нажми, чтобы начать")
        d("Toca para volver", "Tap to go back", "Нажми, чтобы вернуться")
        d("¡Aturdido!", "Stunned!", "Оглушён!")
        d("¡Bloqueado!", "Blocked!", "Блок!")
        d("¡Botas aéreas: doble salto!", "Air boots: double jump!", "Воздушные сапоги: двойной прыжок!")
        d("¡Despejado!", "Cleared!", "Зачищено!")
        d("¡EMBOSCADA!", "AMBUSH!", "ЗАСАДА!")
        d("¡Invulnerable un rato!", "Invulnerable for a while!", "Неуязвимость на время!")
        d("¡Llave! Abre una puerta dorada", "Key! It opens a golden door", "Ключ! Открывает золотую дверь")
        d("¡Látigo mejorado!", "Whip upgraded!", "Кнут улучшен!")
        d("¡Pasadizo secreto!", "Secret passage!", "Тайный проход!")
        d("¡Vida máxima +2!", "Max health +2!", "Макс. здоровье +2!")
        // ventana de borrar progreso
        d("BORRAR PROGRESO", "RESET PROGRESS", "СБРОС ПРОГРЕССА")
        d("Se borrará para siempre:", "This will be erased forever:", "Будет удалено навсегда:")
        d("Las estrellas de todos los niveles", "The stars of every level", "Звёзды всех уровней")
        d("Los niveles desbloqueados", "Unlocked levels", "Открытые уровни")
        d("El récord del modo infinito", "The endless mode record", "Рекорд бесконечного режима")
        d("Los mundos salvados de la Aventura", "Worlds saved in Adventure", "Спасённые миры в Приключении")
        d("Las partidas guardadas de la Aventura", "Adventure saved games", "Сохранения Приключения")
        d("Las apariencias que se abren con estrellas o récord", "Skins unlocked with stars or records", "Облики, открытые звёздами или рекордом")
        d("«Abrir todos» (desarrollador)", "\"Open all\" (developer)", "«Открыть все» (разработчик)")
        d("Se mantienen tus ajustes, tu nombre y el idioma.", "Your settings, name and language are kept.", "Настройки, имя и язык сохранятся.")
        d("Sí, borrar", "Yes, erase", "Да, удалить")
        d("¿ESTÁS SEGURO?", "ARE YOU SURE?", "ВЫ УВЕРЕНЫ?")
        d("Esto no se puede deshacer.", "This cannot be undone.", "Это нельзя отменить.")
        d("Perderás todo tu progreso.", "You will lose all your progress.", "Вы потеряете весь прогресс.")
        d("Sí, estoy seguro", "Yes, I'm sure", "Да, уверен")
        d("No, volver", "No, go back", "Нет, назад")
        d("PROGRESO BORRADO", "PROGRESS ERASED", "ПРОГРЕСС УДАЛЁН")
        d("Empiezas de cero. ¡Suerte!", "Starting from zero. Good luck!", "Начинаем с нуля. Удачи!")
        d("Actualización", "Update", "Обновление")
        d("Buscando", "Checking", "Проверка")
        d("Estás al día", "Up to date", "Актуально")
        d("Descargar nueva", "Download new", "Скачать новую")
        d("Sin conexión", "No connection", "Нет связи")
        d("Reintentar", "Retry", "Повторить")
        d("Buscar", "Check", "Проверить")
        d("Color Flow  ·  v2", "Color Flow  ·  v2", "Color Flow  ·  v2")
        d("TEMAS", "THEMES", "ТЕМЫ")
        d("Clásico", "Classic", "Классика")
        d("Rojo", "Red", "Красная")
        d("Morado", "Purple", "Фиолетовая")
        d("Rosa", "Pink", "Розовая")
        d("Verde", "Green", "Зелёная")
        d("Halloween", "Halloween", "Хэллоуин")
        d("Gatitos", "Kittens", "Котята")
        d("Perritos", "Puppies", "Щенки")
        d("Elige el color de fondo", "Choose the background color", "Выберите цвет фона")
        d("Tu nombre", "Your name", "Ваше имя")
        d("Nombre", "Name", "Имя")
        d("Aceptar", "OK", "ОК")
        d("Cancelar", "Cancel", "Отмена")
        // --- niveles ---
        d("NIVELES", "LEVELS", "УРОВНИ")
        d("PRÓXIMAMENTE", "COMING SOON", "СКОРО")
        d("Coming soon", "More worlds on the way", "Новые миры уже скоро")
        d("Más niveles en próximas actualizaciones", "More levels in future updates", "Больше уровней в будущих обновлениях")
        p("MUNDO (\\d+)", "WORLD $1", "МИР $1")
        p("Nivel (\\d+)", "Level $1", "Уровень $1")
        d("Prisma", "Prism", "Призма")
        d("Aurora", "Aurora", "Аврора")
        d("Océano", "Ocean", "Океан")
        d("Neón", "Neon", "Неон")
        d("Cristal", "Crystal", "Кристалл")
        d("Atardecer", "Sunset", "Закат")
        d("Galaxia", "Galaxy", "Галактика")
        d("Lava", "Lava", "Лава")
        d("Bosque", "Forest", "Лес")
        d("Hielo", "Ice", "Лёд")
        d("Cósmico", "Cosmic", "Космос")
        d("Espectro", "Spectrum", "Спектр")
        // --- apariencias ---
        d("APARIENCIAS", "SKINS", "СКИНЫ")
        d("La bola conserva su color de juego", "The ball keeps its game color", "Шар сохраняет игровой цвет")
        d("Clásicas", "Classic", "Классические")
        d("Animadas", "Animated", "Анимированные")
        d("Estelas", "Trails", "Следы")
        d("El rastro que deja la bola al subir", "The trail the ball leaves behind", "След, который оставляет шар")
        d("Clásica", "Classic", "Классический")
        d("Chispas", "Sparks", "Искры")
        d("Láser", "Laser", "Лазер")
        d("Doble láser", "Double laser", "Двойной лазер")
        d("Arcoíris", "Rainbow", "Радуга")
        d("Cinta", "Ribbon", "Лента")
        d("Estrellas", "Stars", "Звёзды")
        d("Corazones", "Hearts", "Сердечки")
        d("Burbujas", "Bubbles", "Пузыри")
        d("Diamantes", "Diamonds", "Ромбы")
        d("Fuego", "Fire", "Огонь")
        d("Humo", "Smoke", "Дым")
        d("Relámpago", "Lightning", "Молния")
        d("Píxeles", "Pixels", "Пиксели")
        d("Anillos", "Rings", "Кольца")
        d("Cometa", "Comet", "Комета")
        d("Ondas", "Waves", "Волны")
        d("Hilos de luz", "Light threads", "Нити света")
        d("Confeti", "Confetti", "Конфетти")
        p("(\\d+) estrellas", "$1 stars", "$1 звёзд")
        p("(\\d+) pts infinito", "$1 endless pts", "$1 оч. в беск.")
        p("o (\\d+) mundos", "or $1 worlds", "или миров: $1")
        d("Nieve", "Snow", "Снег")
        d("Hojas", "Leaves", "Листья")
        d("Notas musicales", "Music notes", "Ноты")
        d("Pétalos", "Petals", "Лепестки")
        d("Electricidad", "Electricity", "Электричество")
        d("Tinta", "Ink", "Чернила")
        d("Código", "Code", "Код")
        d("Ascuas", "Embers", "Угли")
        d("Cristales", "Crystals", "Кристаллы")
        d("Espiral", "Spiral", "Спираль")
        d("Polvo de hadas", "Fairy dust", "Волшебная пыль")
        d("Plasma", "Plasma", "Плазма")
        d("Huellas", "Paw prints", "Следы")
        d("Píxel arcoíris", "Rainbow pixels", "Радужные пиксели")
        d("Cadena", "Chain", "Цепь")
        d("Meteoros", "Meteors", "Метеоры")
        d("Sombras", "Shadows", "Тени")
        d("Supernova", "Supernova", "Сверхновая")
        d("Constelación", "Constellation", "Созвездие")
        d("Lluvia de meteoros", "Meteor shower", "Метеорный дождь")
        d("Anillos de Saturno", "Saturn's rings", "Кольца Сатурна")
        d("Polvo estelar", "Stardust", "Звёздная пыль")
        d("Quásar", "Quasar", "Квазар")
        d("Estrella fugaz", "Shooting star", "Падающая звезда")
        d("Fases lunares", "Moon phases", "Фазы Луны")
        d("Vía Láctea", "Milky Way", "Млечный Путь")
        d("Latido", "Heartbeat", "Пульс")
        d("Oleaje", "Waves", "Волны")
        d("Reloj", "Clock", "Часы")
        d("Brújula", "Compass", "Компас")
        d("Engranajes", "Gears", "Шестерёнки")
        d("Mirada", "Gaze", "Взгляд")
        d("Lámpara de lava", "Lava lamp", "Лава-лампа")
        d("Corazón latiendo", "Beating heart", "Бьющееся сердце")
        d("Yin yang", "Yin yang", "Инь-ян")
        d("Átomo", "Atom", "Атом")
        d("Llamarada solar", "Solar flare", "Солнечная вспышка")
        d("Caleidoscopio", "Kaleidoscope", "Калейдоскоп")
        p("o (\\d+) en Aventura", "or $1 in Adventure", "или $1 в Приключении")
        p("Infinito (\\d+)  ·  Mundos (\\d+)/(\\d+)", "Endless $1  ·  Worlds $2/$3", "Беск. $1  ·  Миры $2/$3")
        p("Estrellas (\\d+)/(\\d+)", "Stars $1/$2", "Звёзды $1/$2")
        p("Estrellas (\\d+)/(\\d+)   ·   Récord infinito (\\d+)", "Stars $1/$2   ·   Endless best $3", "Звёзды $1/$2   ·   Рекорд $3")
        val sk = arrayOf(
            "Rayas|Stripes|Полосы", "Lunares|Polka dots|Горошек", "Estrella|Star|Звезда", "Planeta|Planet|Планета",
            "Diamante|Diamond|Алмаз", "Núcleo|Core|Ядро", "Oro|Gold|Золото", "Arcoíris|Rainbow|Радуга",
            "Relámpago|Lightning|Молния", "Corazón|Heart|Сердце", "Ojo|Eye|Глаз", "Cuadros|Checkers|Клетка",
            "Burbujas|Bubbles|Пузыри", "Ondas|Waves|Волны", "Sol|Sun|Солнце", "Pixel|Pixel|Пиксель",
            "Voleibol|Volleyball|Волейбол", "Cápsula|Capsule|Капсула", "Expansión|Expansion|Расширение",
            "Calabaza|Pumpkin|Тыква", "Cine|Cinema|Кино", "Bomba|Bomb|Бомба", "Hongo|Mushroom|Гриб",
            "Pergamino|Scroll|Свиток", "Enfoque|Focus|Фокус", "Flor|Flower|Цветок", "Escudo|Shield|Щит",
            "Trofeo|Trophy|Трофей", "Mariposa|Butterfly|Бабочка", "Alerta|Alert|Тревога", "Corona|Crown|Корона",
            "Equilibrio|Balance|Баланс", "Hoja|Leaf|Лист", "Copo|Snowflake|Снежинка", "Gota|Drop|Капля", "Luna|Moon|Луна",
            "Fuego|Fire|Огонь", "Humo|Smoke|Дым", "Agua|Water|Вода", "Tormenta|Storm|Гроза", "Escarcha|Frost|Иней",
            "Remolino|Whirlpool|Вихрь", "Lluvia|Rain|Дождь", "Luciérnagas|Fireflies|Светлячки", "Brasas|Embers|Угли",
            "Nebulosa|Nebula|Туманность", "Agujero negro|Black hole|Чёрная дыра", "Supernova|Supernova|Сверхновая",
            "Cometa|Comet|Комета", "Sistema solar|Solar system|Солнечная система", "Pulsar|Pulsar|Пульсар",
            "Aurora boreal|Northern lights|Северное сияние", "Espiral|Spiral|Спираль", "Eclipse|Eclipse|Затмение",
            "Plasma|Plasma|Плазма",
            "Tornado|Tornado|Торнадо", "Terremoto|Earthquake|Землетрясение", "Volcán|Volcano|Вулкан",
            "Avalancha|Avalanche|Лавина", "Matriz Digital|Digital Matrix|Цифровая матрица", "Holograma|Hologram|Голограмма",
            "Teseracto|Tesseract|Тессеракт", "Caos Cuántico|Quantum Chaos|Квантовый хаос", "Materia Oscura|Dark Matter|Тёмная материя",
            "Agujero de Gusano|Wormhole|Червоточина", "Ecos del Big Bang|Big Bang Echoes|Эхо Большого взрыва",
            "Bioluminiscencia|Bioluminescence|Биолюминесценция", "ADN Giratorio|Spinning DNA|Вращающаяся ДНК",
            "Hámster|Hamster|Хомяк", "Telaraña Estelar|Starweb|Звёздная паутина", "Runas Antiguas|Ancient Runes|Древние руны",
            "Alquimia|Alchemy|Алхимия", "Gema del Tiempo|Time Gem|Камень времени", "Stickman|Stickman|Стикмен"
        )
        for (s in sk) { val a = s.split("|"); d(a[0], a[1], a[2]) }
        // --- partida ---
        d("Habrá tramos donde deslizas el dedo", "Some sections let you slide your finger", "Будут участки, где нужно водить пальцем")
        d("¡Desliza el dedo para mover la bola!", "Slide your finger to move the ball!", "Водите пальцем, чтобы двигать шар!")
        d("DESLIZA", "SLIDE", "ВЕДИ")
        d("Toca para saltar", "Tap to jump", "Нажмите, чтобы прыгнуть")
        d("Atraviesa solo el color de tu pelota", "Only pass through your ball's color", "Проходите только через цвет шара")
        d("META", "FINISH", "ФИНИШ")
        p("Récord (\\d+)", "Best $1", "Рекорд $1")
        d("PAUSA", "PAUSED", "ПАУЗА")
        d("Continuar", "Resume", "Продолжить")
        d("Reiniciar", "Restart", "Заново")
        d("Música: Sí", "Music: On", "Музыка: Вкл")
        d("Música: No", "Music: Off", "Музыка: Выкл")
        d("Efectos: Sí", "Effects: On", "Эффекты: Вкл")
        d("Efectos: No", "Effects: Off", "Эффекты: Выкл")
        d("Abandonar partida", "Leave match", "Покинуть игру")
        d("Salir al menú", "Back to menu", "В меню")
        d("FIN DEL JUEGO", "GAME OVER", "ИГРА ОКОНЧЕНА")
        d("¡NUEVO RÉCORD!", "NEW BEST!", "НОВЫЙ РЕКОРД!")
        d("Jugar de nuevo", "Play again", "Играть снова")
        d("Menú", "Menu", "Меню")
        d("¡CASI!", "SO CLOSE!", "ПОЧТИ!")
        p("Obstáculos (\\d+) / (\\d+)", "Obstacles $1 / $2", "Препятствия $1 / $2")
        d("Reintentar", "Retry", "Ещё раз")
        d("¡NIVEL COMPLETADO!", "LEVEL COMPLETE!", "УРОВЕНЬ ПРОЙДЕН!")
        d("¡Completaste todos los niveles!", "You finished every level!", "Вы прошли все уровни!")
        d("Siguiente nivel", "Next level", "Следующий уровень")
        d("Repetir", "Replay", "Повторить")
        // --- multijugador ---
        d("MULTIJUGADOR", "MULTIPLAYER", "СЕТЕВАЯ ИГРА")
        d("Dos celulares en el mismo Wi-Fi", "Two phones on the same Wi-Fi", "Два телефона в одной сети Wi-Fi")
        d("Mismo recorrido para los dos:", "Same course for both:", "Одинаковая трасса для обоих:")
        d("gana quien sume más puntos", "most points wins", "побеждает тот, у кого больше очков")
        d("Crear sala", "Create room", "Создать комнату")
        d("Unirse a una sala", "Join a room", "Войти в комнату")
        d("Tip: uno puede crear un punto de acceso", "Tip: one phone can create a Wi-Fi", "Совет: один телефон может создать")
        d("Wi-Fi con el celular y el otro conectarse", "hotspot and the other one connects", "точку доступа, а второй подключится")
        d("TU SALA", "YOUR ROOM", "ВАША КОМНАТА")
        d("Actualiza la app en los dos celulares", "Update the app on both phones", "Обновите игру на обоих телефонах")
        d("Dile a tu amigo que toque \"Unirse\"", "Tell your friend to tap \"Join\"", "Скажите другу нажать «Войти»")
        p("Tu IP: (.*)", "Your IP: $1", "Ваш IP: $1")
        d("(por si hay que escribirla a mano)", "(in case you need to type it)", "(на случай ручного ввода)")
        d("Conéctate a un Wi-Fi para jugar", "Connect to Wi-Fi to play", "Подключитесь к Wi-Fi, чтобы играть")
        p("Rondas: (\\d+)", "Rounds: $1", "Раунды: $1")
        d("¡Empezar!", "Start!", "Начать!")
        d("UNIRSE", "JOIN", "ВХОД")
        p("Conectado con (.*)", "Connected to $1", "Подключено: $1")
        d("Esperando que empiece", "Waiting for the start", "Ожидание начала")
        d("Versiones distintas del juego", "Different game versions", "Разные версии игры")
        d("Actualiza Color Flow en los dos celulares", "Update Color Flow on both phones", "Обновите Color Flow на обоих телефонах")
        d("El anfitrión se fue", "The host left", "Хозяин вышел")
        d("Buscando salas en tu Wi-Fi", "Looking for rooms on your Wi-Fi", "Поиск комнат в вашей Wi-Fi")
        d("Aún no aparece ninguna sala", "No rooms found yet", "Комнат пока нет")
        d("Escribir IP", "Type IP", "Ввести IP")
        d("IP del anfitrión", "Host IP", "IP хозяина")
        d("Ejemplo: 192.168.1.20", "Example: 192.168.1.20", "Пример: 192.168.1.20")
        d("Conectando", "Connecting", "Подключение")
        d("El jugador se fue. La sala sigue abierta.", "The player left. The room is still open.", "Игрок вышел. Комната открыта.")
        d("El anfitrión se fue.", "The host left.", "Хозяин вышел.")
        d("No se pudo crear la sala. Cierra y abre el juego.", "Couldn't create the room. Restart the game.", "Не удалось создать комнату. Перезапустите игру.")
        d("No se pudo buscar salas. Usa \"Escribir IP\".", "Couldn't search for rooms. Use \"Type IP\".", "Не удалось найти комнаты. Нажмите «Ввести IP».")
        d("No se pudo conectar. ¿Están en el mismo Wi-Fi?", "Couldn't connect. Are you on the same Wi-Fi?", "Не удалось подключиться. Вы в одной сети Wi-Fi?")
        d("Error de red", "Network error", "Ошибка сети")
        d("El rival se desconectó", "Your opponent disconnected", "Соперник отключился")
        d("Salir", "Exit", "Выйти")
        p("Prepárate · vs (.*)", "Get ready · vs $1", "Приготовьтесь · против $1")
        d("¡YA!", "GO!", "ВПЕРЁД!")
        d("¡Se acabó el tiempo!", "Time's up!", "Время вышло!")
        d("Eliminado", "Eliminated", "Выбыли")
        d("Esperando al rival", "Waiting for your opponent", "Ожидание соперника")
        d("Rival eliminado · ¡sigue sumando!", "Opponent out · keep scoring!", "Соперник выбыл · набирайте очки!")
        p("RONDA (\\d+) DE (\\d+)", "ROUND $1 OF $2", "РАУНД $1 ИЗ $2")
        p("Ronda (\\d+)/(\\d+)", "Round $1/$2", "Раунд $1/$2")
        p("Ronda (\\d+):   (\\d+)  –  (\\d+)", "Round $1:   $2  –  $3", "Раунд $1:   $2  –  $3")
        p("Siguiente ronda en (\\d+)", "Next round in $1", "Следующий раунд через $1")
        d("Tú", "You", "Вы")
        p("Tú  ·  (.*)", "You  ·  $1", "Вы  ·  $1")
        d("¡GANASTE!", "YOU WIN!", "ПОБЕДА!")
        d("PERDISTE", "YOU LOST", "ПОРАЖЕНИЕ")
        d("EMPATE", "DRAW", "НИЧЬЯ")
        d("El rival salió", "Opponent left", "Соперник вышел")
        d("Esperando al rival…", "Waiting for your opponent…", "Ожидание соперника…")
        d("Revancha", "Rematch", "Реванш")
        p("Total  (\\d+) – (\\d+)", "Total  $1 – $2", "Итого  $1 – $2")
    }

    fun t(s: String): String {
        if (lang == 0 || s.isEmpty()) return s
        if (cacheLang != lang) {
            cache.clear()
            cacheLang = lang
        }
        cache[s]?.let { return it }
        val r = translate(s)
        if (cache.size > 600) cache.clear()
        cache[s] = r
        return r
    }

    private fun pick(a: Array<String>): String = a[lang - 1]

    private fun translate(s: String): String {
        dict[s]?.let { return pick(it) }
        // puntos suspensivos animados al final
        var end = s.length
        while (end > 0 && (s[end - 1] == '.' || s[end - 1] == '…')) end--
        if (end < s.length && end > 0) {
            val base = s.substring(0, end)
            dict[base]?.let { return pick(it) + s.substring(end) }
        }
        for (pt in pats) {
            val m = pt.re.matchEntire(s) ?: continue
            var out = if (lang == 1) pt.en else pt.ru
            for (g in 1 until m.groupValues.size) out = out.replace("$" + g, m.groupValues[g])
            return out
        }
        // "nombre  ·  1.2.3.4" u otros nombres propios: sin cambios
        return s
    }
}
