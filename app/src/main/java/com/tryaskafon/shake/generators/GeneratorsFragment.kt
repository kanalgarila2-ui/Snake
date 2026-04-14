package com.tryaskafon.shake.generators

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.*
import com.tryaskafon.shake.utils.ShakeDetectorHelper
import kotlin.random.Random

// ── Хаб генераторов ──────────────────────────────────────────────────────────
class GeneratorsFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!
    private val tabs = listOf(
        "🎲 Числа"      to { RandomNumberFragment() as Fragment },
        "😂 Оправдания" to { ExcuseFragment() as Fragment },
        "💬 Комплименты"to { ComplimentFragment() as Fragment },
        "🐾 Имена"      to { PetNameFragment() as Fragment },
        "🦊 Факты"      to { AnimalFactFragment() as Fragment },
        "💘 Тиндер"     to { TinderLineFragment() as Fragment }
    )
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTabsBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(pos: Int) = tabs[pos].second()
        }
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos -> tab.text = tabs[pos].first }.attach()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Генератор случайных чисел ─────────────────────────────────────────────────
class RandomNumberFragment : Fragment() {
    private var _b: FragmentRandomNumberBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRandomNumberBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.btnGenerate.setOnClickListener { generate() }
        b.tvRngHint.text = "🎲 Тряска или кнопка — получи число"
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }

    private fun generate() {
        val min = b.etRngMin.text.toString().toLongOrNull() ?: 1L
        val max = b.etRngMax.text.toString().toLongOrNull() ?: 1_000_000L
        if (min >= max) { b.tvBigNumber.text = "Мин < Макс!"; return }
        val num = Random.nextLong(min, max + 1)
        b.tvBigNumber.text = num.toString()
        b.tvBigNumber.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
            .withEndAction { b.tvBigNumber.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
            .start()
        // Интересный факт о числе
        b.tvNumberFact.text = when {
            num % 2 == 0L    -> "$num — чётное"
            isPrime(num)     -> "$num — простое число!"
            num % 7 == 0L    -> "$num делится на 7"
            num % 13 == 0L   -> "$num — чёртова дюжина x${num/13}"
            else             -> "$num — нечётное"
        }
    }

    private fun isPrime(n: Long): Boolean {
        if (n < 2) return false
        if (n == 2L) return true
        if (n % 2 == 0L) return false
        var i = 3L
        while (i * i <= n) { if (n % i == 0L) return false; i += 2 }
        return true
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Генератор оправданий ──────────────────────────────────────────────────────
class ExcuseFragment : Fragment() {
    private var _b: FragmentGeneratorBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val excuses = listOf(
        "Не успел — кот сел на клавиатуру и удалил всё.",
        "Телефон завис именно в тот момент когда я открыл задание.",
        "Я сделал, но файл сохранился в облаке, а облако упало.",
        "Бабушка приехала и пришлось помогать. Три дня.",
        "Открыл YouTube посмотреть одно видео — очнулся в 3 ночи.",
        "Wi-Fi сломался. Починил — уже поздно было.",
        "Хотел сделать, но не нашёл ручку. Без ручки — никак.",
        "Меня укусила собака на пути домой. Пришлось в аптеку.",
        "Забыл что задали. Честно.",
        "Сделал всё, но перепутал ветки в git и откатил изменения.",
        "Телефон разрядился, а я как раз читал условие.",
        "Думал что срок в пятницу. Сегодня оказалось пятница.",
        "Делал, но пришёл кот и лёг прямо на тетрадь.",
        "Интернет был только ночью, а ночью я сплю.",
        "Хотел спросить в чате, но постеснялся. Вот так и не сделал."
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGeneratorBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.tvGeneratorTitle.text = "😅 Оправдание"
        b.tvGeneratorHint.text = "Почему не сделал уроки / задачу / дз"
        b.btnGenerate.text = "Ещё оправдание"
        b.btnGenerate.setOnClickListener { generate() }
        b.btnCopyGenerated.setOnClickListener { copy() }
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    private fun generate() { b.tvGeneratedText.text = excuses.random() }
    private fun copy() {
        val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("excuse", b.tvGeneratedText.text))
        Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Генератор комплиментов/оскорблений ────────────────────────────────────────
class ComplimentFragment : Fragment() {
    private var _b: FragmentComplimentBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper
    private var mode = "compliment"

    private val compliments = listOf(
        "Ты светишь ярче любого экрана на максимальной яркости ☀",
        "С тобой даже Monday ощущается как Friday 🎉",
        "Ты — тот человек, ради которого стоит зарядить телефон до 100%",
        "Твой мозг работает быстрее чем анскипабельная реклама ⚡",
        "Рядом с тобой даже баги в коде кажутся фичами",
        "Ты заставляешь весь мир выглядеть как тема Light Mode ✨",
        "Даже ChatGPT не смог бы придумать человека лучше тебя",
        "Ты — редкий экземпляр без единого memory leak в характере"
    )
    private val insults = listOf(
        "Ты такой медленный, что твой ping — это просто time-out",
        "Твой IQ — это хорошее значение влажности, а не IQ",
        "Ты — как JPEG картинка: много артефактов, мало смысла",
        "Обновления выходят чаще, чем ты меняешь своё мнение",
        "Ты как Bluetooth: вроде есть, но толку мало",
        "404: Логика не найдена",
        "Ты думаешь что ты RAM, но на деле — дискета 3.5"
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentComplimentBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.btnCompliment.setOnClickListener { mode = "compliment"; generate() }
        b.btnInsult.setOnClickListener     { mode = "insult";     generate() }
        b.btnCopyCompliment.setOnClickListener {
            val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("gen", b.tvComplimentText.text))
            Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
        }
        b.tvComplimentHint.text = "🤝 Тряска = случайный комплимент или оскорбление (в зависимости от последней кнопки)"
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    private fun generate() {
        b.tvComplimentText.text = if (mode == "compliment") compliments.random() else insults.random()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Генератор имён для питомцев ────────────────────────────────────────────────
class PetNameFragment : Fragment() {
    private var _b: FragmentGeneratorBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val prefixes = listOf("Барон","Граф","Лорд","Сэр","Принц","Герцог","Капитан","Доктор","Профессор","Агент","Мистер","Леди","Маркиза")
    private val names    = listOf("Пушок","Мурзик","Бобик","Флаффи","Снежок","Бублик","Пончик","Котлета","Сосиска","Огурчик","Тортик","Вафля","Шаурма","Пицца","Борщик")
    private val suffixes = listOf("Великий","Пушистый","Хвостатый","Пятнистый","Ленивый","Храбрый","Сонный","Голодный","III","Junior","Pro Max","Ultra")

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGeneratorBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.tvGeneratorTitle.text = "🐾 Имя для питомца"
        b.tvGeneratorHint.text = "Полное официальное имя твоего животного"
        b.btnGenerate.text = "Сгенерировать"
        b.btnGenerate.setOnClickListener { generate() }
        b.btnCopyGenerated.setOnClickListener {
            val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("name", b.tvGeneratedText.text))
            Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
        }
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    private fun generate() {
        b.tvGeneratedText.text = "${prefixes.random()} ${names.random()} ${suffixes.random()}"
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Факты о животных ─────────────────────────────────────────────────────────
class AnimalFactFragment : Fragment() {
    private var _b: FragmentGeneratorBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val facts = listOf(
        "🦒 Жирафы не умеют кашлять. Зато могут чистить уши своим языком длиной 45 см.",
        "🐙 У осьминога три сердца. Два качают кровь к жабрам, одно — к телу.",
        "🐻 Медведи бегают со скоростью до 55 км/ч. Ты от него не убежишь.",
        "🦈 Акулы старше деревьев. Они появились 400 млн лет назад, деревья — 350 млн.",
        "🐘 Слоны — единственные животные, которые не могут прыгать.",
        "🦩 Фламинго розовые из-за еды. Без каротина они были бы белыми.",
        "🐌 Улитка может спать три года подряд.",
        "🦜 Попугаи-какапо — самые толстые и единственные нелетающие попугаи.",
        "🐝 Пчела машет крыльями 200 раз в секунду — отсюда жужжание.",
        "🦔 Ежи иногда смазывают иголки ядовитой слюной. Никто не знает зачем.",
        "🐊 Крокодилы не могут высунуть язык. Он приклеен к нижней челюсти.",
        "🦋 Бабочки пробуют вкус лапками. Вкусовые рецепторы на ногах.",
        "🐠 Рыбы-клоуны все рождаются самцами. При необходимости старший меняет пол.",
        "🦭 Тюлени спят в воде, периодически всплывая чтобы вдохнуть.",
        "🐺 Волки могут пробегать 80 км в день за добычей."
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGeneratorBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.tvGeneratorTitle.text = "🦊 Факт о животном"
        b.tvGeneratorHint.text = "Тряхни чтобы узнать новый факт"
        b.btnGenerate.text = "Следующий факт"
        b.btnGenerate.setOnClickListener { generate() }
        b.btnCopyGenerated.setOnClickListener {
            val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("fact", b.tvGeneratedText.text))
            Toast.makeText(requireContext(), "Скопировано!", Toast.LENGTH_SHORT).show()
        }
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    private fun generate() { b.tvGeneratedText.text = facts.random() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ── Генератор фраз для Тиндера ────────────────────────────────────────────────
class TinderLineFragment : Fragment() {
    private var _b: FragmentGeneratorBinding? = null
    private val b get() = _b!!
    private lateinit var shakeHelper: ShakeDetectorHelper

    private val lines = listOf(
        "Если б я был акселерометром — я бы тебя постоянно трясло.",
        "Ты так красива, что даже мой GPS теряет сигнал рядом с тобой.",
        "Мой телефон на 1% — как моя жизнь без тебя.",
        "Ты — единственный баг который я не хочу фиксить.",
        "Если красота — это 5G, то ты — спутниковый интернет.",
        "У меня нет Wi-Fi, но я чувствую соединение.",
        "Ты заряжаешь меня лучше чем быстрая зарядка 65W.",
        "Я не хакер, но взломал твоё сердце? 👀",
        "Ты — единственный человек ради кого я ставлю телефон на беззвучный.",
        "Без тебя я как Android без Google Play — вроде работаю, но чего-то не хватает."
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGeneratorBinding.inflate(i, c, false); return b.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shakeHelper = ShakeDetectorHelper(requireContext()) { generate() }
        b.tvGeneratorTitle.text = "💘 Тиндер-фраза"
        b.tvGeneratorHint.text = "Лучший способ начать разговор"
        b.btnGenerate.text = "Ещё фраза"
        b.btnGenerate.setOnClickListener { generate() }
        b.btnCopyGenerated.setOnClickListener {
            val cb = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("tinder", b.tvGeneratedText.text))
            Toast.makeText(requireContext(), "Скопировано! Успехов 😏", Toast.LENGTH_SHORT).show()
        }
        generate()
    }
    override fun onResume() { super.onResume(); shakeHelper.start() }
    override fun onPause()  { super.onPause();  shakeHelper.stop() }
    private fun generate() { b.tvGeneratedText.text = lines.random() }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
