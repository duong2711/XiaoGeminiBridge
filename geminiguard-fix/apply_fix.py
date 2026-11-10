from pathlib import Path
root = Path("GeminiGuard/app/src/main/java/com/gemini/guard")
restorer = root / "Restorer.kt"
s = restorer.read_text(encoding="utf-8")
a = s.index("            // Khi nguoi dung tat Tro nang bang phim tat (acc_tile_off), khong tu bat lai o day")
b = s.index("\n        } catch (e: SecurityException)", a)
s = s[:a] + """            // SAFETY: Never auto-enable MiAiWatcher from background or boot.
            // User's Accessibility OFF choice must remain OFF.
            // Google assistant and power button are maintained independently.
""" + s[b:]
restorer.write_text(s, encoding="utf-8")
guard = root / "GuardService.kt"
s = guard.read_text(encoding="utf-8")
s = s.replace('        r.registerContentObserver(Settings.Secure.getUriFor(Restorer.ACC_KEY), false, observer)\n', '')
guard.write_text(s, encoding="utf-8")
tile = root / "AccTileService.kt"
s = tile.read_text(encoding="utf-8")
s = s.replace("        if (!Restorer.hasPermission(c)) Toast.makeText(c, msg, Toast.LENGTH_LONG).show()", "        Toast.makeText(c, msg, Toast.LENGTH_LONG).show()")
tile.write_text(s, encoding="utf-8")
build = Path("GeminiGuard/app/build.gradle.kts")
s = build.read_text(encoding="utf-8")
s = s.replace('versionName = "4.0"', 'versionName = "4.0.1-togglefix"')
build.write_text(s, encoding="utf-8")
print("Patched GeminiGuard switch / restorer / observer / UI.")
