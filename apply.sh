#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(pwd)"
APP="$ROOT/app/src/main/java/ru/woesss/j2me/installer/AppInstaller.java"
DIALOG="$ROOT/app/src/main/java/ru/woesss/j2me/installer/InstallerDialog.java"
HELPER="$ROOT/app/src/main/java/ru/woesss/j2me/installer/GameFingerprint.java"

[ -f "$APP" ] && [ -f "$DIALOG" ] || { echo "Execute dentro da pasta Apexme-."; exit 1; }
command -v python3 >/dev/null || { echo "Instale Python: pkg install python"; exit 1; }

cp "$APP" "$APP.bak"
cp "$DIALOG" "$DIALOG.bak"
cp "$(dirname "$0")/GameFingerprint.java" "$HELPER"

python3 - "$APP" "$DIALOG" <<'PY'
from pathlib import Path
import sys
app_path, dialog_path = map(Path, sys.argv[1:])
app = app_path.read_text()
dialog = dialog_path.read_text()

old_status = '\tstatic final int STATUS_SUCCESS = 5;'
new_status = old_status + '\n\tstatic final int STATUS_DUPLICATE_EXACT = 6;\n\tstatic final int STATUS_DUPLICATE_TITLE = 7;'
if 'STATUS_DUPLICATE_EXACT' not in app:
    if old_status not in app: raise SystemExit('Não encontrei os códigos de status em AppInstaller.java')
    app = app.replace(old_status, new_status, 1)

old_check = '''\tprivate int checkDescriptor() {
\t\t// Remove invalid characters from app path
\t\tString name = newDesc.getName();
\t\tString vendor = newDesc.getVendor();
\t\tcurrentApp = appRepository.get(name, vendor);
\t\tif (currentApp == null) {
\t\t\tgeneratePathName(name.replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "").trim());
\t\t\treturn STATUS_NEW;
\t\t}
\t\tappDirName = currentApp.getPath();
\t\ttargetDir = new File(Config.getAppDir(), appDirName);
\t\treturn newDesc.compareVersion(currentApp.getVersion());
\t}'''
new_check = '''\tprivate int checkDescriptor() {
\t\tString name = newDesc.getName();
\t\ttry {
\t\t\tGameFingerprint.Match match = GameFingerprint.findMatch(srcJar, name, appRepository);
\t\t\tif (match != null) {
\t\t\t\tcurrentApp = match.app;
\t\t\t\tappDirName = currentApp.getPath();
\t\t\t\ttargetDir = new File(Config.getAppDir(), appDirName);
\t\t\t\treturn match.identical ? STATUS_DUPLICATE_EXACT : STATUS_DUPLICATE_TITLE;
\t\t\t}
\t\t} catch (Exception e) {
\t\t\tLog.w(TAG, "Game duplicate scan failed; continuing with normal installer", e);
\t\t}
\t\t// Preserve the original MIDlet name/vendor version check as a fallback.
\t\tcurrentApp = appRepository.get(name, newDesc.getVendor());
\t\tif (currentApp == null) {
\t\t\tgeneratePathName(name.replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "").trim());
\t\t\treturn STATUS_NEW;
\t\t}
\t\tappDirName = currentApp.getPath();
\t\ttargetDir = new File(Config.getAppDir(), appDirName);
\t\treturn newDesc.compareVersion(currentApp.getVersion());
\t}'''
if 'GameFingerprint.findMatch' not in app:
    if old_check not in app: raise SystemExit('Não encontrei checkDescriptor() esperado em AppInstaller.java')
    app = app.replace(old_check, new_check, 1)

needle = '''\t\t\tcase AppInstaller.STATUS_EQUAL:
\t\t\t\tmessage = new SpannableStringBuilder(getString(R.string.reinstall));'''
insert = '''\t\t\tcase AppInstaller.STATUS_DUPLICATE_EXACT: {
\t\t\t\tAppItem existing = installer.getExistsApp();
\t\t\t\tmessage = new SpannableStringBuilder("Este jogo já está instalado. A instalação existente foi mantida; nenhum arquivo foi substituído.");
\t\t\t\tbtnOk.setText(R.string.START_CMD);
\t\t\t\tbtnOk.setOnClickListener(v -> {
\t\t\t\t\tinstaller.clearCache();
\t\t\t\t\tinstaller.deleteTemp();
\t\t\t\t\tConfig.startApp(v.getContext(), existing.getTitle(), existing.getPathExt(), false);
\t\t\t\t\tdismiss();
\t\t\t\t});
\t\t\t\tbtnClose.setText(R.string.close);
\t\t\t\talertConfirm(message, v -> {
\t\t\t\t\tConfig.startApp(v.getContext(), existing.getTitle(), existing.getPathExt(), false);
\t\t\t\t\tdismiss();
\t\t\t\t});
\t\t\t\treturn;
\t\t\t}
\t\t\tcase AppInstaller.STATUS_DUPLICATE_TITLE:
\t\t\t\tmessage = new SpannableStringBuilder("Já existe um jogo com o mesmo título normalizado: ")
\t\t\t\t\t\t.append(installer.getExistsApp().getTitle())
\t\t\t\t\t\t.append(". Se continuar, a instalação existente será substituída. Deseja realmente continuar?");
\t\t\t\tbreak;
\t\t\tcase AppInstaller.STATUS_EQUAL:
\t\t\t\tmessage = new SpannableStringBuilder(getString(R.string.reinstall));'''
if 'STATUS_DUPLICATE_TITLE' not in dialog:
    if needle not in dialog: raise SystemExit('Não encontrei o switch STATUS_EQUAL esperado em InstallerDialog.java')
    dialog = dialog.replace(needle, insert, 1)

app_path.write_text(app)
dialog_path.write_text(dialog)
PY

echo "Patch aplicado. Confira com: git diff"
echo "Backups: AppInstaller.java.bak e InstallerDialog.java.bak"
