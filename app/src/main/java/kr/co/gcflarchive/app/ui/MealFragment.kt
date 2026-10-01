package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.FragmentMealBinding
import kr.co.gcflarchive.app.databinding.ItemMealCardBinding
import kr.co.gcflarchive.app.meal.Meal
import kr.co.gcflarchive.app.meal.MealRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class MealFragment : Fragment(), MainActivity.Reselectable {
    private var _binding: FragmentMealBinding? = null
    private val binding get() = _binding!!
    private var date: LocalDate = MealRepository.today()
    private var loadJob: Job? = null

    private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMealBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(STATE_DATE)?.let { date = LocalDate.parse(it) }
        binding.btnPrev.setOnClickListener { setDate(date.minusDays(1)) }
        binding.btnNext.setOnClickListener { setDate(date.plusDays(1)) }
        binding.btnToday.setOnClickListener { setDate(MealRepository.today()) }
        binding.dateLabel.setOnClickListener { pickDate() }
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
        date = newDate
        load()
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
        b.dateLabel.text = date.format(dateFormat)
        b.todayBadge.isVisible = date == MealRepository.today()
        b.btnToday.isVisible = !b.todayBadge.isVisible
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

    private companion object {
        const val STATE_DATE = "date"
    }
}
