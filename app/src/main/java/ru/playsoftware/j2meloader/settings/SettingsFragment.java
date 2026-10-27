/*
 * Copyright 2017-2018 Nikita Shakarun
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

package ru.playsoftware.j2meloader.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Toast;

import com.nononsenseapps.filepicker.Utils;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.PickDirResultContract;

import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

public class SettingsFragment extends PreferenceFragmentCompat {
	private Preference prefFolder;
	private int pendingTarget;
	private final ActivityResultLauncher<String[]> themePicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			this::onThemeFilePicked);
	private final ActivityResultLauncher<String> openDirLauncher = registerForActivityResult(
			new PickDirResultContract(),
			this::onPickDirResult);

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		findPreference("pref_default_settings").setIntent(new Intent(requireActivity(), ProfilesActivity.class));
		prefFolder = findPreference(PREF_EMULATOR_DIR);
		prefFolder.setSummary(Config.getEmulatorDir());
		prefFolder.setOnPreferenceClickListener(preference -> {
			if (FileUtils.isExternalStorageLegacy()) {
				openDirLauncher.launch(null);
			} else {
				openPicker();
			}
			return true;
		});
		PreviewPreference preview = findPreference("pref_preview");
		Runnable refresh = preview::refresh;
		((ColorPreference) findPreference("pref_color_text")).setup(ApexTheme.K_TEXT, ApexTheme.DEF_TEXT, refresh);
		((ColorPreference) findPreference("pref_color_outline")).setup(ApexTheme.K_OUTLINE, ApexTheme.DEF_OUTLINE, refresh);
		((ColorPreference) findPreference("pref_color_bg")).setup(ApexTheme.K_BG, ApexTheme.DEF_BG, refresh);
		((ColorPreference) findPreference("pref_color_window")).setup(ApexTheme.K_WINDOW, ApexTheme.DEF_BG, refresh);
		((ColorPreference) findPreference("pref_color_pressed")).setup(ApexTheme.K_PRESSED, ApexTheme.DEF_PRESSED, refresh);
		findPreference("pref_reset_theme").setOnPreferenceClickListener(p -> {
			ApexTheme.resetAll(requireContext());
			requireActivity().recreate();
			return true;
		});
		setupImport("pref_import_app_bg", ApexTheme.TARGET_APP_BG, "image/*");
		setupImport("pref_import_window_bg", ApexTheme.TARGET_WINDOW_BG, "image/*");
		setupImport("pref_import_font", ApexTheme.TARGET_FONT, "*/*");
	}

	private void setupImport(String key, int target, String mime) {
		findPreference(key).setOnPreferenceClickListener(p -> {
			new AlertDialog.Builder(requireActivity())
					.setTitle(p.getTitle())
					.setItems(new String[]{getString(R.string.apex_import_choose),
							getString(R.string.apex_remove)}, (d, which) -> {
						if (which == 0) {
							pendingTarget = target;
							try {
								themePicker.launch(new String[]{mime});
							} catch (ActivityNotFoundException e) {
								Toast.makeText(requireContext(), R.string.error_no_picker,
										Toast.LENGTH_SHORT).show();
							}
						} else {
							ApexTheme.clear(requireContext(), target);
							requireActivity().recreate();
						}
					})
					.show();
			return true;
		});
	}

	private void onThemeFilePicked(Uri uri) {
		if (uri == null) {
			return;
		}
		boolean ok = ApexTheme.importFile(requireContext(), pendingTarget, uri);
		Toast.makeText(requireContext(), ok ? R.string.apex_import_ok : R.string.apex_import_fail,
				Toast.LENGTH_SHORT).show();
		if (ok) {
			requireActivity().recreate();
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private void openPicker() {
		try {
			startActivity(getFileManagerIntentOnDocumentProvider(Intent.ACTION_VIEW));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			startActivity(getFileManagerIntentOnDocumentProvider("android.provider.action.BROWSE"));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			// Just try to open the file manager, try the package name used on "normal" phones
			startActivity(getFileManagerIntent("com.google.android.documentsui"));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			// Next, try the AOSP package name
			startActivity(getFileManagerIntent("com.android.documentsui"));
		} catch (ActivityNotFoundException ignored) {}
	}

	private Intent getFileManagerIntent(String packageName) {
		// Fragile, but some phones don't expose the system file manager in any better way
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName(packageName, "com.android.documentsui.files.FilesActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private Intent getFileManagerIntentOnDocumentProvider(String action) {
		String authority = requireContext().getPackageName() + ".documentProvider";
		String root = new File(Config.getEmulatorDir()).getAbsolutePath();
		Intent intent = new Intent(action);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.setData(DocumentsContract.buildRootUri(authority, root));
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
				| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		return intent;
	}

	private void onPickDirResult(Uri uri) {
		if (uri == null) {
			return;
		}
		File file = Utils.getFileForUri(uri);
		String path = file.getAbsolutePath();
		if (!FileUtils.initWorkDir(file)) {
			new AlertDialog.Builder(requireActivity())
					.setTitle(R.string.error)
					.setCancelable(false)
					.setMessage(getString(R.string.create_apps_dir_failed, path))
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(R.string.choose, (d, w) -> openDirLauncher.launch(null))
					.show();
			return;
		}
		getPreferenceManager().getSharedPreferences().edit()
				.putString(PREF_EMULATOR_DIR, path)
				.apply();
		prefFolder.setSummary(path);
	}
}
