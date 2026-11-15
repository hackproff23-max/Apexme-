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

package ru.playsoftware.j2meloader.filepicker;

import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.nononsenseapps.filepicker.AbstractFilePickerActivity;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;

import java.io.File;

import ru.playsoftware.j2meloader.R;

public class FilteredFilePickerActivity extends AbstractFilePickerActivity<File> {

	private FilteredFilePickerFragment currentFragment;

	@Override
	protected AbstractFilePickerFragment<File> getFragment(@Nullable String startPath, int mode, boolean allowMultiple,
														   boolean allowCreateDir, boolean allowExistingFile, boolean singleClick) {
		currentFragment = new FilteredFilePickerFragment();
		currentFragment.setArgs(startPath, mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
		return currentFragment;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.FilePickerTheme);
		// tela cheia de verdade: ocupa tambem a area do recorte (notch) da camera
		if (Build.VERSION.SDK_INT >= 28) {
			WindowManager.LayoutParams lp = getWindow().getAttributes();
			lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			getWindow().setAttributes(lp);
		}
		super.onCreate(savedInstanceState);
		hideSystemBars();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hideSystemBars();
		}
	}

	/** Esconde barra de notificacoes e de navegacao; deslizando da borda elas aparecem so por um instante. */
	private void hideSystemBars() {
		Window w = getWindow();
		WindowInsetsControllerCompat c = new WindowInsetsControllerCompat(w, w.getDecorView());
		c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		c.hide(WindowInsetsCompat.Type.systemBars());
	}

	@Override
	public void onBackPressed() {
		if (currentFragment == null || currentFragment.isBackTop()) {
			super.onBackPressed();
		} else {
			currentFragment.goBack();
		}
	}
}
