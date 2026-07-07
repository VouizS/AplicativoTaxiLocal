#!/usr/bin/env bash
set -euo pipefail

echo "=============================================="
echo " Via Prime v0.2.0 - Split Apps Premium Base"
echo " Cliente + Motoristas + ícone coroa ajustado"
echo "=============================================="

ROOT="$(pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$ROOT/app"

if [ ! -d "$APP_DIR" ]; then
  echo "ERRO: não encontrei a pasta app/."
  echo "Entre na raiz do projeto Android antes de rodar este script."
  echo "Exemplo: cd /sdcard/Download/TaxiConnect"
  exit 1
fi

if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "ERRO: Python não encontrado."
  echo "No Termux, instale com: pkg install python -y"
  exit 1
fi

BACKUP_DIR="$ROOT/backup_v020_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"
echo "Backup em: $BACKUP_DIR"

[ -f "$APP_DIR/build.gradle" ] && cp "$APP_DIR/build.gradle" "$BACKUP_DIR/app_build.gradle.bak"
[ -f "$APP_DIR/build.gradle.kts" ] && cp "$APP_DIR/build.gradle.kts" "$BACKUP_DIR/app_build.gradle.kts.bak"
[ -f "$APP_DIR/src/main/AndroidManifest.xml" ] && cp "$APP_DIR/src/main/AndroidManifest.xml" "$BACKUP_DIR/AndroidManifest.xml.bak"

echo "Copiando ícone premium..."
mkdir -p "$APP_DIR/src/main/res"
cp -R "$SCRIPT_DIR/icons/res/." "$APP_DIR/src/main/res/"

echo "Aplicando patch Android/Gradle/MainActivity..."
"$PY" - "$ROOT" "$SCRIPT_DIR" <<'PYCODE'
import os, re, sys
from pathlib import Path

root = Path(sys.argv[1])
script_dir = Path(sys.argv[2])
app = root / "app"
manifest = app / "src/main/AndroidManifest.xml"

gradle = app / "build.gradle.kts"
is_kts = True
if not gradle.exists():
    gradle = app / "build.gradle"
    is_kts = False

if not gradle.exists():
    raise SystemExit("ERRO: não encontrei app/build.gradle nem app/build.gradle.kts")

text = gradle.read_text(encoding="utf-8")

def find_android_block(s):
    m = re.search(r'\bandroid\s*\{', s)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while i < len(s):
        ch = s[i]
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return m.start(), i
        i += 1
    return None

block = find_android_block(text)
if block is None:
    raise SystemExit("ERRO: não consegui localizar o bloco android { } no Gradle.")

start, end = block

if "Via Prime v0.2.0 split apps" not in text:
    if is_kts:
        flavor_block = '''
    // Via Prime v0.2.0 split apps
    flavorDimensions += "role"

    productFlavors {
        create("cliente") {
            dimension = "role"
            applicationId = "com.swviaprime.cliente"
        }
        create("motorista") {
            dimension = "role"
            applicationId = "com.swviaprime.motoristas"
        }
    }
'''
    else:
        flavor_block = '''
    // Via Prime v0.2.0 split apps
    flavorDimensions "role"

    productFlavors {
        cliente {
            dimension "role"
            applicationId "com.swviaprime.cliente"
        }
        motorista {
            dimension "role"
            applicationId "com.swviaprime.motoristas"
        }
    }
'''
    text = text[:end] + flavor_block + text[end:]
    print("OK: productFlavors cliente/motorista adicionados.")
else:
    print("OK: bloco v0.2.0 já existia no Gradle.")

if is_kts:
    text = re.sub(r'versionName\s*=\s*["\'][^"\']+["\']', 'versionName = "0.2.0"', text)
else:
    text = re.sub(r'versionName\s+["\'][^"\']+["\']', 'versionName "0.2.0"', text)

gradle.write_text(text, encoding="utf-8")

gradle_text = text
namespace = None
m = re.search(r'namespace\s*=\s*["\']([^"\']+)["\']', gradle_text)
if not m:
    m = re.search(r'namespace\s+["\']([^"\']+)["\']', gradle_text)
if m:
    namespace = m.group(1).strip()

if not namespace and manifest.exists():
    mt_read = manifest.read_text(encoding="utf-8")
    m = re.search(r'package\s*=\s*["\']([^"\']+)["\']', mt_read)
    if m:
        namespace = m.group(1).strip()

if not namespace:
    namespace = "com.swviaprime"
    t = gradle.read_text(encoding="utf-8")
    if "namespace" not in t:
        block = find_android_block(t)
        if block:
            s, e = block
            insert_at = t.find("{", s) + 1
            ns_line = f'\n    namespace = "{namespace}"\n' if is_kts else f'\n    namespace "{namespace}"\n'
            t = t[:insert_at] + ns_line + t[insert_at:]
            gradle.write_text(t, encoding="utf-8")

print("Namespace usado:", namespace)

for flavor, app_name in [("cliente", "Via Prime Cliente"), ("motorista", "Via Prime Motoristas")]:
    values = app / f"src/{flavor}/res/values"
    values.mkdir(parents=True, exist_ok=True)
    (values / "strings.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">{app_name}</string>
</resources>
''', encoding="utf-8")

main_values = app / "src/main/res/values"
main_values.mkdir(parents=True, exist_ok=True)
strings = main_values / "strings.xml"
if not strings.exists():
    strings.write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Via Prime</string>
</resources>
''', encoding="utf-8")
else:
    st = strings.read_text(encoding="utf-8")
    if "name=\"app_name\"" not in st:
        st = st.replace("</resources>", "    <string name=\"app_name\">Via Prime</string>\n</resources>")
        strings.write_text(st, encoding="utf-8")

if not manifest.exists():
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

    <application
        android:theme="@style/AppTheme"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
''', encoding="utf-8")

mt = manifest.read_text(encoding="utf-8")

def add_permission(mt, perm):
    if perm not in mt:
        pos = mt.find(">")
        if pos != -1:
            mt = mt[:pos+1] + f'\n    <uses-permission android:name="{perm}"/>' + mt[pos+1:]
    return mt

for perm in [
    "android.permission.INTERNET",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION"
]:
    mt = add_permission(mt, perm)

app_m = re.search(r'<application\b[^>]*>', mt, re.S)
if app_m:
    tag = app_m.group(0)
    def set_attr(tag, name, value):
        pattern = r'\s' + re.escape(name) + r'\s*=\s*"[^"]*"'
        if re.search(pattern, tag):
            return re.sub(pattern, f' {name}="{value}"', tag)
        return tag[:-1] + f' {name}="{value}">'
    new_tag = tag
    new_tag = set_attr(new_tag, "android:label", "@string/app_name")
    new_tag = set_attr(new_tag, "android:icon", "@mipmap/ic_launcher")
    new_tag = set_attr(new_tag, "android:roundIcon", "@mipmap/ic_launcher_round")
    new_tag = set_attr(new_tag, "android:usesCleartextTraffic", "true")
    mt = mt[:app_m.start()] + new_tag + mt[app_m.end():]
else:
    mt = mt.replace("</manifest>", '''
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:usesCleartextTraffic="true">
    </application>
</manifest>''')

launcher_match = None
for m in re.finditer(r'<activity\b.*?</activity>', mt, re.S):
    block_txt = m.group(0)
    if "android.intent.action.MAIN" in block_txt and "android.intent.category.LAUNCHER" in block_txt:
        launcher_match = m
        break

activity_block = '''<activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>'''

if launcher_match:
    blk = launcher_match.group(0)
    if re.search(r'android:name\s*=', blk):
        blk = re.sub(r'android:name\s*=\s*"[^"]*"', 'android:name=".MainActivity"', blk, count=1)
    else:
        blk = blk.replace("<activity", '<activity android:name=".MainActivity"', 1)
    if re.search(r'android:exported\s*=', blk):
        blk = re.sub(r'android:exported\s*=\s*"[^"]*"', 'android:exported="true"', blk, count=1)
    else:
        blk = blk.replace("<activity", '<activity android:exported="true"', 1)
    mt = mt[:launcher_match.start()] + blk + mt[launcher_match.end():]
else:
    mt = mt.replace("</application>", "        " + activity_block + "\n    </application>")

manifest.write_text(mt, encoding="utf-8")

for srcroot in [app / "src/main/java", app / "src/main/kotlin"]:
    if srcroot.exists():
        for p in srcroot.rglob("MainActivity.*"):
            if p.suffix in [".java", ".kt"]:
                backup = p.with_suffix(p.suffix + ".bak_v020")
                if not backup.exists():
                    p.rename(backup)
                    print("Backup MainActivity antigo:", backup)

template = (script_dir / "MainActivity.java.template").read_text(encoding="utf-8")
java_dir = app / "src/main/java" / Path(namespace.replace(".", "/"))
java_dir.mkdir(parents=True, exist_ok=True)
java = template.replace("__PKG__", namespace)
(java_dir / "MainActivity.java").write_text(java, encoding="utf-8")
print("OK: MainActivity.java criado em", java_dir / "MainActivity.java")

styles = main_values / "styles.xml"
if not styles.exists():
    styles.write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:colorAccent">#D6A231</item>
    </style>
</resources>
''', encoding="utf-8")
else:
    st = styles.read_text(encoding="utf-8")
    if "AppTheme" not in st:
        st = st.replace("</resources>", '''    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:colorAccent">#D6A231</item>
    </style>
</resources>''')
        styles.write_text(st, encoding="utf-8")

print("Patch concluído.")
PYCODE

echo ""
echo "Limpando e preparando build..."
if [ -x "./gradlew" ]; then
  chmod +x ./gradlew
  ./gradlew clean
  echo ""
  echo "Gerando APKs separados..."
  ./gradlew :app:assembleClienteDebug :app:assembleMotoristaDebug
else
  echo "ATENÇÃO: não encontrei ./gradlew executável."
  echo "Se o projeto usa Gradle instalado globalmente, rode:"
  echo "gradle :app:assembleClienteDebug :app:assembleMotoristaDebug"
fi

OUT_DIR="$ROOT/ViaPrime_v020_APKs"
mkdir -p "$OUT_DIR"
find "$APP_DIR/build/outputs/apk" -type f -name "*.apk" 2>/dev/null | while read -r apk; do
  case "$apk" in
    *cliente*|*Cliente*) cp "$apk" "$OUT_DIR/ViaPrimeCliente-v0.2.0-debug.apk" ;;
    *motorista*|*Motorista*) cp "$apk" "$OUT_DIR/ViaPrimeMotoristas-v0.2.0-debug.apk" ;;
  esac
done

echo ""
echo "=============================================="
echo "Concluído."
echo "APKs esperados em:"
echo "$OUT_DIR"
echo ""
ls -la "$OUT_DIR" 2>/dev/null || true
echo "=============================================="

if command -v git >/dev/null 2>&1 && [ -d ".git" ]; then
  git add app || true
  git commit -m "Via Prime v0.2.0 split apps premium base" || true
  echo "Commit local criado/tentado."
  echo "Para enviar ao GitHub: git push"
fi
