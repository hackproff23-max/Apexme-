package ru.playsoftware.j2meloader.base;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatViewInflater;
import androidx.appcompat.widget.AppCompatTextView;

import ru.playsoftware.j2meloader.applist.OutlinedTextView;

/** Faz todo TextView inflado por layout virar um OutlinedTextView (texto com contorno). */
public class ApexmeViewInflater extends AppCompatViewInflater {
	@NonNull
	@Override
	protected AppCompatTextView createTextView(Context context, AttributeSet attrs) {
		return new OutlinedTextView(context, attrs);
	}
}
