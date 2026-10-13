package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.FragmentMealBinding
import kr.co.gcflarchive.app.databinding.ItemMealCardBinding
import kr.co.gcflarchive.app.databinding.ItemMealDayBinding
import kr.co.gcflarchive.app.databinding.ItemMealWeekBinding
import kr.co.gcflarchive.app.meal.MealWeek
import kr.co.gcflarchive.app.meal.Meal
import kr.co.gcflarchive.app.meal.MealRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class MealFragment : Fragment(), MainActivity.Reselectable {
    private var _binding: FragmentMealBinding? = null
    private val binding get() = _binding!!
    private var date: LocalDate = MealWeek.schoolDay(MealRepository.today())
    private var loadJob: Job? = null

    /** Week 0 of the strip (page [CENTER]); pages are whole 월~금 weeks around it. */
    private val baseMonday: LocalDate = MealWeek.monday(MealWeek.schoolDay(MealRepository.today()))
    private val weekAdapter = WeekAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMealBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(STATE_DATE)?.let { date = MealWeek.schoolDay(LocalDate.parse(it)) }
        binding.weekPager.adapter = weekAdapter
        binding.weekPager.offscreenPageLimit = 1
        binding.weekPager.setCurrentItem(pageOf(date), false)
        // Swiping the strip moves a whole week and keeps the same weekday selected.
        binding.weekPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val target = mondayOf(position).plusDays((date.dayOfWeek.value - 1).toLong())
                if (target != date) {
                    date = target
                    refreshDays()
                    load()
                }
            }
        })
        binding.btnToday.setOnClickListener { setDate(MealRepository.today()) }
        binding.weekLabel.setOnClickListener { pickDate() }
        binding.swipe.setOnRefreshListener { load() }
        binding.allergyGuide.text = getString(R.string.allergy_guide)
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DATE, date.toString())
    }

    override fun onReselected() = setDate(MealRepository.today())

    private fun setDate(newDate: LocalDate) {
        date = MealWeek.schoolDay(newDate)
        val page = pageOf(date)
        val current = binding.weekPager.currentItem
        if (current != page) binding.weekPager.setCurrentItem(page, abs(current - page) <= 3)
        refreshDays()
        load()
    }

    private fun pageOf(d: LocalDate): Int = (CENTER + MealWeek.weekIndex(baseMonday, d)).toInt().coerceIn(0, PAGES - 1)

    private fun mondayOf(page: Int): LocalDate = baseMonday.plusWeeks((page - CENTER).toLong())

    /** Re-draws the visible week pages so the highlight follows [date]. */
    private fun refreshDays() {
        val page = binding.weekPager.currentItem
        weekAdapter.notifyItemRangeChanged((page - 1).coerceAtLeast(0), 3)
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(childFragmentManager, "date")
    }

    private fun load() {
        val b = binding
        b.weekLabel.text = MealWeek.label(MealWeek.monday(date), MealRepository.today())
        // INVISIBLE (not GONE) so the header doesn't jump when the button appears.
        b.btnToday.visibility = if (date == MealWeek.schoolDay(MealRepository.today())) View.INVISIBLE else View.VISIBLE
        b.mealContainer.removeAllViews()
        showState(getString(R.string.meal_loading), null)
        b.swipe.isRefreshing = true

        loadJob?.cancel()
        val requested = date
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { MealRepository.fetch(requested) }
            val vb = _binding ?: return@launch
            vb.swipe.isRefreshing = false
            result.onSuccess { meals ->
                if (meals.isEmpty()) {
                    showState(getString(R.string.meal_none), getString(R.string.meal_none_desc))
                } else {
                    vb.stateGroup.isVisible = false
                    meals.forEach { addMealCard(it) }
                }
            }.onFailure {
                showState(getString(R.string.meal_load_failed), getString(R.string.meal_load_failed_desc))
            }
        }
    }

    private fun showState(title: String, desc: String?) {
        binding.stateGroup.isVisible = true
        binding.stateTitle.text = title
        binding.stateDesc.isVisible = desc != null
        binding.stateDesc.text = desc
    }

    private fun addMealCard(meal: Meal) {
        val card = ItemMealCardBinding.inflate(layoutInflater, binding.mealContainer, false)
        card.title.text = meal.kind
        card.calories.text = meal.calories ?: "-"
        card.footer.text = Config.SCHOOL_NAME
        for (dish in meal.dishes) {
            val row = layoutInflater.inflate(R.layout.item_dish, card.dishes, false)
            row.findViewById<TextView>(R.id.dishName).text = dish.name
            row.findViewById<TextView>(R.id.dishAllergy).apply {
                isVisible = dish.allergens != null
                text = dish.allergens?.let { "($it)" }
            }
            card.dishes.addView(row)
        }
        binding.mealContainer.addView(card.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** One page per week; each page shows 월~금 with the selected day filled in. */
    private inner class WeekAdapter : RecyclerView.Adapter<WeekHolder>() {
        override fun getItemCount() = PAGES

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekHolder {
            val week = ItemMealWeekBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val days = List(MealWeek.DAYS) { ItemMealDayBinding.inflate(LayoutInflater.from(parent.context), week.days, true) }
            return WeekHolder(week, days)
        }

        override fun onBindViewHolder(holder: WeekHolder, position: Int) {
            val ctx = holder.itemView.context
            val today = MealRepository.today()
            MealWeek.days(mondayOf(position)).forEachIndexed { i, day ->
                val cell = holder.days[i]
                val selected = day == date
                val isToday = day == today
                cell.weekday.text = WEEKDAYS[i]
                cell.dayNumber.text = day.dayOfMonth.toString()
                cell.root.setBackgroundResource(if (selected) R.drawable.bg_meal_day_selected else R.drawable.bg_meal_day)
                val onSel = ctx.getColor(R.color.krds_on_primary)
                cell.weekday.setTextColor(if (selected) onSel else ctx.getColor(R.color.krds_text_secondary))
                cell.dayNumber.setTextColor(
                    when {
                        selected -> onSel
                        isToday -> ctx.getColor(R.color.krds_primary_text)
                        else -> ctx.getColor(R.color.krds_text_strong)
                    },
                )
                // Today: thin outlined circle (white on the selected fill).
                if (isToday) {
                    cell.dayNumber.setBackgroundResource(if (selected) R.drawable.bg_meal_today_ring_selected else R.drawable.bg_meal_today_ring)
                } else {
                    cell.dayNumber.background = null
                }
                cell.root.isSelected = selected
                cell.root.contentDescription = "${day.monthValue}월 ${day.dayOfMonth}일 ${WEEKDAYS[i]}요일" +
                    (if (isToday) ", 오늘" else "") + (if (selected) ", 선택됨" else "")
                cell.root.setOnClickListener { if (day != date) setDate(day) }
            }
        }
    }

    private class WeekHolder(binding: ItemMealWeekBinding, val days: List<ItemMealDayBinding>) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val STATE_DATE = "date"
        /** ±10 years of weeks around today. */
        const val CENTER = 520
        const val PAGES = CENTER * 2 + 1
        val WEEKDAYS = listOf("월", "화", "수", "목", "금")
    }
}
