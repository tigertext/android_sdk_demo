package com.tigertext.ttandroid.sample.views

import android.content.Context
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.tigertext.ttandroid.sample.R
import kotlinx.android.synthetic.main.circle_button.view.*

/**
 * Created by martincazares on 1/10/18.
 */
class CircleButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        LinearLayout(context, attrs, defStyleAttr) {
    private var normalIconColor: Int = 0
    private var normalCircleColor: Int = 0

    private var activeIconColor: Int = 0
    private var activeCircleColor: Int = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.circle_button, this, true)
        setupView(attrs, defStyleAttr)
    }

    private fun setupView(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs != null) {
            val att = context.obtainStyledAttributes(attrs, R.styleable.CircleButton, defStyleAttr, 0)
            val iconValue = att.getResourceId(R.styleable.CircleButton_circle_icon, 0)
            setIcon(iconValue)
            handleSizes(att)
            handleColors(att)
            att.recycle()
        }
    }

    private fun handleSizes(att: TypedArray) {
        if (att.hasValue(R.styleable.CircleButton_circle_size)) {
            val layoutParams = circleButtonIcon.layoutParams
            val size = att.getDimension(R.styleable.CircleButton_circle_size, 0F).toInt()
            layoutParams.width = size
            layoutParams.height = size
            circleButtonIcon.layoutParams = layoutParams
        }

        if (att.hasValue(R.styleable.CircleButton_circle_icon_padding)) {
            val padding = att.getDimension(R.styleable.CircleButton_circle_icon_padding, 0F).toInt()
            circleButtonIcon.setPadding(padding, padding, padding, padding)
        }
    }

    private fun handleColors(att: TypedArray) {
        val defaultIconColor = ContextCompat.getColor(context, android.R.color.white)
        val defaultBackgroundColor = ContextCompat.getColor(context, R.color.voip_video_shadow_color)

        //Setup colors
        normalIconColor = att.getColor(R.styleable.CircleButton_circle_icon_color, defaultIconColor)
        normalCircleColor = att.getColor(R.styleable.CircleButton_circle_background_color, defaultBackgroundColor)

        activeIconColor = att.getColor(R.styleable.CircleButton_active_icon_color, defaultIconColor)
        activeCircleColor = att.getColor(R.styleable.CircleButton_active_circle_background_color, defaultBackgroundColor)

        circleButtonIcon.setColorFilter(normalIconColor, PorterDuff.Mode.SRC_IN)
        setCircleBackground(normalCircleColor)
    }

    private fun setCircleBackground(@ColorInt color: Int) {
        val backgroundGradient = circleButtonIcon.background as GradientDrawable
        backgroundGradient.setColor(color)
    }

    fun toggleState() {
        active = !active
    }

    fun setIcon(@DrawableRes iconDrawable: Int) {
        circleButtonIcon.setImageResource(iconDrawable)
    }

    var active = false
        set(value) {
            if (field != value) {
                field = value
                setCircleBackground(if (active) activeCircleColor else normalCircleColor)
                circleButtonIcon.setColorFilter(if (active) activeIconColor else normalIconColor, PorterDuff.Mode.SRC_IN)
            }
        }

    fun setNormalColor(@ColorInt normalIconColor: Int, @ColorInt normalCircleColor:Int) {
        this.normalIconColor = normalIconColor
        this.normalCircleColor = normalCircleColor
        if (!active) {
            setCircleBackground(normalCircleColor)
            circleButtonIcon.setColorFilter(normalIconColor, PorterDuff.Mode.SRC_IN)
        }
    }
}
