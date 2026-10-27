/*
 * Copyright 2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.base;

import static ru.playsoftware.j2meloader.util.Constants.PREF_BLACK_BACKGROUND;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexTheme;

@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {
	private int themeVersion;

	@Override
	protected void onResume() {
		super.onResume();
		// cores, fundos ou fonte mudaram em outra tela (Configuracoes): recria para aplicar
		if (themeVersion != ApexTheme.version()) {
			recreate();
		}
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		if (getSupportActionBar() != null) {
			getSupportActionBar().setElevation(getResources().getDisplayMetrics().density * 2);
		}
		super.onCreate(savedInstanceState);
		themeVersion = ApexTheme.version();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			// desenha tambem sobre o recorte (notch/camera)
			WindowManager.LayoutParams lp = getWindow().getAttributes();
			lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
					? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
					: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(lp);
		}
	}

	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		removeTopInset();
		ActionBar bar = getSupportActionBar();
		if (bar != null) {
			bar.setBackgroundDrawable(new ColorDrawable(ApexTheme.hasAppImage(this)
					? Color.TRANSPARENT : ApexTheme.bgColor(this)));
		}
	}

	/** Sobe a barra superior: ignora a folga que o sistema reserva no topo (recorte/barra de status). */
	private void removeTopInset() {
		androidx.core.view.OnApplyWindowInsetsListener listener = (v, insets) ->
				ViewCompat.onApplyWindowInsets(v, withoutTopInset(insets));
		View decor = getWindow().getDecorView();
		ViewCompat.setOnApplyWindowInsetsListener(decor, listener);
		ViewCompat.requestApplyInsets(decor);
		View root = findViewById(androidx.appcompat.R.id.decor_content_parent);
		if (root != null) {
			ViewCompat.setOnApplyWindowInsetsListener(root, listener);
			ViewCompat.requestApplyInsets(root);
			pullBarToTop(root);
		}
	}

	/**
	 * Garantia final: mede onde a barra superior realmente ficou na tela e, se sobrar espaco acima
	 * (barra de notificacao/recorte), sobe o conteudo exatamente essa diferenca.
	 */
	private void pullBarToTop(View root) {
		root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
			View bar = root.findViewById(androidx.appcompat.R.id.action_bar_container);
			if (bar == null || bar.getVisibility() != View.VISIBLE
					|| !(root.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams)) {
				return;
			}
			int[] loc = new int[2];
			bar.getLocationInWindow(loc);
			int gap = loc[1];
			int max = Math.round(getResources().getDisplayMetrics().density * 120);
			if (gap > 0 && gap < max) {
				android.view.ViewGroup.MarginLayoutParams lp =
						(android.view.ViewGroup.MarginLayoutParams) root.getLayoutParams();
				lp.topMargin -= gap;
				root.setLayoutParams(lp);
			}
		});
	}

	/** Zera o topo de todos os tipos de inset, em qualquer celular (com ou sem recorte de camera). */
	@SuppressWarnings("deprecation")
	private static WindowInsetsCompat withoutTopInset(WindowInsetsCompat insets) {
		int[] types = {
				WindowInsetsCompat.Type.statusBars(),
				WindowInsetsCompat.Type.systemBars(),
				WindowInsetsCompat.Type.displayCutout(),
				WindowInsetsCompat.Type.captionBar(),
				WindowInsetsCompat.Type.tappableElement(),
				WindowInsetsCompat.Type.mandatorySystemGestures(),
				WindowInsetsCompat.Type.systemGestures()
		};
		WindowInsetsCompat.Builder builder = new WindowInsetsCompat.Builder(insets);
		for (int type : types) {
			Insets i = insets.getInsets(type);
			builder.setInsets(type, Insets.of(i.left, 0, i.right, i.bottom));
			Insets iv = insets.getInsetsIgnoringVisibility(type);
			builder.setInsetsIgnoringVisibility(type, Insets.of(iv.left, 0, iv.right, iv.bottom));
		}
		Insets sw = insets.getSystemWindowInsets();
		Insets st = insets.getStableInsets();
		return builder
				.setSystemWindowInsets(Insets.of(sw.left, 0, sw.right, sw.bottom))
				.setStableInsets(Insets.of(st.left, 0, st.right, st.bottom))
				.build();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hideSystemBars();
		}
	}

	/** Tela cheia: esconde barra de notificacao e botoes de navegacao (voltam com um gesto de deslizar). */
	private void hideSystemBars() {
		WindowInsetsControllerCompat controller =
				WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		controller.setSystemBarsBehavior(
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		controller.hide(WindowInsetsCompat.Type.systemBars());
	}
}
