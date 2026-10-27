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
import java.util.List;
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
import ru.woesss.j2me.installer.InstallerDialog;

public class AppsListFragment extends Fragment {
	private static final String TAG = AppsListFragment.class.getSimpleName();
	private final AppsListAdapter adapter = new AppsListAdapter();
	private Uri appUri;
	private SharedPreferences preferences;
	private AppRepository appRepository;
	private Disposable searchViewDisposable;

	FragmentAppsListBinding binding;

	/** Explorador do proprio app, varios jogos de uma vez. */
	private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
			new ru.playsoftware.j2meloader.util.PickFilesResultContract(),
			this::onFilesPicked);

	private final java.util.ArrayDeque<Uri> installQueue = new java.util.ArrayDeque<>();
	private final ActivityResultLauncher<String> folderPicker = registerForActivityResult(
			new ru.playsoftware.j2meloader.util.PickDirResultContract(),
			this::onFolderPicked);

	private String startPath() {
		String path = preferences.getString(PREF_LAST_PATH, null);
		if (path == null) {
			File dir = Environment.getExternalStorageDirectory();
			if (dir.canRead()) {
				path = dir.getAbsolutePath();
			}
		}
		return path;
	}

	/** Jogos (.jar/.jad/.kjx) direto na pasta escolhida no explorador do app, sem entrar nas subpastas. */
	private void onFolderPicked(Uri dirUri) {
		if (dirUri == null || dirUri.getPath() == null || !isAdded()) {
			return;
		}
		preferences.edit().putString(Constants.PREF_LAST_PATH, FilteredFilePickerFragment.getLastPath()).apply();
		File dir = new File(dirUri.getPath());
		File[] files = dir.listFiles();
		if (files == null) {
			// sem acesso a pasta: normalmente falta "Acesso a todos os arquivos"
			Toast.makeText(requireContext(), "Sem acesso à pasta " + dir.getPath()
					+ ". Ative \"Acesso a todos os arquivos\" para o Apexme.", Toast.LENGTH_LONG).show();
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
					&& !Environment.isExternalStorageManager()) {
				try {
					startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
							Uri.parse("package:" + requireContext().getPackageName())));
				} catch (Exception ignored) {
				}
			}
			return;
		}
		java.util.List<Uri> picked = new java.util.ArrayList<>();
		if (files != null) {
			java.util.Arrays.sort(files);
			for (File f : files) {
				if (f.isFile()) {
					picked.add(Uri.fromFile(f));
				}
			}
		}
		onFilesPicked(picked);
	}

	private static void scanFolder(android.content.Context c, Uri tree, java.util.List<String> names,
								   java.util.List<Uri> uris) {
		android.content.ContentResolver cr = c.getContentResolver();
		Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
				tree, android.provider.DocumentsContract.getTreeDocumentId(tree));
		java.util.List<String> jarNames = new java.util.ArrayList<>();
		java.util.List<Uri> jarUris = new java.util.ArrayList<>();
		java.util.HashSet<String> jads = new java.util.HashSet<>();
		try (android.database.Cursor cur = cr.query(children, new String[]{
				android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
				android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
			while (cur != null && cur.moveToNext()) {
				String mime = cur.getString(2);
				String name = cur.getString(1);
				if (name == null || android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
					continue; // nao entra em subpastas
				}
				String low = name.toLowerCase(java.util.Locale.ROOT);
				if (low.endsWith(".jad")) {
					jads.add(low.substring(0, low.length() - 4));
				} else if (low.endsWith(".jar")) {
					jarNames.add(name);
					jarUris.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)));
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "scan folder", e);
		}
		for (int i = 0; i < jarNames.size(); i++) {
			String name = jarNames.get(i);
			String base = name.substring(0, name.length() - 4).toLowerCase(java.util.Locale.ROOT);
			if (jads.contains(base) || isMidlet(cr, jarUris.get(i))) {
				names.add(name);
				uris.add(jarUris.get(i));
			}
		}
	}

	/** Um .jar compativel tem "MIDlet-1" no META-INF/MANIFEST.MF. */
	private static boolean isMidlet(android.content.ContentResolver cr, Uri uri) {
		try (java.io.InputStream in = cr.openInputStream(uri)) {
			if (in == null) {
				return false;
			}
			java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(in);
			java.util.zip.ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				if ("META-INF/MANIFEST.MF".equalsIgnoreCase(e.getName())) {
					java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
					byte[] buf = new byte[4096];
					int n;
					while ((n = zip.read(buf)) > 0 && out.size() < 64 * 1024) {
						out.write(buf, 0, n);
					}
					return out.toString("UTF-8").contains("MIDlet-1");
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private void showFolderResult(java.util.List<String> names, java.util.List<Uri> uris) {
		if (!isAdded()) {
			return;
		}
		if (uris.isEmpty()) {
			Toast.makeText(requireContext(), R.string.apex_folder_none, Toast.LENGTH_LONG).show();
			return;
		}
		askProfileAndConvert(uris);
	}

	/** Arquivos escolhidos no explorador: so .jar/.jad/.kjx (jad ignorado se o jar tambem foi escolhido). */
	private void onFilesPicked(java.util.List<Uri> picked) {
		if (!isAdded()) {
			return;
		}
		if (picked == null || picked.isEmpty()) {
			Toast.makeText(requireContext(), R.string.apex_folder_none, Toast.LENGTH_LONG).show();
			return;
		}
		preferences.edit().putString(Constants.PREF_LAST_PATH, FilteredFilePickerFragment.getLastPath()).apply();
		android.content.ContentResolver cr = requireContext().getContentResolver();
		java.util.List<Uri> uris = new java.util.ArrayList<>();
		java.util.List<String> bases = new java.util.ArrayList<>();
		java.util.List<Boolean> isJad = new java.util.ArrayList<>();
		for (Uri u : picked) {
			String name = null;
			try (android.database.Cursor c = cr.query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
					null, null, null)) {
				if (c != null && c.moveToFirst()) {
					name = c.getString(0);
				}
			} catch (Exception ignored) {
			}
			if (name == null) {
				name = String.valueOf(u.getLastPathSegment());
			}
			String low = name.toLowerCase(java.util.Locale.ROOT);
			if (low.endsWith(".jar") || low.endsWith(".jad") || low.endsWith(".kjx")) {
				uris.add(u);
				bases.add(low.substring(0, low.length() - 4));
				isJad.add(low.endsWith(".jad"));
			}
		}
		java.util.List<Uri> result = new java.util.ArrayList<>();
		for (int i = 0; i < uris.size(); i++) {
			boolean dup = false;
			if (isJad.get(i)) {
				for (int j = 0; j < uris.size(); j++) {
					if (j != i && !isJad.get(j) && bases.get(j).equals(bases.get(i))) {
						dup = true;
						break;
					}
				}
			}
			if (!dup) {
				result.add(uris.get(i));
			}
		}
		if (result.isEmpty()) {
			Toast.makeText(requireContext(), R.string.apex_folder_none, Toast.LENGTH_LONG).show();
			return;
		}
		askProfileAndConvert(result);
	}

	/** Unica pergunta: qual perfil usar em todos os jogos. Depois converte tudo sem mais janelas. */
	private void askProfileAndConvert(java.util.List<Uri> uris) {
		java.util.ArrayList<ru.playsoftware.j2meloader.config.Profile> profiles =
				ru.playsoftware.j2meloader.config.ProfilesManager.getProfiles();
		java.util.Collections.sort(profiles);
		String[] items = new String[profiles.size() + 1];
		items[0] = getString(R.string.apex_no_profile);
		String def = preferences.getString(Constants.PREF_DEFAULT_PROFILE, null);
		final int[] sel = {0};
		for (int i = 0; i < profiles.size(); i++) {
			items[i + 1] = profiles.get(i).getName();
			if (def != null && def.equals(items[i + 1])) {
				sel[0] = i + 1;
			}
		}
		new AlertDialog.Builder(requireActivity())
				.setTitle(getString(R.string.apex_convert_title, uris.size()))
				.setSingleChoiceItems(items, sel[0], (d, which) -> sel[0] = which)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(R.string.install, (d, w) -> convertAll(uris,
						sel[0] == 0 ? null : profiles.get(sel[0] - 1)))
				.show();
	}

	private void convertAll(java.util.List<Uri> uris, ru.playsoftware.j2meloader.config.Profile profile) {
		Activity activity = requireActivity();
		AlertDialog progress = new AlertDialog.Builder(activity)
				.setTitle(R.string.apex_converting_title)
				.setMessage("…")
				.setCancelable(false)
				.create();
		progress.show();
		ru.woesss.j2me.installer.BatchInstaller.run(activity.getApplication(), appRepository, uris, profile,
				new ru.woesss.j2me.installer.BatchInstaller.Listener() {
					@Override
					public void onProgress(int index, int total, String name) {
						activity.runOnUiThread(() -> progress.setMessage(
								activity.getString(R.string.apex_converting, index, total, name)));
					}

					@Override
					public void onDone(int installed, int skipped, int failed) {
						activity.runOnUiThread(() -> {
							progress.dismiss();
							Toast.makeText(activity, activity.getString(R.string.apex_convert_done, installed, skipped, failed),
									Toast.LENGTH_LONG).show();
						});
					}
				});
	}

	private void installNext() {
		Uri next = installQueue.poll();
		if (next != null && isAdded()) {
			InstallerDialog.newInstance(next).show(getParentFragmentManager(), "installer");
		}
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
		binding.folderActionButton.setBackgroundTintList(new ColorStateList(
				new int[][]{{android.R.attr.state_pressed}, {}},
				new int[]{ApexTheme.pressedColor(requireContext()), ApexTheme.outlineColor(requireContext())}));
		binding.folderActionButton.setOnClickListener(v -> {
			try {
				folderPicker.launch(startPath());
			} catch (ActivityNotFoundException e) {
				Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
			}
		});
		getParentFragmentManager().setFragmentResultListener("installer_done", getViewLifecycleOwner(),
				(key, result) -> installNext());
		binding.list.setAdapter(adapter);
		binding.list.setOnItemClickListener((parent, v, position, id) -> onListItemClick(position));
		binding.floatingActionButton.setOnClickListener(v -> {
			try {
				openFileLauncher.launch(startPath());
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
		ApexTheme.applyFont(editText);
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
		if (item.getItemId() == R.id.action_settings) {
			startActivity(new Intent(activity, SettingsActivity.class));
			return true;
		} else if (itemId == R.id.action_exit_app) {
			activity.finish();
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
