package com.google.android.stardroid.activities.dialogs

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.getNextFullMoon
import com.google.android.stardroid.math.normalizeHours
import com.google.android.stardroid.space.CelestialObject
import com.google.android.stardroid.space.Universe
import com.google.android.stardroid.util.MiscUtil
import org.cosmosmataro.skymap.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Implementation of the time travel dialog.
 *
 * @author Dominic Widdows
 * @author John Taylor
 */
class TimeTravelDialog(
    private val parentActivity: DynamicStarMapActivity,
    private val model: AstronomerModel
) : Dialog(parentActivity) {
    private lateinit var popularDatesMenu: Spinner
    private lateinit var dateTimeReadout: TextView
    private val dateFormat = SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z", Locale.US)

    // This is the date we will apply to the controller when the user hits go.
    private val calendar: Calendar = Calendar.getInstance()
    private var lastClickTime: Long = 0
    private val universe = Universe()

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.time_dialog)
        // Assumes that the dialog's title should be the same as the menu option.
        setTitle(R.string.menu_time)
        // Capture our View elements
        dateTimeReadout = findViewById<View>(R.id.dateDisplay) as TextView
        // Capture and wire up the buttons
        val changeDateButton = findViewById<View>(R.id.pickDate) as Button
        changeDateButton.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClickTime >= MIN_CLICK_TIME) {
                lastClickTime = SystemClock.elapsedRealtime()
                createDatePicker().show()
            }
        }

        val changeTimeButton = findViewById<View>(R.id.pickTime) as Button
        changeTimeButton.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClickTime >= MIN_CLICK_TIME) {
                lastClickTime = SystemClock.elapsedRealtime()
                createTimePicker().show()
            }
        }

        val goButton = findViewById<View>(R.id.timeTravelGo) as Button
        goButton.setOnClickListener {
            parentActivity.setTimeTravelMode(calendar.time)
            dismiss()
        }

        val cancelButton = findViewById<View>(R.id.timeTravelCancel) as Button
        cancelButton.setOnClickListener { dismiss() }

        popularDatesMenu = findViewById<View>(R.id.popular_dates_spinner) as Spinner
        val adapter = ArrayAdapter.createFromResource(
            this.context, R.array.popular_date_examples, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        popularDatesMenu.adapter = adapter
        popularDatesMenu.setSelection(1)
        popularDatesMenu.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // The callback received when the user selects a menu item.
            override fun onItemSelected(arg0: AdapterView<*>, arg1: View?, arg2: Int, arg3: Long) {
                setPopularDate(popularDatesMenu.selectedItemPosition)
            }

            override fun onNothingSelected(arg0: AdapterView<*>) {
                // Do nothing in this case.
            }
        }
        // Start by initializing ourselves to 'now'.  Note that this is the value
        // the first time the dialog is shown.  Thereafter it will remember the
        // last value set.
        calendar.time = Date()
        updateDisplay()
    }

    private fun createTimePicker(): Dialog {
        val timeSetListener =
            TimePickerDialog.OnTimeSetListener { view, hour, minute ->
                setTime(hour, minute)
                Log.d(TAG, "Setting time to: $hour:$minute")
            }
        return TimePickerDialog(
            context,
            timeSetListener,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
    }

    private fun createDatePicker(): Dialog {
        val dateSetListener =
            DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                setDate(year, monthOfYear, dayOfMonth)
                Log.d(TAG, "Setting date to: $year-$monthOfYear-$dayOfMonth")
            }
        return DatePickerDialog(
            context,
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Sets the internal calendar of this dialog.  Remember that months are zero
     * based.  Current time is preserved.
     */
    private fun setDate(year: Int, month: Int, day: Int) {
        calendar.set(year, month, day)
        updateDisplay()
    }

    /**
     * Sets the internal calendar of this dialog.  Current date is preserved.
     */
    private fun setTime(hour: Int, minute: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        updateDisplay()
    }

    /**
     * Sets the internal calendar of this dialog to the given date.
     */
    private fun setDate(date: Date) {
        calendar.time = date
    }

    private fun updateDisplay() {
        val date = calendar.time
        dateTimeReadout.text = parentActivity.getString(
            R.string.now_visiting,
            dateFormat.format(date)
        )
    }

    private fun setToNextSunRiseOrSet(indicator: CelestialObject.RiseSetIndicator) {
        val riseset = universe.solarSystemObjectFor(SolarSystemBody.Sun).calcNextRiseSetTime(
            calendar, model.location, indicator
        )
        if (riseset == null) {
            Toast.makeText(this.context, R.string.sun_wont_set_message, Toast.LENGTH_SHORT).show()
        } else {
            Log.d(
                TAG, "Sun rise or set is at: " + normalizeHours(
                    riseset.get(Calendar.HOUR_OF_DAY).toDouble()
                ) + ":" + riseset.get(Calendar.MINUTE)
            )
            setDate(riseset.time)
            updateDisplay()
        }
    }

    /**
     * Associates time settings with the options in the popular dates menu.
     * It HAS to be kept in sync with res/values/arrays.xml.
     *
     * @param popularDateIndex The index into the popular dates array.
     */
    private fun setPopularDate(popularDateIndex: Int) {
        val s = popularDatesMenu.selectedItem as String
        Log.d(TAG, "Popular date " + popularDatesMenu.selectedItemPosition + "  " + s)
        val c = Calendar.getInstance()
        c.time = model.time
        when (popularDateIndex) {
            0 -> calendar.time = Date()
            1 -> setToNextSunRiseOrSet(CelestialObject.RiseSetIndicator.SET)
            2 -> setToNextSunRiseOrSet(CelestialObject.RiseSetIndicator.RISE)
            3 -> {
                val nextFullMoon = getNextFullMoon(calendar.time)
                setDate(nextFullMoon)
                updateDisplay()
            }
            4 ->                 // Mercury transit 2016.
                // Source: http://eclipsewise.com/oh/tm2016.html
                // http://mainfacts.com/timestamp-date-converter-calculator
                setDate(Date(1462805846000L))
            5 ->                 // Solar Eclipse 2024 North America.
                // Source: http://mainfacts.com/timestamp-date-converter-calculator
                setDate(Date(1712604000000L))
            6 -> setDate(Date(-14182953622L))
            7 -> setDate(Date(1608574800000L))
            8 -> setDate(Date(1786556700000L)) // Eclipse 2026 Spain
            else -> Log.d(TAG, "Incorrect popular date index!")
        }
        updateDisplay()
    }

    companion object {
        private val TAG = MiscUtil.getTag(TimeTravelDialog::class.java)
        private const val MIN_CLICK_TIME = 1000
    }
}
