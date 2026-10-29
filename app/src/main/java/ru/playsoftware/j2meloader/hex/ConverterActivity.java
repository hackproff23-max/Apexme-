package ru.playsoftware.j2meloader.hex;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Locale;

import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/** Conversor: numeros (decimal, hex, binario, octal) e bytes (texto, hex, Base64). */
public class ConverterActivity extends BaseActivity {
	private static final String[] CHARSETS = {"UTF-8", "ISO-8859-1", "US-ASCII", "UTF-16BE", "UTF-16LE"};

	private EditText dec;
	private EditText hexNum;
	private EditText bin;
	private EditText oct;
	private EditText txt;
	private EditText hexBytes;
	private EditText b64;
	private TextView info;
	private Spinner charset;
	private boolean busy;
	private boolean lastWasText = true;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		float d = getResources().getDisplayMetrics().density;
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding((int) (16 * d), (int) (8 * d), (int) (16 * d), (int) (16 * d));

		root.addView(section("Números"));
		dec = field(root, "Decimal");
		hexNum = field(root, "Hexadecimal");
		bin = field(root, "Binário");
		oct = field(root, "Octal");

		root.addView(section("Bytes e texto"));
		charset = new Spinner(this);
		ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CHARSETS);
		ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		charset.setAdapter(ad);
		root.addView(label("Codificação do texto"));
		root.addView(charset);
		txt = field(root, "Texto");
		hexBytes = field(root, "Bytes em hex (ex: 48 65 6C 6C 6F)");
		b64 = field(root, "Base64");

		info = new TextView(this);
		info.setTextColor(ApexTheme.textColor(this));
		info.setTypeface(android.graphics.Typeface.MONOSPACE);
		info.setTextSize(12);
		info.setPadding(0, (int) (10 * d), 0, 0);
		root.addView(info);

		ScrollView sv = new ScrollView(this);
		sv.addView(root);
		setContentView(sv);
		ActionBar bar = getSupportActionBar();
		if (bar != null) {
			bar.setDisplayHomeAsUpEnabled(true);
		}
		setTitle("Conversor");

		watch(dec, () -> fromNumber(dec, 10));
		watch(hexNum, () -> fromNumber(hexNum, 16));
		watch(bin, () -> fromNumber(bin, 2));
		watch(oct, () -> fromNumber(oct, 8));
		watch(txt, () -> {
			lastWasText = true;
			fromText();
		});
		watch(hexBytes, () -> {
			lastWasText = false;
			byte[] b = HexUtil.parseHex(hexBytes.getText().toString());
			if (b == null) {
				hexBytes.setError(hexBytes.getText().length() == 0 ? null : "Hex inválido");
				if (hexBytes.getText().length() == 0) {
					fromBytes(new byte[0], hexBytes);
				}
				return;
			}
			hexBytes.setError(null);
			fromBytes(b, hexBytes);
		});
		watch(b64, () -> {
			lastWasText = false;
			String s = b64.getText().toString().trim();
			if (s.isEmpty()) {
				fromBytes(new byte[0], b64);
				return;
			}
			try {
				byte[] b = Base64.decode(s, Base64.DEFAULT);
				b64.setError(null);
				fromBytes(b, b64);
			} catch (IllegalArgumentException e) {
				b64.setError("Base64 inválido");
			}
		});
		charset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
				if (busy) {
					return;
				}
				busy = true;
				try {
					if (lastWasText) {
						fromText();
					} else {
						byte[] b = HexUtil.parseHex(hexBytes.getText().toString());
						if (b != null) {
							fromBytes(b, hexBytes);
						}
					}
				} finally {
					busy = false;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> p) {
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private TextView label(String s) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(13);
		t.setTextColor(ApexTheme.outlineColor(this));
		t.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
		return t;
	}

	private TextView section(String s) {
		TextView t = label(s);
		t.setTextSize(16);
		t.setPadding(0, (int) (14 * getResources().getDisplayMetrics().density), 0, 0);
		return t;
	}

	private EditText field(LinearLayout root, String title) {
		root.addView(label(title));
		EditText e = new EditText(this);
		e.setTextColor(ApexTheme.textColor(this));
		e.setTypeface(android.graphics.Typeface.MONOSPACE);
		e.setTextSize(15);
		e.setSingleLine(false);
		e.setHorizontallyScrolling(false);
		root.addView(e, new LinearLayout.LayoutParams(-1, -2));
		return e;
	}

	private void watch(EditText e, Runnable r) {
		e.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			@Override
			public void onTextChanged(CharSequence s, int a, int b, int c) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (!busy) {
					busy = true;
					try {
						r.run();
					} finally {
						busy = false;
					}
				}
			}
		});
	}

	private Charset cs() {
		return Charset.forName(CHARSETS[charset.getSelectedItemPosition()]);
	}

	private void setIf(EditText e, EditText source, String v) {
		if (e != source) {
			e.setText(v);
			e.setError(null);
		}
	}

	private void fromNumber(EditText src, int radix) {
		String s = src.getText().toString().trim().replace("_", "").replace(" ", "");
		if (radix == 16) {
			s = s.replace("0x", "").replace("0X", "");
		} else if (radix == 2) {
			s = s.replace("0b", "").replace("0B", "");
		}
		if (s.isEmpty() || s.equals("-")) {
			src.setError(null);
			for (EditText e : new EditText[]{dec, hexNum, bin, oct}) {
				if (e != src) {
					e.setText("");
					e.setError(null);
				}
			}
			info.setText("");
			return;
		}
		BigInteger v;
		try {
			v = new BigInteger(s, radix);
		} catch (NumberFormatException e) {
			src.setError("Número inválido");
			return;
		}
		src.setError(null);
		setIf(dec, src, v.toString(10));
		setIf(hexNum, src, v.toString(16).toUpperCase(Locale.ROOT));
		setIf(bin, src, v.toString(2));
		setIf(oct, src, v.toString(8));
		info.setText("Bits: " + v.abs().bitLength() + (v.signum() < 0 ? "  (negativo)" : ""));
	}

	private void fromText() {
		byte[] b = txt.getText().toString().getBytes(cs());
		fromBytes(b, txt);
	}

	private void fromBytes(byte[] b, EditText src) {
		if (src != txt) {
			txt.setText(new String(b, cs()));
			txt.setError(null);
		}
		if (src != hexBytes) {
			hexBytes.setText(HexUtil.toHex(b, " "));
			hexBytes.setError(null);
		}
		if (src != b64) {
			b64.setText(Base64.encodeToString(b, Base64.NO_WRAP));
			b64.setError(null);
		}
		showInfo(b);
	}

	private void showInfo(byte[] b) {
		if (b.length == 0) {
			info.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(b.length).append(" bytes");
		int n = Math.min(8, b.length);
		BigInteger be = new BigInteger(1, java.util.Arrays.copyOf(b, n));
		byte[] rev = new byte[n];
		for (int i = 0; i < n; i++) {
			rev[i] = b[n - 1 - i];
		}
		BigInteger le = new BigInteger(1, rev);
		sb.append("\nInteiro (").append(n).append(" primeiros bytes) sem sinal:")
				.append("\n  big-endian    ").append(be)
				.append("\n  little-endian ").append(le);
		info.setText(sb);
	}
}
