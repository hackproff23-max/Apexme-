package ru.playsoftware.j2meloader.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Desenha cada linha da lista de configuracoes como um cartao arredondado (por tras do conteudo). */
public final class ApexCardDecoration extends RecyclerView.ItemDecoration {
	private final ApexCardBackground card = new ApexCardBackground();
	private final int horizontal;
	private final int vertical;

	public ApexCardDecoration(Context c) {
		float d = c.getResources().getDisplayMetrics().density;
		horizontal = Math.round(d * 10f);
		vertical = Math.round(d * 4f);
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
							   @NonNull RecyclerView.State state) {
		outRect.set(horizontal, vertical, horizontal, vertical);
	}

	@Override
	public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			int l = Math.round(child.getLeft() + child.getTranslationX());
			int t = Math.round(child.getTop() + child.getTranslationY());
			card.setBounds(l, t, l + child.getWidth(), t + child.getHeight());
			card.draw(canvas);
		}
	}
}
