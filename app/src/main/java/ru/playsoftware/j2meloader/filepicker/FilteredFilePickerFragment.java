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

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nononsenseapps.filepicker.FilePickerFragment;
import com.nononsenseapps.filepicker.LogicHandler;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.theme.ApexCardBackground;
import ru.playsoftware.j2meloader.theme.ApexTheme;

public class FilteredFilePickerFragment extends FilePickerFragment {
	private static final List<String> extList = Arrays.asList(".jad", ".jar", ".kjx");
	private static final Stack<File> history = new Stack<>();
	private static File currentDir = Environment.getExternalStorageDirectory();

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		styleHeader(view);
	}

	/**
	 * Faixa do caminho no topo: cartao com altura fixa, no estilo das docks. Antes era a barra preta da
	 * biblioteca, que ficava baixa demais e cortava o texto do caminho.
	 */
	private void styleHeader(View root) {
		Context c = root.getContext();
		float d = c.getResources().getDisplayMetrics().density;
		int tbId = c.getResources().getIdentifier("nnf_picker_toolbar", "id", c.getPackageName());
		int dirId = c.getResources().getIdentifier("nnf_current_dir", "id", c.getPackageName());
		View tb = tbId != 0 ? root.findViewById(tbId) : null;
		View dir = dirId != 0 ? root.findViewById(dirId) : null;
		if (tb != null) {
			int h = Math.round(48 * d);
			int pad = Math.round(14 * d);
			tb.setBackground(new ApexCardBackground());
			tb.setMinimumHeight(h);
			tb.setPadding(pad, 0, pad, 0);
			ViewGroup.LayoutParams lp = tb.getLayoutParams();
			if (lp != null) {
				lp.height = h;
				if (lp instanceof ViewGroup.MarginLayoutParams) {
					int side = Math.round(16 * d);
					((ViewGroup.MarginLayoutParams) lp).setMargins(side, Math.round(12 * d), side, Math.round(8 * d));
				}
				tb.setLayoutParams(lp);
			}
		}
		if (dir instanceof TextView) {
			TextView t = (TextView) dir;
			t.setTextColor(ApexTheme.textColor(c));
			t.setGravity(Gravity.CENTER_VERTICAL);
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater li = getLayoutInflater();
		switch (viewType) {
			case LogicHandler.VIEWTYPE_HEADER:
				return new HeaderViewHolder(li.inflate(R.layout.listitem_dir, parent, false));
			case LogicHandler.VIEWTYPE_CHECKABLE:
				return new CheckableViewHolder(li.inflate(R.layout.listitem_checkable, parent, false));
			case LogicHandler.VIEWTYPE_DIR:
			default:
				return new DirViewHolder(li.inflate(R.layout.listitem_dir, parent, false));
		}
	}

	private String getExtension(@NonNull File file) {
		String path = file.getPath();
		int i = path.lastIndexOf(".");
		if (i < 0) {
			return null;
		} else {
			return path.substring(i);
		}
	}

	@Override
	protected boolean isItemVisible(final File file) {
		if (!isDir(file) && (mode == MODE_FILE || mode == MODE_FILE_AND_DIR)) {
			String ext = getExtension(file);
			return ext != null && extList.contains(ext.toLowerCase());
		}
		return isDir(file);
	}

	@Override
	public void goToDir(@NonNull File file) {
		history.add(currentDir);
		currentDir = file;
		super.goToDir(file);
	}

	public static String getLastPath() {
		return currentDir.getPath();
	}

	public boolean isBackTop() {
		return history.empty();
	}

	public void goBack() {
		File last = history.pop();
		currentDir = last;
		super.goToDir(last);
	}

	@Override
	public void onBindHeaderViewHolder(@NonNull HeaderViewHolder viewHolder) {
		if (compareFiles(currentDir, getRoot()) != 0) {
			viewHolder.itemView.setEnabled(true);
			super.onBindHeaderViewHolder(viewHolder);
		} else {
			viewHolder.itemView.setEnabled(false);
		}
	}

	/** Android 11+: o acesso vem de "Acesso a todos os arquivos", nao da permissao antiga de leitura/escrita. */
	@Override
	protected boolean hasPermission(@NonNull File path) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			return Environment.isExternalStorageManager();
		}
		return super.hasPermission(path);
	}

	@Override
	protected void handlePermission(@NonNull File path) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			android.content.Context c = requireContext();
			android.widget.Toast.makeText(c, "Ative \"Acesso a todos os arquivos\" para o Apexme e abra o explorador de novo",
					android.widget.Toast.LENGTH_LONG).show();
			try {
				startActivity(new android.content.Intent(
						android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
						android.net.Uri.parse("package:" + c.getPackageName())));
			} catch (Exception e) {
				try {
					startActivity(new android.content.Intent(
							android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
				} catch (Exception ignored) {
				}
			}
			return;
		}
		super.handlePermission(path);
	}
}
