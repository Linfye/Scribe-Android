package be.scri.extensions

import android.content.Context
import android.content.res.Configuration
import android.database.CursorIndexOutOfBoundsException
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Toast
import androidx.loader.content.CursorLoader
import be.scri.R
import be.scri.helpers.DARK_GREY
import be.scri.helpers.INVALID_NAVIGATION_BAR_COLOR
import be.scri.helpers.MyContentProvider
import be.scri.helpers.ensureBackgroundThread
import be.scri.models.SharedTheme
import be.scri.views.MyAppCompatCheckbox
import be.scri.views.MyEditText
import be.scri.views.MyFloatingActionButton
import be.scri.views.MyTextView

// handle system default theme (Material You) specially as the color is taken from the system, not hardcoded by us
fun Context.getProperTextColor() =
    if (baseConfig.isUsingSystemTheme) {
        resources.getColor(R.color.you_neutral_text_color, theme)
    } else {
        baseConfig.textColor
    }

fun Context.getProperKeyColor() =
    if (baseConfig.isUsingSystemTheme) {
        resources.getColor(R.color.you_neutral_text_color, theme)
    } else {
        baseConfig.keyColor
    }

fun Context.getProperBackgroundColor() =
    if (baseConfig.isUsingSystemTheme) {
        resources.getColor(R.color.you_background_color, theme)
    } else {
        baseConfig.backgroundColor
    }

fun Context.getProperPrimaryColor() =
    when {
        baseConfig.isUsingSystemTheme -> resources.getColor(R.color.you_primary_color, theme)
        isWhiteTheme() || isBlackAndWhiteTheme() -> baseConfig.accentColor
        else -> baseConfig.primaryColor
    }

fun Context.getProperStatusBarColor() =
    when {
        baseConfig.isUsingSystemTheme -> resources.getColor(R.color.you_primary_color, theme)
        else -> baseConfig.primaryColor
    }

fun Context.updateTextColors(viewGroup: ViewGroup) {
    val textColor =
        when {
            baseConfig.isUsingSystemTheme -> getProperTextColor()
            else -> baseConfig.textColor
        }

    val accentColor =
        when {
            isWhiteTheme() || isBlackAndWhiteTheme() -> baseConfig.accentColor
            else -> getProperPrimaryColor()
        }

    for (i in 0 until viewGroup.childCount) {
        when (val view = viewGroup.getChildAt(i)) {
            is MyTextView -> view.setColors(textColor, accentColor)
            is MyAppCompatCheckbox -> view.setColors(textColor, accentColor)
            is MyEditText -> view.setColors(textColor, accentColor)
            is MyFloatingActionButton -> view.setColors(textColor)
            is ViewGroup -> updateTextColors(view)
        }
    }
}

fun Context.getLinkTextColor(): Int =
    if (baseConfig.primaryColor == resources.getColor(R.color.color_primary)) {
        baseConfig.primaryColor
    } else {
        baseConfig.textColor
    }

fun Context.isBlackAndWhiteTheme() = baseConfig.textColor == Color.WHITE && baseConfig.primaryColor == Color.BLACK && baseConfig.backgroundColor == Color.BLACK

fun Context.isWhiteTheme() = baseConfig.textColor == DARK_GREY && baseConfig.primaryColor == Color.WHITE && baseConfig.backgroundColor == Color.WHITE

fun Context.isUsingSystemDarkTheme() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES != 0

fun Context.getTimePickerDialogTheme() =
    when {
        baseConfig.isUsingSystemTheme ->
            if (isUsingSystemDarkTheme()) {
                R.style.MyTimePickerMaterialTheme_Dark
            } else {
                R.style.MyDateTimePickerMaterialTheme
            }
        baseConfig.backgroundColor.getContrastColor() == Color.WHITE -> R.style.MyDialogTheme_Dark
        else -> R.style.MyDialogTheme
    }

fun Context.getDatePickerDialogTheme() =
    when {
        baseConfig.isUsingSystemTheme -> R.style.MyDateTimePickerMaterialTheme
        baseConfig.backgroundColor.getContrastColor() == Color.WHITE -> R.style.MyDialogTheme_Dark
        else -> R.style.MyDialogTheme
    }

fun Context.getSharedTheme(callback: (sharedTheme: SharedTheme?) -> Unit) {
    if (!isThankYouInstalled()) {
        callback(null)
    } else {
        val cursorLoader = getMyContentProviderCursorLoader()
        ensureBackgroundThread {
            callback(getSharedThemeSync(cursorLoader))
        }
    }
}

fun Context.getSharedThemeSync(cursorLoader: CursorLoader): SharedTheme? {
    val cursor = cursorLoader.loadInBackground()
    cursor?.use {
        if (cursor.moveToFirst()) {
            try {
                val textColor = cursor.getIntValue(MyContentProvider.COL_TEXT_COLOR)
                val backgroundColor = cursor.getIntValue(MyContentProvider.COL_BACKGROUND_COLOR)
                val primaryColor = cursor.getIntValue(MyContentProvider.COL_PRIMARY_COLOR)
                val accentColor = cursor.getIntValue(MyContentProvider.COL_ACCENT_COLOR)
                val appIconColor = cursor.getIntValue(MyContentProvider.COL_APP_ICON_COLOR)
                val navigationBarColor = cursor.getIntValueOrNull(MyContentProvider.COL_NAVIGATION_BAR_COLOR) ?: INVALID_NAVIGATION_BAR_COLOR
                val lastUpdatedTS = cursor.getIntValue(MyContentProvider.COL_LAST_UPDATED_TS)

                return SharedTheme(textColor, backgroundColor, primaryColor, appIconColor, navigationBarColor, lastUpdatedTS, accentColor)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, "Invalid column index", Toast.LENGTH_SHORT).show()
            } catch (e: CursorIndexOutOfBoundsException) {
                Toast.makeText(this, "Cursor is not in a valid state", Toast.LENGTH_SHORT).show()
            }
        }
    }
    return null
}

fun Context.getAppIconColors() = resources.getIntArray(R.array.md_app_icon_colors).toCollection(ArrayList())
