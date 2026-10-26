package ru.playsoftware.j2meloader.apex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import javax.microedition.util.ActivityResultListener;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Utilitarios de interface das janelas do Apexme. */
final class ApexUi {
	private static int nextCode = 0xA7E0;

	interface IntListener {
		void on(int value);
	}

	interface UriListener {
		void on(Uri uri);
	}

	interface BoolListener {
		void on(boolean value);
	}

	private ApexUi() {
	}

	static int dp(Context c, float v) {
		return (int) (c.getResources().getDisplayMetrics().density * v);
	}

	static LinearLayout column(Context c) {
		LinearLayout l = new LinearLayout(c);
		l.setOrientation(LinearLayout.VERTICAL);
		int p = dp(c, 16);
		l.setPadding(p, dp(c, 8), p, dp(c, 8));
		return l;
	}

	static TextView text(Context c, String s, float sp) {
		TextView t = new TextView(c);
		t.setText(s);
		t.setTextSize(sp);
		t.setTextColor(ApexTheme.textColor(c));
		ApexTheme.applyFont(t);
		return t;
	}

	static TextView section(Context c, String s) {
		TextView t = text(c, s, 14);
		t.setTextColor(ApexTheme.outlineColor(c));
		t.setPadding(0, dp(c, 14), 0, dp(c, 4));
		return t;
	}

	static Button button(Context c, String label, View.OnClickListener l) {
		Button b = new Button(c);
		b.setText(label);
		b.setAllCaps(false);
		b.setOnClickListener(l);
		ApexTheme.applyFont(b);
		return b;
	}

	static LinearLayout row(Context c, View... views) {
		LinearLayout r = new LinearLayout(c);
		r.setOrientation(LinearLayout.HORIZONTAL);
		for (View v : views) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			r.addView(v, lp);
		}
		return r;
	}

	static View slider(Context c, String label, int min, int max, int cur, String suffix, IntListener l) {
		LinearLayout box = new LinearLayout(c);
		box.setOrientation(LinearLayout.VERTICAL);
		TextView t = text(c, label + ": " + cur + suffix, 14);
		SeekBar sb = new SeekBar(c);
		sb.setMax(max - min);
		sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
				t.setText(label + ": " + (min + p) + suffix);
				if (fromUser) {
					l.on(min + p);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar s) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar s) {
			}
		});
		box.addView(t);
		box.addView(sb);
		box.setPadding(0, dp(c, 6), 0, dp(c, 6));
		return box;
	}

	static SwitchCompat toggle(Context c, String label, boolean checked, BoolListener l) {
		SwitchCompat sw = new SwitchCompat(c);
		sw.setText(label);
		sw.setTextColor(ApexTheme.textColor(c));
		sw.setChecked(checked);
		sw.setOnCheckedChangeListener((b, v) -> l.on(v));
		sw.setPadding(0, dp(c, 8), 0, dp(c, 8));
		ApexTheme.applyFont(sw);
		return sw;
	}

	/** Abre o seletor de arquivos do sistema e devolve o Uri escolhido. */
	static void pick(Activity a, String mime, UriListener l) {
		final int code = nextCode++;
		ActivityResultListener[] holder = new ActivityResultListener[1];
		holder[0] = (req, res, data) -> {
			if (req != code) {
				return;
			}
			ContextHolder.removeActivityResultListener(holder[0]);
			if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
				l.on(data.getData());
			}
		};
		ContextHolder.addActivityResultListener(holder[0]);
		Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType(mime);
		try {
			a.startActivityForResult(i, code);
		} catch (Exception e) {
			ContextHolder.removeActivityResultListener(holder[0]);
		}
	}
}
