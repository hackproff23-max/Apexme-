package ru.playsoftware.j2meloader.theme;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import ru.playsoftware.j2meloader.MainActivity;

/** Troca o icone do app no launcher conforme o tema: claro = icone claro, escuro/outro = icone escuro. */
public final class ApexLauncherIcon {
	private ApexLauncherIcon() {
	}

	public static void sync(Context c) {
		try {
			boolean light = ApexThemeMode.current(c) == ApexThemeMode.MODE_LIGHT;
			PackageManager pm = c.getPackageManager();
			String base = MainActivity.class.getName().replace("MainActivity", "");
			ComponentName lightC = new ComponentName(c, base + "LauncherLight");
			ComponentName darkC = new ComponentName(c, base + "LauncherDark");
			boolean lightOn = isOn(pm, lightC, false);
			boolean darkOn = isOn(pm, darkC, true);
			if (light && lightOn && !darkOn || !light && darkOn && !lightOn) {
				return;
			}
			set(pm, light ? lightC : darkC, true);
			set(pm, light ? darkC : lightC, false);
		} catch (Exception ignored) {
		}
	}

	private static boolean isOn(PackageManager pm, ComponentName cn, boolean manifestDefault) {
		int s = pm.getComponentEnabledSetting(cn);
		if (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
			return manifestDefault;
		}
		return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
	}

	private static void set(PackageManager pm, ComponentName cn, boolean on) {
		pm.setComponentEnabledSetting(cn,
				on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
				PackageManager.DONT_KILL_APP);
	}
}
