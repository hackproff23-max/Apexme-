/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2018-2022 Yury Kharchenko
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

package ru.playsoftware.j2meloader.applist;

import static ru.playsoftware.j2meloader.util.Constants.KEY_APP_URI;
import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.PREF_APP_SORT;
import static ru.playsoftware.j2meloader.util.Constants.PREF_LAST_PATH;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ConfigActivity;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.databinding.FragmentAppsListBinding;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerFragment;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.info.HelpDialogFragment;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.theme.ApexTheme;
import ru.playsoftware.j2meloader.theme.ThemeDialog;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.playsoftware.j2meloader.util.PickDirResultContract;
import ru.woesss.j2me.installer.BatchInstaller;
import ru.woesss.j2me.installer.InstallerDialog;

public class AppsListFragment extends Fragment {
	private static final String TAG = AppsListFragment.class.getSimpleName();
	private final AppsListAdapter adapter = new AppsListAdapter();
	private Uri appUri;
	private SharedPreferences preferences;
	private AppRepository appRepository;
	private Disposable searchViewDisposable;

	FragmentAppsListBinding binding;

	private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
			FileUtils.getFilePicker(),
			this::onPickFileResult);

	private final ActivityResultLauncher<String> pickFolderLauncher = registerForActivityResult(
			new PickDirResultContract(),
			this::onPickFolderResult);

	private AlertDialog batchDialog;

	private void startFolderImport() {
		if (!FileUtils.isExternalStorageLegacy()) {
			Toast.makeText(requireContext(), R.string.import_folder_need_access, Toast.LENGTH_LONG).show();
			return;
		}
		String path = preferences.getString(PREF_LAST_PATH, null);
		if (path == null || !new File(path).isDirectory()) {
			path = Environment.getExternalStorageDirectory().getAbsolutePath();
		}
		try {
			pickFolderLauncher.launch(path);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
		}
	}

	private void onPickFolderResult(Uri uri) {
		if (uri == null) {
			return;
		}
		File dir = com.nononsenseapps.filepicker.Utils.getFileForUri(uri);
		if (!dir.isDirectory()) {
			return;
		}
		preferences.edit().putString(PREF_LAST_PATH, dir.getAbsolutePath()).apply();
		// Somente arquivos .jar direto na pasta escolhida (sem ler subpastas)
		File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
		if (files == null || files.length == 0) {
			Toast.makeText(requireContext(), R.string.import_folder_empty, Toast.LENGTH_LONG).show();
			return;
		}
		Arrays.sort(files, Comparator.comparing((File f) -> f.getName().toLowerCase(Locale.ROOT)));
		List<File> jars = new ArrayList<>(Arrays.asList(files));
		new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.action_import_folder)
				.setMessage(getString(R.string.import_folder_confirm, jars.size()))
				.setPositiveButton(android.R.string.ok, (d, w) -> runBatch(jars))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void runBatch(List<File> jars) {
		final FragmentActivity activity = requireActivity();
		final AtomicBoolean cancel = new AtomicBoolean(false);
		batchDialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.import_folder_title)
				.setMessage(getString(R.string.import_folder_progress, 0, jars.size(), ""))
				.setCancelable(false)
				.setNegativeButton(android.R.string.cancel, (d, w) -> cancel.set(true))
				.create();
		batchDialog.setCanceledOnTouchOutside(false);
		batchDialog.show();
		final Context appContext = activity.getApplicationContext();
		BatchInstaller.start(activity.getApplication(), appRepository, jars, cancel,
				new BatchInstaller.Listener() {
					@Override
					public void onProgress(int index, int total, String fileName) {
						if (batchDialog != null && batchDialog.isShowing()) {
							batchDialog.setMessage(appContext.getString(
									R.string.import_folder_progress, index, total, fileName));
						}
					}

					@Override
					public void onDone(int installed, int skipped, List<String> failed, boolean cancelled) {
						try {
							if (batchDialog != null) {
								batchDialog.dismiss();
							}
						} catch (Exception ignored) {
						}
						batchDialog = null;
						StringBuilder msg = new StringBuilder(appContext.getString(
								R.string.import_folder_result, installed, skipped, failed.size()));
						if (cancelled) {
							msg.append("\n\n").append(appContext.getString(R.string.import_folder_cancelled));
						}
						for (int i = 0; i < failed.size() && i < 15; i++) {
							msg.append(i == 0 ? "\n\n" : "\n").append(failed.get(i));
						}
						if (failed.size() > 15) {
							msg.append("\n...");
						}
						if (activity.isFinishing() || activity.isDestroyed()) {
							Toast.makeText(appContext, msg.toString(), Toast.LENGTH_LONG).show();
							return;
						}
						new AlertDialog.Builder(activity)
								.setTitle(R.string.action_import_folder)
								.setMessage(msg)
								.setPositiveButton(android.R.string.ok, null)
								.show();
					}
				});
	}

	private int pendingThemeTarget;
	private final ActivityResultLauncher<String[]> themePicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			this::onThemeFilePicked);

	private void onThemeFilePicked(Uri uri) {
		if (uri == null) {
			return;
		}
		boolean ok = ApexTheme.importFile(requireContext(), pendingThemeTarget, uri);
		Toast.makeText(requireContext(), ok ? R.string.apex_import_ok : R.string.apex_import_fail,
				Toast.LENGTH_SHORT).show();
		if (ok) {
			requireActivity().recreate();
		}
	}

	private void showThemeDialog() {
		ThemeDialog.show(requireActivity(), target -> {
			pendingThemeTarget = target;
			String[] types = target == ApexTheme.TARGET_FONT
					? new String[]{"*/*"} : new String[]{"image/*"};
			try {
				themePicker.launch(types);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
			}
		});
	}

	public static AppsListFragment newInstance(Uri data) {
		AppsListFragment fragment = new AppsListFragment();
		Bundle args = new Bundle();
		args.putParcelable(KEY_APP_URI, data);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = requireArguments();
		appUri = args.getParcelable(KEY_APP_URI);
		args.remove(KEY_APP_URI);
		preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
		AppListModel appListModel = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		appRepository = appListModel.getAppRepository();
		appRepository.observeErrors(this, this::alertDbError);
		appRepository.observeApps(this, this::onDbUpdated);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentAppsListBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		registerForContextMenu(binding.list);
		setHasOptionsMenu(true);
		binding.list.setEmptyView(binding.empty);
		binding.floatingActionButton.setBackgroundTintList(new ColorStateList(
				new int[][]{{android.R.attr.state_pressed}, {}},
				new int[]{ApexTheme.pressedColor(requireContext()), ApexTheme.outlineColor(requireContext())}));
		binding.list.setAdapter(adapter);
		binding.list.setOnItemClickListener((parent, v, position, id) -> onListItemClick(position));
		binding.floatingActionButton.setOnClickListener(v -> {
			String path = preferences.getString(PREF_LAST_PATH, null);
			if (path == null) {
				File dir = Environment.getExternalStorageDirectory();
				if (dir.canRead()) {
					path = dir.getAbsolutePath();
				}
			}
			try {
				openFileLauncher.launch(path);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
		});
	}

	private void alertDbError(Throwable throwable) {
		Activity activity = getActivity();
		if (activity == null) {
			Log.e(TAG, "Db error detected", throwable);
			return;
		}
		if (throwable instanceof SQLiteDiskIOException) {
			Toast.makeText(activity, R.string.error_disk_io, Toast.LENGTH_SHORT).show();
		} else {
			String msg = activity.getString(R.string.error) + ": " + throwable.getMessage();
			Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
		}
	}

	private void onPickFileResult(Uri uri) {
		if (uri == null) {
			return;
		}
		preferences.edit()
				.putString(Constants.PREF_LAST_PATH, FilteredFilePickerFragment.getLastPath())
				.apply();
		InstallerDialog.newInstance(uri).show(getParentFragmentManager(), "installer");
	}

	private void alertRename(final int id) {
		AppItem item = adapter.getItem(id);
		FragmentActivity activity = requireActivity();
		EditText editText = new EditText(activity);
		editText.setText(item.getTitle());
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(activity);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.action_context_rename)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					String title = editText.getText().toString().trim();
					if (title.equals("")) {
						Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
					} else {
						item.setTitle(title);
						appRepository.update(item);
					}
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void alertDelete(AppItem item) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_delete)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					AppUtils.deleteApp(item);
					appRepository.delete(item);
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void onListItemClick(int position) {
		AppItem item = adapter.getItem(position);
		Config.startApp(requireActivity(), item.getTitle(), item.getPathExt(), false);
	}

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
									ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = requireActivity().getMenuInflater();
		inflater.inflate(R.menu.context_main, menu);
		if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
			menu.findItem(R.id.action_context_shortcut).setVisible(false);
		}
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		int index = info.position;
		AppItem appItem = adapter.getItem(index);
		if (!new File(appItem.getPathExt() + Config.MIDLET_RES_FILE).exists()) {
			menu.findItem(R.id.action_context_reinstall).setVisible(false);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		int index = info.position;
		AppItem appItem = adapter.getItem(index);
		int itemId = item.getItemId();
		if (itemId == R.id.action_context_shortcut) {
			requestAddShortcut(appItem);
		} else if (itemId == R.id.action_context_rename) {
			alertRename(index);
		} else if (itemId == R.id.action_context_settings) {
			Config.startApp(requireActivity(), appItem.getTitle(), appItem.getPathExt(), true);
		} else if (itemId == R.id.action_context_reinstall) {
			InstallerDialog.newInstance(appItem.getId()).show(getParentFragmentManager(), "installer");
		} else if (itemId == R.id.action_context_delete) {
			alertDelete(appItem);
		} else {
			return super.onContextItemSelected(item);
		}
		return true;
	}

	private void requestAddShortcut(AppItem appItem) {
		FragmentActivity activity = requireActivity();
		Bitmap bitmap = AppUtils.getIconBitmap(appItem);
		IconCompat icon;
		if (bitmap == null) {
			icon = IconCompat.createWithResource(activity, R.mipmap.ic_launcher);
		} else {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
			int iconSize = am.getLauncherLargeIconSize();
			Rect src;
			if (width > height) {
				int left = (width - height) / 2;
				src = new Rect(left, 0, left + height, height);
			} else if (width < height) {
				int top = (height - width) / 2;
				src = new Rect(0, top, width, top + width);
			} else {
				src = null;
			}
			Bitmap scaled = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(scaled);
			canvas.drawBitmap(bitmap, src, new RectF(0, 0, iconSize, iconSize), null);
			icon = IconCompat.createWithBitmap(scaled);
		}
		String title = appItem.getTitle();
		Intent launchIntent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(appItem.getPathExt()),
				activity, ConfigActivity.class);
		launchIntent.putExtra(KEY_MIDLET_NAME, title);
		ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(activity, title)
				.setIntent(launchIntent)
				.setShortLabel(title)
				.setIcon(icon)
				.build();
		ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		FragmentActivity activity = requireActivity();
		int itemId = item.getItemId();
		if (itemId == R.id.action_about) {
			AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
			aboutDialogFragment.show(getChildFragmentManager(), "about");
		} else if (itemId == R.id.action_profiles) {
			Intent intentProfiles = new Intent(activity, ProfilesActivity.class);
			startActivity(intentProfiles);
		} else if (item.getItemId() == R.id.action_settings) {
			startActivity(new Intent(activity, SettingsActivity.class));
			return true;
		} else if (itemId == R.id.action_help) {
			HelpDialogFragment helpDialogFragment = new HelpDialogFragment();
			helpDialogFragment.show(getChildFragmentManager(), "help");
		} else if (itemId == R.id.action_save_log) {
			try {
				LogUtils.writeLog();
				Toast.makeText(activity, R.string.log_saved, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show();
			}
		} else if (itemId == R.id.action_exit_app) {
			activity.finish();
		} else if (itemId == R.id.action_sort) {
			showSortDialog();
		} else if (itemId == R.id.action_colors) {
			showThemeDialog();
		} else if (itemId == R.id.action_import_folder) {
			startFolderImport();
		}
		return false;
	}

	private void showSortDialog() {
		int variant = appRepository.getSort();
		SortAdapter adapter = new SortAdapter(requireActivity(), variant);
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.pref_app_sort_title)
				.setAdapter(adapter, (d, v) -> {
					adapter.setVariant(v);
					setSort(v);
					d.dismiss();
				});
		builder.show();
	}

	private void setSort(int sortVariant) {
		if (appRepository.getSort() == sortVariant) {
			sortVariant |= 0x80000000;
		}
		preferences.edit().putInt(PREF_APP_SORT, sortVariant).apply();
	}

	private void onDbUpdated(List<AppItem> items) {
		adapter.setItems(items);
		if (appUri != null) {
			InstallerDialog.newInstance(appUri).show(getParentFragmentManager(), "installer");
			appUri = null;
		}
	}

	private static class SortAdapter extends ArrayAdapter<String> {
		private int variant;
		private final Drawable drawableArrowDown;
		private final Drawable drawableArrowUp;

		public SortAdapter(FragmentActivity activity, int variant) {
			super(activity,
					android.R.layout.simple_list_item_1,
					activity.getResources().getStringArray(R.array.pref_app_sort_entries));
			this.variant = variant;
			drawableArrowDown = AppCompatResources.getDrawable(activity, R.drawable.ic_arrow_down);
			drawableArrowUp = AppCompatResources.getDrawable(activity, R.drawable.ic_arrow_up);
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			TextView tv = (TextView) super.getView(position, convertView, parent);
			if ((variant & 0x7FFFFFFF) == position) {
				TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(tv, null, null,
						variant >= 0 ? drawableArrowDown : drawableArrowUp, null);
			} else {
				tv.setCompoundDrawables(null, null, null, null);
			}
			return tv;
		}

		public void setVariant(int variant) {
			if (variant == this.variant) {
				variant |= 0x80000000;
			}
			this.variant = variant;
			notifyDataSetChanged();
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onDestroy() {
		if (searchViewDisposable != null) {
			searchViewDisposable.dispose();
		}
		super.onDestroy();
	}
}
