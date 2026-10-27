/*
 * Copyright 2018 Nikita Shakarun
 * Copyright 2022 Arman Jussupgaliyev
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

package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import java.io.File;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;

import static ru.playsoftware.j2meloader.util.Constants.*;

import ru.playsoftware.j2meloader.util.FileUtils;

public class Config {
	public static final String DEX_OPT_CACHE_DIR = "dex_opt";
	public static final String SETTINGS_DIR = "/configuracoes/";
	public static final String IMPORT_DIR = "/importacao/";
	public static final String PROFILES_DIR = "/perfil/";
	public static final String APPS_DIR = "/data/";
	public static final String FS_DIR = SETTINGS_DIR + "fs/";
	public static final String MIDLET_CONFIG_FILE = "/config.json";
	/** Pasta de cada jogo: config.json, teclado virtual e saves. */
	public static final String MIDLET_CONFIGS_DIR = "/jogos/";
	public static final String MIDLET_SAVES_DIR = "/saves";
	public static final String MIDLET_DEX_FILE = "/converted.dex";
	public static final String MIDLET_ICON_FILE = "/icon.png";
	public static final String MIDLET_KEY_LAYOUT_FILE = "/VirtualKeyboardLayout";
	public static final String MIDLET_MANIFEST_FILE = MIDLET_DEX_FILE + ".conf";
	public static final String MIDLET_RES_DIR = "/res";
	public static final String MIDLET_RES_FILE = "/res.jar";
	public static final String SCREENSHOTS_DIR;
	public static final String SHADERS_DIR = "/shaders/";

	private static String emulatorDir;
	private static String configsDir;
	private static String profilesDir;
	private static String appDir;

	private static final SharedPreferences.OnSharedPreferenceChangeListener sPrefListener =
			(sharedPreferences, key) -> {
				if (key.equals(PREF_EMULATOR_DIR)) {
					initDirs(sharedPreferences.getString(key, emulatorDir));
				}
			};

	static {
		Context context = ContextHolder.getAppContext();
		String appName = "Apexme";
		if (!BuildConfig.FULL_EMULATOR) {
			appName = context.getString(R.string.app_name);
		}
		SCREENSHOTS_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
				+ "/" + appName;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		String path = FileUtils.isExternalStorageLegacy() ?
				preferences.getString(PREF_EMULATOR_DIR, null) :
				context.getExternalFilesDir(null).getPath();
		if (path == null) {
			path = Environment.getExternalStorageDirectory() + "/" + appName;
		}
		initDirs(path);
		preferences.registerOnSharedPreferenceChangeListener(sPrefListener);
	}

	public static String getEmulatorDir() {
		return emulatorDir;
	}

	/** Pasta de saves (RMS e arquivos privados) de um jogo: jogos/NOME/saves. */
	public static String getSavesDir(String appName) {
		return configsDir + appName + MIDLET_SAVES_DIR;
	}

	public static String getSettingsDir() {
		return emulatorDir + SETTINGS_DIR;
	}

	public static String getImportDir() {
		return emulatorDir + IMPORT_DIR;
	}

	public static String getConfigsDir() {
		return configsDir;
	}

	public static String getProfilesDir() {
		return profilesDir;
	}

	public static String getAppDir() {
		return appDir;
	}

	public static String getShadersDir() {
		return emulatorDir + SHADERS_DIR;
	}

	public static String getFsInternalDir() {
		return emulatorDir + FS_DIR + "c/";
	}

	public static String getFsExternalDir() {
		if (FileUtils.isExternalStorageLegacy()) {
			return Environment.getExternalStorageDirectory().getPath() + "/";
		} else {
			return emulatorDir + FS_DIR + "e/";
		}
	}

	public static void startApp(Context context, String name, String path, boolean showSettings) {
		startApp(context, name, path, showSettings, null);
	}

	public static void startApp(Context context, String name, String path, boolean showSettings, String arguments) {
		File appDir = new File(path);
		String workDir = appDir.getParentFile().getParent();
		File file = new File(workDir + Config.MIDLET_CONFIGS_DIR + appDir.getName());
		if (showSettings || !file.exists()) {
			Intent intent = new Intent(ACTION_EDIT, Uri.parse(path),
					context, ConfigActivity.class);
			intent.putExtra(KEY_MIDLET_NAME, name);
			intent.putExtra(KEY_START_ARGUMENTS, arguments);
			context.startActivity(intent);
		} else {
			Intent intent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(path),
					context, MicroActivity.class);
			intent.putExtra(KEY_MIDLET_NAME, name);
			intent.putExtra(KEY_START_ARGUMENTS, arguments);
			context.startActivity(intent);
		}
	}

	private static void initDirs(String path) {
		emulatorDir = path;
		configsDir = emulatorDir + MIDLET_CONFIGS_DIR;
		profilesDir = emulatorDir + PROFILES_DIR;
		appDir = emulatorDir + APPS_DIR;
	}
}
