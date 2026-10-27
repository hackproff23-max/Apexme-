package ru.playsoftware.j2meloader.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.OutlinedTextView;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Exemplo de como os textos ficam com as cores e a fonte escolhidas. */
public class PreviewPreference extends Preference {
	public PreviewPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setLayoutResource(R.layout.pref_preview);
		setSelectable(false);
	}

	public void refresh() {
		notifyChanged();
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		ViewGroup box = (ViewGroup) holder.findViewById(R.id.preview_box);
		if (box == null) {
			return;
		}
		Context c = getContext();
		float d = c.getResources().getDisplayMetrics().density;
		box.removeAllViews();
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ApexTheme.bgColor(c) | 0xFF000000);
		bg.setCornerRadius(d * 8);
		bg.setStroke((int) (d * 2), ApexTheme.outlineColor(c));
		box.setBackground(bg);

		box.addView(text(c, R.string.apex_preview_title, 24));
		TextView body = text(c, R.string.apex_preview_body, 15);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = (int) (d * 6);
		box.addView(body, lp);

		TextView pressed = text(c, R.string.apex_preview_pressed, 15);
		pressed.setBackgroundColor(ApexTheme.pressedColor(c));
		int p = (int) (d * 8);
		pressed.setPadding(p, p, p, p);
		LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp2.topMargin = (int) (d * 12);
		box.addView(pressed, lp2);
	}

	private static TextView text(Context c, int res, float sp) {
		OutlinedTextView tv = new OutlinedTextView(c);
		tv.setText(res);
		tv.setTextSize(sp);
		return tv;
	}
}
