package ru.playsoftware.j2meloader.hex;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Editor hexadecimal para os arquivos dos jogos. */
public class HexEditorActivity extends BaseActivity {
	private final HexBuffer buf = new HexBuffer();
	private HexView view;
	private TextView status;
	private File file;
	private Uri uri;
	private String name = "";
	private boolean pendingHigh;
	private byte[] lastFind;
	private Button btnUndo;
	private Button btnRedo;

	private final ActivityResultLauncher<Intent> openLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), r -> {
				Intent data = r.getData();
				if (r.getResultCode() == Activity.RESULT_OK && data != null && data.getData() != null) {
					Uri u = data.getData();
					try {
						getContentResolver().takePersistableUriPermission(u,
								Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					} catch (Exception ignored) {
					}
					confirmDiscard(() -> loadUri(u));
				}
			});
	private final ActivityResultLauncher<String> saveAsLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument(), u -> {
				if (u != null) {
					uri = u;
					file = null;
					name = queryName(u);
					writeOut();
				}
			});

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		float d = getResources().getDisplayMetrics().density;
		int textColor = ApexTheme.textColor(this);

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);

		view = new HexView(this);
		view.setBuffer(buf);
		view.setListener(i -> {
			pendingHigh = false;
			updateStatus();
		});
		root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1f));

		status = new TextView(this);
		status.setTextColor(textColor);
		status.setTextSize(12);
		status.setTypeface(android.graphics.Typeface.MONOSPACE);
		status.setPadding((int) (8 * d), (int) (4 * d), (int) (8 * d), (int) (4 * d));
		root.addView(status, new LinearLayout.LayoutParams(-1, -2));

		String digits = "0123456789ABCDEF";
		for (int row = 0; row < 2; row++) {
			LinearLayout line = new LinearLayout(this);
			for (int k = 0; k < 8; k++) {
				final int v = row * 8 + k;
				line.addView(button(String.valueOf(digits.charAt(v)), x -> typeNibble(v)),
						new LinearLayout.LayoutParams(0, -2, 1f));
			}
			root.addView(line, new LinearLayout.LayoutParams(-1, -2));
		}

		LinearLayout actions = new LinearLayout(this);
		actions.addView(button("◀", x -> move(-1)));
		actions.addView(button("▶", x -> move(1)));
		actions.addView(button("▲", x -> move(-view.getBytesPerRow())));
		actions.addView(button("▼", x -> move(view.getBytesPerRow())));
		btnUndo = button("Desfazer", x -> undo());
		btnRedo = button("Refazer", x -> redo());
		actions.addView(btnUndo);
		actions.addView(btnRedo);
		actions.addView(button("Inserir", x -> dialogInsert()));
		actions.addView(button("Apagar", x -> dialogDelete()));
		actions.addView(button("Texto", x -> dialogText()));
		HorizontalScrollView hs = new HorizontalScrollView(this);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(actions);
		root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

		setContentView(root);
		ActionBar bar = getSupportActionBar();
		if (bar != null) {
			bar.setDisplayHomeAsUpEnabled(true);
		}
		setTitle("Editor hex");
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				confirmDiscard(HexEditorActivity.this::finish);
			}
		});
		updateStatus();
	}

	private Button button(String label, View.OnClickListener l) {
		Button b = new Button(this);
		b.setText(label);
		b.setAllCaps(false);
		b.setMinWidth(0);
		b.setMinimumWidth(0);
		b.setTextSize(13);
		b.setOnClickListener(l);
		ApexTheme.applyFont(b);
		return b;
	}

	// ---------------- menu ----------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.hex_editor, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			confirmDiscard(this::finish);
		} else if (id == R.id.hex_open) {
			Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			i.addCategory(Intent.CATEGORY_OPENABLE);
			i.setType("*/*");
			i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
					| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			try {
				openLauncher.launch(i);
			} catch (Exception e) {
				toast(getString(R.string.error_no_picker));
			}
		} else if (id == R.id.hex_games) {
			confirmDiscard(() -> browse(new File(Config.getEmulatorDir())));
		} else if (id == R.id.hex_save) {
			save();
		} else if (id == R.id.hex_save_as) {
			saveAsLauncher.launch(name.isEmpty() ? "arquivo.bin" : name);
		} else if (id == R.id.hex_goto) {
			dialogGoto();
		} else if (id == R.id.hex_find) {
			dialogFind();
		} else if (id == R.id.hex_find_next) {
			findNext();
		} else if (id == R.id.hex_converter) {
			startActivity(new Intent(this, ConverterActivity.class));
		} else {
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	// ---------------- abrir ----------------
	private void browse(File dir) {
		File[] list = dir.listFiles();
		if (list == null) {
			toast("Não foi possível abrir a pasta");
			return;
		}
		Arrays.sort(list, (a, b) -> {
			if (a.isDirectory() != b.isDirectory()) {
				return a.isDirectory() ? -1 : 1;
			}
			return a.getName().compareToIgnoreCase(b.getName());
		});
		File parent = dir.getParentFile();
		boolean up = parent != null && parent.canRead();
		String[] items = new String[list.length + (up ? 1 : 0)];
		int o = 0;
		if (up) {
			items[o++] = "⬆ ..";
		}
		for (File f : list) {
			items[o++] = (f.isDirectory() ? "📁 " : "") + f.getName()
					+ (f.isDirectory() ? "" : "  (" + f.length() + " B)");
		}
		final boolean hasUp = up;
		new AlertDialog.Builder(this)
				.setTitle(dir.getPath())
				.setItems(items, (dlg, which) -> {
					if (hasUp && which == 0) {
						browse(parent);
						return;
					}
					File f = list[which - (hasUp ? 1 : 0)];
					if (f.isDirectory()) {
						browse(f);
					} else {
						loadFile(f);
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void loadFile(File f) {
		if (f.length() > HexBuffer.MAX_SIZE) {
			toast("Arquivo maior que 16 MB");
			return;
		}
		new Thread(() -> {
			try (InputStream in = new FileInputStream(f)) {
				byte[] b = readAll(in);
				runOnUiThread(() -> onLoaded(b, f.getName(), f, null));
			} catch (Exception e) {
				runOnUiThread(() -> toast("Erro ao abrir: " + e.getMessage()));
			}
		}).start();
	}

	private void loadUri(Uri u) {
		new Thread(() -> {
			try (InputStream in = getContentResolver().openInputStream(u)) {
				if (in == null) {
					throw new IOException("sem acesso");
				}
				byte[] b = readAll(in);
				String n = queryName(u);
				runOnUiThread(() -> onLoaded(b, n, null, u));
			} catch (Exception e) {
				runOnUiThread(() -> toast("Erro ao abrir: " + e.getMessage()));
			}
		}).start();
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] tmp = new byte[16 * 1024];
		int n;
		while ((n = in.read(tmp)) > 0) {
			out.write(tmp, 0, n);
			if (out.size() > HexBuffer.MAX_SIZE) {
				throw new IOException("arquivo maior que 16 MB");
			}
		}
		return out.toByteArray();
	}

	private String queryName(Uri u) {
		try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
			if (c != null && c.moveToFirst()) {
				int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (i >= 0 && c.getString(i) != null) {
					return c.getString(i);
				}
			}
		} catch (Exception ignored) {
		}
		String last = u.getLastPathSegment();
		return last == null ? "arquivo" : last;
	}

	private void onLoaded(byte[] bytes, String n, File f, Uri u) {
		buf.load(bytes);
		file = f;
		uri = u;
		name = n;
		lastFind = null;
		pendingHigh = false;
		view.setBuffer(buf);
		view.setHighlight(-1, 0);
		setTitle(n);
		updateStatus();
	}

	// ---------------- salvar ----------------
	private void save() {
		if (file == null && uri == null) {
			saveAsLauncher.launch(name.isEmpty() ? "arquivo.bin" : name);
			return;
		}
		writeOut();
	}

	private void writeOut() {
		final byte[] snapshot = buf.toArray();
		final File f = file;
		final Uri u = uri;
		new Thread(() -> {
			try {
				if (f != null) {
					File bak = new File(f.getPath() + ".bak");
					if (!bak.exists() && f.exists()) {
						copy(f, bak);
					}
					File tmp = new File(f.getPath() + ".tmp");
					try (OutputStream os = new FileOutputStream(tmp)) {
						os.write(snapshot);
					}
					if (!tmp.renameTo(f)) {
						try (OutputStream os = new FileOutputStream(f)) {
							os.write(snapshot);
						}
						//noinspection ResultOfMethodCallIgnored
						tmp.delete();
					}
				} else {
					try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
						if (os == null) {
							throw new IOException("sem acesso de escrita");
						}
						os.write(snapshot);
					}
				}
				runOnUiThread(() -> {
					buf.markSaved();
					toast("Salvo" + (f != null ? " (cópia original em " + f.getName() + ".bak)" : ""));
					updateStatus();
				});
			} catch (Exception e) {
				runOnUiThread(() -> toast("Erro ao salvar: " + e.getMessage() + ". Tente \"Salvar como\"."));
			}
		}).start();
		setTitle(name);
	}

	private static void copy(File from, File to) throws IOException {
		try (InputStream in = new FileInputStream(from); OutputStream os = new FileOutputStream(to)) {
			byte[] b = new byte[16 * 1024];
			int n;
			while ((n = in.read(b)) > 0) {
				os.write(b, 0, n);
			}
		}
	}

	private void confirmDiscard(Runnable then) {
		if (!buf.isDirty()) {
			then.run();
			return;
		}
		new AlertDialog.Builder(this)
				.setTitle("Alterações não salvas")
				.setMessage("Descartar as alterações em " + (name.isEmpty() ? "arquivo" : name) + "?")
				.setPositiveButton("Descartar", (dlg, w) -> then.run())
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	// ---------------- edicao ----------------
	private void typeNibble(int d) {
		if (buf.size() == 0) {
			return;
		}
		int i = view.getSelection();
		int old = buf.get(i);
		if (!pendingHigh) {
			buf.set(i, (d << 4) | (old & 0x0F));
			pendingHigh = true;
			changed();
		} else {
			buf.set(i, (old & 0xF0) | d);
			pendingHigh = false;
			changed();
			move(1);
		}
	}

	private void move(int delta) {
		if (buf.size() == 0) {
			return;
		}
		view.setSelection(view.getSelection() + delta);
	}

	private void undo() {
		int p = buf.undo();
		if (p >= 0) {
			pendingHigh = false;
			changed();
			view.setSelection(p);
		}
	}

	private void redo() {
		int p = buf.redo();
		if (p >= 0) {
			pendingHigh = false;
			changed();
			view.setSelection(p);
		}
	}

	private void changed() {
		view.refresh();
		updateStatus();
	}

	private void updateStatus() {
		StringBuilder sb = new StringBuilder();
		int size = buf.size();
		if (size == 0) {
			sb.append(name.isEmpty() ? "Abra um arquivo pelo menu (⋮)" : "Arquivo vazio");
		} else {
			int i = view.getSelection();
			int b = buf.get(i);
			sb.append("Offset ").append(HexUtil.hex(i, 6)).append(" (").append(i).append(")  Byte ")
					.append(HexUtil.hex(b, 2)).append(" = ").append(b).append(" = ").append(HexUtil.bin8(b));
			if (i + 1 < size) {
				int le = buf.get(i) | (buf.get(i + 1) << 8);
				int be = (buf.get(i) << 8) | buf.get(i + 1);
				sb.append("\n16 bits LE ").append(le).append("  BE ").append(be);
			}
			if (i + 3 < size) {
				long le = (buf.get(i) | (buf.get(i + 1) << 8) | (buf.get(i + 2) << 16)
						| ((long) buf.get(i + 3) << 24));
				long be = (((long) buf.get(i) << 24) | (buf.get(i + 1) << 16) | (buf.get(i + 2) << 8)
						| buf.get(i + 3));
				sb.append("  |  32 bits LE ").append(le).append("  BE ").append(be);
			}
			sb.append("\n").append(size).append(" bytes").append(buf.isDirty() ? "  •  modificado" : "");
		}
		status.setText(sb);
		btnUndo.setEnabled(buf.canUndo());
		btnRedo.setEnabled(buf.canRedo());
		setTitle((buf.isDirty() ? "* " : "") + (name.isEmpty() ? "Editor hex" : name));
	}

	// ---------------- dialogos ----------------
	private EditText input(String hint, String initial) {
		EditText e = new EditText(this);
		e.setHint(hint);
		e.setText(initial);
		e.setSingleLine(true);
		e.setTextColor(ApexTheme.textColor(this));
		ApexTheme.applyFont(e);
		return e;
	}

	private View wrap(View... views) {
		float d = getResources().getDisplayMetrics().density;
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), 0);
		for (View v : views) {
			l.addView(v, new LinearLayout.LayoutParams(-1, -2));
		}
		return l;
	}

	private void dialogGoto() {
		if (buf.size() == 0) {
			return;
		}
		EditText e = input("Offset em hex (ou d123 para decimal)", "");
		new AlertDialog.Builder(this)
				.setTitle("Ir para")
				.setView(wrap(e))
				.setPositiveButton(android.R.string.ok, (dlg, w) -> {
					String s = e.getText().toString().trim();
					try {
						long v;
						if (s.toLowerCase(Locale.ROOT).startsWith("d")) {
							v = Long.parseLong(s.substring(1).trim());
						} else {
							v = Long.parseLong(s.replace("0x", "").replace("0X", ""), 16);
						}
						view.setSelection((int) Math.min(v, buf.size() - 1));
					} catch (Exception ex) {
						toast("Offset inválido");
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void dialogFind() {
		if (buf.size() == 0) {
			return;
		}
		EditText e = input("Bytes em hex ou texto", "");
		CheckBox hex = new CheckBox(this);
		hex.setText("Procurar em hexadecimal (senão, texto)");
		hex.setChecked(true);
		hex.setTextColor(ApexTheme.textColor(this));
		new AlertDialog.Builder(this)
				.setTitle("Buscar")
				.setView(wrap(e, hex))
				.setPositiveButton("Buscar", (dlg, w) -> {
					String s = e.getText().toString();
					byte[] pat = hex.isChecked() ? HexUtil.parseHex(s) : s.getBytes(Charset.forName("UTF-8"));
					if (pat == null || pat.length == 0) {
						toast("Padrão inválido");
						return;
					}
					lastFind = pat;
					doFind(view.getSelection());
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void findNext() {
		if (lastFind == null) {
			dialogFind();
		} else {
			doFind(view.getSelection() + 1);
		}
	}

	private void doFind(int from) {
		int at = buf.find(lastFind, from);
		if (at < 0) {
			view.setHighlight(-1, 0);
			toast("Não encontrado");
			return;
		}
		view.setHighlight(at, lastFind.length);
		view.setSelection(at);
	}

	private void dialogInsert() {
		EditText e = input("Bytes em hex (ex: 00 FF 1A)", "00");
		new AlertDialog.Builder(this)
				.setTitle("Inserir bytes na posição")
				.setView(wrap(e))
				.setPositiveButton("Inserir", (dlg, w) -> {
					byte[] b = HexUtil.parseHex(e.getText().toString());
					if (b == null) {
						toast("Hex inválido");
						return;
					}
					int pos = buf.size() == 0 ? 0 : view.getSelection();
					if (!buf.insert(pos, b)) {
						toast("Passa do limite de 16 MB");
						return;
					}
					changed();
					view.setSelection(pos);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void dialogDelete() {
		if (buf.size() == 0) {
			return;
		}
		EditText e = input("Quantidade de bytes", "1");
		e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		new AlertDialog.Builder(this)
				.setTitle("Apagar bytes a partir da posição")
				.setView(wrap(e))
				.setPositiveButton("Apagar", (dlg, w) -> {
					try {
						int n = Integer.parseInt(e.getText().toString().trim());
						if (n <= 0) {
							return;
						}
						int pos = view.getSelection();
						buf.delete(pos, n);
						changed();
						view.setSelection(pos);
					} catch (NumberFormatException ex) {
						toast("Número inválido");
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void dialogText() {
		if (buf.size() == 0) {
			return;
		}
		EditText e = input("Texto", "");
		CheckBox ins = new CheckBox(this);
		ins.setText("Inserir (senão, sobrescreve)");
		ins.setTextColor(ApexTheme.textColor(this));
		CheckBox latin = new CheckBox(this);
		latin.setText("Codificar em ISO-8859-1 (senão, UTF-8)");
		latin.setTextColor(ApexTheme.textColor(this));
		new AlertDialog.Builder(this)
				.setTitle("Texto na posição")
				.setView(wrap(e, ins, latin))
				.setPositiveButton(android.R.string.ok, (dlg, w) -> {
					String s = e.getText().toString();
					if (s.isEmpty()) {
						return;
					}
					byte[] b = s.getBytes(Charset.forName(latin.isChecked() ? "ISO-8859-1" : "UTF-8"));
					int pos = view.getSelection();
					boolean ok = ins.isChecked() ? buf.insert(pos, b) : buf.overwrite(pos, b);
					if (!ok) {
						toast("Passa do limite de 16 MB");
						return;
					}
					changed();
					view.setSelection(pos);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void toast(String s) {
		Toast.makeText(this, s, Toast.LENGTH_LONG).show();
	}
}
