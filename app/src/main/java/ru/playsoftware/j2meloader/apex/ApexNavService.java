package ru.playsoftware.j2meloader.apex;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Servico de acessibilidade sem nenhuma leitura de tela: existe so para a dock poder abrir a
 * janela de tarefas recentes (o Android nao permite isso a apps comuns).
 */
public class ApexNavService extends AccessibilityService {
	private static volatile ApexNavService instance;

	@Override
	protected void onServiceConnected() {
		super.onServiceConnected();
		instance = this;
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
	}

	@Override
	public void onInterrupt() {
	}

	@Override
	public boolean onUnbind(Intent intent) {
		instance = null;
		return super.onUnbind(intent);
	}

	@Override
	public void onDestroy() {
		instance = null;
		super.onDestroy();
	}

	/** Abre as tarefas recentes. False se o servico ainda nao foi ativado nos ajustes. */
	public static boolean recents() {
		ApexNavService s = instance;
		return s != null && s.performGlobalAction(GLOBAL_ACTION_RECENTS);
	}
}
