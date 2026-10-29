package ru.playsoftware.j2meloader.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import yuku.ambilwarna.AmbilWarnaDialog;

/** Linha de configuracao com a cor atual; ao tocar abre o seletor de cor. */
public class ColorPreference extends Preference {
	private String colorKey;
	private int defColor;
	private Runnable onChanged;

	public ColorPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setWidgetLayoutResource(R.layout.pref_swatch);
	}

	public void setup(String key, int def, Runnable changed) {
		colorKey = key;
		defColor = def;
		onChanged = changed;
		notifyChanged();
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		View swatch = holder.findViewById(R.id.swatch);
		if (swatch != null && colorKey != null) {
			float d = getContext().getResources().getDisplayMetrics().density;
			GradientDrawable g = new GradientDrawable();
			g.setColor(ApexTheme.getColor(getContext(), colorKey, defColor));
			g.setCornerRadius(d * 6);
			g.setStroke((int) (d * 2), Color.GRAY);
			swatch.setBackground(g);
		}
	}

	@Override
	protected void onClick() {
		if (colorKey == null) {
			return;
		}
		Context c = getContext();
		int current = ApexTheme.getColor(c, colorKey, defColor);
		new AmbilWarnaDialog(c, current, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				ApexTheme.setColor(c, colorKey, color);
				notifyChanged();
				if (onChanged != null) {
					onChanged.run();
				}
			}

			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
			}
		}).show();
	}
}
