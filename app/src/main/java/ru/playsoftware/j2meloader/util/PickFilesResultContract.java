package ru.playsoftware.j2meloader.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nononsenseapps.filepicker.FilePickerActivity;

import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerActivity;

/** Explorador do proprio app, escolhendo varios jogos (.jar/.jad/.kjx) de uma vez. */
public class PickFilesResultContract extends ActivityResultContract<String, List<Uri>> {
	@NonNull
	@Override
	public Intent createIntent(@NonNull Context context, String input) {
		Intent i = new Intent(context, FilteredFilePickerActivity.class);
		i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, true);
		i.putExtra(FilePickerActivity.EXTRA_SINGLE_CLICK, false);
		i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
		i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
		i.putExtra(FilePickerActivity.EXTRA_START_PATH, input);
		return i;
	}

	@Override
	public List<Uri> parseResult(int resultCode, @Nullable Intent intent) {
		List<Uri> list = new ArrayList<>();
		if (resultCode == Activity.RESULT_OK && intent != null) {
			ClipData clip = intent.getClipData();
			if (clip != null) {
				for (int i = 0; i < clip.getItemCount(); i++) {
					Uri u = clip.getItemAt(i).getUri();
					if (u != null) {
						list.add(u);
					}
				}
			}
			if (list.isEmpty() && intent.getData() != null) {
				list.add(intent.getData());
			}
		}
		return list;
	}
}
