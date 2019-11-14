package com.stgi.kotebook

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import kotlinx.android.synthetic.main.fab_audio.view.*
import kotlin.math.abs
import kotlin.math.min


class AudioCallback(
    val adapter: ItemTouchHelperAdapter,
    val dragEnabled: Boolean,
    val swipeEnabled: Boolean = false
) : ItemTouchHelper.Callback() {

    var swipeBack: Boolean = false
    var dragFrom: Int = -1
    var dragTo: Int = -1

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.END
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val toPosition = target.adapterPosition
        if (dragFrom == -1) {
            dragFrom = viewHolder.adapterPosition
        }
        dragTo = toPosition

        adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            adapter.onItemMoveEnded(dragFrom, dragTo)
            dragFrom = -1
            dragTo = -1
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ACTION_STATE_SWIPE)
            setTouchListener(recyclerView, viewHolder)
        val thresholdX = getSwipeThreshold(viewHolder)
        val actualX = if (dX > thresholdX) thresholdX else dX
        super.onChildDraw(c, recyclerView, viewHolder, actualX, dY, actionState, isCurrentlyActive)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {

        viewHolder.itemView.setOnTouchListener(object : View.OnTouchListener {
            var startX: Float = -1f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                //if (startX > 0)
                if (event != null) {
                    val alpha = min(event.x.toInt() * 255 / getSwipeThreshold(viewHolder).toInt(), 255)
                    viewHolder.itemView.circlet.background.setColorFilter(
                        Color.argb(alpha, 255, 0, 0), PorterDuff.Mode.DARKEN
                    )
                }
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> startX = event.x
                    MotionEvent.ACTION_UP -> {
                        viewHolder.itemView.circlet.background.clearColorFilter()
                        if (abs(startX - event.x) > getSwipeThreshold(viewHolder)) {
                            swipeBack =
                                event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
                            val direction =
                                if (startX - event.x > 0) View.FOCUS_RIGHT else View.FOCUS_LEFT
                            adapter.onItemSwiped(viewHolder.adapterPosition, direction)
                        }
                    }
                }
                return false
            }
        })


    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        val res = viewHolder.itemView.resources
        return res.displayMetrics.widthPixels - (res.getDimension(R.dimen.fab_size) + res.getDimension(R.dimen.fab_margin) * 2)
    }

    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = false
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.onItemSwiped(viewHolder.adapterPosition, direction)
    }

    override fun isLongPressDragEnabled(): Boolean = dragEnabled

    override fun isItemViewSwipeEnabled(): Boolean = swipeEnabled && adapter.isItemViewSwipeEnabled()
}

/**
 * Interface to listen for a move or dismissal event from a {@link ItemTouchHelper.Callback}.
 *
 * @author Paul Burke (ipaulpro)
 */
interface ItemTouchHelperAdapter {

    /**
     * Called when an item has been dragged far enough to trigger a move. This is called every time
     * an item is shifted, and <strong>not</strong> at the end of a "drop" event.<br/>
     * <br/>
     * Implementations should call {@link RecyclerView.Adapter#notifyItemMoved(int, int)} after
     * adjusting the underlying data to reflect this move.
     *
     * @param fromPosition The start position of the moved item.
     * @param toPosition   Then resolved position of the moved item.
     *
     * @see RecyclerView#getAdapterPositionFor(RecyclerView.ViewHolder)
     * @see RecyclerView.ViewHolder#getAdapterPosition()
     */
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onItemMoveEnded(fromPosition: Int, toPosition: Int)

    /**
     * Called when an item has been dismissed by a swipe.<br/>
     * <br/>
     * Implementations should call {@link RecyclerView.Adapter#notifyItemRemoved(int)} after
     * adjusting the underlying data to reflect this removal.
     *
     * @param position The position of the item dismissed.
     *
     * @see RecyclerView#getAdapterPositionFor(RecyclerView.ViewHolder)
     * @see RecyclerView.ViewHolder#getAdapterPosition()
     */
    fun onItemSwiped(position: Int, direction: Int)

    open fun isItemViewSwipeEnabled() = true
}