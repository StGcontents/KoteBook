package com.stgi.kotebook

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

class DatePicker(context: Context, attributeSet: AttributeSet?): LinearLayout(context, attributeSet) {
    private val yearPicker: RecyclerView
    private val monthPicker: RecyclerView
    private val dayPicker: RecyclerView

    init {
        orientation = VERTICAL

        yearPicker = RecyclerView(context)
        val yearParams = LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimension(R.dimen.no_dimen).toInt())
        yearParams.weight = 1f
        yearPicker.layoutParams = yearParams
        yearPicker.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        monthPicker = RecyclerView(context)
        val monthParams = LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimension(R.dimen.no_dimen).toInt())
        monthParams.weight = 2f
        monthPicker.layoutParams = monthParams
        monthPicker.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        dayPicker = RecyclerView(context)
        val dayParams = LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimension(R.dimen.no_dimen).toInt())
        dayParams.weight = 4f
        dayPicker.layoutParams = dayParams
        dayPicker.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        addView(yearPicker)
        addView(monthPicker)
        addView(dayPicker)

        val dayAdapter = DayAdapter()
        val monthAdapter = MonthAdapter(dayAdapter)
        val yearAdapter = YearAdapter(monthAdapter)

        yearPicker.adapter = yearAdapter
        monthPicker.adapter = monthAdapter
        dayPicker.adapter = dayAdapter
    }

    abstract class DatePickerAdapter<T, S, R>: RecyclerView.Adapter<DateViewHolder>() {

        protected var selectedPosition: Int = 0
        protected var data: List<T> = ArrayList()
            set(value) {
                field = value
                if (selectedPosition > field.size) {
                    selectedPosition = max(0, field.size - 1)
                }
                notifyDataSetChanged()
                onItemSelected(selectedPosition)
            }
        private var intervalListener: DatePickerAdapter<S, R, Any>? = null

        private val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        onItemSelected((recyclerView.layoutManager as LinearLayoutManager)
                            .findFirstCompletelyVisibleItemPosition())
                    }
                    RecyclerView.SCROLL_STATE_SETTLING -> {}
                    RecyclerView.SCROLL_STATE_DRAGGING -> {}
                }
            }
        }

        fun setListener(l: DatePickerAdapter<S, R, Any>?) {
            intervalListener = l
        }

        fun getBase(): T = data[0]
        abstract fun getSubList(): List<S>
        abstract fun getSubMax(): T

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
            val view = TextView(parent.context)
            val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            view.layoutParams = params
            view.gravity = Gravity.CENTER
            return DateViewHolder(view)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
            holder.bind(data[position] as Any)
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerView.addOnScrollListener(scrollListener)

        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            recyclerView.removeOnScrollListener(scrollListener)
        }

        fun onItemSelected(position: Int) {
            if (position != selectedPosition) {
                selectedPosition = position
                intervalListener?.data = getSubList()
            }
        }
    }

    class YearAdapter(listener: MonthAdapter?): DatePickerAdapter<Int, MONTH, Int>() {
        init {
            setListener(listener)
            data = (getBase()..(getBase() + 10)).toList()
        }

        override fun getSubList(): List<MONTH> {
            val list = ArrayList<MONTH>()
            val startingMonth: Int
            if (selectedPosition == 0) {
                startingMonth = Calendar.getInstance().apply {
                    timeInMillis = Date().time
                }.get(Calendar.MONTH)
            } else startingMonth = 0

            for (i in startingMonth..11) {
                if (i == 2 && data[selectedPosition] % 4 == 0)
                    list.add(MONTH.values()[12])
                else list.add(MONTH.values()[i])
            }

            return list
        }

        override fun getSubMax(): Int = 11
    }

    class MonthAdapter(listener: DayAdapter?): DatePickerAdapter<MONTH, Int, Any>() {
        init {
            setListener(listener)
        }
        override fun getSubList(): List<Int> = (1..data[selectedPosition].days).toList()
        override fun getSubMax(): MONTH = MONTH.DECEMBER
    }

    class DayAdapter: DatePickerAdapter<Int, Any, Any>() {
        override fun getSubList(): List<Any> = ArrayList()
        override fun getSubMax(): Int = data.last()
    }

    class DateViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind(element: Any) {
            itemView as TextView
            itemView.text = element.toString()
        }
    }

    enum class MONTH(val days: Int) {
        JANUARY(31) {
            override fun toString(): String {
                return "JANUARY"
            }
        },
        FEBRUARY(28) {
            override fun toString(): String {
                return "FEBRUARY"
            }
        },
        MARCH(31) {
            override fun toString(): String {
                return "MARCH"
            }
        },
        APRIL(30) {
            override fun toString(): String {
                return "APRIL"
            }
        },
        MAY(31) {
            override fun toString(): String {
                return "MAY"
            }
        },
        JUNE(30) {
            override fun toString(): String {
                return "JUNE"
            }
        },
        JULY(31) {
            override fun toString(): String {
                return "JULY"
            }
        },
        AUGUST(31) {
            override fun toString(): String {
                return "AUGUST"
            }
        },
        SEPTEMBER(30) {
            override fun toString(): String {
                return "SEPTEMBER"
            }
        },
        OCTOBER(31) {
            override fun toString(): String {
                return "OCTOBER"
            }
        },
        NOVEMBER(3) {
            override fun toString(): String {
                return "NOVEMBER"
            }
        },
        DECEMBER(31) {
            override fun toString(): String {
                return "DECEMBER"
            }
        },
        BISSEXTILE_FEB(29) {
            override fun toString(): String {
                return "FEBRUARY"
            }
        }
    }
}