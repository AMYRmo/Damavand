# Vault removal — remove the Vault tab, VaultScreen, VaultImportHost, the
# space-unlocked easter-egg entry point wiring, and the .zsx intent filter.
# Keep ZsxCrypto itself (SettingsBackup still seals exports with it).
import pathlib, re

R = pathlib.Path('/home/container/work')

def read(p): return (R / p).read_text()
def write(p, s): (R / p).write_text(s)

# 1) TopDestination: drop Vault entry + its imports
p = 'shared/src/commonMain/kotlin/com/amyrmo/damavand/ui/navigation/TopDestination.kt'
s = read(p)
s = s.replace('import com.amyrmo.damavand.shared.resources.ic_encrypted\n', '')
s = s.replace('import com.amyrmo.damavand.shared.resources.nav_locked\n', '')
s = s.replace('    Vault(Res.string.nav_locked, Res.drawable.ic_encrypted),\n', '')
write(p, s)
print('TopDestination: Vault removed')

# 2) MainScaffold: remove Vault wiring
p = 'shared/src/commonMain/kotlin/com/amyrmo/damavand/ui/MainScaffold.kt'
s = read(p)
s = s.replace('import com.amyrmo.damavand.ui.vault.VaultImportHost\n', '')
s = s.replace('import com.amyrmo.damavand.ui.vault.VaultScreen\n', '')
s = s.replace('''    VaultImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Vault },
    )

''', '')
s = s.replace('''                    onBrowseConfigs = {
                        current = if (activeProfile?.isLocked == true) TopDestination.Vault
                        else TopDestination.Servers
                    },
                    onSecretUnlocked = { spaceUnlocked = true },
''', '''                    onBrowseConfigs = { current = TopDestination.Servers },
''')
s = s.replace('''                TopDestination.Vault -> VaultScreen(
                    repository = configRepository,
                    contentPadding = innerPadding,
                    onImportRequested = onImportZsx,
                )
''', '')
s = s.replace('    onImportZsx: () -> Unit,\n', '')
s = re.sub(r'\n    var spaceUnlocked by rememberSaveable \{ mutableStateOf\(false\) \}\n', '\n', s)
s = s.replace('''    if (spaceUnlocked) {
        SpaceScreen(onExit = { spaceUnlocked = false })
    }

''', '')
write(p, s)
print('MainScaffold: Vault wiring removed')

# 3) MainActivity: drop onImportZsx param + VaultImportBus offer path stays inert? Remove.
p = 'app/src/main/java/com/amyrmo/damavand/MainActivity.kt'
s = read(p)
s = s.replace('                        onImportZsx = ::importZsx,\n', '')
# remove importZsx + openZsxLauncher + offerLockedConfig + VaultImportBus import
s = re.sub(r'\n    private val openZsxLauncher =\n(?:.*\n)*?        \}\n', '\n', s, count=1)
s = s.replace('''    private fun importZsx() {
        openZsxLauncher.launch(arrayOf("*/*"))
    }

''', '')
s = s.replace('import com.amyrmo.damavand.core.VaultImportBus\n', '')
s = re.sub(r'\n        VaultImportBus\.request\(bytes, meta\)\n', '\n', s)
write(p, s)
print('MainActivity: zsx import entry removed')

# 4) Manifest: drop the .zsx document filter (keep damavand:// etc.)
p = 'app/src/main/AndroidManifest.xml'
s = read(p)
lines = s.split('\n')
out = []
skip = 0
for i, ln in enumerate(lines):
    if skip > 0 and '</intent-filter>' not in ln:
        continue
    if skip > 0 and '</intent-filter>' in ln:
        skip = 0
        continue
    if '.zsx' in ln:
        # rewind to the <intent-filter containing it and drop through </intent-filter>
        j = len(out) - 1
        while j >= 0 and '<intent-filter' not in out[j]:
            j -= 1
        del out[j:]
        skip = 1
        continue
    out.append(ln)
s = '\n'.join(out)
write(p, s)
print('Manifest: .zsx filter removed')

# 5) delete vault UI dir
import shutil
shutil.rmtree(R / 'shared/src/commonMain/kotlin/com/amyrmo/damavand/ui/vault', ignore_errors=True)
print('ui/vault/ deleted')

# 6) remaining references?
import subprocess
