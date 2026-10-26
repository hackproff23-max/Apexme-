package ru.playsoftware.j2meloader.base;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatViewInflater;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatCheckedTextView;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatTextView;

import ru.playsoftware.j2meloader.applist.OutlinedTextView;
import ru.playsoftware.j2meloader.theme.ApexTheme;

/**
 * Todo texto inflado por layout (TextView com contorno, botoes, campos...) usa a fonte do app
 * (importada ou padrao), sempre em negrito e sem italico.
 */
public class ApexmeViewInflater extends AppCompatViewInflater {
	@NonNull
	@Override
	protected AppCompatTextView createTextView(Context context, AttributeSet attrs) {
		return new OutlinedTextView(context, attrs);
	}

	@NonNull
	@Override
	protected AppCompatButton createButton(Context context, AttributeSet attrs) {
		AppCompatButton v = super.createButton(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatEditText createEditText(Context context, AttributeSet attrs) {
		AppCompatEditText v = super.createEditText(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatCheckBox createCheckBox(Context context, AttributeSet attrs) {
		AppCompatCheckBox v = super.createCheckBox(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatRadioButton createRadioButton(Context context, AttributeSet attrs) {
		AppCompatRadioButton v = super.createRadioButton(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatCheckedTextView createCheckedTextView(Context context, AttributeSet attrs) {
		AppCompatCheckedTextView v = super.createCheckedTextView(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatAutoCompleteTextView createAutoCompleteTextView(Context context, AttributeSet attrs) {
		AppCompatAutoCompleteTextView v = super.createAutoCompleteTextView(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}

	@NonNull
	@Override
	protected AppCompatMultiAutoCompleteTextView createMultiAutoCompleteTextView(Context context, AttributeSet attrs) {
		AppCompatMultiAutoCompleteTextView v = super.createMultiAutoCompleteTextView(context, attrs);
		ApexTheme.applyFont(v);
		return v;
	}
}
